import { Linking, NativeModules } from 'react-native'

export interface StickerConfig {
	emojis?: string[],
	url: string
}

export interface StickerPackConfig {
	identifier: string,
	title: string,
	author: string,
	trayImage: string,
	publisherEmail: string,
	publisherURL: string,
	privacyPolicyURL: string,
	licenseURL: string,
	stickers: StickerConfig[]
}

export interface WhatsAppStickersShare {
	isWhatsAppAvailable: () => Promise<boolean>,
	share: (config: StickerPackConfig) => Promise<true>
}

export default {
	...NativeModules.WhatsAppStickersShare,
	isWhatsAppAvailable: async () => Linking.canOpenURL("whatsapp://send")
} as WhatsAppStickersShare