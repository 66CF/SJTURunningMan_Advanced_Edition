package com.sjtu.runner.login

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

class LoginActivity : Activity() {

    companion object {
        private const val TAG = "LoginActivity"
        private const val JACCOUNT_SCHEME = "jaccount"
        private const val LOGIN_URL = "https://jaccount.sjtu.edu.cn/oauth2/authorize" +
                "?response_type=code" +
                "&scope=profile" +
                "&client_id=9mqzULSXYgUYj5fPOpyL" +
                "&state=8" +
                "&redirect_uri=https://pe.sjtu.edu.cn/oauth2Login"
        private const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) " +
                    "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 " +
                    "Mobile/15E148 Safari/604.1"
    }

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val backgroundColor = if (isDarkMode) {
            android.graphics.Color.parseColor("#121212")
        } else {
            android.graphics.Color.parseColor("#F8FAFC")
        }

        val container = FrameLayout(this).apply {
            setBackgroundColor(backgroundColor)
        }

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                8.dpToPx()
            ).apply { topMargin = 0 }
        }

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = MOBILE_USER_AGENT
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val uri = request?.url ?: return false
                    return handleExternalLoginScheme(uri)
                }

                @Deprecated("Deprecated in Java")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    val uri = url?.let { Uri.parse(it) } ?: return false
                    return handleExternalLoginScheme(uri)
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    progressBar.visibility = View.VISIBLE
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    progressBar.visibility = View.GONE
                    Log.d(TAG, "onPageFinished: $url")

                    val cookies = CookieManager.getInstance().getCookie("https://pe.sjtu.edu.cn")
                    val jaccountCookies = CookieManager.getInstance().getCookie("https://jaccount.sjtu.edu.cn")
                    Log.d(TAG, "Cookies for pe.sjtu.edu.cn: $cookies")
                    Log.d(TAG, "Cookies for jaccount.sjtu.edu.cn: $jaccountCookies")

                    val isLoginComplete = when {
                        url?.startsWith("https://pe.sjtu.edu.cn/oauth2Login") == true -> true
                        !cookies.isNullOrBlank() && cookies.contains("JSESSIONID") -> true
                        url?.startsWith("https://pe.sjtu.edu.cn") == true &&
                                !url.contains("oauth2/authorize") &&
                                !url.contains("jaccount") -> true
                        else -> false
                    }

                    if (isLoginComplete) {
                        Log.d(TAG, "Login complete, finishing activity")
                        setResult(RESULT_OK)
                        finish()
                    }
                }
            }

            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        container.addView(progressBar)
        container.addView(webView)
        setContentView(container)

        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        webView.loadUrl(LOGIN_URL)
    }

    private fun setupSystemBars() {
        val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDarkMode
            isAppearanceLightNavigationBars = !isDarkMode
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    private fun handleExternalLoginScheme(uri: Uri): Boolean {
        val scheme = uri.scheme ?: return false
        if (scheme.equals(JACCOUNT_SCHEME, ignoreCase = true)) {
            return openJwbLogin(uri)
        }
        return scheme != "http" && scheme != "https"
    }

    private fun openJwbLogin(uri: Uri): Boolean {
        return try {
            Log.d(TAG, "Opening jAccount URI with external app: $uri")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
            Toast.makeText(this, "已跳转交我办，请完成登录后返回", Toast.LENGTH_SHORT).show()
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No app can handle jAccount URI: $uri", e)
            Toast.makeText(this, "未找到交我办，留在网页登录", Toast.LENGTH_LONG).show()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to open jAccount URI: $uri", e)
            Toast.makeText(this, "无法跳转交我办，留在网页登录", Toast.LENGTH_LONG).show()
            true
        }
    }
}
