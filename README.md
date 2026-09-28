# 边狱巴士 Shizuku 汉化助手（实验版）

这是一个**通过 Shizuku 调用远程服务**替换《边狱巴士》日语本地化资源的两步操作助手。应用可联网核对 [pzwboy 的最新正式版](https://github.com/pzwboy/LocalizeLimbusCompanyForAndroid/releases/latest)，下载与**已安装游戏版本一致**的 `jp.zip`，同时内置适配游戏 **1.115.0** 的离线补丁。只按版本匹配，不会把新版本补丁强行覆盖旧版游戏。

助手本身不直接执行 `su`。当前仓库 `main` 分支是 **v1.0.4** 源码；v1.0.2 源码在 tag `v1.0.2`。两版包名都是 `com.example.limbusshizuku`，签名相同。v1.0.4 的 versionCode 更高，可以覆盖安装 v1.0.2；要从 v1.0.4 退回 v1.0.2，需要先卸载。请按下面的条件选 APK，不要默认下最新版。

关于如何安装使用shizuku以及因为边狱巴士会读取你的应用列表当发现shizuku等应用时会无法打开游戏的问题，请自行网络搜索相关解决方法。

## 选哪个版本

| 版本 | 适合 | 实测 |
| --- | --- | --- |
| [v1.0.2](https://github.com/DannyLiTHEBEST/limbuscompanylocalizer4android-shizuku/releases/tag/v1.0.2) `limbus-shizuku-v1.0.2.apk` | 安装了 **Shizuku 应用**，并用 **root** 启动它。这一版只走远程 shell，没有 UserService。 | 已在 root 下测过：在线检查并下载 v1.115.0、校验、① 预覆盖、关键文件校验，以及游戏未运行时拦截 ②。**仅 ADB（无线调试或电脑调试，UID 2000、没有 root）能否写入游戏目录，没有条件测试，不能当成可用。** ② 在游戏退到后台时的完整流程，这一版也还没测完。 |
| [v1.0.4](https://github.com/DannyLiTHEBEST/limbuscompanylocalizer4android-shizuku/releases/tag/v1.0.4) `limbus-shizuku-v1.0.4.apk` | **Sui** 用户。Sui 以 root 提供 Shizuku 接口时，远程 shell 的挂载视图经常看不到 `Android/data`；这一版在 UID 0 时改走 UserService。 | **必须 root 才能正常工作。** 已在 Sui/Shizuku root 下测过 UserService：服务进程为 `uid=0`，能定位游戏日语目录并完成关键文件校验。没有 root 时这一版不能正常覆盖。 |

> 两版都不要指望「只开 ADB、不 root」就能用。v1.0.2 的纯 ADB 路径没测过；v1.0.4 已经测过，没有 root 不能正常工作。应用会检查实际写权限，写不了就报错，不会直接调用 `su`，也不会假装覆盖成功。

## 准备

1. 安装 Google Play 版《边狱巴士》，游戏语言设为**日本語**并下载日语资源。内置离线补丁仅适配 **1.115.0**；其他版本必须先联网取得对应正式资源。
2. 准备 **root**。Shizuku 应用用户安装 [Shizuku](https://shizuku.rikka.app/) 并用 root 启动，装 **v1.0.2**。Sui 用户用 Sui 提供 Shizuku 接口，装 **v1.0.4**。
3. 安装上表对应的 APK，点「检查 / 申请 Shizuku 授权」，同意授权。如提示 `Android/data` 不可写，当前身份写不了游戏目录。

## 操作流程

1. **彻底退出游戏**，包括从最近任务划掉游戏。建议先点击「联网检查 / 更新汉化包」：程序从 GitHub 正式发布页核对最新版，仅下载与当前游戏版本匹配的 `jp.zip`，检查 HTTPS 下载来源、压缩包结构、关键 JSON 和大小后存到应用私有缓存。点击 **① 预覆盖** 时也会联网尝试刷新匹配补丁；网络失败则仅使用已验证的同版本缓存或 1.115.0 内置备用包。上游最新版本与游戏版本不同，也会尝试查找当前游戏的对应正式补丁；找不到就拒绝跨版本覆盖。
2. 点击「打开游戏」，若要求下载约 25.4 MB，则同意。**等下载完成、标题/登录界面重新出现后**按 Home，把游戏退到后台；不要划掉游戏任务，不要再次登录/重新启动。
3. 返回助手点 **② 下载后覆盖**。这一步只使用本地已验证且与游戏版本匹配的补丁，**不联网等待**；助手只在检测到游戏进程仍存活且游戏不在前台时执行。成功后从最近任务**切回原游戏任务**。
4. 退出/清理后台后的下次登录会重新校验并还原资源，需要重复流程。仅凭文件校验不能保证游戏内立刻刷新文字，以游戏实际显示为准。

## 构建

推荐 **Android Studio（JDK 17、Android SDK Platform 35）** 打开根目录；或在已配置 SDK 的环境执行 `./gradlew assembleDebug`。APK 输出在 `app/build/outputs/apk/debug/`。仓库附有 `.github/workflows/android.yml`，推送到 GitHub 后也可在 Actions 中触发构建。项目使用 AGP 8.7.3 / Gradle 8.9 / Shizuku API 13.1.5；联网首次构建需要下载这些依赖。当前环境没有用完整 Android SDK 跑通 Gradle 的 `assembleDebug`，GitHub Actions 工作流也尚未实跑；提供的 APK 通过 javac、D8、aapt 和 apksigner 手工构建并在 Android 14 手机上安装、启动验证。`app/src/main/assets/jp.zip` 已包含在项目包内。正式分发时用自己的签名密钥签名，不要上传 `.jks` 或私钥。现有预构建 APK 是**测试签名**，和自己另行签名的版本不能直接覆盖安装。

## 实现与权限

下面描述的是 **v1.0.4**（本仓库 `main`）。v1.0.2 没有 `PatchUserService`，覆盖只走远程 shell。

- 使用官方 `rikka.shizuku.ShizukuProvider` 初始化并申请 Shizuku 权限。v1.0.4 在服务 UID 为 `0`（root，包括 Sui）时绑定应用自己的 `PatchUserService`。代码里仍保留 UID `2000` 的远程 shell 回退，但实测没有 root 不能正常工作，不要使用这条回退。
- 应用使用 `INTERNET` 权限直连 GitHub 正式发布页检查版本，并仅下载与当前游戏版本相同的 `jp.zip`；先核对 HTTPS 跳转来源、下载大小、ZIP 路径和关键 JSON，再计算文件 SHA-256 并保存。上游没有供本工具核验的独立签名或官方哈希，因此 **SHA-256 只能验证下载前后文件一致性，不能证明上游补丁可信**；仅在信任补丁作者时使用自动更新。
- root 路径由 UserService 在服务进程里定位游戏日语目录、解压并核验 `JP_MainUIText.json` 与 `JP_BattleKeywords.json`。远程 shell 路径则经标准输入把已验证补丁传入 `/data/local/tmp/limbus-shizuku-jp-update.zip`，再由 shell 校验摘要并解压。② 使用已缓存的同版本补丁，不在游戏退后台的短暂窗口等待联网。
- **不直接调用 `su`、不需要广泛的外部存储权限**。所有文件操作都经 Shizuku API。root 模式意味着本应用间接使用 root 权限，不能称为“无需 root”。
- `RemoteShell.java` 是为 Shizuku API 13.1.5 中包内可见的远程进程构造器提供的很小的 Java 桥接类。v1.0.4 的 root 路径已经改用 UserService。

## 项目结构

- `app/src/main/java/com/example/limbusshizuku/MainActivity.java`：界面、Shizuku 权限和两步覆盖流程。
- `app/src/main/java/com/example/limbusshizuku/PatchRepository.java`：GitHub 正式版检测、同版本下载与校验、离线缓存。
- `app/src/main/java/com/example/limbusshizuku/PatchUserService.java`：v1.0.4 的 root UserService 覆盖后端。
- `app/src/main/java/rikka/shizuku/RemoteShell.java`：Shizuku 远程进程桥接。v1.0.2 用它做覆盖；v1.0.4 仅在非 root 时会走到这里，而这条路径实测不可用。
- `app/src/main/assets/jp.zip`：社区汉化资源；适配 1.115.0。
- `app/src/main/AndroidManifest.xml`、`app/build.gradle`：Android 应用配置。
- `LICENSE-APP`、`THIRD_PARTY.md`：代码与资源归属说明。

## 常见报错

- **没有 root**：v1.0.4 不能正常工作。v1.0.2 的纯 ADB 模式没有测过，不要反复点。
- **Shizuku 未运行 / 无权限**：先启动 Shizuku 或 Sui，再给本应用授权。应用会显示当前是 root（UID 0）还是 ADB shell（UID 2000）。
- **UserService 已启动但覆盖失败**（v1.0.4）：看报错里的 UID 和目标目录。能工作的情况应是 UID 0。
- **当前模式无法写入 `Android/data`**：Sui 或 ADB shell 的挂载视图经常看不到游戏目录。v1.0.4 就是为了在 root 下改用 UserService；仍然写不了就停，不要反复点击。
- **游戏与补丁版本不匹配**：联网检查后仅缓存与当前游戏版本相同的正式补丁；上游尚未发布对应版本、联网失败且没有同版缓存时拒绝覆盖。内置备用包仅适配 1.115.0。
- **GitHub 连接失败**：检查设备网络；最新版本经 GitHub `/releases/latest` 页面重定向获取。若已缓存同版本补丁或游戏为 1.115.0，可在离线情况下继续按相同版本流程；无法保证缓存以后仍是上游最新。
- **覆盖后仍是日文**：可能是下载后尚未回到标题页就覆盖，或切回游戏后又校验并恢复。先核对游戏状态，再重复标准流程；不要清除游戏数据。

## 来源及授权

译文与预构建补丁来自 [pzwboy/LocalizeLimbusCompanyForAndroid](https://github.com/pzwboy/LocalizeLimbusCompanyForAndroid)，其上游为 [ghcruise/LimbusCompany-IOS-Localization](https://github.com/ghcruise/LimbusCompany-IOS-Localization) 和 [LocalizeLimbusCompany/LocalizeLimbusCompany](https://github.com/LocalizeLimbusCompany/LocalizeLimbusCompany)。**翻译资源遵循 CC BY-NC-SA 4.0**，不是本项目原创；打包和再分发时请保留原作者署名并遵守非商业、相同方式共享条件。Shizuku API 来源于 [RikkaApps/Shizuku-API](https://github.com/RikkaApps/Shizuku-API)（MIT）。助手原创源码采用 `LICENSE-APP` 中的 MIT 许可；此许可**不覆盖**翻译资源、游戏素材或 Shizuku 的独立版权。

这是非官方工具，不隶属 Project Moon；用户应自行考虑使用第三方补丁和修改游戏资源的风险。
