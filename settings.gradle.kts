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
        // miuix 的快照包（玻璃材质等新模块）只发布在 GitHub Packages 上，需 read:packages 凭据。
        // 凭据放在 ~/.gradle/gradle.properties 的 gpr.user / gpr.key，不入仓库。
        maven {
            name = "miuixSnapshots"
            url = uri("https://maven.pkg.github.com/compose-miuix-ui/miuix")
            content { includeGroup("top.yukonga.miuix.kmp") }
            credentials {
                username = providers.gradleProperty("gpr.user")
                    .orElse(System.getenv("GITHUB_ACTOR") ?: "")
                    .get()
                password = providers.gradleProperty("gpr.key")
                    .orElse(System.getenv("GITHUB_TOKEN") ?: "")
                    .get()
            }
        }
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/google")
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "eros-miuix"


include(":shared")
include(":android")
include(":core")
