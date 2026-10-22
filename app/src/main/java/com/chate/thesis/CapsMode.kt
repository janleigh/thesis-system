package com.chate.thesis

enum class CapsMode {
    OFF,
    ON_ONCE,
    LOCKED;

    fun isShifted(): Boolean = this == ON_ONCE || this == LOCKED

    fun nextMode(): CapsMode = when (this) {
        OFF -> ON_ONCE
        ON_ONCE -> LOCKED
        LOCKED -> OFF
    }

    fun onCharacterTyped(char: String): CapsMode {
        val isLetter = char.isNotEmpty() && char[0].isLetter()
        return if (this == ON_ONCE && isLetter) {
            OFF
        } else {
            this
        }
    }
}
