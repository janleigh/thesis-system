"""
Generates fake keyboard data for automated testing.
Two simulation modes:
    LEGITIMATE  — samples timing from a user's own baseline distribution
                (Gaussian noise around real enrollment mean/std)
    IMPOSTOR    — generates typing patterns with a shifted mean and higher
                variance to simulate an attacker trying to mimic the user

Used to test the system's security accuracy (FAR/FRR/EER metrics) instantly
without us lol
"""

from __future__ import annotations

import random
import string
import time
from typing import List, Optional, Tuple

import numpy as np

from engine.feature_extractor import KeyEvent, FeatureVector

# Realistic typing timing distributions (milliseconds) for a baseline typist
BASELINE_DWELL_MEAN  = 80.0    # average key hold duration
BASELINE_DWELL_STD   = 20.0
BASELINE_FLIGHT_MEAN = 120.0   # average time between key release and next press
BASELINE_FLIGHT_STD  = 40.0

# Impostor shift: slower and more variable
IMPOSTOR_DWELL_SHIFT  = 40.0   # added to baseline mean
IMPOSTOR_FLIGHT_SHIFT = 80.0
IMPOSTOR_EXTRA_STD    = 60.0   # extra variance

class SessionSimulator:
    def __init__(
        self,
        dwell_mean:  float          = BASELINE_DWELL_MEAN,
        dwell_std:   float          = BASELINE_DWELL_STD,
        flight_mean: float          = BASELINE_FLIGHT_MEAN,
        flight_std:  float          = BASELINE_FLIGHT_STD,
        seed:        Optional[int]  = None,
    ) -> None:
        self.dwell_mean  = dwell_mean
        self.dwell_std   = dwell_std
        self.flight_mean = flight_mean
        self.flight_std  = flight_std
        self._rng        = np.random.default_rng(seed)

    def generate_legitimate(
        self,
        n_keystrokes: int = 30,
        noise_factor: float = 1.0,
    ) -> List[KeyEvent]:
        return self._build_events(
            n=n_keystrokes,
            dwell_mean=self.dwell_mean,
            dwell_std=self.dwell_std * noise_factor,
            flight_mean=self.flight_mean,
            flight_std=self.flight_std * noise_factor,
        )

    def generate_impostor(self, n_keystrokes: int = 30) -> List[KeyEvent]:
        return self._build_events(
            n=n_keystrokes,
            dwell_mean=self.dwell_mean  + IMPOSTOR_DWELL_SHIFT,
            dwell_std=self.dwell_std    + IMPOSTOR_EXTRA_STD,
            flight_mean=self.flight_mean + IMPOSTOR_FLIGHT_SHIFT,
            flight_std=self.flight_std   + IMPOSTOR_EXTRA_STD,
        )

    def generate_replay_attack(
        self,
        original_events: List[KeyEvent],
        jitter_ms: float = 5.0,
    ) -> List[KeyEvent]:
        if not original_events:
            return []

        start_ts = time.time() * 1000.0
        orig_start = original_events[0][2]
        offset = start_ts - orig_start

        replayed = []
        for key, etype, ts in original_events:
            jitter = self._rng.normal(0, jitter_ms)
            new_ts = ts + offset + jitter
            replayed.append((key, etype, new_ts))
        return replayed

    def generate_enrollment_batch(
        self,
        n_windows: int   = 20,
        window_size: int = 30,
        noise_factor: float = 0.8,
    ) -> List[List[KeyEvent]]:
        return [
            self.generate_legitimate(n_keystrokes=window_size, noise_factor=noise_factor)
            for _ in range(n_windows)
        ]

    def _build_events(
        self,
        n:           int,
        dwell_mean:  float,
        dwell_std:   float,
        flight_mean: float,
        flight_std:  float,
    ) -> List[KeyEvent]:
        events: List[KeyEvent] = []
        keys    = [self._random_key() for _ in range(n)]
        ts      = 0.0  # relative timestamps starting at 0

        for i, key in enumerate(keys):
            # Flight time from previous key's release to this key's press
            if i > 0:
                flight = max(1.0, self._rng.normal(flight_mean, flight_std))
                ts += flight

            key_down_ts = ts
            dwell = max(1.0, self._rng.normal(dwell_mean, dwell_std))
            key_up_ts = key_down_ts + dwell

            events.append((key, "down", key_down_ts))
            events.append((key, "up",   key_up_ts))
            ts = key_up_ts

        return events

    @staticmethod
    def _random_key() -> str:
        pool = string.ascii_lowercase + "     .,!?"
        return random.choice(pool)
