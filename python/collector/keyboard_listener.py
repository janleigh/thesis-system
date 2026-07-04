"""
tldr:
this module captures keyboard events in real-time on a pc using `pynput` and
batches them into tuples-recording the key, action (up/down), and timestamp.
acts as a bandage until we write this shit to native android cause android is better
"""

from __future__ import annotations

import threading
import time
from typing import Callable, List, Optional

from pynput import keyboard

from engine.feature_extractor import KeyEvent

# Type for the window callback
WindowCallback = Callable[[List[KeyEvent], int], None]

class KeyboardListener:
    """
    the class name explains it all.

    window_size    : int    — number of keystrokes per window (default 30)
    window_timeout : float  — max seconds before forcing a window flush (default 30.0)
    on_window      : callable(events, keystroke_count) — called when a window is complete
    stop_key       : str    — key name that stops the listener (default 'esc')
    """
    def __init__(
        self,
        window_size:    int            = 30,
        window_timeout: float          = 30.0,
        on_window:      Optional[WindowCallback] = None,
        stop_key:       str            = "esc",
    ) -> None:
        self.window_size    = window_size
        self.window_timeout = window_timeout
        self.on_window      = on_window
        self.stop_key       = stop_key

        self._events:        List[KeyEvent] = []
        self._down_count:    int            = 0
        self._lock:          threading.Lock = threading.Lock()
        self._listener:      Optional[keyboard.Listener] = None
        self._timer:         Optional[threading.Timer]   = None
        self._stopped:       bool           = False

    def start(self) -> None:
        # Start listening for keyboard events (non-blocking)
        self._listener = keyboard.Listener(
            on_press=self._on_press,
            on_release=self._on_release,
        )
        self._listener.start()
        self._reset_timer()

    def stop(self) -> None:
        # Stop the listener and flush remaining events
        self._stopped = True
        if self._timer:
            self._timer.cancel()
        if self._listener:
            self._listener.stop()
        self._flush_window()

    def join(self) -> None:
        if self._listener:
            self._listener.join()

    def _on_press(self, key) -> Optional[bool]:
        if self._stopped:
            return False
        ts_ms = time.time() * 1000.0
        key_label = self._key_to_str(key)

        if key_label == self.stop_key:
            self.stop()
            return False  # Stop pynput listener

        with self._lock:
            self._events.append((key_label, "down", ts_ms))
            self._down_count += 1
            if self._down_count >= self.window_size:
                self._flush_and_reset()

    def _on_release(self, key) -> None:
        if self._stopped:
            return
        ts_ms = time.time() * 1000.0
        key_label = self._key_to_str(key)
        with self._lock:
            self._events.append((key_label, "up", ts_ms))

    def _flush_and_reset(self) -> None:
        self._flush_window(locked=True)
        self._events     = []
        self._down_count = 0
        self._reset_timer()

    def _flush_window(self, locked: bool = False) -> None:
        if locked:
            events_snapshot = list(self._events)
            count           = self._down_count
        else:
            with self._lock:
                events_snapshot = list(self._events)
                count           = self._down_count

        if events_snapshot and self.on_window:
            self.on_window(events_snapshot, count)

    def _reset_timer(self) -> None:
        if self._timer:
            self._timer.cancel()
        self._timer = threading.Timer(self.window_timeout, self._timeout_flush)
        self._timer.daemon = True
        self._timer.start()

    def _timeout_flush(self) -> None:
        with self._lock:
            if self._events:
                self._flush_and_reset()

    @staticmethod
    def _key_to_str(key) -> str:
        try:
            return key.char if key.char else str(key)
        except AttributeError:
            return str(key).replace("Key.", "")
