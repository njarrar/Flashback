// Reads the words in screenshots with Apple's Vision text reader, for screens where no
// text tree can be read (the iPhone simulator). Works for any app.
//
// Usage: swift .github/qa/ocr.swift a.png b.png ...
// Writes a.txt next to a.png, one line of text per line found.
import AppKit
import Foundation
import Vision

for path in CommandLine.arguments.dropFirst() {
    guard let image = NSImage(contentsOfFile: path),
          let cg = image.cgImage(forProposedRect: nil, context: nil, hints: nil) else {
        FileHandle.standardError.write("Could not open \(path)\n".data(using: .utf8)!)
        exit(1)
    }
    let request = VNRecognizeTextRequest()
    request.recognitionLevel = .accurate
    // Raw keys are plain ASCII with underscores; spelling fixes would hide them.
    request.usesLanguageCorrection = false
    request.recognitionLanguages = ["en-US"]
    try VNImageRequestHandler(cgImage: cg).perform([request])
    let lines = (request.results ?? []).compactMap { $0.topCandidates(1).first?.string }
    let out = (path as NSString).deletingPathExtension + ".txt"
    try lines.joined(separator: "\n").write(toFile: out, atomically: true, encoding: .utf8)
}
