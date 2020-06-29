package org.roborox.whatsapp

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.RectF
import android.util.Log
import com.facebook.react.bridge.*
import kotlinx.coroutines.*
import kotlinx.serialization.UnstableDefault
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception
import java.net.URL
import kotlin.math.min
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime


@UnstableDefault
class WhatsAppStickersShareModule(
        private val reactContext: ReactApplicationContext
) : ReactContextBaseJavaModule(reactContext), ActivityEventListener {
    init {
        reactContext.addActivityEventListener(this)
    }

    override fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != REQUEST_CODE_ADD_PACK || data === null) return
        if (resultCode == Activity.RESULT_CANCELED) {
            val error = data.getStringExtra("validation_error")
            Log.e(TAG, "Failed to add pack: $error")
            return
        }
        Log.e(TAG, "Pack added")
    }

    override fun onNewIntent(intent: Intent?) { }

    override fun getName() = "WhatsAppStickersShare"

    private suspend fun packDir(identifier: String) = withContext(Dispatchers.IO) {
        val cacheDir = this@WhatsAppStickersShareModule.reactContext.externalCacheDir!!
        val stickersDir = File(cacheDir.absolutePath + File.separator + STICKERS_FOLDER_NAME)
        if (!stickersDir.exists()) stickersDir.mkdir()
        val packDir = File(stickersDir.absolutePath + File.separator + identifier)
        if (!packDir.exists()) packDir.mkdir()
        packDir
    }

    private fun packDirUnsafe(identifier: String): File {
        val cacheDir = this@WhatsAppStickersShareModule.reactContext.externalCacheDir!!
        val stickersDir = File(cacheDir.absolutePath + File.separator + STICKERS_FOLDER_NAME)
        return File(stickersDir.absolutePath + File.separator + identifier)
    }

    private fun scaleCenterCrop(source: Bitmap, size: Int): Bitmap {
        val sourceWidth = source.width
        val sourceHeight = source.height

        val xScale = size.toFloat() / sourceWidth
        val yScale = size.toFloat() / sourceHeight
        val scale = min(xScale, yScale)

        val scaledWidth = scale * sourceWidth
        val scaledHeight = scale * sourceHeight

        val left = (size - scaledWidth) / 2
        val top = (size - scaledHeight) / 2

        val targetRect = RectF(left, top, left + scaledWidth, top + scaledHeight)

        val dest = Bitmap.createBitmap(size, size, source.config)
        val canvas = Canvas(dest)
        canvas.drawBitmap(source, null, targetRect, null)
        return dest
    }

    private fun storeImage(imageUrl: String, file: File, size: Int, compress: Bitmap.CompressFormat, quality: Int) {
        URL(imageUrl).openStream().use { input -> FileOutputStream(file).use { output ->
            val source = BitmapFactory.decodeStream(input)
            val image = if (source.width == size && source.height == size) { source } else {
                scaleCenterCrop(source, size)
            }
            image.compress(compress, quality, output)
        } }
    }

    private suspend fun storeTrayImage(imageUrl: String, identifier: String): String {
        val file = File(packDir(identifier).absolutePath + File.separator + TRAY_IMAGE_NAME)
        withContext(Dispatchers.IO) {
            storeImage(imageUrl, file, TRAY_IMAGE_SIZE, Bitmap.CompressFormat.PNG, 100)
        }
        return file.name
    }

    private suspend fun storeStickerImage(imageUrl: String, packIdentifier: String): String {
        @Suppress("BlockingMethodInNonBlockingContext")
        val file = File.createTempFile(STICKER_IMAGE_PREFIX, STICKER_IMAGE_SUFFIX, packDir(packIdentifier))
        storeImage(imageUrl, file, STICKER_IMAGE_SIZE, Bitmap.CompressFormat.WEBP, 50)
        return file.name
    }

    private suspend fun createStickerPack(config: ReadableMap): StickerPack {
        val identifier = config.getString("identifier")!!
        val title = config.getString("title")!!
        val trayImageFileName = storeTrayImage(config.getString("trayImage")!!, identifier)
        val stickerPack = StickerPack(
                identifier = identifier,
                name = title,
                trayImageFileName = trayImageFileName,
                publisher = config.getString("author")!!,
                publisherEmail = config.getString("publisherEmail")!!,
                publisherWebsite = config.getString("publisherURL")!!,
                privacyPolicyWebsite = config.getString("privacyPolicyURL")!!,
                licenseAgreementWebsite = config.getString("licenseURL")!!,
                imageDataVersion = "1",
                avoidCache = false
        )

        val stickers = config.getArray("stickers")!!
        val promises = ArrayList<Deferred<Unit>>()
        for (index in 0 until stickers.size()) {
            val sticker = stickers.getMap(index)
            if (sticker === null) continue
            val imageURL = sticker.getString("url")!!
            val emojis = ArrayList<String>()
            if (sticker.hasKey("emojis")) {
                val emojisReadable = sticker.getArray("emojis")!!
                emojis.ensureCapacity(emojisReadable.size())
                for (emojiIndex in 0 until emojisReadable.size()) {
                    val emoji = emojisReadable.getString(emojiIndex)
                    if (emoji !== null) emojis.add(emoji)
                }
            }
            promises.add(GlobalScope.async(Dispatchers.IO) {
                val image = storeStickerImage(imageURL, identifier)
                stickerPack.stickers.add(Sticker(image, emojis))
                Unit
            })
        }
        awaitAll(*promises.toTypedArray())

        return stickerPack
    }

    @ExperimentalTime
    @ReactMethod
    fun share(config: ReadableMap, promise: Promise) {
        GlobalScope.launch {
            try {
                val packDir = packDirUnsafe(config.getString("identifier")!!)
                if (packDir.exists()) {
                    packDir.deleteRecursively()
                }

                val stickerPack: StickerPack
                val duration = measureTime { stickerPack = createStickerPack(config) }
                Log.d(TAG, "createStickerPack: $duration")
                withContext(Dispatchers.IO) {
                    val json = Json.stringify(StickerPack.serializer(), stickerPack)
                    val metaFile = File(packDir.absolutePath + File.separator + METADATA_FILENAME)
                    metaFile.writeText(json)
                }

                val intent = Intent()
                intent.action = ACTION_ADD_PACK
                intent.putExtra(EXTRA_STICKER_PACK_ID, stickerPack.identifier)
                intent.putExtra(EXTRA_STICKER_PACK_AUTHORITY, BuildConfig.CONTENT_PROVIDER_AUTHORITY)
                intent.putExtra(EXTRA_STICKER_PACK_NAME, stickerPack.name)

                val activity = currentActivity
                if (activity !== null && activity.packageManager.resolveActivity(intent, 0) !== null) {
                    activity.startActivityForResult(intent, REQUEST_CODE_ADD_PACK)
                    promise.resolve(true)
                } else {
                    promise.resolve(false)
                }
            } catch (error: Throwable) {
                promise.reject(error)
            }
        }
    }

    companion object {
        private const val TAG = "org.roborox.whatsapp"

        private const val STICKERS_FOLDER_NAME = "stickers"
        private const val METADATA_FILENAME = "metadata.json"
        private const val TRAY_IMAGE_NAME = "tray.png"
        private const val TRAY_IMAGE_SIZE = 96
        private const val STICKER_IMAGE_PREFIX = "sticker_"
        private const val STICKER_IMAGE_SUFFIX = ".webp"
        private const val STICKER_IMAGE_SIZE = 512

        private const val EXTRA_STICKER_PACK_ID = "sticker_pack_id"
        private const val EXTRA_STICKER_PACK_AUTHORITY = "sticker_pack_authority"
        private const val EXTRA_STICKER_PACK_NAME = "sticker_pack_name"

        private const val ACTION_ADD_PACK = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"
        private const val REQUEST_CODE_ADD_PACK = 200
    }
}
