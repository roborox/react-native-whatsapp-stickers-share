//
// Copyright (c) WhatsApp Inc. and its affiliates.
// All rights reserved.
//
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.
//
import UIKit

extension CGSize {
    public static func ==(left: CGSize, right: CGSize) -> Bool {
        return left.width.isEqual(to: right.width) && left.height.isEqual(to: right.height)
    }
    public static func <(left: CGSize, right: CGSize) -> Bool {
        return left.width.isLess(than: right.width) && left.height.isLess(than: right.height)
    }
    public static func >(left: CGSize, right: CGSize) -> Bool {
        return !left.width.isLessThanOrEqualTo(right.width) && !left.height.isLessThanOrEqualTo(right.height)
    }
    public static func <=(left: CGSize, right: CGSize) -> Bool {
        return left.width.isLessThanOrEqualTo(right.width) && left.height.isLessThanOrEqualTo(right.height)
    }
    public static func >=(left: CGSize, right: CGSize) -> Bool {
        return !left.width.isLess(than: right.width) && !left.height.isLess(than: right.height)
    }
}

/**
 *  Represents the two supported extensions for sticker images: png and webp.
 */
enum ImageDataExtension: String {
    case png = "png"
    case webp = "webp"
    case jpeg = "jpeg"
}

/**
 *  Stores sticker image data along with its supported extension.
 */
class ImageData {
    let data: Data
    let type: ImageDataExtension
    let size: Int

    var bytesSize: Int64 {
        return Int64(data.count)
    }

    /**
     *  Returns whether or not the data represents an animated image.
     *  It will always return false if the image is png.
     */
    lazy var animated: Bool = {
        if type == .webp {
            return WebPManager.shared.isAnimated(webPData: data)
        } else {
            return false
        }
    }()

    /**
     *  Returns a UIImage of the current image data. If data is corrupt, nil will be returned.
     */
    lazy var image: UIImage? = {
        let source = type == .webp ? WebPManager.shared.decode(webPData: data) : UIImage(data: data)
        if (source === nil) { return nil }
        if (source!.size.width == CGFloat(self.size) && source!.size.height == CGFloat(self.size)) { return source }
        let canvasSize = CGSize(width: self.size, height: self.size)
        UIGraphicsBeginImageContextWithOptions(canvasSize, false, 1)
        defer { UIGraphicsEndImageContext() }
        source!.draw(in: CGRect(origin: .zero, size: canvasSize))
        return UIGraphicsGetImageFromCurrentImageContext()
    }()

    /**
     *  Returns the webp data representation of the current image. If the current image is already webp,
     *  the data is simply returned. If it's png, it will returned the webp converted equivalent data.
     */
    lazy var webpData: Data? = {
        guard let image = self.image else { return nil }
        guard let data = image.pngData() else { return nil }
        return WebPManager.shared.encode(pngData: data)
    }()

    init(data: Data, type: ImageDataExtension, size: Int) {
        self.data = data
        self.type = type
        self.size = size
    }
    
    static func imageMimeType(for data: Data) throws -> ImageDataExtension {
        var b: UInt8 = 0
        data.copyBytes(to: &b, count: 1)
        switch b {
        case 0xFF:
            return ImageDataExtension.jpeg
        case 0x89:
            return ImageDataExtension.png
        case 0x52:
            return ImageDataExtension.webp
        default:
            throw StickerPackError.unsupportedImageFormat
        }
    }

    static func imageDataIfCompliant(contentsOfFile filename: URL, isTray: Bool) throws -> ImageData {
        if (!FileManager.default.fileExists(atPath: filename.path)) {
            throw StickerPackError.fileNotFound(filename.path)
        }
        
        let data = try Data(contentsOf: filename)
        let imageType = try imageMimeType(for: data)

        return try ImageData.imageDataIfCompliant(rawData: data, extensionType: imageType, isTray: isTray)
    }

    static func imageDataIfCompliant(rawData: Data, extensionType: ImageDataExtension, isTray: Bool) throws -> ImageData {
        let imageData = ImageData(data: rawData, type: extensionType, size: isTray ? 96 : 512)

        guard !imageData.animated else {
            throw StickerPackError.animatedImagesNotSupported
        }

        if isTray {
            guard imageData.bytesSize <= Limits.MaxTrayImageFileSize else {
                throw StickerPackError.imageTooBig(imageData.bytesSize)
            }
            guard imageData.image!.size == Limits.TrayImageDimensions else {
                throw StickerPackError.incorrectImageSize(imageData.image!.size)
            }
        } else {
            guard imageData.bytesSize <= Limits.MaxStickerFileSize else {
                throw StickerPackError.imageTooBig(imageData.bytesSize)
            }

            guard imageData.image!.size == Limits.ImageDimensions else {
                throw StickerPackError.incorrectImageSize(imageData.image!.size)
            }
        }

        return imageData
    }
}
