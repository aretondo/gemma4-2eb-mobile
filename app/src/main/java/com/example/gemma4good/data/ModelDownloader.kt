package com.example.gemma4good.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import java.io.File

class ModelDownloader(private val context: Context) {

    private val modelUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
    private val modelFileName = "gemma-4-E2B-it.litertlm"

    fun isModelDownloaded(): Boolean {
        val file = File(context.getExternalFilesDir(null), modelFileName)
        // O modelo Gemma 4 E2B tem cerca de 2.6GB.
        return file.exists() && file.length() > 2000 * 1024 * 1024L // 2GB como margem mínima
    }

    fun getDownloadStatus(downloadId: Long): Pair<Int, Int> {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = try {
            downloadManager.query(query)
        } catch (e: Exception) {
            android.util.Log.e("ModelDownloader", "Query failed", e)
            null
        }
        
        var result = Pair(-1, -1)
        if (cursor != null && cursor.moveToFirst()) {
            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
            if (statusIndex != -1 && reasonIndex != -1) {
                result = Pair(cursor.getInt(statusIndex), cursor.getInt(reasonIndex))
            }
        }
        cursor?.close()
        return result
    }

    fun isDownloadComplete(downloadId: Long): Boolean {
        return getDownloadStatus(downloadId).first == DownloadManager.STATUS_SUCCESSFUL
    }

    fun downloadModel(): Long {
        // Hugging Face requer ?download=true para o link de resolve funcionar como stream de download
        val uri = Uri.parse("$modelUrl?download=true")
        val request = DownloadManager.Request(uri)
            .setTitle("Baixando Gemma 4 E2B")
            .setDescription("O cérebro do assistente está sendo preparado...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, null, modelFileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .addRequestHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .addRequestHeader("Accept", "*/*")

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val id = downloadManager.enqueue(request)
        android.util.Log.d("ModelDownloader", "Download enqueued with ID: $id for URL: $uri")
        return id
    }

    fun getModelPath(): String {
        return File(context.getExternalFilesDir(null), modelFileName).absolutePath
    }
}
