"""
Keystroke Biometric Authentication Engine
==========================================
Core analytics modules for the Continuous Behavioral Biometric Authentication System.

Pipeline:
  KeystrokeEvent → FeatureExtractor → MahalanobisNormalizer → OCSVMModel → RiskScorer → Logger
"""
