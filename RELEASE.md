# StarTrail 4.2 发布构建与安装

4.2 的 Android 版本号是 `versionCode 14`、`versionName 4.2`。正式发布包必须继续使用目标安装渠道中上一版本的同一应用签名密钥。Android 以应用 ID 和签名证书共同判断升级资格；现在新建的证书不能覆盖已安装的 4.0，也不能恢复丢失的旧私钥。

项目保留 `work/signing/debug.keystore` 只为本项目既有调试版原地升级。正式版不会回退使用该调试证书。未配置正式签名时，`assembleRelease` 仍可生成明确标记为未签名的 APK，不能直接安装或发布。

## Windows 正式签名构建

准备已有的发布 keystore、别名及密码。不要把 keystore、密码或真实 `signing.properties` 提交到仓库，也不要把密码写进命令历史、构建日志或截图。

方式一适合本机发布：复制 `signing.properties.example` 为仓库根目录下的 `signing.properties`，填写四项。`storeFile` 可使用绝对路径；Windows 路径推荐写成 `C:/secure/path/release.jks`。该文件已被 `.gitignore` 忽略。

方式二适合 CI 或临时 PowerShell 会话：由安全的凭据存储注入以下环境变量。不要将真实值保存到脚本仓库。

```powershell
$env:STARTRAIL_RELEASE_STORE_FILE = 'C:/secure/path/startrail-release.jks'
$env:STARTRAIL_RELEASE_STORE_PASSWORD = '<从安全存储注入>'
$env:STARTRAIL_RELEASE_KEY_ALIAS = '<已有别名>'
$env:STARTRAIL_RELEASE_KEY_PASSWORD = '<从安全存储注入>'
.\gradlew.bat clean :app:assembleRelease
```

环境变量优先于 `signing.properties`。两种方式都必须提供 `storeFile`、`storePassword`、`keyAlias`、`keyPassword` 四项；只提供一部分会立即失败。构建脚本不会输出这些值。

签名包位于 `app\build\outputs\apk\release\app-release.apk`。用 Android SDK Build Tools 验证签名和证书，再安装到专用测试设备：

```powershell
$apksigner = "$env:ANDROID_SDK_ROOT\build-tools\35.0.0\apksigner.bat"
& $apksigner verify --verbose --print-certs .\app\build\outputs\apk\release\app-release.apk
adb install -r .\app\build\outputs\apk\release\app-release.apk
```

如果本机 Build Tools 版本不同，请把 `35.0.0` 改为已安装版本。升级测试前记录 4.0 APK 的证书 SHA-256，再确认 4.2 输出的证书摘要一致。`adb install -r` 出现签名不兼容时应停止；卸载旧应用会清除其应用数据，并不构成升级测试。

只需检查未签名产物时，确保四个环境变量均未设置且没有 `signing.properties`，再运行 `.\gradlew.bat :app:assembleRelease`。Gradle 会明确提示输出是 `UNSIGNED APK`。该产物需要使用预期的既有发布密钥签名后才能分发。

## 发布前检查

- 从干净源码构建，确认版本为 4.2（14），并保存最终 APK 的 SHA-256、签名证书 SHA-256 和构建记录。
- 在保留数据的 4.0 测试设备上执行覆盖安装，确认应用数据仍在；再做一次全新安装测试。
- 检查收藏、观测清单、设置、定位和天气配置的升级行为，并确认天气账户信息符合公共发布要求。
- 执行项目要求的单元测试、Lint 和仪器测试；仪器测试会增删并清空测试手记，只能使用专用测试设备。
- 安装最终待发布的同一 APK 做冒烟测试，不要用另一次构建代替。

## 私钥和应用数据备份边界

发布私钥是未来更新的身份凭据。至少保留两份加密、离线、分开存放的 keystore 备份，并把别名、密码恢复方式、证书摘要和负责人交接信息放在受控的密码/密钥管理系统中。定期在隔离环境验证备份可读，但不要生成新密钥来“测试替换”。丢失自管的旧发布私钥后，无法用新证书覆盖相应渠道中已安装的 4.0；若使用 Google Play App Signing，只能按 Play Console 支持的密钥流程处理。

应用内“手记导出”备份目前只覆盖手记，不包含收藏、观测清单、设置、天气配置或天气私钥。卸载、清除应用数据或换机会丢失这些未导出的内容。天气私钥受 AndroidKeyStore 保护，也不会随普通应用备份迁移；换机后需要重新安全导入。发布前升级测试应使用可牺牲的测试数据，不能把手记导出当作全量恢复方案。

Android 官方资料：[为应用签名](https://developer.android.com/studio/publish/app-signing)；[从命令行构建和验证签名](https://developer.android.com/build/building-cmdline)。
