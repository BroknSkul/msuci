package com.musicplayer.app.util

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FFmpegUtil {

    fun init(context: Context) {
        try {
            FFmpeg.getInstance().init(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun convertToMp3(inputPath: String, outputPath: String): Boolean = withContext(Dispatchers.IO) {
        val request = YoutubeDLRequest(inputPath)
        request.addOption("-o", outputPath)
        request.addOption("--extract-audio")
        request.addOption("--audio-format", "mp3")
        request.addOption("--audio-quality", "0") // 0 is best
        
        try {
            YoutubeDL.getInstance().execute(request)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
