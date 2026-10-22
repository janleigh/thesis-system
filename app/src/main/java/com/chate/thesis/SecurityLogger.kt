package com.chate.thesis

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyStore
import java.time.Instant
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------
private const val TAG = "SecurityLogger"
private const val KEY_ALIAS = "biometric_log_key"
private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
private const val GCM_IV_LENGTH = 12 // bytes
private const val GCM_TAG_LENGTH = 128 // bits

// ---------------------------------------------------------------------------
// Data class
// ---------------------------------------------------------------------------
data class LogEntry(
    val id: Long,
    val userId: String,
    val timestamp: String,
    val eventType: String,
    val riskScore: Double?,
    val action: String?,
    // decrypted JSON payload
    val payload: String,
)

// ---------------------------------------------------------------------------
// SQLiteOpenHelper
// ---------------------------------------------------------------------------
private class SecurityLogDbHelper(context: Context) :
    SQLiteOpenHelper(context, "security_log.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS security_log (
                id           INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id      TEXT    NOT NULL,
                timestamp    TEXT    NOT NULL,
                event_type   TEXT    NOT NULL,
                risk_score   REAL,
                action       TEXT,
                payload_enc  BLOB    NOT NULL
            )
            """.trimIndent(),
        )
        Log.d(TAG, "security_log table created")
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int,
    ) {
        db.execSQL("DROP TABLE IF EXISTS security_log")
        onCreate(db)
    }
}

// ---------------------------------------------------------------------------
// SecurityLogger
// ---------------------------------------------------------------------------
class SecurityLogger(context: Context) {
    private val dbHelper = SecurityLogDbHelper(context)
    private val secretKey: SecretKey = loadOrCreateKey()

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Write an encrypted security event to the log.
     *
     * @param userId    Identifier of the user the event pertains to.
     * @param eventType Short string label (e.g. "AUTH_SUCCESS", "AUTH_FAIL").
     * @param payload   Arbitrary JSON‑serializable payload string.
     * @param riskScore Optional numeric risk score.
     * @param action    Optional action label.
     */
    fun log(
        userId: String,
        eventType: String,
        payload: String,
        riskScore: Double? = null,
        action: String? = null,
    ) {
        val ts = Instant.now().toString()
        val encryptedPayload = encrypt(payload.toByteArray(Charsets.UTF_8))

        val db = dbHelper.writableDatabase
        val cv =
            ContentValues().apply {
                put("user_id", userId)
                put("timestamp", ts)
                put("event_type", eventType)
                if (riskScore != null) put("risk_score", riskScore) else putNull("risk_score")
                if (action != null) put("action", action) else putNull("action")
                put("payload_enc", encryptedPayload)
            }
        db.insert("security_log", null, cv)
        Log.d(TAG, "Logged event '$eventType' for $userId")
    }

    /**
     * Read all log entries, optionally filtered by [userId].
     * Each payload is decrypted before being returned.
     */
    fun readAll(userId: String? = null): List<LogEntry> {
        val db = dbHelper.readableDatabase
        val (selection, args) =
            if (userId != null) {
                "user_id = ?" to arrayOf(userId)
            } else {
                null to null
            }

        val cursor =
            db.query(
                "security_log",
                null,
                selection,
                args,
                null,
                null,
                "id ASC",
            )

        val entries = mutableListOf<LogEntry>()
        cursor.use {
            while (it.moveToNext()) {
                try {
                    val payloadEnc = it.getBlob(it.getColumnIndexOrThrow("payload_enc"))
                    val payloadPlain = String(decrypt(payloadEnc), Charsets.UTF_8)

                    val riskIdx = it.getColumnIndexOrThrow("risk_score")
                    val actionIdx = it.getColumnIndexOrThrow("action")

                    entries.add(
                        LogEntry(
                            id = it.getLong(it.getColumnIndexOrThrow("id")),
                            userId = it.getString(it.getColumnIndexOrThrow("user_id")),
                            timestamp = it.getString(it.getColumnIndexOrThrow("timestamp")),
                            eventType = it.getString(it.getColumnIndexOrThrow("event_type")),
                            riskScore = if (it.isNull(riskIdx)) null else it.getDouble(riskIdx),
                            action = if (it.isNull(actionIdx)) null else it.getString(actionIdx),
                            payload = payloadPlain,
                        ),
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to decrypt log entry, skipping", e)
                }
            }
        }
        return entries
    }

    /**
     * Count log entries, optionally filtered by [userId].
     */
    fun count(userId: String? = null): Long {
        val db = dbHelper.readableDatabase
        return if (userId != null) {
            android.database.DatabaseUtils.queryNumEntries(
                db,
                "security_log",
                "user_id = ?",
                arrayOf(userId),
            )
        } else {
            android.database.DatabaseUtils.queryNumEntries(db, "security_log")
        }
    }

    /**
     * Delete log entries, optionally filtered by [userId].
     * If [userId] is null, all entries are removed.
     */
    fun clear(userId: String? = null) {
        val db = dbHelper.writableDatabase
        val deleted =
            if (userId != null) {
                db.delete("security_log", "user_id = ?", arrayOf(userId))
            } else {
                db.delete("security_log", null, null)
            }
        Log.i(TAG, "Cleared $deleted log entries" + if (userId != null) " for $userId" else "")
    }

    // -----------------------------------------------------------------------
    // Encryption helpers — AES-GCM via Android Keystore
    // -----------------------------------------------------------------------

    /**
     * Encrypt [plaintext] and return IV ∥ ciphertext (IV is first 12 bytes).
     */
    private fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv // auto‑generated 12‑byte IV
        val ciphertext = cipher.doFinal(plaintext)
        // Prepend IV so we can retrieve it during decryption
        return iv + ciphertext
    }

    /**
     * Decrypt [data] where the first 12 bytes are the IV and the rest is
     * ciphertext (including the GCM auth tag).
     */
    private fun decrypt(data: ByteArray): ByteArray {
        val iv = data.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = data.copyOfRange(GCM_IV_LENGTH, data.size)

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        return cipher.doFinal(ciphertext)
    }

    // -----------------------------------------------------------------------
    // Key management
    // -----------------------------------------------------------------------

    /**
     * Load the AES key from the Android Keystore, generating one if it does
     * not yet exist.
     */
    private fun loadOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        val existingKey = keyStore.getEntry(KEY_ALIAS, null)
        if (existingKey is KeyStore.SecretKeyEntry) {
            Log.d(TAG, "Loaded existing AES key from Keystore")
            return existingKey.secretKey
        }

        Log.i(TAG, "Generating new AES-GCM key in Android Keystore")
        val keyGen =
            KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore",
            )
        val spec =
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()

        keyGen.init(spec)
        return keyGen.generateKey()
    }
}
