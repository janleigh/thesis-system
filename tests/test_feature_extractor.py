import pytest
from engine.feature_extractor import FeatureExtractor, FeatureVector

@pytest.fixture
def extractor():
    return FeatureExtractor()

def make_events(key_timings):
    """
    Build a list of KeyEvents from a list of (key, down_ms, up_ms) tuples.
    Events are returned interleaved by time order (as they'd arrive in real use).
    """
    events = []
    for key, down, up in key_timings:
        events.append((key, "down", down))
        events.append((key, "up",   up))
    # Sort by timestamp (real-world ordering)
    return sorted(events, key=lambda e: e[2])


# ---------------------------------------------------------------------------
# Dwell time tests
# --------------------------------------------------------------------------
class TestDwellTime:
    def test_single_key_dwell(self, extractor):
        """Dwell time = up_ts - down_ts for a single key."""
        events = [("a", "down", 100.0), ("a", "up", 180.0)]
        fv = extractor.extract(events)
        assert fv is not None
        assert len(fv.dwell_times) == 1
        assert abs(fv.dwell_times[0] - 80.0) < 1e-9

    def test_multiple_keys_dwell(self, extractor):
        """Dwell times computed independently for each key."""
        events = make_events([
            ("a", 0.0,   80.0),
            ("b", 100.0, 160.0),
            ("c", 180.0, 250.0),
        ])
        fv = extractor.extract(events)
        assert len(fv.dwell_times) == 3
        assert abs(fv.dwell_times[0] - 80.0)  < 1e-9
        assert abs(fv.dwell_times[1] - 60.0)  < 1e-9
        assert abs(fv.dwell_times[2] - 70.0)  < 1e-9

    def test_dwell_mean_computed(self, extractor):
        events = make_events([
            ("a", 0.0,  100.0),
            ("b", 150.0, 250.0),
        ])
        fv = extractor.extract(events)
        assert abs(fv.dwell_mean - 100.0) < 1e-9

    def test_zero_dwell_ignored(self, extractor):
        """Negative dwell (up before down, malformed event) should be excluded."""
        events = [
            ("a", "down", 100.0),
            ("a", "up",   80.0),    # malformed: up before down
        ]
        fv = extractor.extract(events)
        # Dwell of -20 is negative; should not be recorded
        assert fv is not None
        assert len(fv.dwell_times) == 0


# ---------------------------------------------------------------------------
# Flight time tests
# ---------------------------------------------------------------------------
class TestFlightTime:
    def test_basic_flight_time(self, extractor):
        """Flight time = down[n+1] - up[n]."""
        events = make_events([
            ("a", 0.0,  80.0),    # up at 80
            ("b", 120.0, 200.0),  # down at 120
        ])
        fv = extractor.extract(events)
        assert len(fv.flight_times) == 1
        assert abs(fv.flight_times[0] - 40.0) < 1e-9  # 120 - 80 = 40

    def test_negative_flight_allowed(self, extractor):
        """Overlapping keys (key n+1 pressed before key n released) = negative flight."""
        events = make_events([
            ("a", 0.0,  100.0),   # up at 100
            ("b", 60.0, 150.0),   # down at 60  (overlapping)
        ])
        fv = extractor.extract(events)
        # Flight = 60 - 100 = -40 (overlap) — should be recorded as-is
        assert len(fv.flight_times) == 1
        assert fv.flight_times[0] < 0

    def test_flight_mean(self, extractor):
        events = make_events([
            ("a", 0.0,   100.0),
            ("b", 140.0, 220.0),
            ("c", 270.0, 340.0),
        ])
        fv = extractor.extract(events)
        # Flight 1: 140-100=40, Flight 2: 270-220=50  → mean=45
        assert abs(fv.flight_mean - 45.0) < 1e-9


# ---------------------------------------------------------------------------
# IKI tests
# ---------------------------------------------------------------------------
class TestIKI:
    def test_basic_iki(self, extractor):
        """IKI = down[n+1] - down[n]."""
        events = make_events([
            ("a", 0.0,   80.0),
            ("b", 100.0, 180.0),
        ])
        fv = extractor.extract(events)
        assert len(fv.iki_times) == 1
        assert abs(fv.iki_times[0] - 100.0) < 1e-9

    def test_iki_mean(self, extractor):
        events = make_events([
            ("a", 0.0,   80.0),
            ("b", 100.0, 180.0),
            ("c", 200.0, 280.0),
        ])
        fv = extractor.extract(events)
        # IKI 1: 100-0=100, IKI 2: 200-100=100
        assert abs(fv.iki_mean - 100.0) < 1e-9


# ---------------------------------------------------------------------------
# Edge cases
# ---------------------------------------------------------------------------
class TestEdgeCases:
    def test_empty_events_returns_none(self, extractor):
        assert extractor.extract([]) is None

    def test_single_key_no_flight_or_iki(self, extractor):
        events = [("a", "down", 0.0), ("a", "up", 80.0)]
        fv = extractor.extract(events)
        assert len(fv.flight_times) == 0
        assert len(fv.iki_times)    == 0

    def test_keystroke_count(self, extractor):
        events = make_events([
            ("a", 0.0,  80.0),
            ("b", 100.0, 180.0),
            ("c", 200.0, 280.0),
        ])
        fv = extractor.extract(events)
        assert fv.keystroke_count == 3

    def test_stat_array_length(self, extractor):
        events = make_events([
            ("a", 0.0,   80.0),
            ("b", 100.0, 180.0),
        ])
        fv = extractor.extract(events)
        arr = fv.to_stat_array()
        assert len(arr) == 6

    def test_is_valid_requires_all_three(self, extractor):
        """is_valid() must return False when flight/IKI is absent (single key)."""
        events = [("a", "down", 0.0), ("a", "up", 80.0)]
        fv = extractor.extract(events)
        assert not fv.is_valid()

    def test_is_valid_true_multiple_keys(self, extractor):
        events = make_events([
            ("a", 0.0,   80.0),
            ("b", 100.0, 180.0),
        ])
        fv = extractor.extract(events)
        assert fv.is_valid()

    def test_unsorted_events_handled(self, extractor):
        # Extractor must sort events by timestamp internally.
        events = [
            ("b", "down", 100.0),
            ("a", "down", 0.0),
            ("a", "up",   80.0),
            ("b", "up",   180.0),
        ]
        fv = extractor.extract(events)
        assert fv is not None
        assert len(fv.dwell_times) == 2
