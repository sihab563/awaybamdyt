package com.example.data.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream
import kotlin.math.max

data class PendingAttachment(
    val type: String, // "IMAGE" or "FILE"
    val fileName: String,
    val mimeType: String,
    val base64Data: String
)

object AttachmentHelper {
    private const val MAX_IMAGE_DIM = 720
    private const val MAX_AVATAR_DIM = 240
    private const val MAX_BASE64_CHARS = 280_000
    private const val MAX_AVATAR_BASE64_CHARS = 160_000

    private val bitmapCache = LruCache<Int, ImageBitmap>(48)

    fun decodeImageBitmap(base64Data: String): ImageBitmap? {
        if (base64Data.isBlank()) return null
        val key = base64Data.hashCode()
        bitmapCache.get(key)?.let { return it }
        return runCatching {
            val bytes = Base64.decode(base64Data, Base64.DEFAULT)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val imageBmp = bmp.asImageBitmap()
            bitmapCache.put(key, imageBmp)
            imageBmp
        }.getOrNull()
    }

    fun compressAvatarUri(context: Context, uri: Uri): Result<String> =
        compressImageUriInternal(context, uri, MAX_AVATAR_DIM, MAX_AVATAR_BASE64_CHARS).map { it.base64Data }

    fun compressChatImageUri(context: Context, uri: Uri): Result<PendingAttachment> =
        compressImageUriInternal(context, uri, MAX_IMAGE_DIM, MAX_BASE64_CHARS)

    private fun compressImageUriInternal(
        context: Context,
        uri: Uri,
        maxDim: Int,
        maxBase64Chars: Int
    ): Result<PendingAttachment> = runCatching {
        val resolver = context.contentResolver
        val fileName = queryDisplayName(context, uri) ?: "nexus_image.jpg"
        val rawBytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Could not read selected image.")
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, boundsOpts)
        var sampleSize = 1
        while (boundsOpts.outWidth / sampleSize > maxDim * 2 || boundsOpts.outHeight / sampleSize > maxDim * 2) {
            sampleSize *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
            ?: error("Unsupported image format.")

        val scale = maxDim.toFloat() / max(decoded.width, decoded.height).toFloat()
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).toInt().coerceAtLeast(1),
                (decoded.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            decoded
        }

        var quality = 82
        var encoded: String
        do {
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            encoded = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            quality -= 12
        } while (encoded.length > maxBase64Chars && quality >= 30)

        if (encoded.length > maxBase64Chars) {
            error("Image too large after compression. Choose a smaller photo.")
        }

        PendingAttachment(
            type = "IMAGE",
            fileName = fileName,
            mimeType = "image/jpeg",
            base64Data = encoded
        )
    }

    fun readDocumentUri(context: Context, uri: Uri): Result<PendingAttachment> = runCatching {
        val resolver = context.contentResolver
        val fileName = queryDisplayName(context, uri) ?: "attachment.bin"
        val mimeType = resolver.getType(uri) ?: "application/octet-stream"
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Could not open selected file.")
        if (bytes.size > 200_000) {
            error("Files up to 200 KB are supported for instant chat sharing.")
        }
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        PendingAttachment(
            type = "FILE",
            fileName = fileName,
            mimeType = mimeType,
            base64Data = encoded
        )
    }

    fun saveAttachmentToUri(context: Context, destUri: Uri, base64Data: String): Result<Unit> = runCatching {
        val bytes = Base64.decode(base64Data, Base64.DEFAULT)
        context.contentResolver.openOutputStream(destUri)?.use { it.write(bytes) }
            ?: error("Unable to open destination file.")
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
    }
}
