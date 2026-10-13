//
//  ApiService.swift
//  SJTURunningMan
//
//  与安卓版 / 电脑版保持一致的接口封装（UID、打卡规则、上传）。
//

import Foundation
import WebKit  // 需要用来访问 WKWebsiteDataStore

final class ApiService {

    // MARK: - User Agent / 常量

    static let appUserAgent =
        "Mozilla/5.0 (Linux; Android 12; SM-S9080 Build/V417IR; wv) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Version/4.0 Chrome/138.0.7204.67 Safari/537.36 TaskCenterApp/3.5.0"
    static let webUserAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"
    static let taskCenterPackage = "edu.sjtu.infoplus.taskcenter"

    private let session: URLSession = {
        let config = URLSessionConfiguration.default
        config.httpCookieStorage = HTTPCookieStorage.shared
        config.httpShouldSetCookies = true
        config.timeoutIntervalForRequest = 20
        config.timeoutIntervalForResource = 30
        return URLSession(configuration: config)
    }()

    // MARK: - Cookie 工具

    /// 同步 WebView 的 cookie 到 HTTPCookieStorage
    static func syncCookiesFromWebView(completion: @escaping () -> Void) {
        let cookieStore = WKWebsiteDataStore.default().httpCookieStore
        cookieStore.getAllCookies { cookies in
            for cookie in cookies {
                HTTPCookieStorage.shared.setCookie(cookie)
            }
            completion()
        }
    }

    /// 清除所有 cookie
    func clearCookies() {
        HTTPCookieStorage.shared.removeCookies(since: Date.distantPast)
    }

    /// 仅精准匹配 jAccount 登录页特征，避免把正常响应误判为登录页。
    private func isJAccountLoginPage(_ content: String) -> Bool {
        content.contains("Login jAccount") ||
        content.contains("jaccount.sjtu.edu.cn") ||
        content.contains("扫码登录") ||
        content.contains("Scan QR code") ||
        content.contains("统一身份认证")
    }

    private func looksLikeUid(_ text: String) -> Bool {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.range(of: "^[a-zA-Z0-9_-]{4,}$", options: .regularExpression) != nil
    }

    // MARK: - 获取 UID

    func getUid() async -> String? {
        guard let url = URL(string: AppConfig.uidURL) else { return nil }
        var request = URLRequest(url: url)
        request.setValue(Self.appUserAgent, forHTTPHeaderField: "User-Agent")
        request.setValue("application/json, text/plain, */*", forHTTPHeaderField: "Accept")
        request.setValue(Self.taskCenterPackage, forHTTPHeaderField: "X-Requested-With")
        request.setValue("https://\(AppConfig.host)/phone/", forHTTPHeaderField: "Referer")

        do {
            let (data, response) = try await session.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse else { return nil }
            let body = String(data: data, encoding: .utf8) ?? ""

            if isJAccountLoginPage(body) || httpResponse.statusCode == 302 || httpResponse.statusCode == 403 {
                return nil
            }

            if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                let code = (json["code"] as? NSNumber)?.doubleValue
                let uid = (json["data"] as? [String: Any])?["uid"] as? String
                if code != 0 || uid == nil || uid!.isEmpty {
                    return nil
                }
                return uid
            }

            // 可能是纯文本 / JSON 字符串形式的 uid
            let trimmed = body.trimmingCharacters(in: .whitespacesAndNewlines)
            return looksLikeUid(trimmed) ? trimmed : nil
        } catch {
            print("获取 UID 失败: \(error)")
            return nil
        }
    }

    // MARK: - 打卡规则

    /// 拉取跑步打卡规则（配速上下限、run id），失败时返回默认规则。
    func getPointRules(authToken: String, longitude: Double, latitude: Double) async -> PointRules {
        let fallback = PointRules.default

        // 预热一次个人数据接口，失败不影响后续
        if let warmURL = URL(string: AppConfig.myDataURL) {
            var warm = URLRequest(url: warmURL)
            warm.setValue(Self.appUserAgent, forHTTPHeaderField: "User-Agent")
            warm.setValue("application/json, text/plain, */*", forHTTPHeaderField: "Accept")
            warm.setValue(authToken, forHTTPHeaderField: "Authorization")
            _ = try? await session.data(for: warm)
        }

        let locationValue = String(format: "%.14f,%.14f", longitude, latitude)
        var components = URLComponents(string: AppConfig.pointRuleURL)
        components?.queryItems = [URLQueryItem(name: "location", value: locationValue)]
        guard let url = components?.url else { return fallback }

        var request = URLRequest(url: url)
        request.setValue(Self.webUserAgent, forHTTPHeaderField: "User-Agent")
        request.setValue("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8", forHTTPHeaderField: "Accept")
        request.setValue(authToken, forHTTPHeaderField: "Authorization")
        request.setValue("no-cache", forHTTPHeaderField: "Cache-Control")
        request.setValue(url.absoluteString, forHTTPHeaderField: "Referer")

        do {
            let (data, response) = try await session.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse, (200..<300).contains(httpResponse.statusCode) else {
                return fallback
            }
            guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let ruleData = json["data"] as? [String: Any],
                  let rules = ruleData["rules"] as? [String: Any] else {
                return fallback
            }
            let id = (rules["id"] as? NSNumber)?.intValue ?? 6
            let spmin = (rules["spmin"] as? NSNumber)?.intValue ?? 180
            let spmax = (rules["spmax"] as? NSNumber)?.intValue ?? 540
            return PointRules(id: id == 6 ? 9 : id, spmin: spmin, spmax: spmax)
        } catch {
            print("getPointRules 失败，使用默认规则: \(error)")
            return fallback
        }
    }

    // MARK: - 上传

    func upload(authToken: String, payload: [[String: Any]]) async -> (success: Bool, message: String, needRelogin: Bool) {
        guard let url = URL(string: AppConfig.uploadURL) else {
            return (false, "URL error", false)
        }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue(authToken, forHTTPHeaderField: "Authorization")
        request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        request.setValue("gzip", forHTTPHeaderField: "Accept-Encoding")
        request.setValue("okhttp/4.10.0", forHTTPHeaderField: "User-Agent")

        do {
            request.httpBody = try JSONSerialization.data(withJSONObject: payload, options: [])
        } catch {
            return (false, "Payload serialization error", false)
        }

        do {
            let (data, response) = try await session.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse else {
                return (false, "No response", false)
            }
            let body = String(data: data, encoding: .utf8) ?? ""

            if isJAccountLoginPage(body) || httpResponse.statusCode == 403 || httpResponse.statusCode == 401 {
                return (false, "cookie已过期，需要重新登录", true)
            }

            if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               let code = (json["code"] as? NSNumber)?.intValue, code == 0 {
                return (true, json["message"] as? String ?? "成功", false)
            }
            return (false, String(body.prefix(200)), false)
        } catch {
            return (false, "网络错误: \(error.localizedDescription)", false)
        }
    }
}
