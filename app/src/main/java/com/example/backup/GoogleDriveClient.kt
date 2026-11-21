package com.example.backup

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class DriveAuthExpiredException(message: String) : IOException(message)

interface GoogleDriveApi {
    suspend fun fetchUserEmail(accessToken: String): String?
    suspend fun findLatestBackupFile(accessToken: String): DriveBackupMetadata?
    suspend fun uploadBackupJson(
        accessToken: String,
        jsonContent: String,
        existingFileId: String?,
        transactionCount: Int,
        rosterCount: Int,
        balance: Long
    ): DriveBackupMetadata
    suspend fun downloadBackupJson(accessToken: String, fileId: String): String
}

class OkHttpGoogleDriveApi(
    private val httpClient: OkHttpClient = defaultClient()
) : GoogleDriveApi {

    override suspend fun fetchUserEmail(accessToken: String): String? {
        val request = Request.Builder()
            .url(USERINFO_URL)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        val bodyStr = executeAndReadBody(request)
        return parseEmailSafely(bodyStr)
    }

    override suspend fun findLatestBackupFile(accessToken: String): DriveBackupMetadata? {
        val query = URLEncoder.encode(
            "name = '$BACKUP_FILE_NAME' and trashed = false",
            "UTF-8"
        )
        val fields = URLEncoder.encode("files(id,name,modifiedTime,size,appProperties)", "UTF-8")
        val orderBy = URLEncoder.encode("modifiedTime desc", "UTF-8")
        val url = "$DRIVE_FILES_URL?q=$query&spaces=drive&fields=$fields&orderBy=$orderBy&pageSize=5"

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        val responseBody = executeAndReadBody(request)
        val root = JSONObject(responseBody)
        val files = root.optJSONArray("files") ?: return null
        if (files.length() == 0) return null
        val first = files.optJSONObject(0) ?: return null
        return parseFileMetadata(first)
    }

    override suspend fun uploadBackupJson(
        accessToken: String,
        jsonContent: String,
        existingFileId: String?,
        transactionCount: Int,
        rosterCount: Int,
        balance: Long
    ): DriveBackupMetadata {
        val targetFileId = existingFileId ?: findLatestBackupFile(accessToken)?.fileId
        val folderId = if (targetFileId == null) findOrCreateBackupFolder(accessToken) else null

        val metadataJson = JSONObject().apply {
            put("name", BACKUP_FILE_NAME)
            put("mimeType", JSON_MIME_TYPE)
            put("description", "Backup otomatis Room Database aplikasi Kas Kelas")
            if (targetFileId == null && !folderId.isNullOrBlank()) {
                put("parents", JSONArray().put(folderId))
            }
            put("appProperties", JSONObject().apply {
                put("txCount", transactionCount.toString())
                put("rosterCount", rosterCount.toString())
                put("balance", balance.toString())
                put("schemaVersion", KasBackupPayload.CURRENT_SCHEMA_VERSION.toString())
            })
        }.toString()

        val multipartBody = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(metadataJson.toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .addPart(jsonContent.toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .build()

        val fields = URLEncoder.encode("id,name,modifiedTime,size,appProperties", "UTF-8")
        val requestBuilder = Request.Builder()
            .header("Authorization", "Bearer $accessToken")

        if (targetFileId != null) {
            requestBuilder
                .url("$DRIVE_UPLOAD_URL/$targetFileId?uploadType=multipart&fields=$fields")
                .patch(multipartBody)
        } else {
            requestBuilder
                .url("$DRIVE_UPLOAD_URL?uploadType=multipart&fields=$fields")
                .post(multipartBody)
        }

        val responseBody = executeAndReadBody(requestBuilder.build())
        return parseFileMetadata(JSONObject(responseBody))
    }

    override suspend fun downloadBackupJson(accessToken: String, fileId: String): String {
        val request = Request.Builder()
            .url("$DRIVE_FILES_URL/$fileId?alt=media")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        return executeAndReadBody(request)
    }

    private fun findOrCreateBackupFolder(accessToken: String): String? {
        return runCatching {
            val query = URLEncoder.encode(
                "name = '$BACKUP_FOLDER_NAME' and mimeType = 'application/vnd.google-apps.folder' and trashed = false",
                "UTF-8"
            )
            val fields = URLEncoder.encode("files(id,name)", "UTF-8")
            val searchRequest = Request.Builder()
                .url("$DRIVE_FILES_URL?q=$query&spaces=drive&fields=$fields&pageSize=1")
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            val searchBody = executeAndReadBody(searchRequest)
            val files = JSONObject(searchBody).optJSONArray("files")
            if (files != null && files.length() > 0) {
                return files.optJSONObject(0)?.optString("id")?.takeIf { it.isNotBlank() }
            }

            val folderJson = JSONObject().apply {
                put("name", BACKUP_FOLDER_NAME)
                put("mimeType", "application/vnd.google-apps.folder")
            }.toString()

            val createRequest = Request.Builder()
                .url("$DRIVE_FILES_URL?fields=id")
                .header("Authorization", "Bearer $accessToken")
                .post(folderJson.toRequestBody("application/json; charset=UTF-8".toMediaType()))
                .build()

            val createBody = executeAndReadBody(createRequest)
            JSONObject(createBody).optString("id").takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun executeAndReadBody(request: Request): String {
        httpClient.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            checkAuthOrThrow(response.code, bodyStr)
            if (!response.isSuccessful) {
                throw IOException("Google Drive error (${response.code}): ${extractErrorMessage(bodyStr)}")
            }
            return bodyStr
        }
    }

    private fun checkAuthOrThrow(code: Int, body: String) {
        if (code == 401) {
            throw DriveAuthExpiredException("Sesi otorisasi Google Drive berakhir, silakan hubungkan ulang.")
        }
        if (code == 403 && body.contains("insufficient", ignoreCase = true)) {
            throw DriveAuthExpiredException("Izin Google Drive belum diberikan, silakan setujui akses Drive.")
        }
    }

    private fun parseEmailSafely(bodyStr: String): String? {
        if (bodyStr.isBlank()) return null
        return runCatching {
            JSONObject(bodyStr).optString("email").takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun extractErrorMessage(body: String): String {
        return runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message")
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Gagal terhubung ke Google Drive."
    }

    private fun parseFileMetadata(obj: JSONObject): DriveBackupMetadata {
        val props = obj.optJSONObject("appProperties")
        return DriveBackupMetadata(
            fileId = obj.optString("id", ""),
            fileName = obj.optString("name", BACKUP_FILE_NAME),
            modifiedTimeMillis = parseIso8601Millis(obj.optString("modifiedTime", "")),
            sizeBytes = obj.optString("size", "0").toLongOrNull() ?: 0L,
            transactionCount = props?.optString("txCount", "")?.toIntOrNull(),
            rosterCount = props?.optString("rosterCount", "")?.toIntOrNull(),
            balance = props?.optString("balance", "")?.toLongOrNull()
        )
    }

    private fun parseIso8601Millis(iso: String): Long {
        if (iso.isBlank()) return System.currentTimeMillis()
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
        )
        for (pattern in formats) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.parse(iso)?.time
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return System.currentTimeMillis()
    }

    companion object {
        const val BACKUP_FOLDER_NAME = "Kas Kelas Backup"
        const val BACKUP_FILE_NAME = "kaskelas_room_backup.json"
        private const val JSON_MIME_TYPE = "application/json"
        private const val USERINFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo"
        private const val DRIVE_FILES_URL = "https://www.googleapis.com/drive/v3/files"
        private const val DRIVE_UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"

        private fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
        }
    }
}
