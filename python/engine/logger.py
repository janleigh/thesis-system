"""
sqlit logger for most security ecvents xD


schema:
    id          INTEGER  PRIMARY KEY AUTOINCREMENT
    user_id     TEXT     NOT NULL
    timestamp   TEXT     NOT NULL     (ISO-8601 UTC)
    event_type  TEXT     NOT NULL     (ENROLLMENT | VERIFICATION | ANOMALY | SESSION_*)
    risk_score  REAL                  (NULL during enrollment events)
    action      TEXT                  (ALLOW | WARN | STEP_UP_AUTH | BLOCK_SESSION)
    payload_enc BLOB     NOT NULL     (Fernet-encrypted JSON payload)
"""

from __future__ import annotations

import json
import os
import sqlite3
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional

from cryptography.fernet import Fernet

DEFAULT_DB_PATH  = str(Path(__file__).parents[1] / "data" / "security_log.db")
DEFAULT_KEY_PATH = str(Path(__file__).parents[1] / "data" / "biometric.key")

class SecurityLogger:
    def __init__(
        self,
        db_path:  str = DEFAULT_DB_PATH,
        key_path: str = DEFAULT_KEY_PATH,
    ) -> None:
        self.db_path  = db_path
        self.key_path = key_path

        # Ensure data directory exists
        os.makedirs(os.path.dirname(db_path),  exist_ok=True)
        os.makedirs(os.path.dirname(key_path), exist_ok=True)

        self._fernet = Fernet(self._load_or_create_key())
        self._init_db()

    def log(
        self,
        user_id:    str,
        event_type: str,
        payload:    Dict[str, Any],
        risk_score: Optional[float] = None,
        action:     Optional[str]   = None,
    ) -> int:
        # Write a single encrypted log entry to the database.
        ts            = datetime.now(timezone.utc).isoformat()
        payload_json  = json.dumps(payload, ensure_ascii=False)
        payload_enc   = self._fernet.encrypt(payload_json.encode())

        with self._connect() as conn:
            cur = conn.execute(
                """
                INSERT INTO security_log
                    (user_id, timestamp, event_type, risk_score, action, payload_enc)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                (user_id, ts, event_type, risk_score, action, payload_enc),
            )
            return cur.lastrowid

    def read_all(self, user_id: Optional[str] = None) -> List[Dict[str, Any]]:
        # Decrypt and return all log entries, optionally filtered by user_id.
        query = "SELECT * FROM security_log"
        params = ()
        if user_id:
            query += " WHERE user_id = ?"
            params = (user_id,)
        query += " ORDER BY id ASC"

        with self._connect() as conn:
            rows = conn.execute(query, params).fetchall()

        results = []
        for row in rows:
            row_dict = dict(row)
            try:
                decrypted = self._fernet.decrypt(row_dict["payload_enc"]).decode()
                row_dict["payload"] = json.loads(decrypted)
            except Exception:
                row_dict["payload"] = {"error": "decryption_failed"}
            del row_dict["payload_enc"]
            results.append(row_dict)
        return results

    def count(self, user_id: Optional[str] = None) -> int:
        # Return the total number of log entries.
        query  = "SELECT COUNT(*) FROM security_log"
        params = ()
        if user_id:
            query += " WHERE user_id = ?"
            params = (user_id,)
        with self._connect() as conn:
            return conn.execute(query, params).fetchone()[0]

    def clear(self, user_id: Optional[str] = None) -> None:
        # Delete log entries (optionally filtered by user_id).
        with self._connect() as conn:
            if user_id:
                conn.execute("DELETE FROM security_log WHERE user_id = ?", (user_id,))
            else:
                conn.execute("DELETE FROM security_log")

    def _load_or_create_key(self) -> bytes:
        # Load the Fernet key from disk, creating a new one if absent.
        if os.path.exists(self.key_path):
            with open(self.key_path, "rb") as f:
                return f.read().strip()
        else:
            key = Fernet.generate_key()
            with open(self.key_path, "wb") as f:
                f.write(key)
            # Restrict permissions on non-Windows systems
            try:
                os.chmod(self.key_path, 0o600)
            except AttributeError:
                pass
            return key

    def _init_db(self) -> None:
        with self._connect() as conn:
            conn.execute(
                """
                CREATE TABLE IF NOT EXISTS security_log (
                    id          INTEGER  PRIMARY KEY AUTOINCREMENT,
                    user_id     TEXT     NOT NULL,
                    timestamp   TEXT     NOT NULL,
                    event_type  TEXT     NOT NULL,
                    risk_score  REAL,
                    action      TEXT,
                    payload_enc BLOB     NOT NULL
                )
                """
            )

    def _connect(self) -> sqlite3.Connection:
        conn = sqlite3.connect(self.db_path)
        conn.row_factory = sqlite3.Row
        return conn
