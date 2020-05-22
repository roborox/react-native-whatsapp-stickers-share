package org.roborox.whatsapp

import android.app.Activity
import android.content.ContentProviderClient
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.facebook.react.bridge.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import java.io.*
import java.net.URL


class WhatsAppStickersShareModule : ReactContextBaseJavaModule, ActivityEventListener {
    private val reactContext: ReactApplicationContext
    private val providerClient: ContentProviderClient
    private val pending = HashMap<String, ArrayList<Promise>>()

    constructor(reactContext: ReactApplicationContext) : super(reactContext) {
        this.reactContext = reactContext
        providerClient = reactContext.contentResolver.acquireContentProviderClient(BuildConfig.CONTENT_PROVIDER_AUTHORITY)
        reactContext.addActivityEventListener(this)
    }

    override fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode !== REQUEST_CODE_ADD_PACK || data === null) return
        if (resultCode === Activity.RESULT_CANCELED) {
            val error = data.getStringExtra("validation_error")
            Log.e(TAG, "Failed to add pack: $error")
            return
        }
    }

    override fun onNewIntent(intent: Intent?) { }

    override fun getName() = "WhatsAppStickersShare"

    private suspend fun packDir(identifier: String) = withContext(Dispatchers.IO) {
        val cacheDir = this@WhatsAppStickersShareModule.reactContext.cacheDir
        val stickersDir = File(cacheDir.absolutePath + File.pathSeparator + STICKERS_FOLDER_NAME)
        if (!stickersDir.exists()) stickersDir.mkdir()
        val packDir = File(stickersDir.absolutePath + File.pathSeparator + identifier)
        if (!packDir.exists()) packDir.mkdir()
        packDir
    }

    @ReactMethod
    fun share(config: ReadableMap, promise: Promise) {
        GlobalScope.launch {
            try {
                val identifier = config.getString("identifier")!!
                val title = config.getString("title")!!
                if (pending.containsKey(identifier)) {
                    pending[identifier]!!.add(promise)
                    return@launch
                }

                val packDir = packDir(identifier)
                val trayImage = URL(config.getString("trayImage")!!).let {
                    it.openStream().use { input ->
                        val file = File(packDir.absolutePath + File.pathSeparator + TRAY_IMAGE_NAME)
                        FileOutputStream(file).use { output -> input.copyTo(output) }
                        file.name
                    }
                }
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
                pending[identifier] = arrayListOf(promise)

                val stickers = config.getArray("stickers")!!
                val downloads = ArrayList<Deferred<Unit>>()
                for (index in 0 until stickers.size()) {
                    val sticker = stickers.getMap(index)
                    if (sticker === null) continue
                    val imageURL = sticker.getString("url")!!
                    val emojies = ArrayList<String>()
                    if (sticker.hasKey("emojis")) {
                        val emojiesReadable = sticker.getArray("emojis")!!
                        emojies.ensureCapacity(emojiesReadable.size())
                        for (emojiIndex in 0 until emojiesReadable.size()) {
                            val emoji = emojiesReadable!!.getString(emojiIndex)
                            if (emoji !== null) emojies.add(emoji)
                        }
                    }
                    downloads.add(async {
                        val image = URL(imageURL).let {
                            it.openStream().use { input ->
                                val file = File.createTempFile(STICKER_IMAGE_PREFIX, STICKER_IMAGE_SUFFIX, packDir)
                                FileOutputStream(file).use { output -> input.copyTo(output) }
                                file.name
                            }
                        }
                        stickerPack.stickers.add(Sticker(image, emojies))
                        Unit
                    })
                }
                withContext(Dispatchers.IO) {
                    val json = Json.stringify(StickerPack.serializer(), stickerPack)
                    val metaFile = File(packDir.absolutePath + File.pathSeparator + METADATA_FILENAME)
                    metaFile.writeText(json)
                }
                awaitAll(*downloads.toTypedArray())

                val intent = Intent()
                intent.action = ACTION_ADD_PACK
                intent.putExtra(EXTRA_STICKER_PACK_ID, identifier)
                intent.putExtra(EXTRA_STICKER_PACK_AUTHORITY, BuildConfig.CONTENT_PROVIDER_AUTHORITY)
                intent.putExtra(EXTRA_STICKER_PACK_NAME, title)

                val activity = currentActivity!!
                if (activity.packageManager.resolveActivity(intent, 0) !== null) {
                    activity.startActivityForResult(intent, REQUEST_CODE_ADD_PACK)
                    pending[identifier]!!.forEach { it.resolve(true) }
                } else {
                    pending[identifier]!!.forEach { it.resolve(false) }
                }

                providerClient.insert(STICKER_PACK_ADDED_URI, ContentValues().apply {
                    put(STICKER_PACK_IDENTIFIER_IN_INSERT, identifier)
                })
            } catch (error: Throwable) {
                val identifier = config.getString("identifier")
                if (identifier !== null) {
                    pending[identifier]!!.forEach { it.reject(error) }
                }
            }
        }
    }

    companion object {
        private const val TAG = "debug-share"

        private const val STICKERS_FOLDER_NAME = "stickers"
        private const val METADATA_FILENAME = "metadata.json"
        private const val TRAY_IMAGE_NAME = "tray.png"
        private const val STICKER_IMAGE_PREFIX = "sticker-"
        private const val STICKER_IMAGE_SUFFIX = ".webp"

        private const val EXTRA_STICKER_PACK_ID = "sticker_pack_id"
        private const val EXTRA_STICKER_PACK_AUTHORITY = "sticker_pack_authority"
        private const val EXTRA_STICKER_PACK_NAME = "sticker_pack_name"

        private val STICKER_PACK_ADDED_URI = Uri.fromParts("content", "//${BuildConfig.CONTENT_PROVIDER_AUTHORITY}/", null)
        private const val STICKER_PACK_IDENTIFIER_IN_INSERT = "sticker_pack_id"

        private const val ACTION_ADD_PACK = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"
        private const val REQUEST_CODE_ADD_PACK = 200
    }
}
