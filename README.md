<p align="center">
  <img src="assets/app-icon.png" width="160" alt="Ehviewer 图标" />
</p>

# Ehviewer

一款为 e-hentai 与 exhentai 打造的 Android 漫画客户端，使用 Kotlin、Jetpack Compose 与 miuix 构建

> 本项目完全由 AI 编写：代码、文档与测试均出自 AI

源码树沿用了工程名 `eros-miuix`：`:shared` 的包名为 `app.eros.shared`，
`:core` 的包名为 `com.hippo.ehviewer.*`。

## 构建

需要 JDK 21 与 Android SDK（compileSdk 37.2）：

```bash
./gradlew :android:assembleDebug
```

跑数据层与界面层的测试：

```bash
./gradlew :core:test :shared:desktopTest
```


## 感谢

- [EhViewer](https://github.com/seven332/EhViewer)
- [miuix](https://github.com/compose-miuix-ui/miuix)
- [JHenTai](https://github.com/jiangtian616/JHenTai)
- [Ehviewer_CN_SXJ](https://github.com/xiaojieonly/Ehviewer_CN_SXJ)
- [eros_fe](https://github.com/erosTeam/eros_fe)
- [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)
- [DatabaseReleases](https://github.com/EhTagTranslation/DatabaseReleases)
- [Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform)
- [Coil](https://github.com/coil-kt/coil)
- [AndroidX](https://developer.android.com/jetpack/androidx)
- [Kotlin](https://kotlinlang.org)
