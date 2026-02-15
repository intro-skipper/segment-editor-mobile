package org.introskipper.segmenteditor.ui.util

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun getDominantColor(context: Context, imageUrl: String): Int? {
    return withContext(Dispatchers.IO) {
        try {
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                .allowHardware(false) // Palette requires software bitmaps
                .build()

            val result = (loader.execute(request) as SuccessResult).drawable
            val bitmap = (result as BitmapDrawable).bitmap

            val palette = Palette.from(bitmap).generate()
            palette.getDominantColor(0)
        } catch (e: Exception) {
            null
        }
    }
}
