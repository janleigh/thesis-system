import os
import tempfile

import numpy as np
import pytest
from engine.ocsvm_model import OCSVMModel

# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------
@pytest.fixture
def legit_vectors():
    # Tight cluster representing a legitimate user's profile.
    rng = np.random.default_rng(7)
    base = np.array([80.0, 20.0, 120.0, 40.0, 100.0, 30.0])
    return (base + rng.normal(0, 3, (30, 6))).tolist()

@pytest.fixture
def trained_model(legit_vectors):
    model = OCSVMModel(nu=0.1, gamma="scale")
    model.train(legit_vectors)
    return model

@pytest.fixture
def outlier_vector():
    # A vector extremely far from the training distribution.
    return [500.0, 200.0, 600.0, 300.0, 800.0, 400.0]

@pytest.fixture
def inlier_vector():
    # A vector very close to the training distribution mean.
    return [80.0, 20.0, 120.0, 40.0, 100.0, 30.0]


# ---------------------------------------------------------------------------
# Training
# ---------------------------------------------------------------------------
class TestTraining:
    def test_train_succeeds(self, legit_vectors):
        model = OCSVMModel()
        model.train(legit_vectors)
        assert model.is_fitted_

    def test_too_few_samples_raises(self):
        model = OCSVMModel()
        with pytest.raises(ValueError):
            model.train([[80.0, 20.0, 120.0, 40.0, 100.0, 30.0]])

    def test_fluent_api(self, legit_vectors):
        model = OCSVMModel()
        result = model.train(legit_vectors)
        assert result is model

    def test_summary_after_training(self, trained_model):
        s = trained_model.summary()
        assert s["is_fitted"] is True
        assert s["n_support_vectors"] is not None
        assert s["n_support_vectors"] > 0

# ---------------------------------------------------------------------------
# Prediction
# ---------------------------------------------------------------------------
class TestPrediction:
    def test_inlier_predicted_as_positive(self, trained_model, inlier_vector):
        pred = trained_model.predict(inlier_vector)
        assert pred == 1, f"Expected +1 (inlier), got {pred}"

    def test_outlier_predicted_as_negative(self, trained_model, outlier_vector):
        pred = trained_model.predict(outlier_vector)
        assert pred == -1, f"Expected -1 (outlier), got {pred}"

    def test_predict_requires_fitting(self):
        model = OCSVMModel()
        with pytest.raises(RuntimeError):
            model.predict([80.0, 20.0, 120.0, 40.0, 100.0, 30.0])

# ---------------------------------------------------------------------------
# Anomaly score
# ---------------------------------------------------------------------------
class TestAnomalyScore:
    def test_score_in_range_for_inlier(self, trained_model, inlier_vector):
        score = trained_model.anomaly_score(inlier_vector)
        assert 0.0 <= score <= 1.0, f"Score out of range: {score}"

    def test_score_in_range_for_outlier(self, trained_model, outlier_vector):
        score = trained_model.anomaly_score(outlier_vector)
        assert 0.0 <= score <= 1.0, f"Score out of range: {score}"

    def test_inlier_score_lower_than_outlier(
        self, trained_model, inlier_vector, outlier_vector
    ):
        inlier_score  = trained_model.anomaly_score(inlier_vector)
        outlier_score = trained_model.anomaly_score(outlier_vector)
        assert inlier_score < outlier_score, (
            f"Inlier score ({inlier_score:.4f}) should be lower than "
            f"outlier score ({outlier_score:.4f})"
        )

    def test_score_requires_fitting(self):
        model = OCSVMModel()
        with pytest.raises(RuntimeError):
            model.anomaly_score([80.0, 20.0, 120.0, 40.0, 100.0, 30.0])

    def test_scores_range_for_many_samples(self, trained_model):
        rng = np.random.default_rng(55)
        for _ in range(100):
            v = rng.normal(80, 40, 6).tolist()
            score = trained_model.anomaly_score(v)
            assert 0.0 <= score <= 1.0

# ---------------------------------------------------------------------------
# Persistence
# ---------------------------------------------------------------------------
class TestPersistence:
    def test_save_and_load(self, trained_model, inlier_vector, outlier_vector):
        with tempfile.TemporaryDirectory() as tmpdir:
            path = os.path.join(tmpdir, "test_ocsvm.joblib")
            trained_model.save(path)
            assert os.path.exists(path)

            loaded = OCSVMModel.load(path)
            assert loaded.is_fitted_

            # Loaded model must produce same results
            assert trained_model.predict(inlier_vector)  == loaded.predict(inlier_vector)
            assert trained_model.predict(outlier_vector) == loaded.predict(outlier_vector)
            assert abs(
                trained_model.anomaly_score(inlier_vector)
                - loaded.anomaly_score(inlier_vector)
            ) < 1e-9

    def test_load_missing_file_raises(self):
        with pytest.raises(FileNotFoundError):
            OCSVMModel.load("/nonexistent/path/model.joblib")
