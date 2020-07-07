//
//  WhatsAppStickersShare.swift
//  WhatsAppStickersShare
//
//  Created by Ivan K on 02/04/2020.
//  Copyright © 2020 Facebook. All rights reserved.
//

import Foundation

@objc(WhatsAppStickersShare)
class WhatsAppStickersShare: NSObject {

    @objc
    func share(_ config: NSDictionary,
            resolver resolve: @escaping RCTPromiseResolveBlock,
            rejecter reject: @escaping RCTPromiseRejectBlock) -> Void {

        let trayUrl = URL(string: RCTConvert.nsString(config["trayImage"]))!
        var storedError: NSError?

        DownloadManager.shared.get(url: trayUrl) { trayLocalUrl, error in
            if error == nil {
                if trayLocalUrl != nil {
                    let stickerPack: StickerPack?
                    do {
                        stickerPack = try StickerPack(
                            identifier: RCTConvert.nsString(config["identifier"]),
                            name: RCTConvert.nsString(config["title"]),
                            publisher: RCTConvert.nsString(config["author"]),
                            trayImageFileName: trayLocalUrl!,
                            publisherWebsite: RCTConvert.nsString(config["publisherURL"]),
                            privacyPolicyWebsite: RCTConvert.nsString(config["privacyPolicyURL"]),
                            licenseAgreementWebsite: RCTConvert.nsString(config["licenseURL"]),
                            iOSAppStoreLink: RCTConvert.nsString(config["iosAppStoreLink"]),
                            androidStoreLink: RCTConvert.nsString(config["androidPlayStoreLink"])
                        )
                        let stickersMap: [NSDictionary] = RCTConvert.nsArray(config["stickers"]) as! [NSDictionary]
                        let group = DispatchGroup()

                        stickersMap.forEach { (sticker: NSDictionary) -> Void in
                            group.enter()
                            let imageUrl = URL(string: RCTConvert.nsString(sticker["url"]))!
                            let emojis: [String]? = sticker["emojis"] != nil ? RCTConvert.nsArray(sticker["emojis"]) as? [String] : nil

                            DownloadManager.shared.get(url: imageUrl) { currentUrl, error in
                                if error != nil { storedError = error } else {
                                    do {
                                        try stickerPack!.addSticker(contentsOfFile: currentUrl!, emojis: emojis)
                                    } catch {
                                        storedError = NSError(domain: "Cannot add sticker", code: 1000, userInfo: nil)
                                    }
                                }
                                group.leave()
                            }
                        }
                        return group.notify(queue: .main) {
                            if storedError != nil {
                                return reject("WhatsAppStickersShare", storedError?.localizedDescription, storedError)
                            }
                            return stickerPack!.sendToWhatsApp { (completed: Bool) in
                                return completed ? resolve(completed) : reject("WhatsAppStickersShare", "Cannot send sticker pack to whatsapp", error)
                            }
                        }
                    } catch {
                        return reject("WhatsAppStickersShare", error.localizedDescription, error)
                    }
                }
            }
            return reject("WhatsAppStickersShare", error?.localizedDescription, error)
        }
    }
}
