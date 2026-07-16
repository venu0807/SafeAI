#!/usr/bin/env python3
"""
SafeGuard AI - ML Pipeline Status Check
Verifies configuration and readiness to train
"""

import os
import sys
import json

# Colors for terminal output
class Colors:
    GREEN = '\033[92m'
    RED = '\033[91m'
    YELLOW = '\033[93m'
    BLUE = '\033[94m'
    RESET = '\033[0m'
    BOLD = '\033[1m'

def check_file_exists(path, name):
    """Check if file exists"""
    if os.path.exists(path):
        print(f"{Colors.GREEN}✓{Colors.RESET} {name}: {path}")
        return True
    else:
        print(f"{Colors.RED}✗{Colors.RESET} {name}: {path} (NOT FOUND)")
        return False

def check_directory_exists(path, name):
    """Check if directory exists"""
    if os.path.isdir(path):
        count = len([f for f in os.listdir(path) if os.path.isfile(os.path.join(path, f))])
        print(f"{Colors.GREEN}✓{Colors.RESET} {name}: {path} ({count} files)")
        return True
    else:
        print(f"{Colors.RED}✗{Colors.RESET} {name}: {path} (NOT FOUND)")
        return False

def check_config():
    """Check configuration file"""
    config_file = os.path.join(os.path.dirname(__file__), 'config.py')
    
    print(f"\n{Colors.BOLD}{Colors.BLUE}Checking config.py...{Colors.RESET}")
    
    checks = []
    try:
        with open(config_file, 'r', encoding='utf-8') as f:
            content = f.read()
            
            # Critical checks
            has_spectral_sub = 'USE_SPECTRAL_SUBTRACTION' in content and 'SPECTRAL_FLOOR' in content
            has_threshold = "THREAT_THRESHOLD = 0.45" in content
            has_preemphasis = 'PRE_EMPHASIS' in content
            has_mfcc = 'N_MFCC = 40' in content
            has_fft = 'N_FFT = 2048' in content
            has_hop = 'HOP_LENGTH = 512' in content
            has_timesteps = 'MAX_TIME_STEPS = 100' in content
            has_random_seed = 'RANDOM_SEED' in content
            has_class_weights = 'USE_CLASS_WEIGHTS' in content
            
            checks = [
                ('Spectral Subtraction Config', has_spectral_sub),
                ('Detection Threshold 0.45', has_threshold),
                ('Pre-emphasis Coefficient', has_preemphasis),
                ('MFCC=40', has_mfcc),
                ('FFT=2048', has_fft),
                ('HOP_LENGTH=512', has_hop),
                ('MAX_TIME_STEPS=100', has_timesteps),
                ('Random Seed', has_random_seed),
                ('Class Weights', has_class_weights)
            ]
    except Exception as e:
        print(f"{Colors.RED}✗ Error reading config.py: {e}{Colors.RESET}")
        return False
    
    all_ok = True
    for check_name, result in checks:
        status = f"{Colors.GREEN}✓{Colors.RESET}" if result else f"{Colors.RED}✗{Colors.RESET}"
        print(f"  {status} {check_name}")
        all_ok = all_ok and result
    
    return all_ok

def check_preprocessing():
    """Check preprocessing script"""
    script_file = os.path.join(os.path.dirname(__file__), 'scripts', '2_preprocess_audio.py')
    
    print(f"\n{Colors.BOLD}{Colors.BLUE}Checking 2_preprocess_audio.py...{Colors.RESET}")
    
    checks = []
    try:
        with open(script_file, 'r', encoding='utf-8') as f:
            content = f.read()
            
            has_spectral_func = 'def spectral_subtraction_denoise' in content
            has_vad = 'def voice_activity_detection' in content
            has_preemphasis = 'PRE_EMPHASIS * audio[:-1]' in content
            has_normalization = 'np.mean(mfcc, axis=0' in content
            has_mfcc_shape_check = '(target_length, N_MFCC)' in content
            has_research_comments = 'RESEARCH PAPER' in content or 'research paper' in content
            
            checks = [
                ('Spectral Subtraction Function', has_spectral_func),
                ('Voice Activity Detection', has_vad),
                ('Pre-emphasis Implementation', has_preemphasis),
                ('Per-Feature Normalization', has_normalization),
                ('Output Shape Verification', has_mfcc_shape_check),
                ('Research Paper Compliance', has_research_comments)
            ]
    except Exception as e:
        print(f"{Colors.RED}✗ Error reading 2_preprocess_audio.py: {e}{Colors.RESET}")
        return False
    
    all_ok = True
    for check_name, result in checks:
        status = f"{Colors.GREEN}✓{Colors.RESET}" if result else f"{Colors.RED}✗{Colors.RESET}"
        print(f"  {status} {check_name}")
        all_ok = all_ok and result
    
    return all_ok

def check_training():
    """Check training script"""
    script_file = os.path.join(os.path.dirname(__file__), 'scripts', '3_train_mfcc_cnn.py')
    
    print(f"\n{Colors.BOLD}{Colors.BLUE}Checking 3_train_mfcc_cnn.py...{Colors.RESET}")
    
    checks = []
    try:
        with open(script_file, 'r', encoding='utf-8') as f:
            content = f.read()
            
            has_metrics = 'Precision' in content and 'Recall' in content
            has_class_weights = 'class_weight.compute_class_weight' in content
            has_f1 = 'F1-Score' in content or 'f1_score' in content
            has_confusion = 'confusion_matrix' in content or 'Confusion Matrix' in content
            has_plots = 'plot_training_history' in content
            has_detailed_eval = 'classification_report' in content
            has_callbacks = 'EarlyStopping' in content and 'ModelCheckpoint' in content
            has_validation = 'evaluate' in content or 'test_loss' in content
            
            checks = [
                ('Advanced Metrics (Precision, Recall)', has_metrics),
                ('Class Weight Computation', has_class_weights),
                ('F1-Score Calculation', has_f1),
                ('Confusion Matrix', has_confusion),
                ('Training Curves Plotting', has_plots),
                ('Detailed Classification Report', has_detailed_eval),
                ('Advanced Callbacks', has_callbacks),
                ('Comprehensive Evaluation', has_validation)
            ]
    except Exception as e:
        print(f"{Colors.RED}✗ Error reading 3_train_mfcc_cnn.py: {e}{Colors.RESET}")
        return False
    
    all_ok = True
    for check_name, result in checks:
        status = f"{Colors.GREEN}✓{Colors.RESET}" if result else f"{Colors.RED}✗{Colors.RESET}"
        print(f"  {status} {check_name}")
        all_ok = all_ok and result
    
    return all_ok

def check_data_structure():
    """Check data directory structure"""
    base_dir = os.path.dirname(__file__)
    
    print(f"\n{Colors.BOLD}{Colors.BLUE}Checking Data Structure...{Colors.RESET}")
    
    # Check directories
    checks = []
    
    distress_dir = os.path.join(base_dir, 'processed_data', 'distress')
    normal_dir = os.path.join(base_dir, 'processed_data', 'normal')
    models_dir = os.path.join(base_dir, 'models')
    results_dir = os.path.join(base_dir, 'results')
    
    check_directory_exists(distress_dir, 'Distress audio directory')
    check_directory_exists(normal_dir, 'Normal audio directory')
    
    # Check processed data
    print(f"\n{Colors.BOLD}Processed Data Files:{Colors.RESET}")
    processed_dir = os.path.join(base_dir, 'processed_data')
    
    if os.path.isdir(processed_dir):
        metadata_csv = os.path.join(processed_dir, 'metadata.csv')
        mfcc_npy = os.path.join(processed_dir, 'mfcc_features.npy')
        labels_npy = os.path.join(processed_dir, 'labels.npy')
        
        check_file_exists(metadata_csv, 'metadata.csv')
        check_file_exists(mfcc_npy, 'mfcc_features.npy')
        check_file_exists(labels_npy, 'labels.npy')
    
    # Check models
    print(f"\n{Colors.BOLD}Model Output Directory:{Colors.RESET}")
    check_directory_exists(models_dir, 'Models directory')
    
    # Check results
    print(f"\n{Colors.BOLD}Results Output Directory:{Colors.RESET}")
    check_directory_exists(results_dir, 'Results directory')

def main():
    """Main status check"""
    print(f"\n{Colors.BOLD}{Colors.BLUE}{'='*70}{Colors.RESET}")
    print(f"{Colors.BOLD}{Colors.BLUE}SafeGuard AI - ML Pipeline Status Check{Colors.RESET}")
    print(f"{Colors.BOLD}{Colors.BLUE}{'='*70}{Colors.RESET}")
    
    # Check all components
    config_ok = check_config()
    preprocess_ok = check_preprocessing()
    training_ok = check_training()
    
    check_data_structure()
    
    # Summary
    print(f"\n{Colors.BOLD}{Colors.BLUE}{'='*70}{Colors.RESET}")
    print(f"{Colors.BOLD}{Colors.BLUE}Summary:{Colors.RESET}")
    print(f"{Colors.BOLD}{Colors.BLUE}{'='*70}{Colors.RESET}")
    
    if config_ok and preprocess_ok and training_ok:
        print(f"\n{Colors.GREEN}{Colors.BOLD}✓ ML PIPELINE READY!{Colors.RESET}")
        print(f"\n{Colors.BOLD}Next steps:{Colors.RESET}")
        print(f"1. Ensure audio files are in: {Colors.BLUE}processed_data/distress/{Colors.RESET} and {Colors.BLUE}processed_data/normal/{Colors.RESET}")
        print(f"2. Run: {Colors.BLUE}python scripts/1_organize_datasets.py{Colors.RESET}")
        print(f"3. Run: {Colors.BLUE}python scripts/2_preprocess_audio.py{Colors.RESET}")
        print(f"4. Run: {Colors.BLUE}python scripts/3_train_mfcc_cnn.py{Colors.RESET}")
        print(f"5. Run: {Colors.BLUE}python scripts/5_export_tflite.py{Colors.RESET} (for Android)")
        return 0
    else:
        print(f"\n{Colors.RED}{Colors.BOLD}✗ Issues detected!{Colors.RESET}")
        print(f"Please review the checks above and fix any marked with {Colors.RED}✗{Colors.RESET}")
        return 1

if __name__ == "__main__":
    sys.exit(main())
