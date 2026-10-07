# 本地版本管理

本项目从 4.1 工程整理版本起建立本地 Git 仓库，默认分支 main。首次提交只包含源码、资源、测试、构建配置与说明；旧版历史仍保留在 outputs 的交付 ZIP 中，没有伪造过去的提交历史。

常用命令：

```powershell
git status
git diff
git log --oneline --decorate
git switch -c codex/next-change
```

完成并验证改动后，按文件添加并提交：

```powershell
git add app/src README.md CHANGELOG.md
git commit -m "Describe the validated change"
```

发布标签 v4.1 保留工程整理版本，v4.2 指向观星体验收尾的验证提交。远程 origin 为私有仓库 https://github.com/BSRain/startrail 。4.3 使用 codex/celestial-calendar-43 分支开发，验证后归档 v4.3。需要异机备份时，可复制生成的 Git bundle，或配置自己的远程仓库。

```powershell
git clone outputs/startrail-4.3-history.bundle startrail-copy
```

被忽略：Gradle/Android 工具、构建产物、outputs、日志、个人 local.properties、signing.properties、keystore/jks/pem。不要使用 git add -f 绕过密钥排除。原始天气私钥和正式签名密钥均需单独妥善备份。
