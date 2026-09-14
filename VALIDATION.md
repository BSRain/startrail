# 星迹 4.1 验证结果

日期：2026-09-14（Asia/Hong_Kong）。

- debug APK、unsigned release APK、测试 APK、JUnit 与 Lint 最终构建成功（55 秒）。
- 43 项 JVM 测试全部通过。
- Android 15 / API 35 模拟器单次全量 129 项全部通过，耗时 109.826 秒。使用 -e package com.startrail.app 限定项目测试，避免旧运行器扫描依赖库。
- 备份流读写/预览/取消/合并测试改为检查独立 JournalTransferController 的状态；原有行为断言保持。目录/收藏、天气、私钥导入、星图与地平线下显示等回归继续通过。
- 截图核对目录文案、坐标、分类及配置页面布局。天气配置全新安装不预填个人账号，测试只使用公开向量及假数据。
- Lint 从 25 条降到 2 条，0 错误。剩余 LockedOrientationActivity / DiscouragedApi 为竖屏锁定，保留待横屏布局及传感器专项验证。需要失败确认及回滚的 commit 采用带原因的窄范围 ApplySharedPref 抑制。
- 自定义 View 支持标准构造函数；Android 新旧云备份及设备迁移规则显式排除全部应用数据，天气私钥仍由 AndroidKeyStore 加密保存。
- APK 4.1 / versionCode 13，debug 签名与 4.0 一致，可覆盖升级。没有生成新的正式签名密钥。
- assembleRelease 未配置签名时生成 app-release-unsigned.apk。另用不完整环境配置验证构建会拒绝，未记录真实密码或密钥。
- Git 本地 main 分支从本次工程整理建立；v4.1 标签指向验证提交，无远程上传。源码与交付 ZIP 排除工具、私钥和构建缓存。

## 范围与限制

MainActivity 从 768 行变为 683 行，拆出文件任务、目录/收藏、帮助/许可与共享控件组件。Activity 仍保留星图、导航和部分设置/观测流程，本次不是全部重写。

主要 UI 文案资源化，未提供完整外语翻译；模型和天文数据仍以中文为主。未为消除告警直接升级 targetSdk 或开放横屏。

正式 release 产物尚未签名，不能直接安装。用户安装应选择已验证同证书的 debug 升级包。公开发布需按 RELEASE.md 配置并妥善保管正式密钥，采用新证书不能覆盖旧调试版。

天气真实账号连通性仍需用户私钥与权限，未在此次工程整理中访问用户天气账户。普通手记备份仍不包含收藏、清单、设置或私钥。

记录：work/build-v41.log、work/instrumentation-v41.txt、work/jvm-v41.txt、work/signature-v41.txt、work/signing-config-guard-v41.txt、work/refactor-v41.md。
