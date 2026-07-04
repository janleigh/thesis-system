import os
import tempfile

import numpy as np
import pytest

from engine.feature_extractor import FeatureExtractor, FeatureVector
from engine.profile_manager import ProfileManager, TESTING_MIN_KEYSTROKES

# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------
@pytest.fixture
def pm(tmp_path):
    # ProfileManager in testing mode with isolated temp directories.
    return ProfileManager(
        db_path=str(tmp_path / "profiles.db"),
        model_dir=str(tmp_path / "models"),
        testing_mode=True,
    )

def _make_valid_fv(
    dwell_mean=80.0, flight_mean=120.0, iki_mean=100.0,
    n_keystrokes=30
) -> FeatureVector:
    # Build a FeatureVector with realistic stats and enough keystrokes.
    rng = np.random.default_rng(42)
    fv = FeatureVector(
        dwell_times=rng.normal(dwell_mean, 15, n_keystrokes).tolist(),
        flight_times=rng.normal(flight_mean, 30, n_keystrokes - 1).tolist(),
        iki_times=rng.normal(iki_mean, 20, n_keystrokes - 1).tolist(),
        keystroke_count=n_keystrokes,
    )
    fv.dwell_mean  = float(np.mean(fv.dwell_times))
    fv.dwell_std   = float(np.std(fv.dwell_times))
    fv.flight_mean = float(np.mean(fv.flight_times))
    fv.flight_std  = float(np.std(fv.flight_times))
    fv.iki_mean    = float(np.mean(fv.iki_times))
    fv.iki_std     = float(np.std(fv.iki_times))
    return fv

def _enroll_until_ready(pm, user_id, n_windows=3, ks_per_window=20) -> None:
    # Enroll enough windows to push a user past the testing minimum.
    for i in range(n_windows):
        fv = _make_valid_fv(
            dwell_mean=80 + i,
            n_keystrokes=ks_per_window,
        )
        pm.add_enrollment_window(user_id, fv)

# ---------------------------------------------------------------------------
# Profile creation
# ---------------------------------------------------------------------------
class TestProfileCreation:
    def test_profile_created_on_first_window(self, pm):
        fv = _make_valid_fv()
        pm.add_enrollment_window("alice", fv)
        profile = pm.get_profile("alice")
        assert profile is not None
        assert profile.user_id == "alice"

    def test_status_pending_below_minimum(self, pm):
        fv = _make_valid_fv(n_keystrokes=5)
        pm.add_enrollment_window("alice", fv)
        profile = pm.get_profile("alice")
        assert profile.status == "PENDING"

    def test_keystroke_count_accumulates(self, pm):
        for _ in range(3):
            fv = _make_valid_fv(n_keystrokes=10)
            pm.add_enrollment_window("alice", fv)
        profile = pm.get_profile("alice")
        assert profile.keystroke_count == 30

    def test_profile_not_found_returns_none(self, pm):
        result = pm.get_profile("nonexistent_user")
        assert result is None

# ---------------------------------------------------------------------------
# Testing mode / minimum enforcement
# ---------------------------------------------------------------------------
class TestMinimumEnforcement:
    def test_testing_mode_uses_reduced_minimum(self, pm):
        assert pm.min_ks == TESTING_MIN_KEYSTROKES  # 50

    def test_status_ready_after_minimum_reached(self, pm):
        # Need at least 50 keystrokes AND ≥ 5 windows to fit models
        for _ in range(5):
            fv = _make_valid_fv(n_keystrokes=12)
            pm.add_enrollment_window("alice", fv)
        profile = pm.get_profile("alice")
        assert profile.status == "READY"

    def test_status_pending_when_not_enough_windows(self, pm):
        # Even with enough keystrokes, model needs ≥ 5 windows
        fv = _make_valid_fv(n_keystrokes=60)  # Above threshold but only 1 window
        pm.add_enrollment_window("alice", fv)
        profile = pm.get_profile("alice")
        # Only 1 window → can't fit models
        assert profile.status == "PENDING"

# ---------------------------------------------------------------------------
# Progress
# ---------------------------------------------------------------------------
class TestProgress:
    def test_progress_zero_on_new_profile(self, pm):
        fv = _make_valid_fv(n_keystrokes=1)
        profile = pm.add_enrollment_window("alice", fv)
        assert 0.0 <= profile.progress_pct <= 100.0

    def test_progress_100_when_ready(self, pm):
        for _ in range(5):
            fv = _make_valid_fv(n_keystrokes=12)
            profile = pm.add_enrollment_window("alice", fv)
        assert profile.progress_pct == 100.0

# ---------------------------------------------------------------------------
# Model files
# ---------------------------------------------------------------------------
class TestModelFiles:
    def test_models_saved_on_ready(self, pm, tmp_path):
        for _ in range(5):
            fv = _make_valid_fv(n_keystrokes=12)
            pm.add_enrollment_window("alice", fv)

        ocsvm = pm.get_ocsvm("alice")
        mahal = pm.get_mahalanobis("alice")
        assert ocsvm.is_fitted_
        assert mahal.is_fitted_

    def test_ocsvm_not_found_raises(self, pm):
        from engine.ocsvm_model import OCSVMModel
        with pytest.raises(FileNotFoundError):
            pm.get_ocsvm("ghost_user")

    def test_mahal_not_found_raises(self, pm):
        with pytest.raises(FileNotFoundError):
            pm.get_mahalanobis("ghost_user")

# ---------------------------------------------------------------------------
# User management
# ---------------------------------------------------------------------------
class TestUserManagement:
    def test_list_users_empty(self, pm):
        assert pm.list_users() == []

    def test_list_users_returns_all(self, pm):
        for name in ["alice", "bob", "carol"]:
            fv = _make_valid_fv()
            pm.add_enrollment_window(name, fv)
        users = pm.list_users()
        assert set(users) == {"alice", "bob", "carol"}

    def test_delete_user_removes_profile(self, pm):
        fv = _make_valid_fv()
        pm.add_enrollment_window("alice", fv)
        pm.delete_user("alice")
        assert pm.get_profile("alice") is None

    def test_delete_nonexistent_user_no_error(self, pm):
        pm.delete_user("ghost")  # Must not raise

# ---------------------------------------------------------------------------
# Invalid/degenerate vectors
# --------------------------------------------------------------------------
class TestInvalidVectors:
    def test_invalid_feature_vector_skipped(self, pm):
        # A FeatureVector with no dwell/flight/IKI should not be added.
        fv = FeatureVector(keystroke_count=0)  # all empty
        profile = pm.add_enrollment_window("alice", fv)
        assert profile.keystroke_count == 0
