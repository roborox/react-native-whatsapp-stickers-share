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
	stickers: StickerConfig[],
	iosAppStoreLink?: string,
	androidPlayStoreLink?: string,
}

export class WhatsAppStickersShare {
	share(config: StickerPackConfig) {
		return NativeModules.WhatsAppStickersShare.share(config)
	}
	isWhatsAppAvailable() {
		return Linking.canOpenURL("whatsapp://send")
	}
}

export default new WhatsAppStickersShare()
