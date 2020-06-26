//
//  DownloadManager.swift
//  WhatsAppStickersShare
//
//  Created by Ivan K on 02/04/2020.
//  Copyright © 2020 Facebook. All rights reserved.
//

import Foundation

enum DownloadError: Int {
    case cannotDownload = 1001
    case cannotExtractExtensionData = 1002
    case dataIsNotReturned = 1003
    case cannotWriteFile = 1004
}

class DownloadManager {
    private init() {}
    
    static var shared: DownloadManager = {
        let instance = DownloadManager()
        return instance
    }()
    
    static func createTempFilename(ext: String) -> URL {
        let directory = NSTemporaryDirectory()
        let fileName = String(format: "%@.%@", NSUUID().uuidString, ext)

        return NSURL.fileURL(withPathComponents: [directory, fileName])!
    }
    
    static func extractExtension(response: HTTPURLResponse) -> String? {
        if (response.mimeType == "image/png") {
            return "png"
        }
        if (response.mimeType == "image/jpeg") {
            return "jpeg"
        }
        if (response.mimeType == "image/webp") {
            return "webp"
        }
        return nil
    }
 
    func get(url: URL, completion: @escaping (_ path: URL?, _ error: NSError?) -> Void) {
        if (!url.absoluteString.hasPrefix("http")) {
            completion(URL(string: url.absoluteString), nil)
            return
        }
        
        let sessionConfig = URLSessionConfiguration.default
        
        let session = URLSession(configuration: sessionConfig, delegate: nil, delegateQueue: nil)
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        
        session.dataTask(with: request, completionHandler: { (data: Data?, response: URLResponse?, error: Error?) -> Void in
            if error == nil {
                if let response = response as? HTTPURLResponse {
                    if (200...299).contains(response.statusCode) {
                        do {
                            let ext = DownloadManager.extractExtension(response: response)
                            if ext == nil {
                                return completion(nil, NSError(domain: "Can't extract extension", code: DownloadError.cannotExtractExtensionData.rawValue, userInfo: nil))
                            }
                            
                            let destination = DownloadManager.createTempFilename(ext: ext!);
                            if data != nil {
                                try data!.write(to: destination)
                                return completion(destination, nil)
                            }
                            return completion(nil, NSError(domain: "Data is not defined", code: DownloadError.dataIsNotReturned.rawValue, userInfo: nil))
                        } catch {
                            return completion(nil, NSError(domain: "Can't write file", code: DownloadError.cannotWriteFile.rawValue, userInfo: nil))
                        }
                    }
                }
            }
            completion(nil, NSError(domain: "Can't download file", code: DownloadError.cannotDownload.rawValue, userInfo: nil))
        }).resume()
    }
}
