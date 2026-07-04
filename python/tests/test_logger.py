import os
import sqlite3
from pathlib import Path

import pytest
from engine.logger import SecurityLogger

@pytest.fixture
def tmp_logger(tmp_path):
    """
    create a SecurityLogger with pytest-managed tmp_path.
    using tmp_path (pytest fixture) instead of tempfile.TemporaryDirectory
    avoids windows PermissionError when sqlite holds file locks during cleanup idk y
    """
    db_path  = str(tmp_path / "test_log.db")
    key_path = str(tmp_path / "test.key")
    logger   = SecurityLogger(db_path=db_path, key_path=key_path)
    yield logger, db_path, key_path
    # explicitly close all sqlite connections before pytest cleans up tmp_path or else it breaks
    try:
        conn = sqlite3.connect(db_path)
        conn.close()
    except Exception:
        pass

# ---------------------------------------------------------------------------
# Key management
# ---------------------------------------------------------------------------
class TestKeyManagement:
    def test_key_file_created(self, tmp_logger):
        _, _, key_path = tmp_logger
        assert os.path.exists(key_path)

    def test_key_file_not_empty(self, tmp_logger):
        _, _, key_path = tmp_logger
        with open(key_path, "rb") as f:
            data = f.read()
        assert len(data) > 0

    def test_same_key_loaded_on_reinit(self, tmp_logger):
        logger, db_path, key_path = tmp_logger
        # Write something with the original logger
        logger.log("user1", "TEST", {"val": "hello"})

        # reinit with same paths - must load the same key or we r screwed
        logger2 = SecurityLogger(db_path=db_path, key_path=key_path)
        entries = logger2.read_all()
        assert len(entries) == 1
        assert entries[0]["payload"]["val"] == "hello"


# ---------------------------------------------------------------------------
# Write and read
# ---------------------------------------------------------------------------
class TestWriteRead:
    def test_write_and_read_single_entry(self, tmp_logger):
        logger, *_ = tmp_logger
        logger.log(
            user_id="alice",
            event_type="VERIFICATION",
            payload={"window": 1, "ocsvm_score": 0.12},
            risk_score=0.15,
            action="ALLOW",
        )
        entries = logger.read_all()
        assert len(entries) == 1
        e = entries[0]
        assert e["user_id"]    == "alice"
        assert e["event_type"] == "VERIFICATION"
        assert e["action"]     == "ALLOW"
        assert abs(e["risk_score"] - 0.15) < 1e-9

    def test_payload_round_trip(self, tmp_logger):
        logger, *_ = tmp_logger
        payload = {"window": 42, "score": 0.333, "msg": "test data"}
        logger.log("bob", "TEST", payload)
        entries = logger.read_all()
        assert entries[0]["payload"] == payload

    def test_multiple_entries_ordered_by_id(self, tmp_logger):
        logger, *_ = tmp_logger
        for i in range(5):
            logger.log("user1", "VERIFICATION", {"i": i}, risk_score=i * 0.1)
        entries = logger.read_all()
        assert len(entries) == 5
        ids = [e["id"] for e in entries]
        assert ids == sorted(ids)

    def test_null_risk_score_stored(self, tmp_logger):
        logger, *_ = tmp_logger
        logger.log("user1", "SESSION_START", {})
        e = logger.read_all()[0]
        assert e["risk_score"] is None

    def test_schema_fields_present(self, tmp_logger):
        logger, *_ = tmp_logger
        logger.log("user1", "TEST", {"x": 1})
        e = logger.read_all()[0]
        for field in ["id", "user_id", "timestamp", "event_type",
                    "risk_score", "action", "payload"]:
            assert field in e, f"Missing field: {field}"

    def test_log_returns_row_id(self, tmp_logger):
        logger, *_ = tmp_logger
        row_id = logger.log("user1", "TEST", {})
        assert isinstance(row_id, int)
        assert row_id >= 1

# ---------------------------------------------------------------------------
# Count and filter
# ---------------------------------------------------------------------------
class TestCountFilter:
    def test_count_all(self, tmp_logger):
        logger, *_ = tmp_logger
        for _ in range(7):
            logger.log("user1", "TEST", {})
        assert logger.count() == 7

    def test_count_by_user(self, tmp_logger):
        logger, *_ = tmp_logger
        for _ in range(3):
            logger.log("alice", "TEST", {})
        for _ in range(5):
            logger.log("bob",   "TEST", {})
        assert logger.count("alice") == 3
        assert logger.count("bob")   == 5

    def test_read_filter_by_user(self, tmp_logger):
        logger, *_ = tmp_logger
        logger.log("alice", "TEST", {"who": "alice"})
        logger.log("bob",   "TEST", {"who": "bob"})
        entries = logger.read_all(user_id="alice")
        assert len(entries) == 1
        assert entries[0]["user_id"] == "alice"

# ---------------------------------------------------------------------------
# Clear
# ---------------------------------------------------------------------------
class TestClear:
    def test_clear_all(self, tmp_logger):
        logger, *_ = tmp_logger
        for _ in range(5):
            logger.log("user1", "TEST", {})
        logger.clear()
        assert logger.count() == 0

    def test_clear_by_user(self, tmp_logger):
        logger, *_ = tmp_logger
        for _ in range(3):
            logger.log("alice", "TEST", {})
        for _ in range(3):
            logger.log("bob",   "TEST", {})
        logger.clear("alice")
        assert logger.count("alice") == 0
        assert logger.count("bob")   == 3

# ---------------------------------------------------------------------------
# Tamper detection
# ---------------------------------------------------------------------------
class TestTamper:
    def test_tampered_ciphertext_returns_error_payload(self, tmp_logger):
        logger, db_path, _ = tmp_logger
        logger.log("user1", "TEST", {"secret": "data"})

        # Directly corrupt the ciphertext in SQLite
        conn = sqlite3.connect(db_path)
        conn.execute("UPDATE security_log SET payload_enc = X'DEADBEEF01020304'")
        conn.commit()
        conn.close()

        # read should not crash but should return error marker or somethin
        entries = logger.read_all()
        assert entries[0]["payload"].get("error") == "decryption_failed"
