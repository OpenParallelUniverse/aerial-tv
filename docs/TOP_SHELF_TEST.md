# Top Shelf P1/P2 隔离测试版

本版本为预发布优化 release，尚待真机验收。包名 `app.orchard.tv.topshelf`，Activity 是 `app.orchard.tv.MainActivity`。

## 安装与正式版隔离

- 本轮 standard／guard 测试包都不声明 HOME，不注册为默认桌面候选。开机、守护、复活、配置接管和系统屏保组件均禁用。
- 不授予 WRITE_SECURE_SETTINGS、悬浮窗、系统设置写入或开机权限；不使用正式安装脚本，不切换默认桌面，不开启测试版无障碍服务。
- 使用公开测试证书，仅能更新之前独立包名的测试版，不能覆盖正式版。测试偏好、缓存、隐藏记录与正式版隔离；手机页端口为8791。
- 推荐下载 APK 和 `install-topshelf-test.sh` 到同一目录，设置好设备连接后运行：

```sh
bash install-topshelf-test.sh aerial-topshelf-standard.apk
# 多设备时：TV=设备序列号 bash install-topshelf-test.sh aerial-topshelf-standard.apk
```

脚本要求本机 adb、apkanalyzer 和 unzip；先核对测试包身份，再安装 APK 自带的匹配 DM，只申请测试包电视节目读取权限，比较安装前后 HOME 和无障碍列表，最后手动打开测试版。Android 9–11 使用 api28.dm，12+ 使用 api31.dm，旧版仅装 APK。

也可手工安装 APK（Android 9+ 建议连同同名 DM 使用 install-multiple），然后：

```sh
adb shell pm grant app.orchard.tv.topshelf android.permission.READ_TV_LISTINGS
adb shell am start -n app.orchard.tv.topshelf/app.orchard.tv.MainActivity
```

按 HOME 应返回现有正常桌面。如果原桌面守护阻止测试版出现，记录阻塞；不得关闭、修改正式版守护。不要把此包设为默认桌面，不要运行 tools/install.sh。

## 本轮内容

真实可见的继续观看／下一集／新一集／待看和推荐混合轨道；季集、剩余时间和频道名称；本地隐藏与按应用关闭；个性化中恢复全部隐藏条目和关闭的应用。

封面按目标尺寸后台处理连续圆角与暗角，内存12MiB、磁盘48MiB、3天TTL、下载并发2。图片失败仍保留文字。支持应用切换溶解、时钟淡变、焦点外框、弹簧滚动和边界回弹。

## 遥控器

首排有内容的应用停留180ms显示展架，上键进入；左右选片、确认打开；卡片上键直达状态栏，状态栏下键返回刚才卡片；下键／返回回到来源应用。菜单键或长按确认打开操作菜单。原磁贴确认仍打开应用首页。建议单排布局，双排自动压缩卡片。

## 必须记录的真机结果

1. 安装前后的 HOME 解析结果、enabled_accessibility_services、screensaver_components、dreams_enabled、正式包版本及开机配置，全部保持一致。
2. 原应用确实发布可见条目后，逐个记录封面、标题、类型、进度与深链落点。不能只凭应用名称认定支持。
3. 遥控器全部路径、长按抬键不误触、快速换应用、记录更新／删除、本地隐藏重启保持与恢复。
4. 断网、撤权、低内存、静态／动态壁纸、不同布局和4K UI；帧时和掉帧与同机基准比较。
5. 按 HOME 后的焦点应属于原正常桌面。无法连接或原守护阻挡时，明确记录“待验收”，不能宣称通过。

Watch Next不是完整观看历史，深链也不保证直接续播。多账号快照、自动选人和PIN未实现，详见SPEC.md的独立实验提案。诊断tag为AerialDiag，top shelf行只记录条目数量与错误类型。

## CI

feature/top-shelf 每次推送自动运行两种release单测、lint、优化构建、测试包身份与无接管Manifest校验，成功后发布新的独立prerelease，不成为Latest。原有lint问题由精确基线记录，新增错误和NewApi仍阻止发布。
