package org.roborox.whatsapp


@kotlinx.serialization.Serializable
internal data class Sticker(
        val imageFileName: String,
        val emojis: List<String>
) { }
