package org.roborox.whatsapp

import android.app.Activity
import android.content.Intent
import com.facebook.react.bridge.*
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.net.URL


class WhatsAppStickersShareModule : ReactContextBaseJavaModule, ActivityEventListener {
    companion object {
        private const val TRAY_IMAGE_PREFIX = "tray_image-"
        private const val TRAY_IMAGE_SUFFIX = ".png"

        private const val STICKER_ASSET_PREFIX = "sticker-"
        private const val STICKER_ASSET_SUFFIX = ".webp"

        private const val EXTRA_STICKER_PACK_ID = "sticker_pack_id"
        private const val EXTRA_STICKER_PACK_AUTHORITY = "sticker_pack_authority"
        private const val EXTRA_STICKER_PACK_NAME = "sticker_pack_name"

        private const val ACTION_ADD_PACK = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"
        private const val REQUEST_CODE_ADD_PACK = 200
    }

    private val reactContext: ReactApplicationContext
    private val pending = HashMap<String, Pair<StickerPack, ArrayList<Promise>>>()

    constructor(reactContext: ReactApplicationContext) : super(reactContext) {
        this.reactContext = reactContext
        reactContext.addActivityEventListener(this)
    }

    override fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent) {
        if (requestCode !== REQUEST_CODE_ADD_PACK) return
        val identifier = data.extras.getString(EXTRA_STICKER_PACK_ID)
        val (pack, promises) = pending[identifier] ?: null to null
        pending.remove(identifier)
        if (pack === null || promises === null) return
        if (resultCode === Activity.RESULT_CANCELED) {
            promises.forEach { it.reject("STICKERS_PACK_ADDITION_FAILED", "Failed to add stickers pack to WhatsApp") }
            return
        }
        StickersStorage.appendPack(identifier, pack)
        promises.forEach { it.resolve(true) }
    }

    override fun onNewIntent(intent: Intent?) { }

    override fun getName() = "WhatsAppStickersShare"

    private suspend fun URL.fetchAsTempFile(identifier: String, prefix: String, suffix: String) = withContext(Dispatchers.IO) {
        this@fetchAsTempFile.openStream().use { input ->
            val cacheDir = this@WhatsAppStickersShareModule.reactContext.cacheDir
            val tempDir = File(cacheDir.absolutePath + File.pathSeparator + identifier)
            if (!tempDir.exists()) tempDir.mkdir()
            val file = File.createTempFile(prefix, suffix, tempDir)
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
            file.absolutePath
//            AssetFileDescriptor(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY), 0, AssetFileDescriptor.UNKNOWN_LENGTH)
        }
    }

    @ReactMethod
    fun share(config: ReadableMap, promise: Promise) {
        val identifier = config.getString("identifier")!!
        val title = config.getString("title")!!
        if (pending.containsKey(identifier)) {
            pending[identifier]!!.second.add(promise)
            return
        }
        if (StickersStorage.containsPack(identifier)) return promise.resolve(true)

        GlobalScope.launch {
            try {
                val trayImage = URL(config.getString("trayImage")!!).fetchAsTempFile(identifier, TRAY_IMAGE_PREFIX, TRAY_IMAGE_SUFFIX)
                val stickerPack = StickerPack(
                        identifier,
                        title,
                        config.getString("author")!!,
                        trayImage,
                        config.getString("publisherEmail")!!,
                        config.getString("publisherURL")!!,
                        config.getString("privacyPolicyURL")!!,
                        config.getString("licenseURL")!!,
                        "",
                        false
                )
                val stickers = config.getArray("stickers")
                if (stickers !== null) {
                    val downloads = ArrayList<Deferred<Unit>>()
                    for (index in 0 until stickers.size()) {
                        val sticker = stickers.getMap(index)
                        if (sticker === null) continue
                        val imageURL = sticker.getString("url")
                        if (imageURL === null) continue
                        val emojiesReadable = sticker.getArray("emojis")
                        val emojiesSize = emojiesReadable?.size() ?: 0
                        val emojies = ArrayList<String>(emojiesSize)
                        for (emojiIndex in 0 until emojiesSize) {
                            val emoji = emojiesReadable!!.getString(emojiIndex)
                            if (emoji !== null) emojies.add(emoji)
                        }
                        downloads.add(async {
                            val image = URL(imageURL).fetchAsTempFile(identifier, STICKER_ASSET_PREFIX, STICKER_ASSET_SUFFIX)
                            stickerPack.stickers.add(Sticker(image, emojies))
                            Unit
                        })
                    }
                    awaitAll(*downloads.toTypedArray())
                }

                val intent = Intent()
                intent.action = ACTION_ADD_PACK
                intent.putExtra(EXTRA_STICKER_PACK_ID, identifier)
                intent.putExtra(EXTRA_STICKER_PACK_AUTHORITY, reactContext.packageName + ".StickerContentProvider")
                intent.putExtra(EXTRA_STICKER_PACK_NAME, title)

                val activity = currentActivity!!
                val should = activity.packageManager.resolveActivity(intent, 0)
                if (should != null) {
                    pending[identifier] = stickerPack to arrayListOf(promise)
                    activity.startActivityForResult(intent, REQUEST_CODE_ADD_PACK)
                } else {
                    promise.resolve(false)
                }
            } catch (error: Throwable) {
                promise.reject(error)
            }
        }
    }
}
