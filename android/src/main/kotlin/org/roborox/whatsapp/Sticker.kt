package org.roborox.whatsapp

import android.os.Parcel
import android.os.Parcelable


internal class Sticker : Parcelable {
    val imageFileName: String
    val emojies: List<String>
    var size: Long = 0

    constructor(imageFileName: String, emojies: List<String>) {
        this.imageFileName = imageFileName
        this.emojies = emojies
    }

    private constructor(parcel: Parcel) {
        imageFileName = parcel.readString()
        emojies = parcel.createStringArrayList()
        size = parcel.readLong()
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(imageFileName)
        dest.writeStringList(emojies)
        dest.writeLong(size)
    }

    companion object {
        val CREATOR = object : Parcelable.Creator<Sticker> {
            override fun createFromParcel(parcel: Parcel): Sticker? {
                return Sticker(parcel)
            }

            override fun newArray(size: Int): Array<Sticker?> {
                return arrayOfNulls(size)
            }
        }
    }
}
