// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.android

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.res.painterResource
import app.eros.shared.App
import app.eros.shared.settings.AndroidPlatform
import app.eros.shared.ui.LocalAppIcon

class MainActivity : ComponentActivity() {

    companion object {
        private const val REQ_NOTIFICATION = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        println("[Life] MainActivity.onCreate savedInstanceState=${savedInstanceState != null}")
        AndroidPlatform.appContext = applicationContext
        AndroidPlatform.currentActivity = this
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestNotificationPermission()
        setContent {
            // 账号页头部要显示应用图标，shared 模块读不到 app 模块的资源，这里注入进去。
            // 用 drawable 里的光栅版而不是 R.mipmap.ic_eros：后者在 API 26+ 是自适应图标 XML，
            // painterResource 只认 VectorDrawable 与位图，会在首帧抛 IllegalArgumentException。
            CompositionLocalProvider(LocalAppIcon provides painterResource(R.drawable.ic_app_icon)) {
                App()
            }
        }
    }

    /** Android 13+ 需要运行时申请通知权限，否则下载通知不可见。 */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATION)
        }
    }

    override fun onDestroy() {
        if (AndroidPlatform.currentActivity === this) AndroidPlatform.currentActivity = null
        super.onDestroy()
    }
}
