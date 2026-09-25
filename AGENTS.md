# 开发约束

- 同时保留 `LAUNCHER` 和 `LEANBACK_LAUNCHER`，支持 BlueStacks 普通桌面与电视桌面启动。
- 电影、综艺的片库、搜索、详情和播放使用手机/HD 接口及客户端身份，禁止使用 `/x/tv/` 内容接口或 `android_tv_yst` 身份。分页保持同一来源和排序。
- 移动端影视搜索 `type=8` 包含多个影视分类，电影、综艺必须按 `season_type` 隔离；过滤后空页不能直接判定整个搜索无结果。
- 电影、综艺搜索分别维护关键词和结果状态。内容目录接口与播放权限分开处理，沿用服务端的会员、地区和试看限制。
- HD 的扫码登录使用共享的 `/x/passport-tv-login/qrcode/` 路径，但必须使用 HD appkey 签名；路径名称不代表 TV 客户端身份。令牌绑定签发 appkey，旧客户端令牌不能用于 HD 签名请求，迁移时重新扫码并保留历史记录。
- 节目导航、重试、切换清晰度和历史记录必须保留 season_id、ep_id、cid；节目播放使用手机接口，不重新依赖普通视频的详情接口。
- 播放器同时处理 DASH 与 `durl` MP4；服务端的 `is_preview` 决定试看状态，不能用节目总时长代替实际试看流时长，也不能强制把所有节目降成试看。
- 手机/HD API 和媒体请求使用移动端 UA，不带网页 Origin / Referer；用独立客户端处理，避免通用网页拦截器覆盖这些请求头。
- x86 环境默认 AVC 解码器为 `OMX.qcom.video.decoder.avc` 时，优先已有的 `OMX.ffmpeg.h264.decoder`，避免 BlueStacks 虚拟 AVC 解码停滞；不要改变 ARM 盒子的默认解码器顺序。
