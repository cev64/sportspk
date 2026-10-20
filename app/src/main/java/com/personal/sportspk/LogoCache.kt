package com.personal.sportspk

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Downloads team logos once, shrinks them to widget size and keeps them on disk. */
object LogoCache {
    private const val SIZE_PX = 96

    private fun dir(context: Context) = File(context.filesDir, "logos").apply { mkdirs() }

    private fun fileFor(context: Context, url: String) =
        File(dir(context), Integer.toHexString(url.hashCode()) + "_$SIZE_PX.png")

    fun ensure(context: Context, url: String) {
        val file = fileFor(context, url)
        if (file.exists()) return
        runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("User-Agent", GameRepository.USER_AGENT)
            val source = conn.inputStream.use { BitmapFactory.decodeStream(it) } ?: return
            val scaled = Bitmap.createScaledBitmap(source, SIZE_PX, SIZE_PX, true)
            val tmp = File(file.path + ".tmp")
            tmp.outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
            tmp.renameTo(file)
        }
    }

    fun load(context: Context, url: String?): Bitmap? {
        if (url == null) return null
        val file = fileFor(context, url)
        return if (file.exists()) BitmapFactory.decodeFile(file.path) else null
    }
}
