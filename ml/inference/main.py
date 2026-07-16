"""
SafeguardAI — FastAPI Inference Server
Production-ready ML inference with health checks, Prometheus metrics, and model registry.
"""

import os
import time
import logging
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Optional

import uvicorn
from fastapi import FastAPI, HTTPException, Request, Depends
from fastapi.responses import JSONResponse, Response
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from prometheus_client import Counter, Histogram, Gauge, generate_latest, CONTENT_TYPE_LATEST
import numpy as np

# ── Configuration ──────────────────────────────────────────────────────────────
MODEL_DIR = Path(os.environ.get("MODEL_DIR", "/home/venu/Proposals/SafeguardAI/ml/models"))
DEFAULT_MODEL = "audio_mfcc_cnn.tflite"

# ── Logging ───────────────────────────────────────────────────────────────────
logging.basicConfig(
    level=os.environ.get("LOG_LEVEL", "INFO"),
    format="{ \"time\": \"%(asctime)s\", \"level\": \"%(levelname)s\", \"logger\": \"%(name)s\", \"message\": \"%(message)s\" }",
)
logger = logging.getLogger("safeguardai.inference")

# ── Prometheus Metrics ────────────────────────────────────────────────────────
REQUEST_COUNT = Counter(
    "safeguardai_inference_requests_total",
    "Total inference requests",
    ["endpoint", "status"],
)
REQUEST_LATENCY = Histogram(
    "safeguardai_inference_latency_seconds",
    "Inference request latency",
    ["endpoint"],
)
MODEL_LOADED = Gauge(
    "safeguardai_model_loaded",
    "Whether model is loaded (1) or not (0)",
)
MODEL_VERSION = Gauge(
    "safeguardai_model_version",
    "Model version info",
    ["version", "path"],
)

# ── Model Registry ────────────────────────────────────────────────────────────
class ModelRegistry:
    """Simple file-based model registry with version tracking."""

    def __init__(self, model_dir: Path):
        self.model_dir = model_dir
        self.models = {}
        self.current_model: Optional[str] = None
        self._scan_models()

    def _scan_models(self):
        """Scan model directory for available models."""
        for model_file in self.model_dir.glob("*.tflite"):
            stat = model_file.stat()
            version = f"v{int(stat.st_mtime)}"
            self.models[model_file.name] = {
                "path": model_file,
                "version": version,
                "size_bytes": stat.st_size,
                "modified": stat.st_mtime,
            }
            logger.info("Discovered model: %s (%s)", model_file.name, version)

        if DEFAULT_MODEL in self.models:
            self.current_model = DEFAULT_MODEL
            MODEL_LOADED.set(1)
            MODEL_VERSION.labels(version=self.models[DEFAULT_MODEL]["version"], path=str(self.models[DEFAULT_MODEL]["path"])).set(1)
        elif self.models:
            self.current_model = next(iter(self.models))
            MODEL_LOADED.set(1)
            m = self.models[self.current_model]
            MODEL_VERSION.labels(version=m["version"], path=str(m["path"])).set(1)
            logger.info("Using default model: %s", self.current_model)
        else:
            MODEL_LOADED.set(0)
            logger.warning("No models found in %s", self.model_dir)

    def get_model_path(self) -> Optional[Path]:
        if self.current_model and self.current_model in self.models:
            return self.models[self.current_model]["path"]
        return None

    def switch_model(self, model_name: str) -> bool:
        if model_name in self.models:
            self.current_model = model_name
            m = self.models[model_name]
            MODEL_VERSION.labels(version=m["version"], path=str(m["path"])).set(1)
            logger.info("Switched to model: %s", model_name)
            return True
        return False

    def list_models(self) -> list[dict]:
        return [
            {"name": name, **info}
            for name, info in self.models.items()
        ]


# ── Interpreter Pool (Thread-safe TFLite) ─────────────────────────────────────
import threading
from collections import deque

class InterpreterPool:
    """Thread-safe pool of TFLite interpreters for concurrent inference."""

    def __init__(self, model_path: Path, pool_size: int = 4):
        self.model_path = model_path
        self.pool_size = pool_size
        self._pool = deque()
        self._lock = threading.Lock()
        self._init_pool()

    def _init_pool(self):
        import tflite_runtime.interpreter as tflite
        for _ in range(self.pool_size):
            interpreter = tflite.Interpreter(model_path=str(self.model_path))
            interpreter.allocate_tensors()
            self._pool.append(interpreter)
        logger.info("Initialized interpreter pool with %d interpreters", self.pool_size)

    def acquire(self):
        with self._lock:
            if self._pool:
                return self._pool.popleft()
        import tflite_runtime.interpreter as tflite
        interpreter = tflite.Interpreter(model_path=str(self.model_path))
        interpreter.allocate_tensors()
        return interpreter

    def release(self, interpreter):
        with self._lock:
            if len(self._pool) < self.pool_size:
                self._pool.append(interpreter)


# ── Lifespan ──────────────────────────────────────────────────────────────────
registry = ModelRegistry(MODEL_DIR)
interpreter_pool: Optional[InterpreterPool] = None


def get_interpreter_pool() -> InterpreterPool:
    global interpreter_pool
    if interpreter_pool is None:
        model_path = registry.get_model_path()
        if model_path is None:
            raise HTTPException(status_code=503, detail="No model loaded")
        interpreter_pool = InterpreterPool(model_path)
    return interpreter_pool


@asynccontextmanager
async def lifespan(app: FastAPI):
    global interpreter_pool
    logger.info("Starting SafeguardAI inference server")
    model_path = registry.get_model_path()
    if model_path:
        interpreter_pool = InterpreterPool(model_path)
        logger.info("Model loaded: %s", model_path)
    else:
        logger.warning("No model available at startup")
    yield
    logger.info("Shutting down SafeguardAI inference server")
    interpreter_pool = None


# ── FastAPI App ───────────────────────────────────────────────────────────────
app = FastAPI(
    title="SafeguardAI Inference API",
    version="1.0.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=os.environ.get("CORS_ORIGINS", "*").split(","),
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# ── Request/Response Models ───────────────────────────────────────────────────
class AudioFeatures(BaseModel):
    """MFCC features for audio classification."""
    mfcc: list[list[float]] = Field(..., description="MFCC features [time_steps, n_mfcc]")
    sample_rate: int = Field(default=16000, description="Audio sample rate")


class PredictionResponse(BaseModel):
    """Inference response."""
    prediction: str = Field(..., description="Predicted class: 'normal' or 'distress'")
    confidence: float = Field(..., description="Confidence score 0-1")
    inference_time_ms: float = Field(..., description="Inference time in milliseconds")
    model_version: str = Field(..., description="Model version used")


class HealthResponse(BaseModel):
    """Health check response."""
    status: str
    model_loaded: bool
    current_model: Optional[str] = None
    available_models: list[str] = []


class ModelSwitchRequest(BaseModel):
    model_name: str


# ── Middleware ────────────────────────────────────────────────────────────────
@app.middleware("http")
async def metrics_middleware(request: Request, call_next):
    start = time.time()
    response = await call_next(request)
    REQUEST_COUNT.labels(endpoint=request.url.path, status=response.status_code).inc()
    REQUEST_LATENCY.labels(endpoint=request.url.path).observe(time.time() - start)
    return response


# ── Routes ────────────────────────────────────────────────────────────────────
@app.get("/health", response_model=HealthResponse)
async def health_check():
    """Health check endpoint for K8s/load balancer."""
    model_path = registry.get_model_path()
    return HealthResponse(
        status="healthy" if model_path else "degraded",
        model_loaded=model_path is not None,
        current_model=registry.current_model,
        available_models=list(registry.models.keys()),
    )


@app.get("/ready")
async def readiness_check():
    """Readiness probe - model must be loaded."""
    if registry.get_model_path() is None:
        raise HTTPException(status_code=503, detail="Model not loaded")
    return {"status": "ready"}


@app.get("/metrics")
async def metrics():
    """Prometheus metrics endpoint."""
    return Response(content=generate_latest(), media_type=CONTENT_TYPE_LATEST)


@app.get("/models", response_model=list[dict])
async def list_models():
    """List all available models in registry."""
    return registry.list_models()


@app.post("/models/switch", response_model=HealthResponse)
async def switch_model(req: ModelSwitchRequest):
    """Switch active model (hot-swap)."""
    global interpreter_pool
    if registry.switch_model(req.model_name):
        interpreter_pool = None  # Force re-initialization on next request
        model_path = registry.get_model_path()
        interpreter_pool = InterpreterPool(model_path)
        return await health_check()
    raise HTTPException(status_code=404, detail=f"Model {req.model_name} not found in registry")


@app.post("/predict", response_model=PredictionResponse)
async def predict(features: AudioFeatures, pool: InterpreterPool = Depends(get_interpreter_pool)):
    """Run inference on MFCC features."""
    start = time.time()

    # Validate input shape: [100, 40]
    mfcc = np.array(features.mfcc, dtype=np.float32)
    if mfcc.shape != (100, 40):
        raise HTTPException(
            status_code=400,
            detail=f"Invalid input shape: expected (100, 40), got {mfcc.shape}"
        )

    # Add batch dimension: [1, 100, 40, 1]
    input_data = mfcc[np.newaxis, ..., np.newaxis]

    interpreter = pool.acquire()
    try:
        input_details = interpreter.get_input_details()
        output_details = interpreter.get_output_details()

        interpreter.set_tensor(input_details[0]['index'], input_data)
        interpreter.invoke()
        output = interpreter.get_tensor(output_details[0]['index'])
    finally:
        pool.release(interpreter)

    inference_time = (time.time() - start) * 1000

    # Output: [normal_prob, distress_prob]
    distress_prob = float(output[0][1])
    prediction = "distress" if distress_prob > 0.5 else "normal"
    confidence = distress_prob if prediction == "distress" else 1.0 - distress_prob

    model_info = registry.models.get(registry.current_model, {})
    return PredictionResponse(
        prediction=prediction,
        confidence=confidence,
        inference_time_ms=inference_time,
        model_version=model_info.get("version", "unknown"),
    )


# ── Entrypoint ────────────────────────────────────────────────────────────────
if __name__ == "__main__":
    port = int(os.environ.get("PORT", "8000"))
    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=port,
        workers=int(os.environ.get("WORKERS", "1")),
        log_config=None,
    )