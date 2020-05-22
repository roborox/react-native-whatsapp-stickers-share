package org.roborox.whatsapp

import android.os.Parcel
import android.os.Parcelable

internal class StickerPack: Parcelable {
    val identifier: String
    val name: String
    val publisher: String
    val trayImageFile: String
    val publisherEmail: String
    val publisherWebsite: String
    val privacyPolicyWebsite: String
    val licenseAgreementWebsite: String
    val imageDataVersion: String
    val avoidCache: Boolean

    var iosAppStoreLink: String? = null
    var stickers = ArrayList<Sticker>()
        set(value) {
            field = value
            totalSize = 0
            for (sticker in value) {
                totalSize += sticker.size
            }
        }
    var totalSize: Long = 0
        private set
    var androidPlayStoreLink: String? = null
    var isWhitelisted = false

    constructor(identifier: String, name: String, publisher: String, trayImageFile: String, publisherEmail: String, publisherWebsite: String, privacyPolicyWebsite: String, licenseAgreementWebsite: String, imageDataVersion: String, avoidCache: Boolean) {
        this.identifier = identifier
        this.name = name
        this.publisher = publisher
        this.trayImageFile = trayImageFile
        this.publisherEmail = publisherEmail
        this.publisherWebsite = publisherWebsite
        this.privacyPolicyWebsite = privacyPolicyWebsite
        this.licenseAgreementWebsite = licenseAgreementWebsite
        this.imageDataVersion = imageDataVersion
        this.avoidCache = avoidCache
    }

    private constructor(parcel: Parcel) {
        identifier = parcel.readString()
        name = parcel.readString()
        publisher = parcel.readString()
        trayImageFile = parcel.readString()
        publisherEmail = parcel.readString()
        publisherWebsite = parcel.readString()
        privacyPolicyWebsite = parcel.readString()
        licenseAgreementWebsite = parcel.readString()
        iosAppStoreLink = parcel.readString()
        stickers = parcel.createTypedArrayList(Sticker.CREATOR)
        totalSize = parcel.readLong()
        androidPlayStoreLink = parcel.readString()
        isWhitelisted = parcel.readByte().toInt() != 0
        imageDataVersion = parcel.readString()
        avoidCache = parcel.readByte().toInt() != 0
    }

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
        dest.writeTypedList<Sticker>(stickers)
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
