import Foundation
import AVFoundation

final class PlayerDaemon {
    private var player: AVPlayer?
    private var itemObserver: NSKeyValueObservation?
    private var endObserver: Any?
    private var timer: Timer?
    private var isPlayingState: Bool = false
    private var durationMs: Int64 = 0

    init() {
        setupTimer()
    }

    private func setupTimer() {
        timer = Timer.scheduledTimer(withTimeInterval: 0.1, repeats: true) { [weak self] _ in
            self?.reportProgress()
        }
    }

    private func reportProgress() {
        guard let player = player, let item = player.currentItem else { return }
        let curSec = CMTimeGetSeconds(player.currentTime())
        let durSec = CMTimeGetSeconds(item.duration)
        let curMs = curSec.isNaN || curSec < 0 ? 0 : Int64(curSec * 1000)
        let dur = durSec.isNaN || durSec < 0 ? durationMs : Int64(durSec * 1000)
        if dur > 0 { durationMs = dur }
        let playing = (player.timeControlStatus == .playing) ? 1 : 0
        isPlayingState = (playing == 1)
        print("PROGRESS \(curMs) \(durationMs) \(playing)")
        fflush(stdout)
    }

    func play(urlString: String) {
        guard let url = URL(string: urlString) else {
            print("ERROR Invalid URL: \(urlString)")
            fflush(stdout)
            return
        }

        if let endObserver = endObserver {
            NotificationCenter.default.removeObserver(endObserver)
            self.endObserver = nil
        }
        itemObserver?.invalidate()
        itemObserver = nil

        durationMs = 0
        let item = AVPlayerItem(url: url)

        itemObserver = item.observe(\.status, options: [.new]) { [weak self] observedItem, _ in
            guard let self = self else { return }
            if observedItem.status == .readyToPlay {
                let durSec = CMTimeGetSeconds(observedItem.duration)
                let dur = durSec.isNaN || durSec < 0 ? 0 : Int64(durSec * 1000)
                self.durationMs = dur
                print("READY \(dur)")
                fflush(stdout)
            } else if observedItem.status == .failed {
                let msg = observedItem.error?.localizedDescription ?? "Playback error"
                print("ERROR \(msg)")
                fflush(stdout)
            }
        }

        endObserver = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime,
            object: item,
            queue: .main
        ) { [weak self] _ in
            print("ENDED")
            fflush(stdout)
            self?.isPlayingState = false
        }

        if player == nil {
            player = AVPlayer(playerItem: item)
        } else {
            player?.replaceCurrentItem(with: item)
        }

        player?.play()
        isPlayingState = true
        print("STATE PLAYING")
        fflush(stdout)
    }

    func pause() {
        player?.pause()
        isPlayingState = false
        print("STATE PAUSED")
        fflush(stdout)
    }

    func resume() {
        player?.play()
        isPlayingState = true
        print("STATE PLAYING")
        fflush(stdout)
    }

    func stop() {
        player?.pause()
        player?.replaceCurrentItem(with: nil)
        isPlayingState = false
        durationMs = 0
        print("STATE STOPPED")
        fflush(stdout)
    }

    func seek(ms: Int64) {
        let sec = Double(ms) / 1000.0
        let time = CMTime(seconds: sec, preferredTimescale: 1000)
        player?.seek(to: time, toleranceBefore: .zero, toleranceAfter: .zero)
        print("STATE SEEK \(ms)")
        fflush(stdout)
    }

    func setVolume(vol: Float) {
        player?.volume = max(0.0, min(1.0, vol))
        print("STATE VOLUME \(vol)")
        fflush(stdout)
    }
}

let daemon = PlayerDaemon()

// Read stdin line by line in a background thread while main thread runs the RunLoop
DispatchQueue.global(qos: .userInitiated).async {
    while let line = readLine() {
        let trimmed = line.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { continue }

        DispatchQueue.main.async {
            if trimmed.hasPrefix("PLAY ") {
                let urlStr = String(trimmed.dropFirst(5)).trimmingCharacters(in: .whitespaces)
                daemon.play(urlString: urlStr)
            } else if trimmed == "PAUSE" {
                daemon.pause()
            } else if trimmed == "RESUME" {
                daemon.resume()
            } else if trimmed == "STOP" {
                daemon.stop()
            } else if trimmed.hasPrefix("SEEK ") {
                let msStr = String(trimmed.dropFirst(5)).trimmingCharacters(in: .whitespaces)
                if let ms = Int64(msStr) {
                    daemon.seek(ms: ms)
                }
            } else if trimmed.hasPrefix("VOLUME ") {
                let volStr = String(trimmed.dropFirst(7)).trimmingCharacters(in: .whitespaces)
                if let vol = Float(volStr) {
                    daemon.setVolume(vol: vol)
                }
            } else if trimmed == "QUIT" {
                exit(0)
            }
        }
    }
    DispatchQueue.main.async {
        exit(0)
    }
}

RunLoop.main.run()
