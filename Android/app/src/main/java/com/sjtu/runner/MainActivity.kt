package com.sjtu.runner

import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.sjtu.runner.login.LoginActivity
import com.sjtu.runner.route.RouteDesignActivity
import com.sjtu.runner.ui.MainScreen
import com.sjtu.runner.ui.theme.SJtuRunnerTheme
import com.sjtu.runner.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    companion object {
        fun getAppUid(context: android.content.Context): Int {
            return try {
                context.packageManager
                    .getApplicationInfo(context.packageName, 0)
                    .uid
            } catch (e: Exception) {
                -1
            }
        }
    }

    private val viewModel by lazy {
        ViewModelProvider(this)[MainViewModel::class.java]
    }

    private val loginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            logMsg("重新登录成功，重新加载界面...")
            setContent {
                AppContent()
            }
        } else {
            logMsg("重新登录失败")
            finish()
        }
    }

    private val routeDesignLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val routeSaved = result.data?.getBooleanExtra(RouteDesignActivity.EXTRA_ROUTE_SAVED, false) ?: false
            if (routeSaved) {
                logMsg("新路线已创建，刷新列表")
                viewModel.refreshRoutes()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        viewModel.onReloginRequired = {
            runOnUiThread {
                logMsg("检测到cookie过期，正在重新登录...")
                loginLauncher.launch(Intent(this, LoginActivity::class.java))
            }
        }

        val cookie = CookieManager.getInstance().getCookie("https://jaccount.sjtu.edu.cn")
        if (cookie?.contains("JAAuthCookie") == true) {
            setContent {
                AppContent()
            }
        } else {
            logMsg("未检测到登录状态，启动登录页面...")
            loginLauncher.launch(Intent(this, LoginActivity::class.java))
        }
    }

    @Composable
    private fun AppContent() {
        SJtuRunnerTheme {
            MainScreen(
                viewModel = viewModel,
                onNavigateToRouteDesign = {
                    routeDesignLauncher.launch(Intent(this@MainActivity, RouteDesignActivity::class.java))
                },
                onReloginRequested = {
                    loginLauncher.launch(Intent(this@MainActivity, LoginActivity::class.java))
                }
            )
        }
    }

    private fun logMsg(msg: String) {
        println("MainActivity: $msg")
    }
}