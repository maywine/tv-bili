# tv-bili

TV 原生、单 ABI armeabi-v7a、Compose-only 的盒子专用 B 站客户端。

> 🤖 **本项目由 AI 生成，不保证代码正确性。** 仅供学习参考，使用前请自行审阅。

> ⚠️ **本项目仅用于学习研究**：不绕过版权墙、不上架应用市场、不分发签名 APK。所有使用风险由使用者自行承担。

设计与决策见 [`DESIGN.md`](./DESIGN.md)。

## 自行构建

仓库不提供预编译 APK，请自行编译：

```bash
git clone https://github.com/<your-username>/tv-bili.git
cd tv-bili
./gradlew :app:assembleRelease     # ~3.3 MB, armeabi-v7a
# 或 debug 版（含调试符号，~17 MB）：
./gradlew :app:assembleDebug
```

产物位于 `app/build/outputs/apk/{release,debug}/`，通过 `adb install` 或 sideload 到电视盒子。

**环境要求**：JDK 21、Android SDK（API 34）、`gradlew` 会自动下 Gradle 8.x。

## 接口与签名

WBI / AppSign / Media3 LoadControl / DanmakuFlameMaster 桥接的实现思路参考 [BiliPai](https://github.com/jay3-yy/BiliPai.git)；上游接口规范以 [bilibili-API-collect](https://github.com/SocialSisterYi/bilibili-API-collect) 为准。

## License

[MIT](./LICENSE) — 仅代码本身。第三方依赖各自遵循其许可证；B 站接口使用受 B 站用户协议约束，与本项目无关。
