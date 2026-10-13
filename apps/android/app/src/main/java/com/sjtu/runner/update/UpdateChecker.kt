package com.sjtu.runner.update

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.sjtu.runner.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.math.max
import java.util.concurrent.TimeUnit

data class ReleaseInfo(
    val tag: String,
    val url: String,
    val prerelease: Boolean,
    val publishedAt: String
)

sealed class UpdateResult {
    data class UpToDate(val currentTag: String, val latestTag: String) : UpdateResult()
    data class UpdateAvailable(val currentTag: String, val latest: ReleaseInfo) : UpdateResult()
    data class Failed(val reason: String) : UpdateResult()
}

object UpdateChecker {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private data class GhRelease(
        @SerializedName("tag_name") val tagName: String?,
        @SerializedName("html_url") val htmlUrl: String?,
        val draft: Boolean?,
        val prerelease: Boolean?,
        @SerializedName("published_at") val publishedAt: String?
    )

    /** 把 tag 拆成 (数字元组, 预发布后缀)，例如 "v4.4.0" -> ([4,4,0], "")。 */
    fun parseVersion(text: String?): Pair<List<Int>, String> {
        if (text.isNullOrBlank()) return emptyList<Int>() to ""
        val raw = text.trim().removePrefix("v").removePrefix("V")
        val (head, tail) = raw.split("-", limit = 2).let {
            if (it.size == 2) it[0] to it[1] else it[0] to ""
        }
        val numbers = mutableListOf<Int>()
        for (chunk in head.split(".")) {
            val digits = chunk.takeWhile { it.isDigit() }
            if (digits.isEmpty()) break
            numbers.add(digits.toInt())
        }
        return numbers to tail
    }

    /** 远程 tag 是否比本地版本新；版本号相同时稳定版优先于预发布版。 */
    fun isNewer(remoteTag: String?, localTag: String): Boolean {
        val (remoteNumbers, remotePre) = parseVersion(remoteTag)
        val (localNumbers, localPre) = parseVersion(localTag)
        if (remoteNumbers.isEmpty()) return false
        val cmp = compareKeys(remoteNumbers to 1, localNumbers to 1)
        if (cmp != 0) return cmp > 0
        return remotePre.isEmpty() && localPre.isNotEmpty()
    }

    suspend fun fetchLatestRelease(): Result<ReleaseInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(AppConfig.GITHUB_RELEASES_API)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "SJTURunningMan/${AppConfig.VERSION_NAME}")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error(shortError(response.code))
                }
                val body = response.body?.string() ?: error("空响应")
                val releases = Gson().fromJson(body, Array<GhRelease>::class.java) ?: emptyArray()
                val candidates = releases.filter { it.draft != true && parseVersion(it.tagName).first.isNotEmpty() }
                var best: GhRelease? = null
                var bestKey: Pair<List<Int>, Int> = emptyList<Int>() to -1
                for (release in candidates) {
                    val key = sortKey(release.tagName)
                    if (compareKeys(key, bestKey) > 0) {
                        bestKey = key
                        best = release
                    }
                }
                val chosen = best ?: error("未找到可用的 Release")
                ReleaseInfo(
                    tag = chosen.tagName.orEmpty(),
                    url = chosen.htmlUrl ?: "${AppConfig.GITHUB_REPO_URL}/releases",
                    prerelease = chosen.prerelease == true,
                    publishedAt = chosen.publishedAt.orEmpty()
                )
            }
        }
    }

    private fun sortKey(tag: String?): Pair<List<Int>, Int> {
        val (numbers, pre) = parseVersion(tag)
        return numbers to if (pre.isEmpty()) 1 else 0
    }

    private fun compareKeys(a: Pair<List<Int>, Int>, b: Pair<List<Int>, Int>): Int {
        val (aNumbers, aStable) = a
        val (bNumbers, bStable) = b
        val size = max(aNumbers.size, bNumbers.size)
        for (i in 0 until size) {
            val x = aNumbers.getOrElse(i) { 0 }
            val y = bNumbers.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return aStable.compareTo(bStable)
    }

    suspend fun checkForUpdates(currentVersion: String = AppConfig.VERSION_NAME): UpdateResult {
        return fetchLatestRelease().fold(
            onSuccess = { latest ->
                if (isNewer(latest.tag, currentVersion)) {
                    UpdateResult.UpdateAvailable(currentVersion, latest)
                } else {
                    UpdateResult.UpToDate(currentVersion, latest.tag)
                }
            },
            onFailure = { throwable -> UpdateResult.Failed(shortError(throwable)) }
        )
    }

    private fun shortError(throwable: Throwable): String = when {
        throwable is java.net.SocketTimeoutException -> "连接超时"
        throwable is java.io.IOException -> "网络不可用"
        else -> throwable.message ?: throwable.javaClass.simpleName
    }

    private fun shortError(httpCode: Int): String = when (httpCode) {
        403 -> "请求过于频繁 (403)"
        else -> "HTTP $httpCode"
    }
}
