# 星迹 4.2 验证结果

日期：2026-09-19（Asia/Hong_Kong）。

- debug APK、unsigned release APK、测试 APK、JVM 测试与 Lint 最终构建成功（56 秒）。
- 43 项 JVM 测试全部通过。
- Android 15 / API 35 模拟器最终全量 140 项全部通过，耗时 104.832 秒。使用 `-e package com.startrail.app` 限定项目测试，避免旧运行器扫描依赖库。
- 约 320×569dp 小屏（720×1280 px、360 dpi）额外 4 项弹窗、设置事务与视觉捕获测试全部通过，耗时 11.323 秒。
- 新增标签缩放密度、深空名称、HUD 避让、选中标签、红光绘制、装饰浓度、设置预览/取消/应用和弹窗回调测试。原地平线下显示、时间轴、传感器、天气模拟数据、收藏、清单及备份回归通过。
- 固定香港冬季夜空截图核对猎户座、月球和设置。日月图标按可读性放大，不表示真实角直径或月面纹理。
- Lint：0 错误、2 条原有竖屏相关警告（LockedOrientationActivity / DiscouragedApi）。弹窗 framework 标题 ID 查询只在打开时执行，因无公开常量采用有原因的窄范围抑制。
- APK 为 4.2 / versionCode 14。已执行 adb install -r 覆盖安装，debug 签名沿用旧版证书，SHA-256 为 `9c10bef51743e82ffea0c70d2e8cfcaebbeae7fd5ade4784f843d108830884a6`。
- 未生成或更换正式签名密钥；未配置正式签名的 release 仍明确生成 unsigned APK。
- 本地 Git 保留 v4.1 历史，4.2 以 v4.2 标签归档；没有配置远程或上传源码。

## 首轮问题与复验

首轮 140 跑 / 0 断言失败 / 1 清理错误。新增设置测试在父类 tearDown 清空字段后读取 prefs，发生空指针；已改为提前保存局部引用及恢复数值。最终全量和小屏附加测试均通过，首轮日志保留在 work/instrumentation-v42-attempt1.txt。

## 范围与限制

本轮完成既定观星体验与工程收尾。仍保留竖屏，未为消除警告直接升级 SDK 或开放横屏。极端重合的选中与寻星两个活动目标不保证名称完全无重叠，普通候选标签执行矩形避让。

本轮未进行真实和风账号连接、户外指南针或 GPS 测量；用户此前反馈真机使用正常。天气测试使用模拟响应及公开测试向量，不包含用户私钥。手记备份仍不包含收藏、清单、设置或私钥。

用户安装选择已验证同证书的 debug 升级包；unsigned release 不能直接安装。正式发布按 RELEASE.md 配置自有密钥，新证书不能覆盖旧调试版。

记录：work/build-v42.log、work/instrumentation-v42.txt、work/instrumentation-v42-attempt1.txt、work/small-screen-v42.txt、work/jvm-v42.txt、work/signature-v42.txt、work/experience-v42.md。
