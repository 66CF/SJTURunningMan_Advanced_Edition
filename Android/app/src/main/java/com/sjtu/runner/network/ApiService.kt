package com.sjtu.runner.network

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.sjtu.runner.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class PointRules(
    val id: Int,
    val spmin: Int,
    val spmax: Int
)

class ApiService(private val context: Context? = null) {

    fun setContext(context: Context) {
        // Keep for backward compatibility
    }

    /** 自定义 CookieJar，同步 WebView 的 CookieManager，保证登录态在 OkHttp 侧可用。 */
    private class WebViewCookieJar : CookieJar {
        private val webViewCookieManager = CookieManager.getInstance()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookies.forEach { cookie ->
                webViewCookieManager.setCookie(url.toString(), cookie.toString())
            }
            webViewCookieManager.flush()
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val cookieStr = webViewCookieManager.getCookie(url.toString()) ?: return emptyList()
            return cookieStr.split("; ").mapNotNull { Cookie.parse(url, it) }
        }
    }

    private val client = OkHttpClient.Builder()
        .cookieJar(WebViewCookieJar())
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /** 仅精准匹配 jAccount 登录页特征，避免把正常响应误判为登录页。 */
    private fun isJAccountLoginPage(content: String): Boolean {
        return content.contains("Login jAccount") ||
            content.contains("jaccount.sjtu.edu.cn") ||
            content.contains("扫码登录") ||
            content.contains("Scan QR code") ||
            content.contains("统一身份认证")
    }

    private fun cookiesFor(url: String): String? =
        CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }

    private fun looksLikeUid(text: String): Boolean = text.trim().matches(Regex("[a-zA-Z0-9_-]{4,}"))

    suspend fun getUid(): String? = withContext(Dispatchers.IO) {
        val cookie = cookiesFor("https://${AppConfig.HOST}")
        val builder = Request.Builder()
            .url(AppConfig.UID_URL)
            .header("User-Agent", APP_USER_AGENT)
            .header("Accept", "application/json, text/plain, */*")
            .header("X-Requested-With", TASK_CENTER_PACKAGE)
            .header("Referer", "https://${AppConfig.HOST}/phone/")
        if (cookie != null) builder.header("Cookie", cookie)

        val response = client.newCall(builder.build()).execute()
        val body = response.body?.string() ?: return@withContext null
        Log.d(TAG, "getUid code=${response.code}")

        if (isJAccountLoginPage(body) || response.code == 302 || response.code == 403) {
            Log.w(TAG, "getUid: 返回登录页/重定向，cookie 已过期")
            return@withContext null
        }

        try {
            val element = Gson().fromJson(body, JsonElement::class.java)
            when {
                element.isJsonObject -> {
                    val obj = element.asJsonObject
                    val code = obj.get("code")?.takeIf { it.isJsonPrimitive }?.asDouble
                    val data = obj.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
                    val uid = data?.get("uid")?.takeIf { it.isJsonPrimitive }?.asString
                    if (code != 0.0 || uid.isNullOrBlank()) {
                        Log.w(TAG, "getUid: code=$code message=${obj.get("message")}")
                        null
                    } else uid
                }

                element.isJsonPrimitive && element.asJsonPrimitive.isString ->
                    element.asString.takeIf { looksLikeUid(it) }

                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "getUid: 响应非 JSON", e)
            body.trim().takeIf { looksLikeUid(it) }
        }
    }

    /** 拉取跑步打卡规则（配速上下限、run id），失败时返回默认规则。 */
    suspend fun getPointRules(authToken: String, longitude: Double, latitude: Double): PointRules =
        withContext(Dispatchers.IO) {
            val default = PointRules(id = 9, spmin = 180, spmax = 540)
            try {
                // 预热一次个人数据接口，失败不影响后续
                runCatching {
                    val warmUp = Request.Builder()
                        .url(AppConfig.MY_DATA_URL)
                        .header("User-Agent", APP_USER_AGENT)
                        .header("Accept", "application/json, text/plain, */*")
                        .header("Authorization", authToken)
                        .apply { cookiesFor("https://${AppConfig.HOST}")?.let { header("Cookie", it) } }
                        .build()
                    client.newCall(warmUp).execute().use { it.close() }
                }

                val location = URLEncoder.encode(
                    String.format("%.14f,%.14f", longitude, latitude),
                    "UTF-8"
                )
                val request = Request.Builder()
                    .url("${AppConfig.POINT_RULE_URL}?location=$location")
                    .header("User-Agent", WEB_USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Authorization", authToken)
                    .header("Cache-Control", "no-cache")
                    .header("Referer", "${AppConfig.POINT_RULE_URL}?location=$location")
                    .apply { cookiesFor("https://${AppConfig.HOST}")?.let { header("Cookie", it) } }
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: return@use default
                    if (!response.isSuccessful) {
                        Log.w(TAG, "getPointRules: HTTP ${response.code}")
                        return@use default
                    }
                    val element = Gson().fromJson(body, JsonElement::class.java)
                    val rules = element.takeIf { it.isJsonObject }
                        ?.asJsonObject
                        ?.get("data")
                        ?.takeIf { it.isJsonObject }
                        ?.asJsonObject
                        ?.get("rules")
                        ?.takeIf { it.isJsonObject }
                        ?.asJsonObject
                        ?: return@use default

                    val id = rules.get("id")?.takeIf { it.isJsonPrimitive }?.asInt ?: 6
                    val spmin = rules.get("spmin")?.takeIf { it.isJsonPrimitive }?.asInt ?: 180
                    val spmax = rules.get("spmax")?.takeIf { it.isJsonPrimitive }?.asInt ?: 540
                    PointRules(id = if (id == 6) 9 else id, spmin = spmin, spmax = spmax)
                }
            } catch (e: Exception) {
                Log.w(TAG, "getPointRules 失败，使用默认规则", e)
                default
            }
        }

    suspend fun upload(
        authToken: String,
        payload: List<Map<String, Any>>
    ): UploadResult = withContext(Dispatchers.IO) {
        val json = Gson().toJson(payload)
        val builder = Request.Builder()
            .url(AppConfig.UPLOAD_URL)
            .header("Authorization", authToken)
            .header("Content-Type", "application/json; charset=utf-8")
            .header("Accept-Encoding", "gzip")
            .header("User-Agent", "okhttp/4.10.0")
            .post(json.toRequestBody(jsonMediaType))
        cookiesFor("https://${AppConfig.HOST}")?.let { builder.header("Cookie", it) }

        val response = client.newCall(builder.build()).execute()
        val respBody = response.body?.string() ?: ""
        Log.d(TAG, "upload code=${response.code} body=${respBody.take(200)}")

        if (isJAccountLoginPage(respBody) || response.code == 403 || response.code == 401) {
            return@withContext UploadResult(false, "cookie已过期，需要重新登录", true)
        }
        try {
            val resMap = Gson().fromJson(respBody, Map::class.java)
            val code = (resMap?.get("code") as? Double)?.toInt() ?: -1
            UploadResult(code == 0, resMap?.get("message")?.toString() ?: "未知错误", false)
        } catch (e: Exception) {
            UploadResult(false, respBody.take(200), false)
        }
    }

    data class UploadResult(val success: Boolean, val message: String, val needRelogin: Boolean = false)

    private companion object {
        const val TAG = "ApiService"
        const val TASK_CENTER_PACKAGE = "edu.sjtu.infoplus.taskcenter"
        const val APP_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 12; SM-S9080 Build/V417IR; wv) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Version/4.0 Chrome/138.0.7204.67 Safari/537.36 TaskCenterApp/3.5.0"
        const val WEB_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"
    }
}
