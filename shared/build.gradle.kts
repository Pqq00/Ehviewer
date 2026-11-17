plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    android {
        namespace = "app.eros.shared"
        compileSdk {
            version = release(37) {
                minorApiLevel = 2
            }
        }
        minSdk = 24
        androidResources.enable = true
    }

    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            implementation(libs.miuix.ui)
            implementation(libs.miuix.preference)
            implementation(libs.miuix.icons)
            implementation(libs.miuix.squircle)
    implementation(libs.miuix.blur)
    implementation(libs.miuix.nav)
    // 玻璃材质与玻璃控件：miuix main 分支的新模块，来自 GitHub Packages 快照（见 settings.gradle.kts）。
    implementation(libs.miuix.material)
    implementation(libs.miuix.glass)
    implementation(libs.kotlinx.serialization.json)
            implementation(libs.coil.compose)
            implementation(compose.runtime)
            implementation(compose.foundation)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            implementation(project(":core"))
            implementation(libs.androidx.activity.compose)
            // FileProvider：把缓存里的图片以 content:// 交给系统分享
            implementation(libs.androidx.core.ktx)
            implementation(libs.coil.network.okhttp)
        }
        val desktopMain by getting
        desktopMain.dependencies {
            // desktop 只作为 commonTest 的运行宿主（没有桌面端 app），
            // 不跑渲染，所以只取 compose.ui；不需要 compose.desktop.currentOs
            // 那套 skiko 原生运行时。
            implementation(compose.ui)
            implementation(project(":core"))
            implementation(libs.coil.network.okhttp)
        }
    }
}
