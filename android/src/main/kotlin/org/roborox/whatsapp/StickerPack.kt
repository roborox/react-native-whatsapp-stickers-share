package org.roborox.whatsapp

import android.os.Parcel
import android.os.Parcelable


@kotlinx.serialization.Serializable
internal data class StickerPack(
        var identifier: String,
        var name: String,
        var publisher: String,
        var trayImageFile: String,
        var publisherEmail: String,
        var publisherWebsite: String,
        var privacyPolicyWebsite: String,
        var licenseAgreementWebsite: String,
        var imageDataVersion: String,
        var avoidCache: Boolean,
        var stickers: ArrayList<Sticker> = ArrayList<Sticker>(),
        var totalSize: Long = 0,
        var iosAppStoreLink: String? = null,
        var androidPlayStoreLink: String? = null,
        var isWhitelisted: Boolean = false
): Parcelable {
    private constructor(parcel: Parcel) : this(
            identifier = parcel.readString(),
            name = parcel.readString(),
            publisher = parcel.readString(),
            trayImageFile = parcel.readString(),
            publisherEmail = parcel.readString(),
            publisherWebsite = parcel.readString(),
            privacyPolicyWebsite = parcel.readString(),
            licenseAgreementWebsite = parcel.readString(),
            iosAppStoreLink = parcel.readString(),
            stickers = parcel.createTypedArrayList(Sticker.CREATOR),
            totalSize = parcel.readLong(),
            androidPlayStoreLink = parcel.readString(),
            isWhitelisted = parcel.readByte().toInt() != 0,
            imageDataVersion = parcel.readString(),
            avoidCache = parcel.readByte().toInt() != 0
    ) { }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(identifier)
        dest.writeString(name)
        dest.writeString(publisher)
        dest.writeString(trayImageFile)
        dest.writeString(publisherEmail)
        dest.writeString(publisherWebsite)
        dest.writeString(privacyPolicyWebsite)
        dest.writeString(licenseAgreementWebsite)
        dest.writeString(iosAppStoreLink)
        dest.writeTypedList(stickers)
        dest.writeLong(totalSize)
        dest.writeString(androidPlayStoreLink)
        dest.writeByte((if (isWhitelisted) 1 else 0).toByte())
        dest.writeString(imageDataVersion)
        dest.writeByte((if (avoidCache) 1 else 0).toByte())
    }

    companion object {
        val CREATOR = object : Parcelable.Creator<StickerPack> {
            override fun createFromParcel(source: Parcel): StickerPack {
                return StickerPack(source)
            }

            override fun newArray(size: Int): Array<StickerPack?> {
                return arrayOfNulls(size)
            }
        }
    }
}
