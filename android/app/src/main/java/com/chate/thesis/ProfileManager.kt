package com.chate.thesis

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.time.Instant

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------
private const val PRODUCTION_MIN_KEYSTROKES = 500
private const val TESTING_MIN_KEYSTROKES = 250

private const val TAG = "ProfileManager"

// ---------------------------------------------------------------------------
// Data class
// ---------------------------------------------------------------------------
data class UserProfile(
    val userId: String,
    var keystrokeCount: Int,
    var status: String,           // PENDING | READY | ACTIVE
    val createdAt: String,
    var updatedAt: String,
    val minKeystrokes: Int,
    val vectors: MutableList<DoubleArray>
) {
    /** Enrollment progress as a percentage (0‑100). */
    val progressPct: Double
        get() = minOf(keystrokeCount.toDouble() / minKeystrokes * 100.0, 100.0)

    /** True when the profile has been trained and is usable. */
    val isReady: Boolean
        get() = status == "READY" || status == "ACTIVE"
}

// ---------------------------------------------------------------------------
// SQLiteOpenHelper
// ---------------------------------------------------------------------------
private class ProfileDbHelper(context: Context) :
    SQLiteOpenHelper(context, "profiles.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS profiles (
                user_id        TEXT PRIMARY KEY,
                keystroke_count INTEGER NOT NULL DEFAULT 0,
                status         TEXT    NOT NULL DEFAULT 'PENDING',
                created_at     TEXT    NOT NULL,
                updated_at     TEXT    NOT NULL,
                min_keystrokes INTEGER NOT NULL,
                vectors_json   TEXT    NOT NULL DEFAULT '[]'
            )
            """.trimIndent()
        )
        Log.d(TAG, "profiles table created")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS profiles")
        onCreate(db)
    }
}

// ---------------------------------------------------------------------------
// ProfileManager
// ---------------------------------------------------------------------------
class ProfileManager(
    context: Context,
    private val testingMode: Boolean = true
) {
    private val dbHelper = ProfileDbHelper(context)
    private val modelDir: File = File(context.filesDir, "models").also { it.mkdirs() }
    private val minKs = if (testingMode) TESTING_MIN_KEYSTROKES else PRODUCTION_MIN_KEYSTROKES
    private val gson = Gson()

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Append a validated feature‑vector window to the user's enrollment
     * profile. When enough keystrokes and vectors have been collected the
     * OCSVM and Mahalanobis models are fitted automatically and the profile
     * transitions from PENDING → READY.
     */
    fun addEnrollmentWindow(userId: String, featureVector: FeatureVector): UserProfile {
        val profile = getOrCreateProfile(userId)

        if (!featureVector.isValid()) {
            Log.w(TAG, "Invalid feature vector for $userId — skipping")
            return profile
        }

        val statArr = featureVector.toStatArray()
        profile.vectors.add(statArr)
        profile.keystrokeCount += featureVector.keystrokeCount

        val now = Instant.now().toString()
        profile.updatedAt = now

        if (profile.status == "PENDING"
            && profile.keystrokeCount >= profile.minKeystrokes
            && profile.vectors.size >= 5
        ) {
            Log.i(TAG, "Enrollment threshold reached for $userId — fitting models")
            fitModels(userId, profile.vectors)
            profile.status = "READY"
        }

        saveProfile(profile)
        return profile
    }

    /** Load a previously trained OCSVM model from disk, or null. */
    fun getOcsvm(userId: String): OCSVMModel? {
        val file = File(modelDir, "${userId}_ocsvm.json")
        if (!file.exists()) {
            Log.w(TAG, "OCSVM model file not found for $userId")
            return null
        }
        return try {
            val json = file.readText()
            OCSVMModel.fromJson(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load OCSVM model for $userId", e)
            null
        }
    }

    /** Load a previously trained Mahalanobis normalizer from disk, or null. */
    fun getMahalanobis(userId: String): MahalanobisNormalizer? {
        val file = File(modelDir, "${userId}_mahalanobis.json")
        if (!file.exists()) {
            Log.w(TAG, "Mahalanobis model file not found for $userId")
            return null
        }
        return try {
            val json = file.readText()
            MahalanobisNormalizer.fromJson(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Mahalanobis model for $userId", e)
            null
        }
    }

    /** Retrieve a single profile, or null if it does not exist. */
    fun getProfile(userId: String): UserProfile? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "profiles",
            null,
            "user_id = ?",
            arrayOf(userId),
            null, null, null
        )
        cursor.use {
            if (!it.moveToFirst()) return null
            return cursorToProfile(it)
        }
    }

    /** Return every enrolled user id. */
    fun listUsers(): List<String> {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "profiles",
            arrayOf("user_id"),
            null, null, null, null,
            "user_id ASC"
        )
        val users = mutableListOf<String>()
        cursor.use {
            while (it.moveToNext()) {
                users.add(it.getString(it.getColumnIndexOrThrow("user_id")))
            }
        }
        return users
    }

    /** Delete a user's profile and all associated model files. */
    fun deleteUser(userId: String) {
        val db = dbHelper.writableDatabase
        val deleted = db.delete("profiles", "user_id = ?", arrayOf(userId))
        Log.i(TAG, "Deleted $deleted profile row(s) for $userId")

        // Clean up model files
        File(modelDir, "${userId}_ocsvm.json").delete()
        File(modelDir, "${userId}_mahalanobis.json").delete()
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    fun getOrCreateProfile(userId: String): UserProfile {
        val existing = getProfile(userId)
        if (existing != null) return existing

        val now = Instant.now().toString()
        val profile = UserProfile(
            userId = userId,
            keystrokeCount = 0,
            status = "PENDING",
            createdAt = now,
            updatedAt = now,
            minKeystrokes = minKs,
            vectors = mutableListOf()
        )
        saveProfile(profile)
        Log.i(TAG, "Created new profile for $userId (minKs=$minKs)")
        return profile
    }

    private fun fitModels(userId: String, vectors: MutableList<DoubleArray>) {
        val vectorList: List<DoubleArray> = vectors.toList()

        // Train & persist OCSVM
        val ocsvm = OCSVMModel()
        ocsvm.train(vectorList)
        val ocsvmJson = ocsvm.toJson()
        File(modelDir, "${userId}_ocsvm.json").writeText(ocsvmJson)
        Log.d(TAG, "OCSVM model saved for $userId (${ocsvmJson.length} chars)")

        // Train & persist Mahalanobis normalizer
        val mahal = MahalanobisNormalizer()
        mahal.fit(vectorList)
        val mahalJson = mahal.toJson()
        File(modelDir, "${userId}_mahalanobis.json").writeText(mahalJson)
        Log.d(TAG, "Mahalanobis model saved for $userId (${mahalJson.length} chars)")
    }

    private fun saveProfile(profile: UserProfile) {
        val db = dbHelper.writableDatabase
        val vectorsJson = gson.toJson(profile.vectors.map { it.toList() })

        val cv = ContentValues().apply {
            put("user_id", profile.userId)
            put("keystroke_count", profile.keystrokeCount)
            put("status", profile.status)
            put("created_at", profile.createdAt)
            put("updated_at", profile.updatedAt)
            put("min_keystrokes", profile.minKeystrokes)
            put("vectors_json", vectorsJson)
        }
        db.insertWithOnConflict("profiles", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun cursorToProfile(cursor: android.database.Cursor): UserProfile {
        val vectorsJson = cursor.getString(cursor.getColumnIndexOrThrow("vectors_json"))
        val listType = object : TypeToken<List<List<Double>>>() {}.type
        val rawVectors: List<List<Double>> = gson.fromJson(vectorsJson, listType)
        val vectors = rawVectors.map { it.toDoubleArray() }.toMutableList()

        return UserProfile(
            userId = cursor.getString(cursor.getColumnIndexOrThrow("user_id")),
            keystrokeCount = cursor.getInt(cursor.getColumnIndexOrThrow("keystroke_count")),
            status = cursor.getString(cursor.getColumnIndexOrThrow("status")),
            createdAt = cursor.getString(cursor.getColumnIndexOrThrow("created_at")),
            updatedAt = cursor.getString(cursor.getColumnIndexOrThrow("updated_at")),
            minKeystrokes = cursor.getInt(cursor.getColumnIndexOrThrow("min_keystrokes")),
            vectors = vectors
        )
    }
}
