# 三角狐应用内更新与发布

## 本次接入

客户端版本为 **1.1.2（versionCode 45）**。入口是「个人 → 关于」中版本号左侧的「检查更新」小按钮。点击直接检查，没有独立更新页面；无可用更新时只显示简短提示，有新版本时弹出下载及安装提示，跟随应用主题及深色模式。

- 手动检查立即执行；进入前台距上次成功检查超过 24 小时才自动检查。失败后的自动尝试至少间隔 15 分钟，手动检查不受此限制。
- 自动发现新版本，在个人页关于入口和关于页显示标记，不弹窗打断使用。
- 用户选择下载后，默认使用加速线路；失败自动尝试官方线路一次。断网等待、主动取消、两条线路均失败，都不会反复切换。
- 下载交给 Android DownloadManager，应用关闭后可以继续下载。回到关于页时恢复任务，点击检查更新可查看进度或安装；自定义线路切换不会在应用进程结束后自行运行。
- 校验完成后显示「立即安装」。用户点击、授予安装权限并在 Android 系统界面确认后才能覆盖安装。

**现有 1.1.1 没有此更新功能，需要先手动安装一次 1.1.2；以后从 1.1.2 升到更高版本才能在应用里完成。** 同一版本不会提示更新。

本地已生成 `updates/stable/version.json`，初始 `enabled` 为 `false`。脚本和本地构建均不会创建 GitHub Release，也不会提交或推送仓库。

## 更新来源

版本清单原文件在本仓库的 `updates/stable/version.json`，客户端固定使用：

1. `https://cdn.jsdelivr.net/gh/shiyuyu0w0/sanjiaohu_JHUN@main/updates/stable/version.json`
2. `https://raw.githubusercontent.com/shiyuyu0w0/sanjiaohu_JHUN/main/updates/stable/version.json`
3. `https://ghproxy.net/https://raw.githubusercontent.com/shiyuyu0w0/sanjiaohu_JHUN/main/updates/stable/version.json`

前两个并发请求；遇到失败，或半程仍未收齐结果，再尝试第三个。整轮检查最多等待 12 秒，比较所有已返回有效结果的 `manifestRevision`。相同修订号内容冲突会停用下载；仅返回比本地已知修订更旧的数据会提示来源未同步。全部失败显示检查失败，不声称已是最新版。

APK 只接受本仓库 `releases/download/v版本号/Sanjiaohu-版本号.apk` 和套在该地址外的 `https://ghproxy.net/` 代理。公共代理可用性不作保证，官方直链始终保留。检查请求不附带学校登录 Cookie 或 GitHub Token。

## 第一次发布 1.1.2

在项目根目录打开 PowerShell。先确认当前根目录 APK 是要发布的最终文件；清单生成之后不要再次构建或重新签名这一份 APK，否则哈希会变化。

### 1. 本地构建和验证

```powershell
./build-local.ps1 -Work "$PWD/build/app-update"
```

如果重新构建了本次安装包，重新生成清单并提高修订号：

```powershell
./scripts/prepare-update.ps1 `
  -Apk ./Sanjiaohu-1.1.2.apk `
  -ManifestRevision 2 `
  -NotesFile ./updates/release-notes-1.1.2.txt

./scripts/verify-update.ps1 -Offline
```

不传 `-Enable` 时清单保持关闭。仓库里已有修订号时，新值必须比它大。`-Offline` 验证本地签名、包名、版本、最低 Android 版本、大小和 SHA-256，不检查网络线路。

下文修订号仅作示例，请先查看本地及线上清单，使用比两者都大的值。本次调整按钮后，本地清单修订号已递增。

### 2. 上传 GitHub Release

在 `shiyuyu0w0/sanjiaohu_JHUN` 创建 Release：

- 标签：`v1.1.2`
- 附件：项目根目录的 `Sanjiaohu-1.1.2.apk`
- 更新说明可使用 `updates/release-notes-1.1.2.txt`。

发布后，官方 APK 地址应为：

```text
https://github.com/shiyuyu0w0/sanjiaohu_JHUN/releases/download/v1.1.2/Sanjiaohu-1.1.2.apk
```

只能上传最终签名 APK，签名密钥和密码不要上传。

### 3. 验证两条线路并启用

```powershell
./scripts/prepare-update.ps1 `
  -Apk ./Sanjiaohu-1.1.2.apk `
  -ManifestRevision 3 `
  -NotesFile ./updates/release-notes-1.1.2.txt `
  -Enable
```

此命令先从官方和代理各下载一遍，确认它们与本地签名 APK 的大小、SHA-256 一致，才写入 `enabled: true`。任何线路返回错误页、未上传、超时或文件不一致，都不会覆盖已有清单。

命令成功后，把代码及 `updates/stable/version.json` 正常提交并推送到 `main`。清单只存在本地时，手机无法读取它。首次发布的 1.1.2 对已安装 1.1.2 的手机不应显示可升级，这是正常行为。

## 以后每次发布

以 1.1.3 为例：

1. 同时更新 `app/build.gradle` 和 `app/src/main/AndroidManifest.xml`：`versionName` 改成 1.1.3，`versionCode` 改成 46。实际值要高于所有已发布版本，不能只改显示名称。
2. 更新关于页说明、相应测试的版本预期，以及两个构建/重签名脚本的默认输出文件名。
3. 新建更新说明文本，每行一条，不需要自己写圆点或 JSON。
4. 使用原签名密钥构建最终 APK；上传 `v1.1.3` Release，附件名必须为 `Sanjiaohu-1.1.3.apk`。
5. 从当前线上修订号继续递增，运行：

```powershell
./scripts/prepare-update.ps1 `
  -Apk ./Sanjiaohu-1.1.3.apk `
  -ManifestRevision 4 `
  -NotesFile ./updates/release-notes-1.1.3.txt `
  -Enable

./scripts/verify-update.ps1 -Apk ./Sanjiaohu-1.1.3.apk
```

6. 提交并推送清单到 `main`。在已安装 1.1.2 的手机上检查更新、下载、确认安装，核对升级后版本及课表等本地数据。

不要用旧的修订号修改清单内容；不要替换已发布版本的 APK 字节。需要修复时发布更高 `versionCode` 的版本。

## 撤回有问题的更新

保留同一版本和最终 APK，用更高修订号重新生成关闭清单，然后推送：

```powershell
./scripts/prepare-update.ps1 `
  -Apk ./Sanjiaohu-1.1.3.apk `
  -ManifestRevision 5 `
  -NotesFile ./updates/release-notes-1.1.3.txt
```

省略 `-Enable` 即关闭。客户端下一次取得新清单后停止推荐并取消不再适用的下载。缓存与离线设备可能暂时看不到撤回，因此不能保证即时撤回，也不能降级已经安装新版的手机。

## 校验与安装边界

下载文件先复制到应用内部私有目录，再对该快照核对大小、SHA-256、包名、版本名、版本号、最低系统要求和签名。安装前再次校验同一份文件，然后写入 PackageInstaller 会话。Android 最终验证 APK 签名并要求用户确认；没有静默安装。

当前仅接受现有发布证书，不支持签名密钥轮换。客户端及发布脚本固定的 SHA-256 证书指纹为：

```text
2d5c4c7ab3f5e829dfb2273f333a2af987e512dc270fbe03347638329cdef22a
```

更新数据独立保存在 `app_updates` 偏好设置及更新目录，不与教务账号、课表缓存混用。APK 大小上限 256 MiB，清单上限 64 KiB。

## 本地验证与待实机验证

`tests/UpdateTest.java` 覆盖版本及系统条件、地址限制、无效清单、修订冲突、缓存回退、并发检查、总时限和线路切换策略。使用真实 `org.json` Java 库编译运行，不要使用项目里只供其他测试用的 JSON 简化桩。

发布脚本通过最终 APK 的 `aapt2 dump badging`、`apksigner verify` 和 SHA-256 检查本地清单。`verify-update.ps1` 默认还会检查两条线上下载线路；只有 `-Offline` 会跳过网络验证。

本次环境没有连接 Android 测试设备，以下还需要实机验收：下载中断网/切换线路/取消、关闭进程后恢复、拒绝及重新授予安装权限、系统确认取消和重新安装、真实旧版本覆盖升级。不能用本地策略测试替代这些设备测试。
