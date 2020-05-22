package org.roborox.whatsapp

internal object StickersStorage {
    private val providers = HashSet<StickerContentProvider>()
    val stickerPacks = HashMap<String, StickerPack>()

    fun registerContentProvider(provider: StickerContentProvider) {
        providers.add(provider)
        stickerPacks.forEach { provider.onStickerPack(it.value) }
    }

    fun containsPack(identifier: String) = stickerPacks.containsKey(identifier)

    fun getPack(identifier: String) = stickerPacks[identifier]

    fun appendPack(identifier: String, pack: StickerPack) {
        if (stickerPacks.containsKey(identifier)) return
        stickerPacks[identifier] = pack
        providers.forEach { it.onStickerPack(pack) }
    }
}
