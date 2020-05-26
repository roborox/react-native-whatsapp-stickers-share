package org.roborox.whatsapp


@kotlinx.serialization.Serializable
internal data class StickerPack(
        val identifier: String,
        val name: String,
        val publisher: String,
        val trayImageFileName: String,
        val publisherEmail: String,
        val publisherWebsite: String,
        val privacyPolicyWebsite: String,
        val licenseAgreementWebsite: String,
        val imageDataVersion: String,
        val avoidCache: Boolean,
        val stickers: ArrayList<Sticker> = ArrayList(),
        val iosAppStoreLink: String? = null,
        val androidPlayStoreLink: String? = null
) { }
