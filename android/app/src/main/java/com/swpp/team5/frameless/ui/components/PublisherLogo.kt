package com.swpp.team5.frameless.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.swpp.team5.frameless.R
import com.swpp.team5.frameless.data.PublisherItem
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Brand artwork is not tinted or cropped; the name next to it supplies its accessible label. */
@Composable
internal fun PublisherLogo(publisher: PublisherItem, modifier: Modifier = Modifier.size(38.dp)) {
    val context = LocalContext.current.applicationContext
    val bitmap by produceState<Bitmap?>(null, publisher.code, publisher.logoUrl) {
        value = withContext(Dispatchers.IO) { PublisherLogoImages.bundled(context, publisher.code) }
        publisher.logoUrl?.let { url ->
            val remote = withContext(Dispatchers.IO) { PublisherLogoImages.remote(url) }
            if (remote != null) value = remote
        }
    }
    Box(
        modifier.clip(RoundedCornerShape(12.dp)).background(Color.White)
            .testTag("publisher-logo-${publisher.code}"),
        contentAlignment = Alignment.Center
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(3.dp)
            )
        } else {
            Icon(
                painterResource(R.drawable.ic_portal_news),
                contentDescription = null,
                tint = Color(0xFF7B8493),
                modifier = Modifier.size(25.dp)
            )
        }
    }
}

private object PublisherLogoImages {
    private val cache = object : LruCache<String, Bitmap>(2 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
    }

    fun bundled(context: Context, code: String): Bitmap? {
        if (!code.matches(Regex("[a-z0-9_-]+"))) return null
        val key = "asset:$code"
        cache.get(key)?.let { return it }
        return runCatching {
            context.assets.open("publishers/$code.png").use(BitmapFactory::decodeStream)
        }.getOrNull()?.also { cache.put(key, it) }
    }

    fun remote(url: String): Bitmap? {
        cache.get(url)?.let { return it }
        return runCatching {
            val location = URL(url)
            if (location.protocol != "https") return null
            val connection = location.openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 4_000
                connection.readTimeout = 4_000
                connection.instanceFollowRedirects = false
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
                val bytes = connection.inputStream.use { it.readBytesLimited(1_048_576) } ?: return null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth !in 1..2048 || bounds.outHeight !in 1..2048) return null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } finally {
                connection.disconnect()
            }
        }.getOrNull()?.also { cache.put(url, it) }
    }

    private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray? {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            if (output.size() + count > limit) return null
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
