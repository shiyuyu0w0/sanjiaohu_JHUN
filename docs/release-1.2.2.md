# v1.2.2 发布准备

1.2.2 的签名安装包、更新说明和更新清单已在本地准备好。GitHub 和 Gitee 的安装包上传与下载校验待完成。

## 发布材料

| 项目 | 文件 |
| --- | --- |
| 正式安装包 | `Sanjiaohu-1.2.2.apk` |
| GitHub 和 Gitee 发布正文 | [v1.2.2 更新说明](../updates/release-notes-1.2.2.md) |
| 应用内更新说明 | `updates/release-notes-1.2.2.txt` |
| 安装包校验值 | `updates/Sanjiaohu-1.2.2.apk.sha256` |
| 本地 stable 更新清单 | `updates/stable/version.json`，修订号 23，`enabled: true` |
| 待发布清单 | `updates/staged/1.2.2.json`，修订号 22，`enabled: false` |

安装包的版本号为 1.2.2，versionCode 为 55，支持 Android 8.0 及以上系统。包名为 `cn.jhun.sanjiaohu`，文件大小为 2,668,117 字节，沿用原有签名，APK v2 和 v3 签名校验通过。

SHA-256 为 `bcbaf263b96790c1662be0b1c62d158b97da37a149c5c9e3dd4e590b819bb62a`。

## 已验证

- 48 组本地测试与 11 项发布脚本检查通过。
- 待发布清单和本地 stable 清单与签名安装包的版本、包名、大小和 SHA-256 一致。
- Android 15 模拟器从正式版 1.2.1 覆盖安装 1.2.2 后，自定义课程、日程、壁纸和偏好保留。换老师的教务课程仍能读取旧版保存的备注。
- Android 模拟器通过 39 项课表刷新与缓存检查，以及 2,132 项界面检查。
- 重新打包后已在 Android 模拟器中确认「赞助记得备注名字」显示在赞助码下方。

## 发布顺序

1. 提交本次源码和发布材料，将 `v1.2.2` 标签指向该提交。`updates/stable/version.json` 留到第 4 步提交。
2. 在 GitHub 和 Gitee 的 `v1.2.2` Release 上传同一个 `Sanjiaohu-1.2.2.apk`，并使用同一份更新说明。
3. 运行 `./scripts/verify-update.ps1`，确认三条下载线路提供的 APK 大小和 SHA-256 与本地包一致。
4. 提交并推送 `updates/stable/version.json` 到 `main`，向旧版客户端提供应用内更新。

重新构建或重签名后，要用新安装包生成清单和校验文件。完整流程见 [应用内更新与发布](in-app-updates.md)。
