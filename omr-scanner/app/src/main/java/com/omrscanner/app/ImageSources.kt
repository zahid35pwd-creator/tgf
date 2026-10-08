package com.omrscanner.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import com.omrscanner.core.ArgbImage
import java.io.File
import kotlin.math.max

/** Getting a picture of the sheet: from the camera, the gallery, or another app. */
object ImageSources {
    const val REQUEST_CAMERA = 101
    const val REQUEST_GALLERY = 102

    private fun captureFile(context: Context) = File(FilesProvider.sharedDir(context), "capture.jpg")

    fun openCamera(activity: Activity) {
        val file = captureFile(activity)
        file.delete()
        val uri = FilesProvider.uriFor(file)
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            clipData = ClipData.newRawUri("photo", uri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            activity.startActivityForResult(intent, REQUEST_CAMERA)
        } catch (e: ActivityNotFoundException) {
            activity.toast("No camera app found. Pick a picture from the gallery instead.")
        }
    }

    fun openGallery(activity: Activity) {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        try {
            activity.startActivityForResult(Intent.createChooser(intent, "Choose the scanned sheet"), REQUEST_GALLERY)
        } catch (e: ActivityNotFoundException) {
            activity.toast("No gallery or file app found.")
        }
    }

    /** The picture chosen in an onActivityResult call, or null if the user cancelled. */
    fun resultUri(context: Context, requestCode: Int, resultCode: Int, data: Intent?): Uri? {
        if (resultCode != Activity.RESULT_OK) return null
        return when (requestCode) {
            REQUEST_CAMERA -> captureFile(context).takeIf { it.length() > 0 }?.let { FilesProvider.uriFor(it) }
            REQUEST_GALLERY -> data?.data
            else -> null
        }
    }

    /** The picture behind a share or "open with" intent. */
    @Suppress("DEPRECATION")
    fun sharedUri(intent: Intent): Uri? = when (intent.action) {
        Intent.ACTION_SEND -> intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
        else -> intent.data
    }

    /** Decodes [uri] at a size suited to the reader (longer side roughly 1800–3600 px). */
    fun load(context: Context, uri: Uri): ArgbImage? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val stream = resolver.openInputStream(uri) ?: return null
        stream.use { BitmapFactory.decodeStream(it, null, bounds) } // only fills in the size
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1800) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val pixels = IntArray(bmp.width * bmp.height)
        bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        val image = ArgbImage(bmp.width, bmp.height, pixels)
        bmp.recycle()
        return image
    }
}
