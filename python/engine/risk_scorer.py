"""
Fuses OCSVM anomaly score + Mahalanobis normalized distance into a single
composite risk score ∈ [0, 1] and maps it to a security action.

Action thresholds (current):
    [0.00 – 0.35)  →  ALLOW
    [0.35 – 0.60)  →  WARN
    [0.60 – 0.80)  →  STEP_UP_AUTH
    [0.80 – 1.00]  →  BLOCK_SESSION
"""

from __future__ import annotations

import collections
from dataclasses import dataclass
from enum import Enum
from typing import Deque, List, Optional

import numpy as np

class Action(str, Enum):
    ALLOW         = "ALLOW"
    WARN          = "WARN"
    STEP_UP_AUTH  = "STEP_UP_AUTH"
    BLOCK_SESSION = "BLOCK_SESSION"

@dataclass
class RiskDecision:
    """Result of a single scoring window evaluation."""
    ocsvm_score:      float   # Raw OCSVM anomaly score [0, 1]
    mahal_score:      float   # Normalized Mahalanobis distance [0, 1]
    composite_score:  float   # Fused risk score [0, 1]
    action:           Action
    is_anomaly:       bool
    threshold_used:   float   # The adaptive WARN threshold applied
    keystroke_count:  int     # Number of keystrokes in this window

class RiskScorer:
    def __init__(
        self,
        ocsvm_weight:     float = 0.6,
        mahal_weight:     float = 0.4,
        base_warn_thresh: float = 0.35,
        step_up_thresh:   float = 0.60,
        block_thresh:     float = 0.80,
        adaptive_window:  int   = 20,
        adaptive_k:       float = 1.5,
    ) -> None:
        # Normalize weights
        total = ocsvm_weight + mahal_weight
        self.ocsvm_weight    = ocsvm_weight / total
        self.mahal_weight    = mahal_weight / total

        self.base_warn_thresh = base_warn_thresh
        self.step_up_thresh   = step_up_thresh
        self.block_thresh     = block_thresh
        self.adaptive_window  = adaptive_window
        self.adaptive_k       = adaptive_k

        # Rolling history of recent composite scores for adaptive threshold
        self._score_history: Deque[float] = collections.deque(
            maxlen=adaptive_window
        )

    def score(
        self,
        ocsvm_score:     float,
        mahal_score:     float,
        keystroke_count: int = 0,
    ) -> RiskDecision:
        ocsvm_score  = float(np.clip(ocsvm_score, 0.0, 1.0))
        mahal_score  = float(np.clip(mahal_score, 0.0, 1.0))

        composite = (
            self.ocsvm_weight * ocsvm_score
            + self.mahal_weight * mahal_score
        )
        composite = float(np.clip(composite, 0.0, 1.0))

        # Determine adaptive WARN threshold
        adaptive_thresh = self._adaptive_threshold()

        # Record this score in history
        self._score_history.append(composite)

        # Map score to action
        action = self._map_action(composite, adaptive_thresh)
        is_anomaly = action in (Action.STEP_UP_AUTH, Action.BLOCK_SESSION)

        return RiskDecision(
            ocsvm_score=ocsvm_score,
            mahal_score=mahal_score,
            composite_score=composite,
            action=action,
            is_anomaly=is_anomaly,
            threshold_used=adaptive_thresh,
            keystroke_count=keystroke_count,
        )

    def reset_history(self) -> None:
        # Clear rolling score history (call at session start)
        self._score_history.clear()

    def _adaptive_threshold(self) -> float:
        """
        compute the current WARN threshold adjusted for session dynamics.
        if there is enough history, the threshold is shifted upward when the
        user's recent scores are consistently low (e.g., comfortable typing),
        giving legitimate users more breathing room.
        """
        if len(self._score_history) < 5:
            return self.base_warn_thresh

        hist = list(self._score_history)
        mean = float(np.mean(hist))
        std  = float(np.std(hist))

        # Adaptive upper limit = mean + k * std, clamped to [base, step_up - 0.05]
        adaptive = mean + self.adaptive_k * std
        return float(np.clip(adaptive, self.base_warn_thresh, self.step_up_thresh - 0.05))

    def _map_action(self, score: float, warn_thresh: float) -> Action:
        if score >= self.block_thresh:
            return Action.BLOCK_SESSION
        elif score >= self.step_up_thresh:
            return Action.STEP_UP_AUTH
        elif score >= warn_thresh:
            return Action.WARN
        else:
            return Action.ALLOW
