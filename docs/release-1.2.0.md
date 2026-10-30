# v1.2.0 发布准备

准备日期：2026-10-30。正式签名安装包和更新清单已在本地准备好，尚未上传 GitHub / Gitee 或推送清单。

- 安装包：根目录 `Sanjiaohu-1.2.0.apk`。
- 版本：1.2.0；versionCode：53；最低系统：Android 8.0（API 26）。
- 包名：`cn.jhun.sanjiaohu`；沿用原有签名，APK v2 / v3 校验通过。
- 文件大小：2,647,555 字节。
- SHA-256：`16484fda480580443267fa8c0f80b1fef73e44e77b1ebdaf6a5d960e92ecca9c`。
- 更新清单：`updates/stable/version.json`，修订号 17，`enabled: true`。
- 更新内容：见 `updates/stable/version.json` 的 `releaseNotes`。

## 已验证

- 46 组本地测试通过，包括关于页版本历史及更新策略。
- 11 项发布脚本检查通过。
- 清单中的版本、包名、签名、大小和 SHA-256 与最终 APK 一致。
- 同一功能版本已在 Android 15 模拟器验证 versionCode 52 → 53 的覆盖升级；自定义课程、课程备注、壁纸和测试偏好值保留，测试数据已恢复。
- 最终 APK 通过 2,132 项 Android 界面与课表缓存检查；教务账号与自动登录合并、完整课表自动替换、离线接续旧候选课表、解析异常保留当前课表及学期隔离均已验证。
- 更正版本名称后重新构建；关于页 253 项检查及最终 APK 签名、清单校验通过。
- 新增今日课表桌面小组件，支持图片裁切、课程状态和深浅色适配。
- 原有配色保留；调色预设已移除，仍可使用色盘、明暗调节、颜色代码和跟随背景主色。

## 发布顺序

1. 在 GitHub 和 Gitee 的 `v1.2.0` Release 上传同一份 `Sanjiaohu-1.2.0.apk`，发布正文使用更新清单中的主要更新内容。
2. 执行 `./scripts/verify-update.ps1`，验证 Gitee、GitHub 加速、GitHub 官方下载线路均返回正确安装包。
3. 再推送 `updates/stable/version.json`。本地清单已启用，不应在安装包上传前推送。

远程下载线路尚未验证。若重新构建或重签名，必须重新计算大小和 SHA-256，并增加清单修订号。
