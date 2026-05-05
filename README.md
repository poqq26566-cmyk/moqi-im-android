# 墨奇输入法 (Moqi IM) - Android

一款注重隐私安全的开源安卓输入法，所有数据均在本地处理，不上传任何用户数据。

## 特性

- **隐私安全** — 所有数据本地处理，零数据上传
- **拼音输入** — 支持全拼/简拼，智能音节切分
- **五笔输入** — 五笔字型输入法
- **英文输入** — 英文键盘及联想
- **语音输入** — 基于系统本地语音识别
- **深色模式** — 支持系统深色模式

## 架构

```
app/src/main/java/com/moqi/im/
├── core/               # IME 核心服务
│   └── MoqiInputMethodService.kt
├── engine/             # 输入引擎
│   ├── InputEngine.kt      # 引擎接口
│   ├── EngineFactory.kt    # 引擎工厂
│   ├── PinyinEngine.kt     # 拼音引擎
│   ├── WubiEngine.kt       # 五笔引擎
│   ├── EnglishEngine.kt    # 英文引擎
│   └── VoiceEngine.kt      # 语音引擎
├── keyboard/           # 键盘 UI
│   ├── KeyboardView.kt     # 自绘键盘
│   ├── CandidateView.kt    # 候选词栏
│   ├── ComposeView.kt      # 编码区
│   └── KeyDefinition.kt   # 按键定义
├── dict/               # 词典系统
│   ├── Dictionary.kt       # 词典接口
│   ├── TrieDictionary.kt   # Trie 字典树
│   ├── DictionaryManager.kt # 词典管理
│   └── LazyDictionary.kt   # 延迟加载
├── settings/           # 设置页面
│   ├── SettingsActivity.kt
│   └── SettingsFragment.kt
└── MoqiApplication.kt   # Application 入口
```

## 构建

```bash
./gradlew assembleDebug
```

## 安装

1. 构建并安装 APK
2. 在系统设置中启用「墨奇输入法」
3. 在输入法选择中切换到墨奇输入法

## 许可证

MIT License