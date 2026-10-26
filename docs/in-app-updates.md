# 三角狐应用内更新与发布

## 当前发布准备

待发布版本为 **1.1.9（versionCode 52）**，最低 Android 8.0（API 26）。本次发布材料、校验结果与操作清单见 [v1.1.9 发布准备](release-1.1.9.md)。

本次准备保留 `updates/stable/version.json` 中已上线的 1.1.8（修订号 12），将 1.1.9 的关闭清单放在 `updates/staged/1.1.9.json`（修订号 13）。客户端只读取 stable 路径，不会发现尚未上传的安装包。脚本不会创建远程 Release、提交代码或推送仓库。

## 更新功能

入口在「个人 → 关于 → 检查更新」。手动检查立即执行；自动检查按成功后 24 小时、失败后至少 15 分钟的间隔进行。自动发现新版只标记入口，不弹窗打断使用。

下载默认优先 Gitee，再尝试 GitHub 加速和 GitHub 官方线路。下载完成后核对安装包，由用户在 Android 系统界面确认覆盖安装。应用内更新从 1.1.2 开始支持；更早版本需要手动下载覆盖安装。同一版本不会重复提示更新。

## 发布前：构建并生成待发布清单

同时更新以下文件的版本信息：

- `app/build.gradle` 与 `app/src/main/AndroidManifest.xml`：版本名及递增的 versionCode。
- `AboutActivity.java` 与 `tests/AboutChangelogTest.java`：应用内更新日志及对应检查。
- 构建、重签名、更新准备、更新校验及发布测试脚本：默认安装包文件名。
- `updates/release-notes-版本号.txt`：每行一条，供更新弹窗使用。
- `updates/release-notes-版本号.md`：供 GitHub、Gitee Release 使用。

在项目根目录打开 PowerShell：

```powershell
./build-local.ps1 -Work "$PWD/build/release-1.1.9"
./scripts/test-local.ps1 -Classes "$PWD/build/release-1.1.9/classes"
./tests/UpdateReleaseTest.ps1 -Apk ./Sanjiaohu-1.1.9.apk
```

构建使用 `signing/development.keystore` 中的原有签名；不能换成新密钥。最终安装包为根目录 `Sanjiaohu-1.1.9.apk`。

首次准备 1.1.9 的清单命令如下（本次已经执行，不要直接用同一修订号覆盖）：

```powershell
./scripts/prepare-update.ps1 `
  -Apk ./Sanjiaohu-1.1.9.apk `
  -ManifestRevision 13 `
  -NotesFile ./updates/release-notes-1.1.9.txt `
  -Output ./updates/staged/1.1.9.json

./scripts/verify-update.ps1 `
  -Apk ./Sanjiaohu-1.1.9.apk `
  -Manifest ./updates/staged/1.1.9.json `
  -Offline
```

不传 `-Enable` 时清单关闭。每次清单变化必须提高修订号，取比本地 stable、staged 和线上清单都大的值。重新构建或签名后必须重新生成清单和校验文件；发布时上传的必须是校验过的同一份 APK。

## 发布：上传安装包后启用更新

1. 提交所有本次功能及发布准备改动，确保 `v1.1.9` 标签指向最终源码提交。GitHub、Gitee 使用相同版本标签与源码。
2. 分别创建 GitHub 和 Gitee 的 `v1.1.9` Release，标题为「三角狐 v1.1.9」。正文使用 `updates/release-notes-1.1.9.md`，两处上传同一个 `Sanjiaohu-1.1.9.apk`。可同时附上校验文件，勿上传密钥或整个构建目录。
3. 两处附件可访问后再生成开启清单。若本地及线上修订号仍不超过 13，可用 14：

```powershell
./scripts/prepare-update.ps1 `
  -Apk ./Sanjiaohu-1.1.9.apk `
  -ManifestRevision 14 `
  -NotesFile ./updates/release-notes-1.1.9.txt `
  -Output ./updates/stable/version.json `
  -Enable

./scripts/verify-update.ps1 -Apk ./Sanjiaohu-1.1.9.apk -Offline
```

`-Enable` 会下载 Gitee、GitHub 加速和 GitHub 官方三条线路的文件，全部通过大小与 SHA-256 检查才写入开启清单。任何一条失败都保留原清单，应修复上传或线路问题后重试，不要手工绕过检查。

4. 提交并推送开启后的 `updates/stable/version.json` 到 `main`。只在本地保存或只上传 APK，都不能让旧版客户端收到更新。
5. 用旧版手机检查更新、下载、确认安装，再核对版本与本地数据。

## 更新来源与校验

客户端固定读取以下清单来源，比较所有已返回有效结果的修订号：

- `https://cdn.jsdelivr.net/gh/shiyuyu0w0/sanjiaohu_JHUN@main/updates/stable/version.json`
- `https://raw.githubusercontent.com/shiyuyu0w0/sanjiaohu_JHUN/main/updates/stable/version.json`
- `https://ghproxy.net/https://raw.githubusercontent.com/shiyuyu0w0/sanjiaohu_JHUN/main/updates/stable/version.json`

整轮检查最多等待 12 秒。同修订号内容冲突时停用下载；仅返回更旧数据时提示来源未同步；全部失败时显示检查失败。更新请求不附带学校登录凭证。

APK 校验包含包名、版本名、versionCode、最低系统版本、文件大小、SHA-256 及签名。安装前再次校验，再交给 Android 系统安装。APK 上限 256 MiB，清单上限 64 KiB。当前固定接受的证书 SHA-256 为：

```text
2d5c4c7ab3f5e829dfb2273f333a2af987e512dc270fbe03347638329cdef22a
```

## 撤回更新

针对已经启用的相同版本、相同 APK，用更高修订号运行准备脚本，输出到 stable，省略 `-Enable`，校验后推送。不要复用旧修订号，也不要替换已经发布的 APK 字节。修复安装包应发布更高 versionCode 的新版。

撤回不会卸载或降级已经安装的应用，离线设备及缓存也可能延迟看到清单变化。

## 验证范围

本地策略与发布脚本测试之外，还应检查真实设备的更新下载、断网恢复、线路切换、取消下载、安装权限与系统确认流程。模拟器覆盖升级可验证签名兼容和本地数据保留，但不代替真实手机的触感体验、学校账号登录及线上下载验收。本次已经完成和仍待发布后执行的项目分别记录在版本发布准备文档中。
