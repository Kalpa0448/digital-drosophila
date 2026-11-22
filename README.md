<div align="center">

# 数字果蝇 · Digital Drosophila

**把一整套果蝇全脑模拟实验，做成了能装进口袋的原生 Android 应用。**

3D 活体围栏 · 2D 生态大世界 · 3D 养果蝇 —— 断网也能跑

</div>

---

## 这是什么

一个原生 Android 外壳，把 [fly-sim](https://github.com/sjy20100819mc-cmyk/fly-sim) 的果蝇模拟搬进手机，并重新做了整套移动端交互：

- **围栏**：单只果蝇的 3D 活体观察，138,639 个神经元实时放电
- **大世界**：2D 俯视生态，果蝇 / 蜘蛛 / 青蛙 / 捕蝇草 / 食物链，带种群与能量曲线
- **养果蝇**：3D 培养皿，种群自主行为、取食、粘板、学习与记忆

界面不是网页套壳 —— 顶栏、数据卡、按钮、参数面板、观察面板、图例、曲线全部是 **原生 Android View**，只有 3D 渲染与高频数据流留在 WebView 里。

## 特性

| | |
|---|---|
| 完全离线 | 连接组数据、JS、样式全部内置于 APK，无网络请求 |
| 本地运算 | 138,639 神经元 / 15,091,983 连接边，1 ms 步长 LIF |
| 原生控件 | 数据卡、参数滑块、观察记录、种群曲线、图例均为原生实现 |
| 状态高亮 | 加速 / 暂停 / 脑活动 / 气味场 / 曲线等按钮跟随真实状态高亮 |
| 零依赖 | 无第三方 Java 库，仅 Android SDK + 内置 three.js |

## 构建

需要 JDK 17 + Android SDK（compileSdk 34）。

```bash
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:assembleDebug
```

Release 签名需要自建 `release.jks` 与 `keystore.properties`（未包含在本仓库中）：

```properties
storeFile=release.jks
storePassword=***
keyAlias=flybrain
keyPassword=***
```

## 网页资源更新

原生层与网页层通过 **DOM 契约** 通信（约 70 个元素 id / class）。网页更新后执行：

```bash
python3 tools/adapt_web.py /path/to/flybrain
```

脚本会完成资源同步、连接组数据补全（含 `.gz.bin` 改名，绕开 AAPT 解包）、样式注入与契约检查。契约缺失时会明确指出需要改哪个选择器。

## 数据来源

- **连接组**：FlyWire FAFB v783（Dorkenwald et al., *Nature* 634:124–138, 2024）
- **模型**：Shiu et al., *Nature* (2024) 全脑 LIF
- **降维脑**：FlyEM 雄性 CNS v1.0 — 165,122 神经元 / 25,568,639 突触（Janelia / Cambridge / Google, 2026-09）

## 开源引用与致谢

本项目**养果蝇场景**的神经群体模型、行为读出与参数体系参考自：

- [**fly-sim**](https://github.com/sjy20100819mc-cmyk/fly-sim) — 果蝇 3D 模拟器（MIT）
  把 16.5 万神经元降维成每只 48 个（25 功能群 / 145 条边，其中 119 条来自实测）

其他依赖：

- **three.js** r147（MIT）
- **flybench** / Shiu et al. 2024 的反射判据

应用内「关于」页与养果蝇页右上角 **i** 按钮均内置上述引用信息。

## 许可

本仓库代码以 **MIT** 发布，详见 [LICENSE](LICENSE)。

连接组数据版权归原作者所有，**仅用于学习与研究**，请勿用于商业用途。
