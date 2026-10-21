package com.sjtu.runner.network

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.sjtu.runner.config.AppConfig
import com.sjtu.runner.stats.DayState
import com.sjtu.runner.stats.SemesterRange
import com.sjtu.runner.stats.SemesterStats
import com.sjtu.runner.stats.percentFromRatio
import com.sjtu.runner.stats.recordDay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.time.LocalDate
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class PointRules(
    val id: Int,
    val spmin: Int,
    val spmax: Int
)

/** 会话 Cookie 失效：调用方据此提示重新登录，错误文本里不带响应正文。 */
class SessionExpiredException(message: String) : Exception(message)

/** 学期统计接口返回了无法使用的数据。 */
class StatsException(message: String) : Exception(message)

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

    /**
     * 只读学期统计：达标公里、完成率、学期起止与本学期打卡日期。
     *
     * 这些接口只要 pe.sjtu.edu.cn 的会话 Cookie，不需要 `/sports/my/uid` 换的 token。
     * 其中学期目标与学期起止日期失败时不阻塞其余数据。
     */
    suspend fun semesterStats(): SemesterStats = withContext(Dispatchers.IO) {
        val summary = firstObject(getJson(AppConfig.MY_DATA_URL, emptyMap(), "学期统计"), "data")
        val resultKm = jsonNumber(summary?.get("resultKm"))
        val totalKm = jsonNumber(summary?.get("totalKm"))
        val percent = jsonNumber(summary?.get("percent")) ?: percentFromRatio(resultKm, totalKm)

        val targetKm = optional {
            jsonNumber(objOf(getJson(AppConfig.SEMESTER_TARGET_URL, emptyMap(), "学期目标"), "data")?.get("target"))
        }

        val entry = firstObject(
            getJson(AppConfig.SEMESTER_LIST_URL, mapOf("type" to "1"), "学期列表"),
            "data",
        )
        val semesterId = entry.stringOrNull("semesterId")?.takeIf { it.isNotBlank() }
            ?: throw StatsException("学期列表缺少 semesterId")
        val semesterName = entry.stringOrNull("semesterName")?.trim()?.takeIf { it.isNotEmpty() } ?: "本学期"

        val range = optional {
            val item = firstObject(
                objOf(postEmptyJson(AppConfig.USER_INFO_URL, "学期日期"), "data"),
                "semesterDateList",
            )
            val start = serverDate(item.stringOrNull("startDate"))
            val end = serverDate(item.stringOrNull("endDate"))
            if (start != null && end != null) SemesterRange.of(start, end) else null
        }

        val records = getJson(
            AppConfig.RESULT_LIST_URL,
            mapOf("selectId" to semesterId, "type" to "1"),
            "跑步记录",
        )
        val runDays = LinkedHashMap<LocalDate, DayState>()
        for (week in arrOf(records, "data")) {
            for (item in arrOf(week.asObjectOrNull(), "details")) {
                val record = item.asObjectOrNull() ?: continue
                val date = serverDate(record.stringOrNull("timeFrom")) ?: continue
                val state = if (countsTowardScore(record.get("state"))) DayState.VALID else DayState.INVALID
                recordDay(runDays, date, state)
            }
        }

        Log.d(TAG, "semesterStats ok: runDays=${runDays.size} range=${range != null}")
        SemesterStats(
            resultKm = resultKm,
            totalKm = totalKm,
            percent = percent,
            targetKm = targetKm,
            semesterName = semesterName,
            range = range,
            runDays = runDays,
        )
    }

    private fun getJson(url: String, query: Map<String, String>, what: String): JsonElement {
        val builder = url.toHttpUrl().newBuilder()
        query.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        val request = Request.Builder()
            .url(builder.build())
            .header("User-Agent", APP_USER_AGENT)
            .header("Accept", "application/json, text/plain, */*")
            .header("Referer", "https://${AppConfig.HOST}/phone/")
            .apply { cookiesFor("https://${AppConfig.HOST}")?.let { header("Cookie", it) } }
            .build()
        return client.newCall(request).execute().use { decodeJson(it, what) }
    }

    private fun postEmptyJson(url: String, what: String): JsonElement {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", APP_USER_AGENT)
            .header("Accept", "application/json, text/plain, */*")
            .header("Referer", "https://${AppConfig.HOST}/phone/")
            .header("Content-Type", "application/json; charset=utf-8")
            .apply { cookiesFor("https://${AppConfig.HOST}")?.let { header("Cookie", it) } }
            .post("{}".toRequestBody(jsonMediaType))
            .build()
        return client.newCall(request).execute().use { decodeJson(it, what) }
    }

    /**
     * 未登录时这些接口会 302 到登录页，跟随跳转后拿到的是 HTML；
     * 统一识别成「登录状态已过期」，并且绝不把响应正文带进错误信息。
     */
    private fun decodeJson(response: Response, what: String): JsonElement {
        val finalUrl = response.request.url.toString()
        if (finalUrl.contains("/login") || finalUrl.contains("jaccount")) {
            throw SessionExpiredException("$what：$LOGIN_EXPIRED")
        }
        if (!response.isSuccessful) {
            Log.w(TAG, "$what: HTTP ${response.code}")
            throw StatsException("$what：服务器返回 HTTP ${response.code}")
        }
        val body = response.body?.string()
        val value = try {
            Gson().fromJson(body, JsonElement::class.java)
        } catch (e: Exception) {
            null
        }
        if (body == null || value == null || value.isJsonNull) {
            Log.w(TAG, "$what: 响应不是可用的 JSON")
            throw SessionExpiredException("$what：$LOGIN_EXPIRED")
        }
        val code = value.asObjectOrNull()?.get("code")
        if (code != null && !code.isJsonNull && !isZeroCode(code)) {
            Log.w(TAG, "$what: code 非 0")
            throw SessionExpiredException("$what：$LOGIN_EXPIRED")
        }
        return value
    }

    private fun isZeroCode(value: JsonElement): Boolean {
        val text = try {
            value.asString.trim()
        } catch (e: Exception) {
            return false
        }
        return text == "0" || text.toDoubleOrNull() == 0.0
    }

    /** 服务器的数值都是字符串（"80.00"、"100.00"、"80"），偶尔带百分号或千分位。 */
    private fun jsonNumber(value: JsonElement?): Double? {
        val raw = when {
            value == null || value.isJsonNull -> return null
            value.isJsonPrimitive && value.asJsonPrimitive.isNumber -> value.asString.trim()
            value.isJsonPrimitive && value.asJsonPrimitive.isString -> value.asString.trim()
            else -> return null
        }
        return raw.trimEnd('%').replace(",", "").toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    /** 服务器日期形如 `2026-09-19 07:52:19`，只取前 10 个字符；解析不出的记录直接跳过。 */
    private fun serverDate(text: String?): LocalDate? {
        if (text == null || text.length < 10) {
            return null
        }
        return try {
            LocalDate.parse(text.substring(0, 10))
        } catch (e: Exception) {
            null
        }
    }

    /** `state == "0"` 计入成绩，12/13 等是无效记录。 */
    private fun countsTowardScore(value: JsonElement?): Boolean {
        if (value == null || value.isJsonNull || !value.isJsonPrimitive) {
            return false
        }
        val text = try {
            value.asString.trim()
        } catch (e: Exception) {
            return false
        }
        return text.toLongOrNull() == 0L
    }

    private fun objOf(parent: JsonElement?, key: String): JsonObject? =
        parent.asObjectOrNull()?.get(key).asObjectOrNull()

    private fun arrOf(parent: JsonElement?, key: String): List<JsonElement> {
        val array = parent.asObjectOrNull()?.get(key)
        return if (array != null && array.isJsonArray) array.asJsonArray.toList() else emptyList()
    }

    private fun firstObject(parent: JsonElement?, key: String): JsonObject? =
        arrOf(parent, key).firstOrNull().asObjectOrNull()

    private fun JsonElement?.asObjectOrNull(): JsonObject? =
        this?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject?.stringOrNull(key: String): String? {
        val value = this?.get(key) ?: return null
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) {
            return null
        }
        return value.asString
    }

    /** 非阻塞步骤：失败只说明「这一步没有数据」，但协程取消必须照常传播。 */
    private fun <T> optional(block: () -> T?): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val TAG = "ApiService"
        const val LOGIN_EXPIRED = "登录状态已过期，请重新登录后重试"
        const val TASK_CENTER_PACKAGE = "edu.sjtu.infoplus.taskcenter"
        const val APP_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 12; SM-S9080 Build/V417IR; wv) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Version/4.0 Chrome/138.0.7204.67 Safari/537.36 TaskCenterApp/3.5.0"
        const val WEB_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"
    }
}
