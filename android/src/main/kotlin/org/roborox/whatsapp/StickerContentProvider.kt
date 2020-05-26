package org.roborox.whatsapp

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.serialization.UnstableDefault
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileNotFoundException


@UnstableDefault
class StickerContentProvider : ContentProvider() {
    private val matcher: UriMatcher = UriMatcher(UriMatcher.NO_MATCH)

    override fun onCreate(): Boolean {
        val authority = BuildConfig.CONTENT_PROVIDER_AUTHORITY
        matcher.addURI(authority, "metadata", METADATA)
        matcher.addURI(authority, "metadata/*", METADATA_SINGLE)
        matcher.addURI(authority, "stickers/*", STICKERS)
        matcher.addURI(authority, "stickers_asset/*/tray.png", TRAY_FILE)
        matcher.addURI(authority, "stickers_asset/*/*", STICKER_FILE)
        return true
    }

    private suspend fun stickersDir() = withContext(Dispatchers.IO) {
        val cacheDir = this@StickerContentProvider.context!!.externalCacheDir!!
        val stickersDir = File(cacheDir.absolutePath + File.separator + STICKERS_FOLDER_NAME)
        if (!stickersDir.exists()) stickersDir.mkdir()
        stickersDir
    }

    private suspend fun packDir(identifier: String) = withContext(Dispatchers.IO) {
        val packDir = File(stickersDir().absolutePath + File.separator + identifier)
        if (!packDir.exists()) packDir.mkdir()
        packDir
    }

    private suspend fun readStickerPack(identifier: String): StickerPack = withContext(Dispatchers.IO) {
        val metadataFile = File(packDir(identifier).absolutePath + File.separator + METADATA_FILENAME)
        Json.parse(StickerPack.serializer(), metadataFile.readText())
    }

    private suspend fun readAllStickerPacks(): ArrayList<StickerPack> = withContext(Dispatchers.IO) {
        val stickersDir = stickersDir()
        val stickerPacks = ArrayList<StickerPack>()
        for (packDir in stickersDir.listFiles()) {
            if (!packDir.isDirectory) continue
            try {
                val stickerPack = readStickerPack(packDir.name)
                stickerPacks.add(stickerPack)
            } catch (error: Throwable) {
                packDir.deleteRecursively()
            }
        }
        stickerPacks
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
                LICENSE_AGREENMENT_WEBSITE,
                IMAGE_DATA_VERSION,
                AVOID_CACHE
        ))
        for (stickerPack in packs) {
            val builder = cursor.newRow()
            builder.add(stickerPack.identifier)
            builder.add(stickerPack.name)
            builder.add(stickerPack.publisher)
            builder.add(stickerPack.trayImageFileName)
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

    private suspend fun getStickers(id: String): Cursor {
        Log.d(TAG, "getStickers id=$id")
        val cursor = MatrixCursor(arrayOf(STICKER_FILE_NAME_IN_QUERY, STICKER_FILE_EMOJI_IN_QUERY))
        val pack = readStickerPack(id)
        for (sticker in pack.stickers) {
            val b = cursor.newRow()
            b.add(sticker.imageFileName)
            b.add(sticker.emojis)
        }
        return cursor
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        return when (matcher.match(uri)) {
            METADATA -> getMetadata(runBlocking { readAllStickerPacks() })
            METADATA_SINGLE -> getMetadata(listOf(runBlocking { readStickerPack(uri.lastPathSegment!!) }))
            STICKERS -> runBlocking { getStickers(uri.lastPathSegment!!) }
            else -> throw IllegalArgumentException("uri not supported: $uri")
        }
    }

    private suspend fun openFileDescriptor(uri: Uri): ParcelFileDescriptor {
        Log.d(TAG, "openStickerAsset $uri")
        val segments = uri.pathSegments
        val fileName = segments[segments.size - 1]
        val identifier = segments[segments.size - 2]
        val file = File(packDir(identifier).absolutePath + File.separator + fileName)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun openFile(uri: Uri, mode: String?): ParcelFileDescriptor {
        return when (matcher.match(uri)) {
            STICKER_FILE, TRAY_FILE -> runBlocking { openFileDescriptor(uri) }
            else -> throw FileNotFoundException("Not supported for $uri")
        }
    }

    override fun getType(uri: Uri): String {
        val authority = BuildConfig.CONTENT_PROVIDER_AUTHORITY
        val type = when (matcher.match(uri)) {
            METADATA -> "vnd.android.cursor.dir/vnd.$authority.metadata"
            METADATA_SINGLE -> "vnd.android.cursor.item/vnd.$authority.metadata"
            STICKERS -> "vnd.android.cursor.dir/vnd.$authority.stickers"
            TRAY_FILE -> "image/webp"
            STICKER_FILE -> "image/webp"
            else -> throw IllegalArgumentException("unsupported uri: $uri")
        }
        Log.d(TAG, "type for $uri = $type")
        return type
    }

    override fun insert(uri: Uri, values: ContentValues): Uri {
        throw UnsupportedOperationException("Not supported")
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
        private const val IMAGE_DATA_VERSION = "image_data_version"
        private const val AVOID_CACHE = "whatsapp_will_not_cache_stickers"

        private const val STICKER_FILE_NAME_IN_QUERY = "sticker_file_name"
        private const val STICKER_FILE_EMOJI_IN_QUERY = "sticker_emoji"
    }
}
