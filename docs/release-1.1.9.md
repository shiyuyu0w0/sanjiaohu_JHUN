# v1.1.9 发布准备

准备日期：2026-10-26。状态：本地材料和验证已完成，尚未创建远程 Release、标签或启用新版更新清单。

## 发布材料

| 用途 | 文件 |
| --- | --- |
| 正式签名安装包 | 根目录 `Sanjiaohu-1.1.9.apk` |
| GitHub / Gitee 发布正文 | `updates/release-notes-1.1.9.md` |
| 应用内更新说明 | `updates/release-notes-1.1.9.txt` |
| SHA-256 校验文件 | `updates/Sanjiaohu-1.1.9.apk.sha256` |
| 待发布更新清单 | `updates/staged/1.1.9.json` |
| 完整发布流程 | [应用内更新与发布](in-app-updates.md) |

- 版本：1.1.9；versionCode：52；最低系统：Android 8.0（API 26）。
- 包名：`cn.jhun.sanjiaohu`。
- 文件大小：2,626,117 字节。
- 原有正式签名，APK v2 / v3 验证通过。
- SHA-256：`b8e100210c17f9012379f6f818e8338b02381749c8a1e68b303405add1d1d466`。
- 构建目录：`build/release-1.1.9`。此目录和测试 APK 不作为发布附件。

以上哈希对应已经验证的最终 APK。若重新构建或重签名，必须重新生成清单、校验文件并更新本页数据。

## 已完成

- [x] Gradle 与 AndroidManifest.xml 同步到 1.1.9 / 52。
- [x] 默认启动页改为课表，保留本次切页动效、震动和壁纸修复。
- [x] 关于页新增 v1.1.9 更新日志，保留历史记录。
- [x] 构建、重签名、清单准备、清单验证及发布测试脚本的默认版本同步。
- [x] 更新说明已对照实际线上 v1.1.8 Release；未把已经发布的图书查询、课程备注再次列为新增功能。
- [x] 45 组本地测试通过，其中关于页 226 项检查通过。
- [x] 11 项发布脚本检查通过。
- [x] 正式包与待发布清单的包名、版本、签名、大小及 SHA-256 一致。
- [x] Android 15 模拟器：从正式 v1.1.8（51）直接覆盖安装 v1.1.9（52）成功。
- [x] 覆盖升级后，自定义课程、备注和壁纸文件逐字节一致，测试偏好值保留，课程及备注仍可读取，壁纸可解码；测试数据已恢复。
- [x] 最终 v1.1.9 APK 的 2,219 项 Android 界面检查通过，包括默认课表、底栏所有切换方向、震动请求、翻周和壁纸覆盖。
- [x] 关于页截图确认实际显示 v1.1.9，截图保存在 `build/release-1.1.9/about.png`。

覆盖升级所用旧 APK 与线上 v1.1.8 附件的大小及 SHA-256 一致：`ad00ef385d1840c29a2168118a31afbd965c4da3bfd33eb95d44683e417ba654`。

## 发布时按顺序执行

1. 提交工作区中本次功能与发布准备改动，再将 `v1.1.9` 标签指向最终提交；GitHub、Gitee 对应相同源码。
2. 在两个平台创建 `v1.1.9` Release，标题使用「三角狐 v1.1.9」，正文复制发布说明 Markdown。
3. 两个平台均上传同一份根目录 `Sanjiaohu-1.1.9.apk`；可同时附上 SHA-256 文件。不要重新打包或改名。
4. 查看线上、本地 stable 和 staged 清单的最新修订号。准备时分别为 12、12、13；若未变化，使用 14 启用新版：

```powershell
./scripts/prepare-update.ps1 `
  -Apk ./Sanjiaohu-1.1.9.apk `
  -ManifestRevision 14 `
  -NotesFile ./updates/release-notes-1.1.9.txt `
  -Output ./updates/stable/version.json `
  -Enable

./scripts/verify-update.ps1 -Apk ./Sanjiaohu-1.1.9.apk -Offline
```

5. 等脚本验证 Gitee、GitHub 加速、GitHub 官方三条下载线路成功后，提交并推送生成的 stable 清单到 `main`。
6. 用安装旧版的手机执行检查更新、下载、确认覆盖安装，核对版本和个人数据。

`updates/staged/1.1.9.json` 的 `enabled` 为 `false`；正式 stable 清单仍保留线上 1.1.8，避免提前指向未上传的附件。不能直接把待发布清单当成开启清单推送。

## 尚待发布后或真机验收

- [ ] GitHub 与 Gitee 的最终附件、三条下载线路校验、stable 清单推送和缓存同步。
- [ ] 真机触感强度与流畅度。
- [ ] 应用内更新的断网、恢复、取消、安装权限和系统确认流程。
- [ ] 真实学校账号登录、校内图书查询；本次未使用真实账号或发起缴费、上传。

这些项目不以模拟器和本地策略测试代替。当前工作区保留待提交改动，没有代为发布线上版本。
