pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        // miuix 新版本刚发 Central 时 aliyun 镜像通常还要滞后几小时，给这个 group 直连 Central。
        maven("https://repo1.maven.org/maven2") {
            name = "miuixCentral"
            content { includeGroup("top.yukonga.miuix.kmp") }
        }
        // Google Maven 必须排在 aliyun 镜像之前：aliyun 的 google 镜像有时只有模块元数据、
        // 缺 desktop 分类 jar（如 compose 1.13 的 runtime-retain-desktop），Gradle 命中元数据后
        // 不会回退到下一个仓库，会直接报 Could not find ...jar。
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/google")
        mavenCentral()
    }
}

rootProject.name = "eros-miuix"


include(":shared")
include(":android")
include(":core")
