# tv-bili — 设计与决策

TV 原生、单 ABI armeabi-v7a、Compose-only 的 B 站客户端设计文档。记录技术选型、模块边界、关键决策。

> 实现状态：核心功能（登录 / 首页 / 视频播放 / 直播 / 搜索 / 历史 / 个人中心 / 32 位优化）均已落地，详见 `git log`。

---

## 项目目标

为电视盒子写一个 **遥控器优先、功能极简、APK ≤ 20 MB** 的第三方 B 站客户端：

- 官方「云视听小电视」功能阉割、广告位多、登录后部分功能仍受限
- 现有第三方 TV 客户端大多手机优先，1 GB 旧盒子吃力
- 用户只关心「推荐 / 热门 / 直播」三个主入口，其它按需开关

## 不做哪些（NOT Building）

- ❌ 评论展示 / 发送 — 遥控器输入痛苦，10 ft 距离看不清
- ❌ 弹幕发送 — 同上
- ❌ 动态（关注流）发布与互动
- ❌ 离线缓存 / 下载
- ❌ 投屏（DLNA 收/发）
- ❌ 多账号
- ❌ 插件系统
- ❌ 番剧版权墙处理 / 港澳台解锁 — 不做绕过
- ❌ 手机/平板布局 — Manifest 层就排除非 leanback 设备
- ❌ Play Store 上架 / 隐私政策页

## 成功指标

| 指标 | 目标 |
|---|---|
| APK 体积 | ≤ 20 MB（release） |
| 单 ABI | `armeabi-v7a` only |
| 冷启 → 首页可交互 | < 2.5s（1 GB 盒子） |
| 稳态内存 | ≤ 200 MB |
| 滚动 fps | ≥ 55 fps |

---

## 技术栈

| 类别 | 选型 | 备注 |
|---|---|---|
| 语言 | Kotlin 2.0+ | — |
| UI | Jetpack Compose（Material 3） | **不引** tv-material（alpha 风险）/ Haze / Backdrop / Cupertino / Miuix |
| 焦点系统 | Compose 原生 `focusable / focusGroup / focusRestorer / bringIntoView` | 不依赖 alpha `tv-foundation` |
| 网络 | Retrofit + OkHttp + Kotlinx Serialization | — |
| 存储 | DataStore Preferences | 历史 / 分区配置 / 凭据；不引 Room |
| 媒体 | AndroidX Media3 ExoPlayer | 视频 DASH / 直播 HLS |
| 弹幕 | DanmakuFlameMaster | 不引 DanmakuRenderEngine |
| 图片 | Coil 3（按 RAM 分档：6%/5% maxMemory）| — |
| 二维码 | ZXing core（仅生成）| 显示在屏让手机扫；不做扫码 |
| DI | 手动 `object NetworkModule` | 项目小不值得 Hilt |
| 后台任务 | 无 | — |
| Analytics / Crashlytics | **不接** | — |
| Build | minSdk 26 / targetSdk 34 / JDK 21 | armeabi-v7a only |

## 模块划分

单 `:app` 模块（15k LoC 量级，多模块编译收益不抵复杂度）：

```
app/src/main/java/dev/tvbili/
├── ui/             # Compose screens
│   ├── home/       # SideBar + 分区切换 + 卡片网格
│   ├── login/      # 二维码登录
│   ├── profile/    # 个人中心 + 退出登录
│   ├── video/      # 视频详情 + 播放器 + 弹幕浮层
│   ├── live/       # 直播间 + 实时弹幕
│   ├── search/     # 搜索
│   └── settings/   # 分区编辑
├── data/
│   ├── api/        # Retrofit 接口
│   ├── model/      # 响应 DTO（@Immutable）
│   ├── repo/       # 仓库层
│   └── store/      # DataStore（cookie / 分区 / 历史）
├── net/            # OkHttp / WBI / 拦截器
├── player/         # Media3 配置 + 弹幕桥接 + LoadControl
└── tv/             # 焦点工具 / 设备探测 / KeepScreenOn
```

## B 站接口清单（最小集）

| 域 | 接口 | 用途 |
|---|---|---|
| Passport | `passport.bilibili.com/x/passport-tv-login/qrcode/auth_code` | TV 端二维码生成（AppSign） |
| Passport | `passport.bilibili.com/x/passport-tv-login/qrcode/poll` | 轮询扫码状态 |
| Passport | `api.bilibili.com/x/web-interface/nav` | 用户信息 / 验证登录 / 拉 WBI key |
| Feed | `api.bilibili.com/x/web-interface/wbi/index/top/feed/rcmd` | 推荐流 ★WBI |
| Feed | `api.bilibili.com/x/web-interface/popular` | 热门 |
| Feed | `api.bilibili.com/x/web-interface/ranking/v2` | 排行（分区候选） |
| Live | `api.live.bilibili.com/xlive/web-interface/v1/second/getList` | 直播分区列表 |
| Live | `api.live.bilibili.com/xlive/web-room/v2/index/getRoomPlayInfo` | 直播间播放信息（HLS） |
| Live | `api.live.bilibili.com/xlive/web-room/v1/index/getDanmuInfo` | 弹幕 WS token |
| Video | `api.bilibili.com/x/web-interface/view` | 视频详情 |
| Video | `api.bilibili.com/x/player/wbi/playurl` | DASH 播放地址 ★WBI |
| Video | `comment.bilibili.com/{cid}.xml` | XML 弹幕（按 cid） |
| Search | `api.bilibili.com/x/web-interface/wbi/search/all/v2` | 综合搜索 ★WBI |

> 完整规范：[bilibili-API-collect](https://socialsisteryi.github.io/bilibili-API-collect/)；新接口先查文档对 WBI / 签名规则。

---

## 关键技术决策

| 决策 | 选择 | 备选 | 理由 |
|---|---|---|---|
| TV 焦点库 | Compose 原生 | `androidx.tv:tv-foundation` (alpha) | 原生 API 够用；alpha 库有 R8 + breakage 风险 |
| 二维码登录终端 | TV 端 AppSign + auth_code 协议 | web 端 QR | TV 端协议更稳定、CookieInfo 直接返回 |
| 单 ABI | `armeabi-v7a` only | universal | 32 位盒子兼容性最广，APK 体积减半 |
| 启动 Activity | 单 Activity + Compose state nav | 多 Activity / NavHost | 项目小，sealed interface 路由够用 |
| 配色 | 暗色 only | 跟随系统 | 客厅 10 ft 暗色舒适，OLED 防烧屏 |
| DI 方案 | 手动 `object` 容器 | Hilt / Koin | 依赖图浅，KAPT 拖编译速度 |
| 存储 | DataStore Preferences only | Room + DataStore | 无大表数据；历史 200 条 K-V 足够 |
| 弹幕引擎 | DanmakuFlameMaster | DanmakuRenderEngine | 体积更小、配置成熟 |
| 视频默认清晰度 | 1080P（4K 可选） | 720P / 自动 | TV 屏大；4K 多数 1 GB 盒子带不动 |
| Player Buffer | 按 RAM 分档（32/64 MB） | 固定值 | 1 GB 盒子保守、3 GB 中端放宽，单元测试验证过 |
| Coil cache | 按 RAM 分档（5% / 6%） | 默认 25% | TV 上仅播放页用图，默认浪费 |

## 技术风险与缓解

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| WBI 算法上游变更 | M | 整站接口 401 | 监听 `bilibili-API-collect`；签名独立模块便于热替换 |
| Compose 焦点在低端 GPU 卡顿 | M | 体验差 | TV 上禁用 `crossfade`；首焦明确 `requestFocus` |
| ExoPlayer HEVC 软解 OOM | M | 视频崩 | DASH 优先 H.264；HEVC 走硬解黑名单 |
| 部分盒子无 IME → 搜索不能输入 | M | 搜索不可用 | 上层显式提示；后续可加 9 宫格兜底 |
| `armeabi-v7a` 库缺失 | L | 构建失败 | Coil 3 / Media3 / ZXing core 都有 v7a；上线前 `unzip -l` 巡检 |

## 与 BiliPai 的代码复用

[BiliPai](https://github.com/jay3-yy/BiliPai) 已在多款盒子上验证过 B 站接口/签名/播放/弹幕等核心路径；本项目移植以下模块的算法/数值，**不建立代码依赖**，独立演进：

| 来源（思路参考） | 目标 |
|---|---|
| `core/network/WbiUtils.kt` | `net/WbiUtils.kt`（删 Hilt 注解） |
| `core/network/WbiKeyManager.kt` | `net/WbiKeyManager.kt`（改用 DataStore） |
| `core/network/AppSignUtils.kt` | `net/AppSignUtils.kt` |
| `core/network/socket/` (WebSocket 协议) | `net/socket/LiveDanmakuClient.kt` + `LiveDanmakuProtocol.kt` |
| `feature/video/danmaku/DanmakuConfig.kt`（精简版）| `player/DanmakuConfig.kt`（TV 默认值：轨道少 / 字号大 / displayArea ≤ 50%） |
| `feature/video/state/VideoPlayerState.kt` 的 Buffer 数值 | `player/PlayerBufferPolicy.kt`（按 RAM 分档） |

上游接口规范以 [bilibili-API-collect](https://github.com/SocialSisterYi/bilibili-API-collect) 为准。
