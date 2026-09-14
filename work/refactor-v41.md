# 4.1 工程整理

MainActivity 拆出 JournalTransferController（文件选择结果、后台流读写、恢复预览及销毁取消）、CatalogDialogs（深空分类与收藏弹窗）、AppInfoDialog（帮助及许可）与 DialogUi（共享程序化控件）。Activity 保留生命周期、星图、导航及尚未拆分的设置/观测流程；没有声称所有页面已拆完。

MainActivity、帮助、文档控制器的 262 条中文 UI 文本移入 main_strings.xml，名称按所属方法组织；共用按钮另取公共名字。WeatherPlanDialog 使用独立 weather_strings.xml。关键 setText 拼接改成有参数的格式字符串。仍有天文数据、部分模型提示和字符串片段拼接，未宣称完整多语言支持。

ApplySharedPref 的同步 commit 用于发现失败与回滚，采用带原因的窄范围抑制。补自定义 View 构造函数、Android 新旧自动备份/迁移排除规则。保留固定竖屏的两条警告：改变方向会影响传感器和布局，应以专门横屏测试后再开放，不为归零警告冒险修改。

正式签名来自外部配置；没有正式密钥时构建 unsigned release，绝不退回 debug 签名。现有签名的 debug APK 用于继续覆盖升级。新安装不再预填个人和风账号，已有 SharedPreferences 配置保留。
