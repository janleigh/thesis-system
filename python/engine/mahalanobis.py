from __future__ import annotations

import numpy as np
from typing import List, Optional

# Minimum number of enrollment feature vectors needed before the full
# covariance matrix can be reliably inverted.
MIN_SAMPLES_FOR_COVARIANCE = 10

class MahalanobisNormalizer:
    def __init__(self) -> None:
        self.mean_:      Optional[np.ndarray] = None
        self.cov_inv_:   Optional[np.ndarray] = None
        self.is_fitted_: bool = False
        self.cold_start_: bool = True

    def fit(self, vectors: List[List[float]]) -> "MahalanobisNormalizer":
        if not vectors:
            raise ValueError("Cannot fit MahalanobisNormalizer on empty vector list.")

        X = np.array(vectors, dtype=np.float64)

        if X.ndim == 1:
            X = X.reshape(1, -1)

        self.mean_ = np.mean(X, axis=0)

        if len(vectors) < MIN_SAMPLES_FOR_COVARIANCE:
            # Cold-start: use identity-scaled diagonal (Euclidean distance)
            variances = np.var(X, axis=0)
            # Replace near-zero variances with 1 to avoid division by zero
            variances[variances < 1e-9] = 1.0
            self.cov_inv_ = np.diag(1.0 / variances)
            self.cold_start_ = True
        else:
            cov = np.cov(X, rowvar=False)
            try:
                self.cov_inv_ = np.linalg.inv(cov)
                self.cold_start_ = False
            except np.linalg.LinAlgError:
                # Singular matrix: add a small regularization term
                reg = 1e-6 * np.eye(cov.shape[0])
                self.cov_inv_ = np.linalg.inv(cov + reg)
                self.cold_start_ = True

        self.is_fitted_ = True
        return self

    def distance(self, vector: List[float]) -> float:
        """
        this shit fucking computes the distance between the two vectors
        formula: D(x) = sqrt( (x - μ)ᵀ · Σ⁻¹ · (x - μ) )
                        ^ damn i fucking copypasted these symbols lol
        """
        if not self.is_fitted_:
            raise RuntimeError(
                "MahalanobisNormalizer.fit() must be called before distance()."
            )

        x = np.array(vector, dtype=np.float64)
        diff = x - self.mean_ # (x - μ)
        dist_sq = diff @ self.cov_inv_ @ diff  # damn python has a matrix multiplication operator?

        # clamp negative values caused by floating-point error
        return float(np.sqrt(max(dist_sq, 0.0)))

    def normalized_score(self, vector: List[float], max_dist: float = 50.0) -> float:
        """
        returns the MD normalized to [0, 1] for use in the risk scorer.
        score = min(distance / max_dist, 1.0)
        where:
            0 - LEGITIMATE
            1 - ANOMALOUS
        """
        raw = self.distance(vector)
        return min(raw / max_dist, 1.0)

    def summary(self) -> dict:
        return {
            "is_fitted": self.is_fitted_,
            "cold_start": self.cold_start_,
            "mean": self.mean_.tolist() if self.mean_ is not None else None,
        }
