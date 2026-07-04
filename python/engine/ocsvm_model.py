from __future__ import annotations

import os
from typing import List, Optional

import joblib
import numpy as np
from sklearn.preprocessing import StandardScaler
from sklearn.svm import OneClassSVM

# Default OCSVM hyperparameters.
# nu     : upper bound on the fraction of outliers / lower bound on support vectors.
#          0.1 means we expect at most 10% of training points to be anomalies.
# gamma  : RBF kernel bandwidth. 'scale' = 1/(n_features * X.var())
DEFAULT_NU    = 0.1
DEFAULT_GAMMA = "scale"

class OCSVMModel:
    def __init__(
        self,
        nu:    float = DEFAULT_NU,
        gamma: str   = DEFAULT_GAMMA,
    ) -> None:
        self.nu    = nu
        self.gamma = gamma

        self._svm:     Optional[OneClassSVM]   = None
        self._scaler:  Optional[StandardScaler] = None
        self.is_fitted_: bool = False

        # train score distribution for normalization
        self._train_scores: Optional[np.ndarray] = None

    def train(self, vectors: List[List[float]]) -> "OCSVMModel":
        # fit the OCSVM on enrollment feature vectors
        if len(vectors) < 2:
            raise ValueError(
                f"OCSVM requires at least 2 training samples; got {len(vectors)}."
            )

        X = np.array(vectors, dtype=np.float64)

        # standardize features (zero mean, unit variance) before SVM fitting
        self._scaler = StandardScaler()
        X_scaled = self._scaler.fit_transform(X)

        self._svm = OneClassSVM(kernel="rbf", nu=self.nu, gamma=self.gamma)
        self._svm.fit(X_scaled)

        # store training scores for later normalization
        self._train_scores = self._svm.decision_function(X_scaled)
        self.is_fitted_ = True
        return self

    def predict(self, vector: List[float]) -> int:
        """
        hard binary prediction. 
        upd: i dont know what i did here gang
        +1  if the vector is classified as inlier  (legitimate)
        -1  if the vector is classified as outlier (anomaly)
        """
        self._assert_fitted()
        x = np.array(vector, dtype=np.float64).reshape(1, -1)
        x_scaled = self._scaler.transform(x)
        return int(self._svm.predict(x_scaled)[0])

    def anomaly_score(self, vector: List[float]) -> float:
        """
        Continuous anomaly score ∈ [0, 1].

        The raw decision_function returns a signed distance from the
        hyperplane; negative = outlier. We invert and normalize so that:
            score → 0  : deep inside the legitimate boundary
            score → 1  : far outside (highly anomalous)

        Normalization uses the range of training scores as a reference
        to give the score real-world calibration.
        """
        self._assert_fitted()

        x = np.array(vector, dtype=np.float64).reshape(1, -1)
        x_scaled = self._scaler.transform(x)
        raw_score = float(self._svm.decision_function(x_scaled)[0])

        # normalize
        score_min = float(np.min(self._train_scores))
        score_max = float(np.max(self._train_scores))

        score_range = score_max - score_min
        if score_range < 1e-9:
            # degenerate case: all training scores identical
            return 0.0 if raw_score >= score_max else 1.0

        # Map: 
        #   raw_score == score_max → 0 (most legitimate)
        #   raw_score == score_min → 0.5 (boundary)
        #   raw_score << score_min → → 1.0 (most anomalous)
        normalized = (score_max - raw_score) / (score_max - score_min)
        return float(np.clip(normalized, 0.0, 1.0))

    def save(self, path: str) -> None:
        # serialize the fitted model and scaler to disk
        self._assert_fitted()
        os.makedirs(os.path.dirname(path), exist_ok=True)
        joblib.dump(
            {
                "svm":           self._svm,
                "scaler":        self._scaler,
                "train_scores":  self._train_scores,
                "nu":            self.nu,
                "gamma":         self.gamma,
            },
            path,
        )

    @classmethod
    def load(cls, path: str) -> "OCSVMModel":
        # deserializes a previously saved model from disk
        if not os.path.exists(path):
            raise FileNotFoundError(f"OCSVM model not found at: {path}")

        payload = joblib.load(path)
        obj = cls(nu=payload["nu"], gamma=payload["gamma"])
        obj._svm           = payload["svm"]
        obj._scaler        = payload["scaler"]
        obj._train_scores  = payload["train_scores"]
        obj.is_fitted_     = True
        return obj

    def _assert_fitted(self) -> None:
        if not self.is_fitted_:
            raise RuntimeError(
                "OCSVMModel.train() must be called (or model loaded) before scoring."
            )

    def summary(self) -> dict:
        return {
            "is_fitted": self.is_fitted_,
            "nu": self.nu,
            "gamma": self.gamma,
            "n_support_vectors": (
                int(self._svm.support_vectors_.shape[0]) if self.is_fitted_ else None
            ),
        }
