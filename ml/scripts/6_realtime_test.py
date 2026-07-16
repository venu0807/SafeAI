"""
SafeGuard AI - Real-Time Audio Testing
Test model with live microphone input
"""

import os
import sys
import numpy as np
import pyaudio
import wave
import tensorflow as tf
from tensorflow import keras
import threading
import time

# Add parent directory to path
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from config import *

# Import preprocessing from script 2
sys.path.append(os.path.join(os.path.dirname(__file__)))
from preprocessing_utils import extract_mfcc_features

class RealtimeAudioDetector:
    """Real-time audio threat detector"""
    
    def __init__(self, model_path=MODEL_H5):
        """Initialize detector"""
        print("🎤 Initializing Real-Time Audio Detector...")
        
        # Load model
        self.model = keras.models.load_model(model_path)
        print(f"   ✅ Model loaded from: {model_path}")
        
        # Audio parameters
        self.sample_rate = SAMPLE_RATE
        self.chunk_duration = DURATION
        self.chunk_size = int(self.sample_rate * self.chunk_duration)
        
        # PyAudio
        self.audio = pyaudio.PyAudio()
        self.stream = None
        
        # State
        self.is_running = False
        self.detection_count = 0
        self.threat_count = 0
        
        print("   ✅ Detector initialized")
    
    def start(self):
        """Start audio monitoring"""
        print("\n" + "=" * 60)
        print("🎙️  STARTING REAL-TIME AUDIO MONITORING")
        print("=" * 60)
        print(f"Sample Rate: {self.sample_rate} Hz")
        print(f"Chunk Duration: {self.chunk_duration} s")
        print(f"Threat Threshold: {THREAT_THRESHOLD*100}%")
        print("=" * 60)
        print("\n🔴 Recording... (Press Ctrl+C to stop)")
        
        # Open audio stream
        self.stream = self.audio.open(
            format=pyaudio.paInt16,
            channels=1,
            rate=self.sample_rate,
            input=True,
            frames_per_buffer=self.chunk_size,
            stream_callback=self._audio_callback
        )
        
        self.is_running = True
        self.stream.start_stream()
        
        try:
            while self.is_running:
                time.sleep(0.1)
        except KeyboardInterrupt:
            print("\n\n⏹️  Stopping monitoring...")
            self.stop()
    
    def stop(self):
        """Stop audio monitoring"""
        self.is_running = False
        
        if self.stream:
            self.stream.stop_stream()
            self.stream.close()
        
        self.audio.terminate()
        
        print("\n" + "=" * 60)
        print("📊 SESSION SUMMARY")
        print("=" * 60)
        print(f"Total Detections: {self.detection_count}")
        print(f"Threat Alerts: {self.threat_count}")
        print("=" * 60)
        print("✅ Monitoring stopped")
    
    def _audio_callback(self, in_data, frame_count, time_info, status):
        """Audio stream callback"""
        if not self.is_running:
            return (in_data, pyaudio.paComplete)
        
        # Convert bytes to numpy array
        audio_data = np.frombuffer(in_data, dtype=np.int16)
        
        # Process in separate thread to avoid blocking
        threading.Thread(target=self._process_audio, args=(audio_data,)).start()
        
        return (in_data, pyaudio.paContinue)
    
    def _process_audio(self, audio_data):
        """Process audio chunk"""
        try:
            self.detection_count += 1
            
            # Extract MFCC features
            mfcc = self._extract_mfcc(audio_data)
            
            if mfcc is None:
                return
            
            # Reshape for model
            mfcc_input = mfcc[np.newaxis, ..., np.newaxis]
            
            # Predict
            start_time = time.time()
            prediction = self.model.predict(mfcc_input, verbose=0)
            inference_time = (time.time() - start_time) * 1000
            
            normal_prob = prediction[0][0]
            distress_prob = prediction[0][1]
            
            # Print results
            timestamp = time.strftime("%H:%M:%S")
            
            if distress_prob > THREAT_THRESHOLD:
                self.threat_count += 1
                print(f"\n🚨 [{timestamp}] THREAT DETECTED!")
                print(f"   Distress: {distress_prob*100:.1f}% | Normal: {normal_prob*100:.1f}%")
                print(f"   Inference: {inference_time:.1f}ms")
                print(f"   🔔 Alert #{self.threat_count}")
            else:
                # Show periodic updates
                if self.detection_count % 10 == 0:
                    print(f"✅ [{timestamp}] Monitoring... (Checks: {self.detection_count}, Threats: {self.threat_count})")
        
        except Exception as e:
            print(f"❌ Error processing audio: {e}")
    
    def _extract_mfcc(self, audio_data):
        """Extract MFCC from audio data"""
        try:
            # Normalize
            audio_float = audio_data.astype(np.float32) / 32768.0
            
            # Save to temp file (librosa requires file)
            temp_file = "temp_audio.wav"
            
            with wave.open(temp_file, 'wb') as wf:
                wf.setnchannels(1)
                wf.setsampwidth(2)
                wf.setframerate(self.sample_rate)
                wf.writeframes(audio_data.tobytes())
            
            # Extract MFCC using same function as training
            import librosa
            
            audio, sr = librosa.load(temp_file, sr=self.sample_rate)
            
            # Ensure correct length
            if len(audio) < N_SAMPLES:
                audio = np.pad(audio, (0, N_SAMPLES - len(audio)))
            else:
                audio = audio[:N_SAMPLES]
            
            # Pre-emphasis
            audio = np.append(audio[0], audio[1:] - PRE_EMPHASIS * audio[:-1])
            
            # Extract MFCC
            mfcc = librosa.feature.mfcc(
                y=audio,
                sr=sr,
                n_mfcc=N_MFCC,
                n_fft=N_FFT,
                hop_length=HOP_LENGTH,
                n_mels=N_MELS
            )
            
            mfcc = mfcc.T
            
            # Normalize
            mfcc = (mfcc - np.mean(mfcc, axis=0)) / (np.std(mfcc, axis=0) + 1e-8)
            
            # Pad/truncate
            if mfcc.shape[0] < MAX_TIME_STEPS:
                pad_width = MAX_TIME_STEPS - mfcc.shape[0]
                mfcc = np.pad(mfcc, ((0, pad_width), (0, 0)), mode='constant')
            else:
                mfcc = mfcc[:MAX_TIME_STEPS, :]
            
            # Clean up
            if os.path.exists(temp_file):
                os.remove(temp_file)
            
            return mfcc
        
        except Exception as e:
            print(f"❌ MFCC extraction error: {e}")
            return None

def test_microphone():
    """Test with microphone input"""
    print("=" * 60)
    print("🎤 SAFEGUARD AI - REAL-TIME TESTING")
    print("=" * 60)
    
    # Check if model exists
    if not os.path.exists(MODEL_H5):
        print("❌ ERROR: Trained model not found!")
        print("   Run 3_train_mfcc_cnn.py first")
        return
    
    # Create detector
    detector = RealtimeAudioDetector(MODEL_H5)
    
    # Start monitoring
    detector.start()

if __name__ == "__main__":
    test_microphone()
