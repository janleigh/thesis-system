"""
transforms raw keystroke event streams into micro-temporal feature vectors.

propoal spec:
    - dwell time  : key_up_ts - key_down_ts          (ms) per key
    - flight time : key_down[n+1] - key_up[n]         (ms) between consecutive keys
    - iki         : key_down[n+1] - key_down[n]       (ms) inter-key interval

input tuple format: (key_label: str, event_type: str, timestamp_ms: float) 
    event_type is either 'down' or 'up' whatever
"""

from __future__ import annotations

import statistics
from dataclasses import dataclass, field
from typing import List, Optional, Tuple

KeyEvent = Tuple[str, str, float]  # (key, 'down'|'up', timestamp_ms)

@dataclass
class FeatureVector:
    dwell_times:   List[float] = field(default_factory=list)  # ms
    flight_times:  List[float] = field(default_factory=list)  # ms
    iki_times:     List[float] = field(default_factory=list)  # ms

    # Aggregate statistics — populated by _compute_stats()
    dwell_mean:   float = 0.0
    dwell_std:    float = 0.0
    flight_mean:  float = 0.0
    flight_std:   float = 0.0
    iki_mean:     float = 0.0
    iki_std:      float = 0.0

    keystroke_count: int = 0

    def to_stat_array(self) -> List[float]:
        # 2x3 matrix of mean and std for each metric
        return [
            self.dwell_mean, self.dwell_std,
            self.flight_mean, self.flight_std,
            self.iki_mean, self.iki_std,
        ]

    def is_valid(self) -> bool:
        # only 1 metric of each is fine should be valid by me
        return (
            len(self.dwell_times) >= 1
            and len(self.flight_times) >= 1
            and len(self.iki_times) >= 1
        )

class FeatureExtractor:
    def extract(self, events: List[KeyEvent]) -> Optional[FeatureVector]:
        # process a sequence of key events and return a populated FeatureVector
        if not events:
            return None

        # sort by timestamp to be safe (events should already be ordered but u never know)
        events = sorted(events, key=lambda e: e[2])

        # Build per-key down/up timestamp maps
        down_times: dict[str, float] = {}
        up_times:   dict[str, float] = {}
        key_order:  list[str]        = []  # ordered list of keys (by first down event)

        # get all the key events in order of keydown
        for key, etype, ts in events:
            if etype == 'down':
                if key not in down_times:
                    key_order.append(key)
                down_times[key] = ts
            elif etype == 'up':
                up_times[key] = ts

        fv = FeatureVector()

        # how long each key is held down
        for key in key_order:
            if key in down_times and key in up_times:
                dwell = up_times[key] - down_times[key]
                if dwell >= 0:
                    fv.dwell_times.append(dwell)

        # how long between consecutive keypresses
        for i in range(len(key_order) - 1):
            key_n  = key_order[i]
            key_n1 = key_order[i + 1]

            if key_n in up_times and key_n1 in down_times:
                flight = down_times[key_n1] - up_times[key_n]
                fv.flight_times.append(flight)  # can be negative (overlap)

            if key_n in down_times and key_n1 in down_times:
                iki = down_times[key_n1] - down_times[key_n]
                if iki >= 0:
                    fv.iki_times.append(iki)

        fv.keystroke_count = len(fv.dwell_times)
        self._compute_stats(fv)
        return fv

    @staticmethod
    def _compute_stats(fv: FeatureVector) -> None:
        def safe_mean(lst: List[float]) -> float:
            return statistics.mean(lst) if lst else 0.0

        def safe_std(lst: List[float]) -> float:
            return statistics.pstdev(lst) if len(lst) >= 2 else 0.0

        fv.dwell_mean  = safe_mean(fv.dwell_times)
        fv.dwell_std   = safe_std(fv.dwell_times)
        fv.flight_mean = safe_mean(fv.flight_times)
        fv.flight_std  = safe_std(fv.flight_times)
        fv.iki_mean    = safe_mean(fv.iki_times)
        fv.iki_std     = safe_std(fv.iki_times)
