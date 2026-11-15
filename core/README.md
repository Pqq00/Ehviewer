# :core —— 数据层

E-Hentai 站点协议与解析层

## 结构

```
core/src/main/
├─ kotlin/com/hippo/
│  ├─ ehviewer/client/EhEngine.kt        ← 站点请求与解析的对外入口
│  ├─ ehviewer/client/EhHttp.kt          ← OkHttp 客户端 + 代理 + HTTP 缓存
│  ├─ ehviewer/client/EhAuth.kt          ← 账号 cookie 管理
│  ├─ ehviewer/client/PageBatchHarvester.kt ← 页地址批量抓取（原 shared 两份实现合并至此）
│  ├─ ehviewer/client/parser/*           ← 各页面解析器
│  └─ ehviewer/net/*                     ← cookie jar、HTTP 缓存、日志、异常分类
└─ java/com/hippo/
   ├─ ehviewer/client/parser/*.java      ← 上游派生或重写的解析器
   ├─ lib/yorozuya/*                     ← 上游工具库（保留 Apache 头）
   └─ network/UrlBuilder.java
```

## 测试

```bash
./gradlew :core:test
```

解析器测试只用 `src/test/resources/fixtures` 里的真实页面快照，不访问网络。
需要联真实端点时可参考 `LiveFetchTest`（经 `EH_PROXY` 指定代理）。

## 传输层

`net/EhHttp.kt` 是全局 OkHttp 客户端，支持两种代理模式：

| 模式 | 行为 |
|---|---|
| `ProxyConfig.System` | 跟随系统：Android 用系统代理/VPN，桌面用 JVM 默认 ProxySelector |
| `ProxyConfig.Http(host, port)` | 手动指定，例如 `127.0.0.1:7897` |

## 踩坑记录

**e-hentai 会间歇性"假死"**：同一个代理、同一秒，curl 5/5 成功，OkHttp 偶尔 read timeout 15s。
不是 TLS 指纹问题（强制 HTTP/1.1、换 Conscrypt 都试过，默认 JVM 栈也能 200）。
结论：Cloudflare 侧针对该出口 IP 的限流/抖动，**重试 + 足够超时**即可，不要为此换 TLS 实现。

## 开发约定

1. 新增文件必须带许可头：自有代码用 `// Copyright 2026, eros-miuix contributors` + `// SPDX-License-Identifier: Apache-2.0`。
2. 直接派生自上游的文件保留上游原头，不要改成自有头。
3. 改动解析器后跑 `:core:test`，必要时补 fixture。
4. 不引入 GPL / 非自由许可的依赖。
