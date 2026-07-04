"""
Manages user enrollment, behavioral vector profile storage, and model lifecycle.

    PENDING   → collecting enrollment data (below minimum)
    READY     → minimum reached, OCSVM + Mahalanobis fitted
    ACTIVE    → model is in active session use
"""

from __future__ import annotations

import json
import os
import sqlite3
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import List, Optional

import numpy as np

from engine.feature_extractor import FeatureVector
from engine.mahalanobis import MahalanobisNormalizer
from engine.ocsvm_model import OCSVMModel

PRODUCTION_MIN_KEYSTROKES = 500
TESTING_MIN_KEYSTROKES    = 50   # Reduced minimum for developer test suite

DEFAULT_DB_PATH    = str(Path(__file__).parents[1] / "data" / "profiles.db")
DEFAULT_MODEL_DIR  = str(Path(__file__).parents[1] / "data" / "models")

@dataclass
class UserProfile:
    user_id:          str
    keystroke_count:  int
    status:           str   # PENDING | READY | ACTIVE
    created_at:       str
    updated_at:       str
    min_keystrokes:   int
    vectors:          List[List[float]]  # stat arrays from FeatureVector.to_stat_array()

    @property
    def progress_pct(self) -> float:
        return min(self.keystroke_count / self.min_keystrokes * 100, 100.0)

    @property
    def is_ready(self) -> bool:
        return self.status in ("READY", "ACTIVE")

class ProfileManager:
    def __init__(
        self,
        db_path:      str  = DEFAULT_DB_PATH,
        model_dir:    str  = DEFAULT_MODEL_DIR,
        testing_mode: bool = False,
    ) -> None:
        self.db_path      = db_path
        self.model_dir    = model_dir
        self.testing_mode = testing_mode
        self.min_ks       = (
            TESTING_MIN_KEYSTROKES if testing_mode else PRODUCTION_MIN_KEYSTROKES
        )

        os.makedirs(os.path.dirname(db_path), exist_ok=True)
        os.makedirs(model_dir, exist_ok=True)
        self._init_db()

    def add_enrollment_window(
        self,
        user_id:        str,
        feature_vector: FeatureVector,
    ) -> UserProfile:
        profile = self._get_or_create_profile(user_id)

        if not feature_vector.is_valid():
            return profile  # Skip degenerate windows

        stat_arr = feature_vector.to_stat_array()
        profile.vectors.append(stat_arr)
        profile.keystroke_count += feature_vector.keystroke_count

        now = datetime.now(timezone.utc).isoformat()
        profile.updated_at = now

        # Check if enrollment threshold is met
        if (
            profile.status == "PENDING"
            and profile.keystroke_count >= self.min_ks
            and len(profile.vectors) >= 5
        ):
            self._fit_models(user_id, profile.vectors)
            profile.status = "READY"

        self._save_profile(profile)
        return profile

    def get_ocsvm(self, user_id: str) -> OCSVMModel:
        # Load the fitted OCSVM model for a user.
        path = self._model_path(user_id, "ocsvm")
        return OCSVMModel.load(path)

    def get_mahalanobis(self, user_id: str) -> MahalanobisNormalizer:
        # Load the fitted Mahalanobis normalizer for a user.
        path = self._mahal_path(user_id)
        if not os.path.exists(path):
            raise FileNotFoundError(f"Mahalanobis model not found for user '{user_id}'")
        import joblib
        return joblib.load(path)

    def get_profile(self, user_id: str) -> Optional[UserProfile]:
        # Return the stored UserProfile or None if not found.
        with self._connect() as conn:
            row = conn.execute(
                "SELECT * FROM profiles WHERE user_id = ?", (user_id,)
            ).fetchone()
        if row is None:
            return None
        return self._row_to_profile(row)

    def list_users(self) -> List[str]:
        # Return all enrolled user IDs.
        with self._connect() as conn:
            rows = conn.execute("SELECT user_id FROM profiles").fetchall()
        return [r["user_id"] for r in rows]

    def delete_user(self, user_id: str) -> None:
        # Remove a user's profile and model files.
        with self._connect() as conn:
            conn.execute("DELETE FROM profiles WHERE user_id = ?", (user_id,))
        for path in [
            self._model_path(user_id, "ocsvm"),
            self._mahal_path(user_id),
        ]:
            if os.path.exists(path):
                os.remove(path)

    def _fit_models(self, user_id: str, vectors: List[List[float]]) -> None:
        # Train and persist OCSVM and Mahalanobis models from enrollment vectors.
        # OCSVM
        ocsvm = OCSVMModel()
        ocsvm.train(vectors)
        ocsvm.save(self._model_path(user_id, "ocsvm"))

        # Mahalanobis
        mahal = MahalanobisNormalizer()
        mahal.fit(vectors)
        import joblib
        joblib.dump(mahal, self._mahal_path(user_id))

    def _init_db(self) -> None:
        with self._connect() as conn:
            conn.execute(
                """
                CREATE TABLE IF NOT EXISTS profiles (
                    user_id         TEXT    PRIMARY KEY,
                    keystroke_count INTEGER NOT NULL DEFAULT 0,
                    status          TEXT    NOT NULL DEFAULT 'PENDING',
                    created_at      TEXT    NOT NULL,
                    updated_at      TEXT    NOT NULL,
                    min_keystrokes  INTEGER NOT NULL,
                    vectors_json    TEXT    NOT NULL DEFAULT '[]'
                )
                """
            )

    def _get_or_create_profile(self, user_id: str) -> UserProfile:
        existing = self.get_profile(user_id)
        if existing:
            return existing
        now = datetime.now(timezone.utc).isoformat()
        profile = UserProfile(
            user_id=user_id,
            keystroke_count=0,
            status="PENDING",
            created_at=now,
            updated_at=now,
            min_keystrokes=self.min_ks,
            vectors=[],
        )
        self._save_profile(profile)
        return profile

    def _save_profile(self, profile: UserProfile) -> None:
        with self._connect() as conn:
            conn.execute(
                """
                INSERT INTO profiles
                    (user_id, keystroke_count, status, created_at, updated_at,
                    min_keystrokes, vectors_json)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(user_id) DO UPDATE SET
                    keystroke_count = excluded.keystroke_count,
                    status          = excluded.status,
                    updated_at      = excluded.updated_at,
                    vectors_json    = excluded.vectors_json
                """,
                (
                    profile.user_id,
                    profile.keystroke_count,
                    profile.status,
                    profile.created_at,
                    profile.updated_at,
                    profile.min_keystrokes,
                    json.dumps(profile.vectors),
                ),
            )

    def _row_to_profile(self, row: sqlite3.Row) -> UserProfile:
        return UserProfile(
            user_id=row["user_id"],
            keystroke_count=row["keystroke_count"],
            status=row["status"],
            created_at=row["created_at"],
            updated_at=row["updated_at"],
            min_keystrokes=row["min_keystrokes"],
            vectors=json.loads(row["vectors_json"]),
        )

    def _connect(self) -> sqlite3.Connection:
        conn = sqlite3.connect(self.db_path)
        conn.row_factory = sqlite3.Row
        return conn

    def _model_path(self, user_id: str, model_name: str) -> str:
        safe_id = user_id.replace(" ", "_").lower()
        return os.path.join(self.model_dir, f"{safe_id}_{model_name}.joblib")

    def _mahal_path(self, user_id: str) -> str:
        safe_id = user_id.replace(" ", "_").lower()
        return os.path.join(self.model_dir, f"{safe_id}_mahal.joblib")
