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
    // 玻璃图标集（MiuixIcons.Glass.*，ClockOutline/BookmarkOutline/HomeOutline/GlobalVpn/StarOutline 等）。
    implementation(libs.miuix.glass.icons)
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


// Phase 3/H4 守卫：页面骨架只允许 ErosPageScaffold / ErosImmersiveScaffold 使用 miuix Scaffold。
// 本地检查（无 CI）：./gradlew :shared:checkUiStructure
tasks.register("checkUiStructure") {
    group = "verification"
    description = "Ui screens must go through ErosPageScaffold / ErosImmersiveScaffold instead of raw Scaffold"
    doLast {
        val root = file("src/commonMain/kotlin/app/eros/shared/ui")
        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter {
                it.name != "ErosPageScaffold.kt" &&
                    it.name != "ErosImmersiveScaffold.kt" &&
                    !it.invariantSeparatorsPath.contains("/reader/")
            }
            .filter { file ->
                file.readLines().any {
                    it.trimStart().startsWith("Scaffold(") ||
                        it.contains("import top.yukonga.miuix.kmp.basic.Scaffold")
                }
            }
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .toList()
        check(offenders.isEmpty()) {
            "raw Scaffold found in ui screens (use ErosPageScaffold): " + offenders.joinToString()
        }
    }
}

// H5 守卫：浮层与记录层的层级契约，规则见仓库根 UI_LAYERS.md。
// 本地检查（无 CI）：./gradlew :shared:checkLayering
tasks.register("checkLayering") {
    group = "verification"
    description = "Overlays and glass record layers must follow UI_LAYERS.md"
    doLast {
        val root = file("src/commonMain/kotlin/app/eros/shared/ui")

        class Rule(
            val name: String,
            val matches: (String) -> Boolean,
            val allowed: (String) -> Boolean,
        )

        fun rel(file: java.io.File) = file.relativeTo(root).invariantSeparatorsPath
        fun codeLines(file: java.io.File) = file.readLines().filter {
            val trimmed = it.trimStart()
            !trimmed.startsWith("//") && !trimmed.startsWith("*") && !trimmed.startsWith("/*")
        }

        val rules = listOf(
            Rule(
                name = "miuix Overlay*/Popup/Dialog 直达（应走 ErosBottomSheet / ErosDialog / ErosConfirmDialog）",
                matches = { line ->
                    val trimmed = line.trimStart()
                    trimmed.startsWith("Popup(") || trimmed.startsWith("Dialog(") ||
                        trimmed.startsWith("OverlayDialog(") || trimmed.startsWith("OverlayBottomSheet(") ||
                        trimmed.startsWith("OverlayPopup(")
                },
                allowed = { path ->
                    path == "ErosGlassOverlays.kt" || path == "ConfirmDialog.kt" || path.startsWith("reader/")
                },
            ),
            Rule(
                name = "MiuixPopupUtils 直达（宿主由 App.kt 统一提供）",
                matches = { it.contains("MiuixPopupUtils.") },
                allowed = { false },
            ),
            Rule(
                name = "layerBackdrop( 直达（应使用 ErosGlassRecordLayer）",
                matches = { it.contains("layerBackdrop(") },
                allowed = { path ->
                    path == "chrome/ErosGlassRecordLayer.kt" ||
                        path == "ErosPageScaffold.kt" ||
                        path == "AccountScreen.kt" || // 页面背景自建的渐变色层
                        path.startsWith("liquid/") ||
                        path.startsWith("reader/")
                },
            ),
        )

        val offenders = rules.flatMap { rule ->
            root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter { !rule.allowed(rel(it)) }
                .filter { file -> codeLines(file).any(rule.matches) }
                .map { "${rule.name} -> ${rel(it)}" }
                .toList()
        }
        check(offenders.isEmpty()) {
            "ui layering violations (see UI_LAYERS.md):\n" + offenders.joinToString("\n")
        }

        // H3：L3 浮层必须显式传 backdrop，不能吃默认值。
        val missingBackdrop = mutableListOf<String>()
        root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.name != "ErosGlassOverlays.kt" }
            .forEach { file ->
                val lines = file.readLines()
                lines.forEachIndexed { index, line ->
                    val trimmed = line.trimStart()
                    if (trimmed.startsWith("ErosBottomSheet(") || trimmed.startsWith("ErosDialog(")) {
                        val window = lines.drop(index + 1).take(12)
                        if (window.none { it.contains("backdrop =") }) {
                            missingBackdrop +=
                                "${file.relativeTo(root).invariantSeparatorsPath}:${index + 1}"
                        }
                    }
                }
            }
        check(missingBackdrop.isEmpty()) {
            "overlay calls missing an explicit backdrop= (see UI_LAYERS.md): " +
                missingBackdrop.joinToString()
        }
    }
}
