//
//  UpdateChecker.swift
//  SJTURunningMan
//
//  通过 GitHub Releases 检查更新（对齐安卓版 UpdateChecker.kt）。
//

import Foundation

struct ReleaseInfo: Equatable {
    let tag: String
    let url: String
    let prerelease: Bool
    let publishedAt: String
}

enum UpdateResult: Equatable {
    case upToDate(currentTag: String, latestTag: String)
    case updateAvailable(currentTag: String, latest: ReleaseInfo)
    case failed(reason: String)
}

enum UpdateChecker {

    private struct GhRelease: Decodable {
        let tagName: String?
        let htmlUrl: String?
        let draft: Bool?
        let prerelease: Bool?
        let publishedAt: String?

        enum CodingKeys: String, CodingKey {
            case tagName = "tag_name"
            case htmlUrl = "html_url"
            case draft
            case prerelease
            case publishedAt = "published_at"
        }
    }

    private static let session: URLSession = {
        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = 15
        config.timeoutIntervalForResource = 15
        return URLSession(configuration: config)
    }()

    /// 把 tag 拆成 (数字元组, 预发布后缀)，例如 "v4.4.0" -> ([4,4,0], "")。
    static func parseVersion(_ text: String?) -> (numbers: [Int], prerelease: String) {
        guard let text, !text.trimmingCharacters(in: .whitespaces).isEmpty else {
            return ([], "")
        }
        var raw = text.trimmingCharacters(in: .whitespaces)
        if raw.hasPrefix("v") || raw.hasPrefix("V") { raw.removeFirst() }

        var head = raw
        var tail = ""
        if let dashIndex = raw.firstIndex(of: "-") {
            head = String(raw[raw.startIndex..<dashIndex])
            tail = String(raw[raw.index(after: dashIndex)...])
        }

        var numbers: [Int] = []
        for chunk in head.split(separator: ".") {
            let digits = chunk.prefix { $0.isNumber }
            if digits.isEmpty { break }
            if let value = Int(digits) { numbers.append(value) } else { break }
        }
        return (numbers, tail)
    }

    private static func sortKey(_ tag: String?) -> ([Int], Int) {
        let parsed = parseVersion(tag)
        return (parsed.numbers, parsed.prerelease.isEmpty ? 1 : 0)
    }

    private static func compareKeys(_ a: ([Int], Int), _ b: ([Int], Int)) -> Int {
        let size = max(a.0.count, b.0.count)
        for i in 0..<size {
            let x = i < a.0.count ? a.0[i] : 0
            let y = i < b.0.count ? b.0[i] : 0
            if x != y { return x < y ? -1 : 1 }
        }
        if a.1 == b.1 { return 0 }
        return a.1 < b.1 ? -1 : 1
    }

    /// 远程 tag 是否比本地版本新；版本号相同时稳定版优先于预发布版。
    static func isNewer(remoteTag: String?, localTag: String) -> Bool {
        let (remoteNumbers, remotePre) = parseVersion(remoteTag)
        let (localNumbers, localPre) = parseVersion(localTag)
        if remoteNumbers.isEmpty { return false }
        let cmp = compareKeys((remoteNumbers, 1), (localNumbers, 1))
        if cmp != 0 { return cmp > 0 }
        return remotePre.isEmpty && !localPre.isEmpty
    }

    static func fetchLatestRelease() async -> Result<ReleaseInfo, Error> {
        guard let url = URL(string: AppConfig.githubReleasesAPI) else {
            return .failure(UpdateError.badURL)
        }
        var request = URLRequest(url: url)
        request.setValue("application/vnd.github+json", forHTTPHeaderField: "Accept")
        request.setValue("SJTURunningMan/\(AppConfig.versionName)", forHTTPHeaderField: "User-Agent")

        do {
            let (data, response) = try await session.data(for: request)
            guard let http = response as? HTTPURLResponse else {
                return .failure(UpdateError.emptyResponse)
            }
            guard (200..<300).contains(http.statusCode) else {
                return .failure(UpdateError.http(http.statusCode))
            }
            let releases = (try? JSONDecoder().decode([GhRelease].self, from: data)) ?? []
            let candidates = releases.filter { $0.draft != true && !parseVersion($0.tagName).numbers.isEmpty }
            var best: GhRelease?
            var bestKey: ([Int], Int) = ([], -1)
            for release in candidates {
                let key = sortKey(release.tagName)
                if compareKeys(key, bestKey) > 0 {
                    bestKey = key
                    best = release
                }
            }
            guard let chosen = best else { return .failure(UpdateError.noRelease) }
            return .success(ReleaseInfo(
                tag: chosen.tagName ?? "",
                url: chosen.htmlUrl ?? "\(AppConfig.githubRepoURL)/releases",
                prerelease: chosen.prerelease == true,
                publishedAt: chosen.publishedAt ?? ""
            ))
        } catch {
            return .failure(error)
        }
    }

    static func checkForUpdates(currentVersion: String = AppConfig.versionName) async -> UpdateResult {
        switch await fetchLatestRelease() {
        case .success(let latest):
            if isNewer(remoteTag: latest.tag, localTag: currentVersion) {
                return .updateAvailable(currentTag: currentVersion, latest: latest)
            }
            return .upToDate(currentTag: currentVersion, latestTag: latest.tag)
        case .failure(let error):
            return .failed(reason: shortError(error))
        }
    }

    private static func shortError(_ error: Error) -> String {
        if let updateError = error as? UpdateError { return updateError.message }
        let nsError = error as NSError
        switch nsError.code {
        case NSURLErrorTimedOut: return "连接超时"
        case NSURLErrorNotConnectedToInternet, NSURLErrorNetworkConnectionLost: return "网络不可用"
        default: return error.localizedDescription
        }
    }

    enum UpdateError: Error {
        case badURL
        case emptyResponse
        case noRelease
        case http(Int)

        var message: String {
            switch self {
            case .badURL: return "地址无效"
            case .emptyResponse: return "空响应"
            case .noRelease: return "未找到可用的 Release"
            case .http(let code): return code == 403 ? "请求过于频繁 (403)" : "HTTP \(code)"
            }
        }
    }
}
