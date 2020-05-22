package org.roborox.whatsapp

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileNotFoundException


class StickerContentProvider : ContentProvider() {
    private val matcher: UriMatcher = UriMatcher(UriMatcher.NO_MATCH)
    private var stickerPacks: MutableList<StickerPack> = ArrayList()

    override fun onCreate(): Boolean {
        val authority = BuildConfig.CONTENT_PROVIDER_AUTHORITY
        matcher.addURI(authority, "metadata", METADATA)
        matcher.addURI(authority, "metadata/*", METADATA_SINGLE)
        matcher.addURI(authority, "stickers/*", STICKERS)
        matcher.addURI(authority, "stickers_asset/*/tray.png", TRAY_FILE)
        matcher.addURI(authority, "stickers_asset/*/*", STICKER_FILE)
        GlobalScope.launch {
            stickerPacks = readAllStickerPacks()
        }
        return true
    }

    private val stickersDir by lazy { GlobalScope.async { withContext(Dispatchers.IO) {
        val cacheDir = this@StickerContentProvider.context.cacheDir
        val stickersDir = File(cacheDir.absolutePath + File.pathSeparator + STICKERS_FOLDER_NAME)
        if (!stickersDir.exists()) stickersDir.mkdir()
        stickersDir
    } } }

    private suspend fun packDir(identifier: String) = withContext(Dispatchers.IO) {
        val packDir = File(stickersDir.await().absolutePath + File.pathSeparator + identifier)
        if (!packDir.exists()) packDir.mkdir()
        packDir
    }

    private suspend fun readAllStickerPacks(): ArrayList<StickerPack> {
        val stickersDir = stickersDir.await()
        val stickerPacks = ArrayList<StickerPack>()
        for (packDir in stickersDir.listFiles()) {
            if (!packDir.isDirectory) continue
            try {
                val metadataFile = File(packDir.absolutePath + File.pathSeparator + METADATA_FILENAME)
                val stickerPack = Json.parse(StickerPack.serializer(), metadataFile.readText())
                stickerPacks.add(stickerPack)
            } catch (error: Throwable) {
                Log.e(TAG, "Failed to read stickers pack from file system", error)
                packDir.deleteRecursively()
            }
        }
        return stickerPacks
    }

    private suspend fun readStickerPack(identifier: String): StickerPack {
        val metadataFile = File(packDir(identifier).absolutePath + File.pathSeparator + METADATA_FILENAME)
        return Json.parse(StickerPack.serializer(), metadataFile.readText())
    }

    private fun getStickerPack(identifier: String): StickerPack {
        val stored = stickerPacks.firstOrNull { it.identifier === identifier }
        if (stored !== null) return stored
        return runBlocking {
            val pack = readStickerPack(identifier)
            stickerPacks.add(pack)
            pack
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        return when (matcher.match(uri)) {
            METADATA -> getMetadata(stickerPacks)
            METADATA_SINGLE -> getMetadata(listOf(getStickerPack(uri.lastPathSegment)))
            STICKERS -> getStickers(uri.lastPathSegment)
            else -> throw IllegalArgumentException("uri not supported: $uri")
        }
    }

    private fun getMetadata(packs: List<StickerPack>): Cursor {
        Log.d(TAG, "getMetadata packs=$packs")
        val cursor = MatrixCursor(arrayOf(
                STICKER_PACK_IDENTIFIER_IN_QUERY,
                STICKER_PACK_NAME_IN_QUERY,
                STICKER_PACK_PUBLISHER_IN_QUERY,
                STICKER_PACK_ICON_IN_QUERY,
                ANDROID_APP_DOWNLOAD_LINK_IN_QUERY,
                IOS_APP_DOWNLOAD_LINK_IN_QUERY,
                PUBLISHER_EMAIL,
                PUBLISHER_WEBSITE,
                PRIVACY_POLICY_WEBSITE,
                LICENSE_AGREENMENT_WEBSITE
        ))
        for (stickerPack in packs) {
            val builder = cursor.newRow()
            builder.add(stickerPack.identifier)
            builder.add(stickerPack.name)
            builder.add(stickerPack.publisher)
            builder.add(stickerPack.trayImageFile)
            builder.add(stickerPack.androidPlayStoreLink)
            builder.add(stickerPack.iosAppStoreLink)
            builder.add(stickerPack.publisherEmail)
            builder.add(stickerPack.publisherWebsite)
            builder.add(stickerPack.privacyPolicyWebsite)
            builder.add(stickerPack.licenseAgreementWebsite)
            builder.add(stickerPack.imageDataVersion)
            builder.add(if (stickerPack.avoidCache) 1 else 0)
        }
        return cursor
    }

    private fun getStickers(id: String): Cursor {
        Log.d(TAG, "getStickers id=$id")
        val cursor = MatrixCursor(arrayOf(STICKER_FILE_NAME_IN_QUERY, STICKER_FILE_EMOJI_IN_QUERY))
        val pack = getStickerPack(id)
        for (sticker in pack.stickers) {
            val b = cursor.newRow()
            b.add(sticker.imageFileName)
            b.add(sticker.emojies)
        }
        return cursor
    }

    override fun openAssetFile(uri: Uri, mode: String?): AssetFileDescriptor {
        return when (matcher.match(uri)) {
            STICKER_FILE -> openStickerAsset(uri)
            TRAY_FILE -> openStickerAsset(uri)
            else -> throw FileNotFoundException("Not supported for $uri")
        }
    }

    private fun openStickerAsset(uri: Uri): AssetFileDescriptor {
        Log.d(TAG, "openStickerAsset $uri")
        val parts = uri.pathSegments
        val fileName = parts.removeAt(parts.lastIndex)
        val identifier = parts.removeAt(parts.lastIndex)
        val packDir = runBlocking { packDir(identifier).absolutePath }
        val file = File(packDir + File.pathSeparator + fileName)
        return AssetFileDescriptor(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY), 0, -1)
    }

    override fun getType(uri: Uri): String {
        val authority = BuildConfig.CONTENT_PROVIDER_AUTHORITY
        val type = when (matcher.match(uri)) {
            METADATA -> "vnd.android.cursor.dir/vnd.$authority.metadata"
            METADATA_SINGLE -> "vnd.android.cursor.item/vnd.$authority.metadata"
            STICKERS -> "vnd.android.cursor.dir/vnd.$authority.stickers"
            TRAY_FILE -> "image/png"
            STICKER_FILE -> "image/webp"
            else -> throw IllegalArgumentException("unsupported uri: $uri")
        }
        Log.d(TAG, "type for $uri = $type")
        return type
    }

    override fun insert(uri: Uri, values: ContentValues): Uri {
        val identifier = values.getAsString(STICKER_PACK_IDENTIFIER_IN_QUERY)
        GlobalScope.launch {
            val pack = readStickerPack(identifier)
            stickerPacks.add(pack)
        }
        return Uri.fromParts("content", "//${BuildConfig.CONTENT_PROVIDER_AUTHORITY}/metadata/$identifier", null)
    }

    override fun update(uri: Uri?, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        throw UnsupportedOperationException("Not supported")
    }

    override fun delete(uri: Uri?, selection: String?, selectionArgs: Array<out String>?): Int {
        throw UnsupportedOperationException("Not supported")
    }

    companion object {
        private const val TAG = "debug-share"

        private const val METADATA = 1
        private const val METADATA_SINGLE = 2
        private const val STICKERS = 3
        private const val TRAY_FILE = 4
        private const val STICKER_FILE = 5

        private const val STICKERS_FOLDER_NAME = "stickers"
        private const val METADATA_FILENAME = "metadata.json"

        private const val STICKER_PACK_IDENTIFIER_IN_INSERT = "sticker_pack_id"

        private const val STICKER_PACK_IDENTIFIER_IN_QUERY = "sticker_pack_identifier"
        private const val STICKER_PACK_NAME_IN_QUERY = "sticker_pack_name"
        private const val STICKER_PACK_PUBLISHER_IN_QUERY = "sticker_pack_publisher"
        private const val STICKER_PACK_ICON_IN_QUERY = "sticker_pack_icon"
        private const val ANDROID_APP_DOWNLOAD_LINK_IN_QUERY = "android_play_store_link"
        private const val IOS_APP_DOWNLOAD_LINK_IN_QUERY = "ios_app_download_link"
        private const val PUBLISHER_EMAIL = "sticker_pack_publisher_email"
        private const val PUBLISHER_WEBSITE = "sticker_pack_publisher_website"
        private const val PRIVACY_POLICY_WEBSITE = "sticker_pack_privacy_policy_website"
        private const val LICENSE_AGREENMENT_WEBSITE = "sticker_pack_license_agreement_website"

        private const val STICKER_FILE_NAME_IN_QUERY = "sticker_file_name"
        private const val STICKER_FILE_EMOJI_IN_QUERY = "sticker_emoji"
    }
}
