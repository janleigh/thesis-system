# thesis-system

**Full Name**: *Development of a Continuous Behavioral Biometric Authentication System Using One-Class Support Vector Machine and Mahalanobis Distance for Mobile Device Keystroke Dynamics*

## Quick Start (Python Prototype)

```bash
# 1. Install dependencies
pip install -r requirements.txt

# 2. Run the full automated test suite (thanks nes)
pytest tests/ -v

# 3. Enroll yourself (50 keystrokes in testing mode)
python -m cli.enroll --user test --test

# 4. Run a live verification session
python -m cli.verify --user test

# 5. Run the evaluation report (FAR / FRR / EER)
python -m cli.evaluate

# 6. Inspect the encrypted security log
python -m cli.inspect_log --user test
```

## Python Pipeline Design

```
Keystroke Events
      │
      ▼
FeatureExtractor          → dwell_time, flight_time, IKI (per key, per window)
      │
      ▼
[6-d Stat Vector]         → [dwell_mean, dwell_std, flight_mean, flight_std, iki_mean, iki_std]
      │
      ├──▶ OCSVMModel.anomaly_score()     → score_ocsvm ∈ [0,1]
      │
      └──▶ MahalanobisNormalizer.normalized_score() → score_mahal ∈ [0,1]
                │
                ▼
          RiskScorer.score()
          composite = 0.6 × score_ocsvm + 0.4 × score_mahal
                │
                ▼
          RiskDecision
          ├── ALLOW         (< 0.35)
          ├── WARN          (0.35 – 0.60)
          ├── STEP_UP_AUTH  (0.60 – 0.80)
          └── BLOCK_SESSION (≥ 0.80)
                │
                ▼
          SecurityLogger → Fernet-encrypted SQLite
```