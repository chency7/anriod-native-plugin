# 项目规则（HNTQWIDGET）

## UniApp 本地原生插件（Android / AAR）`package.json` 模板

HBuilderX 校验要求：`_dp_nativeplugin.android.plugins` 必须存在，且数组元素必须包含非空的 `type`、`name`、`class`，否则会报：
`package.json中 _dp_nativeplugin.android.plugins节点下存在为空的对象`

推荐模板（适用于：插件以 `aar` 形式集成，不在 JS 层直接调用，仅用于随 App 安装合并原生能力/清单/资源）：

```json
{
  "name": "HitokotoWidget",
  "id": "HNTQ-Widget",
  "version": "1.0.0",
  "description": "Android Native Widget Plugin for UniApp - Hitokoto",
  "_dp_type": "nativeplugin",
  "_dp_nativeplugin": {
    "android": {
      "plugins": [
        {
          "type": "module",
          "name": "HNTQWidget",
          "class": "com.hntq.destop.widget.library.UniEntry"
        }
      ],
      "integrateType": "aar",
      "dependencies": [
        "com.squareup.retrofit2:retrofit:2.9.0",
        "com.squareup.retrofit2:converter-gson:2.9.0"
      ],
      "parameters": {}
    }
  }
}
```

约束要点：

- `id` 必须与 `nativeplugins/<插件目录名>/` 一致
- `plugins[0].class` 必须是实际存在的类（可用占位类），避免空字符串或空对象
- `integrateType` 使用 `aar` 时，`aar` 放到 `nativeplugins/<插件目录>/android/libs/*.aar`
