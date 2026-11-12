# Aerial Top Shelf 初版测试

实验性预发布版本，尚未在真机验证。使用 release 优化构建，提供 standard 和 guard 两种 APK。

## 安装

- 包名为 `app.orchard.tv.topshelf`，名称为「Aerial Top Shelf 测试版」，可与原版共存；原版的设置和授权不继承。
- 测试包使用独立、公开的测试证书，不会覆盖原版。后续本分支 CI 测试包可互相覆盖升级。
- 能安装第三方桌面的设备选择 standard；限制 HOME 声明的固件选择 guard。
- 使用 ADB 安装时，Android 9–11 选择 api28.dm，Android 12+ 选择 api31.dm。把它重命名为与 APK 相同的文件名，再一同安装，例如：

```sh
cp aerial-topshelf-standard-api31.dm aerial-topshelf-standard.dm
adb install-multiple -r aerial-topshelf-standard.apk aerial-topshelf-standard.dm
```

Android 8 及以下仅安装 APK。初版建议先从原桌面打开测试 App，验证内容；需要正式试用 HOME 接管时再按项目安装说明设置默认桌面／守护，避免两个桌面守护同时运行。

## 功能和操作

- Android 8.0+ 且存在系统 TvProvider 时，读取授权后可见的 Watch Next 和 Preview Programs。
- 首次打开会申请电视节目读取权限。也可以使用：

```sh
adb shell pm grant app.orchard.tv.topshelf android.permission.READ_TV_LISTINGS
```

- 测试包默认打开 Top Shelf。在「个性化 → 主屏幕布局 → 顶部内容」可以关闭；拒绝授权后，关闭并重新开启可再次申请权限。
- 推荐单排布局。首排应用聚焦停留 250 毫秒后，有内容才显示上方卡片；无内容恢复原桌面。双排布局使用紧凑卡片。
- 应用上按上键进入内容；左右选片；确认打开应用提供的链接；下键或返回回到来源应用。
- 内容上按上键进入分类；分类左右切换继续观看／推荐；下键回内容，再按上键可进右上状态区。
- 应用图标的确认、长按及往下进入应用网格保持原有行为。儿童／长辈模式关闭此功能。
- 内容变化或从播放应用回到桌面后重新读取；按包名分组、去重、每类最多 20 条。

## 验收

1. 授权后在应用里播放一段内容，返回测试桌面，聚焦它的首排图标，检查标题、封面、进度是否出现。
2. 快速左右切换应用，确认内容不串应用；工具／设置等无内容应用不显示空白面板。
3. 上键进入卡片，左右选片，确认验证内容链接；分别记录直接续播、详情页、登录／选用户页的结果。
4. 下键／返回回来源图标，不能直接进入屏保；长按方向键不能一次跨越多个焦点层。
5. 打开控制中心、应用菜单、屏保以及儿童模式，再退出，检查焦点和原有行为。
6. 断网、撤销权限、卸载来源应用后不得崩溃。缺失封面显示文字占位。

## 限制与诊断

Watch Next 不是完整播放历史。应用必须主动发布对第三方可见的条目；推荐是另一套数据。Netflix、Disney+、YouTube、Emby 等均未在本版本真机验证，不能仅凭应用名承诺支持。链接可能只到详情页、登录或选用户页，能否直接续播由提供方决定。

原生 Android 8 以下、缺少 TvProvider 的固件、未授权或无可见条目时保留原桌面。Lite 不在本次范围内。不使用 Root，也不申请系统特权 EPG 权限。

诊断日志 tag `AerialDiag` 的 `top shelf:` 行包含可见应用数、条目数或访问失败类型，不记录标题、封面或播放链接。`READ_TV_LISTINGS` 需要授予读取应用，直接在 ADB shell 查询得到空结果不能证明应用没有发布数据。

## CI

推送 `feature/top-shelf` 自动执行 standard／guard 的 release 单测、lint 和 APK 构建。检查通过后创建独立的 `topshelf-test-<run number>` 预发布 Release（不标为 Latest），附带两个 APK、dex metadata、校验和、混淆映射及本文档。Pull Request 只检查和上传构建产物，不发布 Release。
