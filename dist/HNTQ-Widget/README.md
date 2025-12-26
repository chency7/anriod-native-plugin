# HNTQ-Widget UniApp 原生插件

本插件包含以下 Android 桌面小组件 (AppWidget):

1. **HitokotoWidget** (一言)
2. **HotSearchWidget** (热搜)

## 目录结构

```
HNTQ-Widget/
  ├── android/
  │   └── libs/
  │       └── hntq_widget_library.aar  (插件核心库)
  └── package.json                     (插件配置)
```

## 集成步骤

1. **复制插件**:

   **无论你是 HBuilderX 项目还是 CLI (Vite/Webpack) 项目，都必须将插件放在项目根目录下的 `nativeplugins` 文件夹中。**

   > **特别注意**: 对于 Vue3/Vite CLI 项目，千万**不要**将 `nativeplugins` 放在 `src` 目录下，否则 CLI 构建时会无法识别插件，导致打包出的基座缺少插件功能（报插件加载为空错误）。

   正确目录结构示例：

   ```text
   MyUniAppProject/
     ├── package.json
     ├── src/
     ├── nativeplugins/           <-- 必须放在根目录
     │   └── HNTQ-Widget/
     │       ├── android/
     │       └── package.json
     ├── manifest.json
     └── ...
   ```

2. **配置 manifest.json**:

   - 打开 `manifest.json` -> "App 原生插件配置"。
   - 选择 "本地插件"，找到 `HNTQ-Widget` 并勾选。

3. **制作自定义基座**:

   - 运行 -> 运行到手机或模拟器 -> 制作自定义调试基座。
   - 打包成功后，选择 "运行到手机或模拟器" -> "运行基座选择" -> "自定义调试基座"。

4. **注意事项**:
   - 本插件依赖 `Retrofit` 和 `Gson`，已在 `package.json` 中声明，云打包会自动处理。
   - 如果使用离线打包，请确保在你的 Android 主工程 `build.gradle` 中添加相应依赖。

## 常见问题 (Troubleshooting)

### Q: 为什么使用 CLI 打包 (Vite/Webpack) 时，基座运行提示“插件加载为空”？

**A:** 这通常是因为构建机制差异导致的。
UniApp CLI 项目的构建流程只会扫描 **根目录** 下的 `nativeplugins` 文件夹。如果你将插件放在 `src` 目录下，或者使用了非官方的构建命令，CLI 无法将原生资源正确打包到 `dist` 目录中。

**解决方案：**

1. 确保 `nativeplugins` 放在项目根目录（与 `package.json` 同级）。
2. 使用官方 CLI 命令进行构建（如 `npm run dev:app-plus` 或 `npm run build:app-plus`）。
3. 检查 `dist/dev/app` 或 `dist/build/app` 目录下是否包含 `nativeplugins` 文件夹。如果包含，说明构建正确。
4. 重新制作自定义调试基座。

## JS 调用 (可选)

虽然 Widget 主要由系统管理，但你可以通过以下方式在 JS 层获取插件对象（目前插件主要功能在原生层，JS 层接口待扩展）：

```javascript
const HNTQWidget = uni.requireNativePlugin("HNTQWidget");
console.log(HNTQWidget);
```
