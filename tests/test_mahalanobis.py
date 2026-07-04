import math

import numpy as np
import pytest
from engine.mahalanobis import MahalanobisNormalizer, MIN_SAMPLES_FOR_COVARIANCE

@pytest.fixture
def large_vectors():
    """20 controlled 6-d vectors around a known mean."""
    rng = np.random.default_rng(42)
    base = np.array([80.0, 20.0, 120.0, 40.0, 100.0, 30.0])
    return (base + rng.normal(0, 5, (20, 6))).tolist()


@pytest.fixture
def small_vectors():
    """5 vectors — below MIN_SAMPLES_FOR_COVARIANCE, triggers cold-start."""
    return [
        [80.0, 20.0, 120.0, 40.0, 100.0, 30.0],
        [82.0, 21.0, 118.0, 39.0,  98.0, 31.0],
        [78.0, 19.0, 122.0, 41.0, 102.0, 29.0],
        [81.0, 20.5, 119.0, 40.5, 101.0, 30.5],
        [79.0, 19.5, 121.0, 39.5,  99.0, 30.5],
    ]

# ---------------------------------------------------------------------------
# Basic functionality
# ---------------------------------------------------------------------------
class TestFitting:
    def test_fit_large_succeeds(self, large_vectors):
        norm = MahalanobisNormalizer()
        norm.fit(large_vectors)
        assert norm.is_fitted_
        assert not norm.cold_start_

    def test_fit_small_triggers_cold_start(self, small_vectors):
        norm = MahalanobisNormalizer()
        norm.fit(small_vectors)
        assert norm.is_fitted_
        assert norm.cold_start_

    def test_fit_empty_raises(self):
        norm = MahalanobisNormalizer()
        with pytest.raises(ValueError):
            norm.fit([])

    def test_mean_vector_shape(self, large_vectors):
        norm = MahalanobisNormalizer()
        norm.fit(large_vectors)
        assert norm.mean_.shape == (6,)

    def test_fluent_api_returns_self(self, large_vectors):
        norm = MahalanobisNormalizer()
        result = norm.fit(large_vectors)
        assert result is norm


# ---------------------------------------------------------------------------
# Distance computation
# ---------------------------------------------------------------------------
class TestDistance:
    def test_mean_vector_distance_near_zero(self, large_vectors):
        """A vector equal to the mean should have distance ≈ 0."""
        norm = MahalanobisNormalizer()
        norm.fit(large_vectors)
        mean_vec = norm.mean_.tolist()
        dist = norm.distance(mean_vec)
        assert dist < 1.0, f"Expected near-zero distance, got {dist}"

    def test_far_vector_has_large_distance(self, large_vectors):
        """A vector very far from the baseline should have a large distance."""
        norm = MahalanobisNormalizer()
        norm.fit(large_vectors)
        # Vector 10x the typical std away from mean
        mean = norm.mean_
        far_vec = (mean + 200.0).tolist()
        dist = norm.distance(far_vec)
        assert dist > 10.0, f"Expected large distance, got {dist}"

    def test_distance_is_non_negative(self, large_vectors):
        norm = MahalanobisNormalizer()
        norm.fit(large_vectors)
        for _ in range(20):
            rng = np.random.default_rng()
            v = rng.normal(80, 30, 6).tolist()
            assert norm.distance(v) >= 0.0

    def test_cold_start_distance_near_zero_for_mean(self, small_vectors):
        norm = MahalanobisNormalizer()
        norm.fit(small_vectors)
        mean_vec = norm.mean_.tolist()
        dist = norm.distance(mean_vec)
        assert dist < 1.0

    def test_error_before_fit(self):
        norm = MahalanobisNormalizer()
        with pytest.raises(RuntimeError):
            norm.distance([1.0, 2.0, 3.0, 4.0, 5.0, 6.0])

# ---------------------------------------------------------------------------
# Normalized score
# ---------------------------------------------------------------------------
class TestNormalizedScore:
    def test_score_in_range(self, large_vectors):
        norm = MahalanobisNormalizer()
        norm.fit(large_vectors)
        rng = np.random.default_rng(99)
        for _ in range(50):
            v = rng.normal(80, 50, 6).tolist()
            score = norm.normalized_score(v)
            assert 0.0 <= score <= 1.0, f"Score out of range: {score}"

    def test_mean_vector_score_near_zero(self, large_vectors):
        norm = MahalanobisNormalizer()
        norm.fit(large_vectors)
        mean_vec = norm.mean_.tolist()
        score = norm.normalized_score(mean_vec, max_dist=50.0)
        assert score < 0.1

    def test_far_vector_score_near_one(self, large_vectors):
        norm = MahalanobisNormalizer()
        norm.fit(large_vectors)
        mean = norm.mean_
        far_vec = (mean + 500.0).tolist()
        score = norm.normalized_score(far_vec, max_dist=50.0)
        assert score == 1.0

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
class TestSummary:
    def test_summary_before_fit(self):
        norm = MahalanobisNormalizer()
        s = norm.summary()
        assert s["is_fitted"] is False
        assert s["mean"] is None

    def test_summary_after_fit(self, large_vectors):
        norm = MahalanobisNormalizer()
        norm.fit(large_vectors)
        s = norm.summary()
        assert s["is_fitted"] is True
        assert isinstance(s["mean"], list)
        assert len(s["mean"]) == 6
