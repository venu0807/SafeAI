"""
SafeGuard AI - GPU Configuration Manager
Unified GPU setup for TensorFlow and PyTorch across all scripts
"""

import os
import torch
import tensorflow as tf
import logging

# Suppress verbose TensorFlow logging
tf.get_logger().setLevel(logging.ERROR)
os.environ['TF_CPP_MIN_LOG_LEVEL'] = '3'

class GPUManager:
    """Centralized GPU management for the project"""
    
    @staticmethod
    def setup_tensorflow_gpu(memory_fraction=0.8):
        """Setup TensorFlow GPU with memory growth"""
        gpus = tf.config.list_physical_devices('GPU')
        
        if gpus:
            try:
                # Allow memory growth to avoid OOM
                for gpu in gpus:
                    tf.config.experimental.set_memory_growth(gpu, True)
                
                # Optional: set GPU memory fraction
                # tf.config.set_logical_device_configuration(
                #     gpus[0],
                #     [tf.config.LogicalDeviceConfiguration(memory_limit=int(gpus[0].memory_limit * memory_fraction))]
                # )
                
                print(f"OK TensorFlow GPU configured: {len(gpus)} GPU(s) detected")
                for i, gpu in enumerate(gpus):
                    print(f"   GPU {i}: {gpu.name}")
                return True
            except Exception as e:
                print(f"WARN TensorFlow GPU setup error: {e}")
                return False
        else:
            print("WARN No TensorFlow GPUs found")
            return False
    
    @staticmethod
    def setup_pytorch_gpu():
        """Setup PyTorch GPU"""
        if torch.cuda.is_available():
            print(f"\nOK PyTorch CUDA available")
            print(f"   CUDA version: {torch.version.cuda}")
            print(f"   GPU count: {torch.cuda.device_count()}")
            for i in range(torch.cuda.device_count()):
                print(f"   GPU {i}: {torch.cuda.get_device_name(i)}")
                props = torch.cuda.get_device_properties(i)
                print(f"      Memory: {props.total_memory / 1e9:.2f} GB")
            
            # Set default device
            torch.cuda.set_device(0)
            print(f"   Default device set to GPU 0")
            return True
        else:
            print("\nWARN PyTorch CUDA not available")
            print("   Install with: pip install --upgrade torch --index-url https://download.pytorch.org/whl/cu118")
            return False
    
    @staticmethod
    def get_device(framework='torch'):
        """Get the appropriate device for training"""
        if framework == 'torch':
            return torch.device('cuda' if torch.cuda.is_available() else 'cpu')
        elif framework == 'tf':
            gpus = tf.config.list_physical_devices('GPU')
            return '/GPU:0' if gpus else '/CPU:0'
    
    @staticmethod
    def verify_all():
        """Verify GPU setup for entire project"""
        print("\n" + "=" * 70)
        print("SAFEGUARD AI - GPU VERIFICATION")
        print("=" * 70 + "\n")
        
        tf_ok = GPUManager.setup_tensorflow_gpu()
        pt_ok = GPUManager.setup_pytorch_gpu()
        
        print("\n" + "=" * 70)
        if tf_ok and pt_ok:
            print("OK GPU READY FOR FULL PROJECT")
        elif tf_ok or pt_ok:
            print("WARN PARTIAL GPU SETUP - Some frameworks may use CPU")
        else:
            print("ERROR NO GPU AVAILABLE - Using CPU (slower)")
        print("=" * 70 + "\n")
        
        return tf_ok or pt_ok


if __name__ == '__main__':
    GPUManager.verify_all()
