<<<<<<< HEAD
# AI Agent for Minecraft

Minecraft 1.21.11 / Fabric 内置 AI 智能体模组，自带**独立窗口**。可以把 AI 接入任意 **OpenAI 兼容** API（OpenAI、DeepSeek、Ollama、vLLM、SiliconFlow、通义/智谱兼容端点等），让它在游戏里和你对话，并**真实控制角色**：移动、跳跃、攻击、放置方块、使用物品、切换物品栏、发聊天消息，同时读取你的位置、血量、背包和周围环境。

## 功能

- **独立窗口**：按 `K` 键打开游戏内 AI 窗口（滚动对话记录 + 输入框 + 发送/设置/清空/停止/启停按钮）
- **完全可自定义 API**：游戏内设置界面可配置 Base URL、API Key、Model、Temperature、Max Tokens、Max History、System Prompt；配置保存为 `.minecraft/config/mcai.json`
- **工具调用**：AI 通过函数调用感知世界并执行动作（get_state / move / jump / look / attack / use_held_item / select_hotbar / drop_held_item / chat 等）
- **读取游戏状态**：位置、朝向、维度、血量、饥饿、经验、物品栏（热栏）、准星目标方块、附近实体与方块
- **后台运行**：窗口打开时世界不暂停（非暂停界面），AI 可实时行动；HUD 显示 thinking 状态
- **纯手动构建**：不使用 Gradle，一键脚本用 `javac` + `tiny-remapper` 直接编译打包

## 使用

1. 安装 **Fabric Loader ≥ 0.16** 与 **Fabric API**（1.21.11 版本），Java ≥ 21
2. 把 `dist/mcai-1.0.0.jar` 复制到 `.minecraft/mods/`
3. 启动游戏进入世界，按 `K` 打开 AI 窗口
4. 点 `Settings`，填好 `API Base URL`（如 `https://api.deepseek.com/v1` 或 `http://localhost:11434/v1`）、`API Key`、`Model`，点 `Save & Reload`
5. 在输入框提问，例如：
   - “告诉我现在我在哪里，周围有什么”
   - “向前走两步”
   - “把我面前那个方块打掉”
   - “把第 3 格物品换到手上”

> 本地模型（Ollama 等）API Key 留空即可。

## 配置说明

配置文件：`.minecraft/config/mcai.json`

| 字段 | 含义 | 默认 |
| --- | --- | --- |
| `baseUrl` | OpenAI 兼容端点（不含 `/chat/completions`） | `https://api.openai.com/v1` |
| `apiKey` | API 密钥 | 空 |
| `model` | 模型名 | `gpt-4o-mini` |
| `temperature` | 采样温度 0~2 | 0.7 |
| `maxTokens` | 单次最大输出 token | 1024 |
| `maxHistory` | 保留的对话条数 | 24 |
| `maxToolIterations` | 单轮最多工具调用轮数 | 10 |
| `enabled` | 智能体总开关 | true |
| `systemPrompt` | 系统提示词 | 内置 |

## 工具列表

游戏类：`get_state` 感知世界；`move`/`jump`/`sneak`/`sprint`/`stop` 控制移动；`look` 瞄准；`use_held_item` 使用/放置/交互；`attack` 攻击/挖掘；`select_hotbar` 切换物品栏；`drop_held_item` 丢弃物品；`chat` 发言。

电脑类（**默认禁用**，需在设置里开启）：
- `run_command` 在电脑上执行系统命令（cmd.exe，30 秒超时）
- `write_file` / `read_file` / `list_dir` 读写文档、代码、目录（相对路径存到 `.minecraft/mcai_workspace/`）
- `copy_text` 把文本复制到系统剪贴板

## 安全开关

电脑控制能力有风险，默认全部关闭，需在**设置**界面手动开启：
- **允许命令**：控制 `run_command`
- **允许文件**：控制 `read_file` / `write_file` / `list_dir`

设置保存在 `config/mcai.json` 的 `allowShell` / `allowFileWrite` 字段。开启后请谨慎使用，系统提示词也会要求 AI 评估命令安全性。

窗口底部还有**复制**按钮，可一键复制最后一条 AI 回复。

## 手动构建（无需 Gradle）

```powershell
powershell -ExecutionPolicy Bypass -File build.ps1
```

脚本自动：下载所需依赖（Mojang client、Yarn 映射、Mojang 官方映射、Fabric API/Loader、tiny-remapper 等，已缓存到 `lib/`）→ 生成映射 → 把 Fabric API 重映射为 Mojang 命名 → `javac` 编译源码 → 用 tiny-remapper 把产物重映射回 **intermediary** 命名 → 打包到 `dist/mcai-1.0.0.jar`。产物约几十 KB。

### 构建原理（技术要点）

Minecraft 1.21.x 分发的 client.jar 使用 Mojang 可读映射，而 Fabric 要求模组 jar 为 **intermediary** 命名。本项目：
1. 用 Yarn tiny + Mojang `client.txt` 合并出 `mojang ↔ official ↔ intermediary` 双向映射（`tools/MergeMappings.java`）
2. 源码直接以 **Mojang 命名** 编写，对着官方 client.jar 编译（无需重映射客户端）
3. Fabric API 从 intermediary 两段重映射到 Mojang 命名供编译
4. 产物两段重映射回 intermediary 分发，由 Fabric Loader 在运行时解析

## 项目结构

```
src/main/java/com/mcai/   模组源码（Mojang 命名）
  McAiClient.java         入口 + 快捷键 + HUD
  config/                 配置模型与读写（mcai.json）
  llm/                    OpenAI 兼容客户端 + DTO + 工具规格
  agent/                  状态读取 / 动作执行 / 智能体循环 / 工具定义
  gui/                    AI 独立窗口 + 设置界面
src/main/resources/       fabric.mod.json / 语言文件 / 图标
tools/                    映射合并与图标生成工具
test/smoke/               LLM 客户端离线冒烟测试
build.ps1                 一键手动构建脚本
```

## 说明与限制

- 1.21.11 的输入系统已重构为 `KeyEvent`/`MouseButtonEvent` 记录，GUI 已适配
- 智能体动作通过客户端 tick 队列在渲染线程执行，LLM 请求在工作线程执行，线程安全
- 目前为**非流式**响应（更稳定）；后续版本可加流式与更多版本支持
=======
# Ai-Agent-For-Minecraft
This is an AI agent for the Minecraft, you can use the AI agent in playing!
>>>>>>> d6f5158fb0409fb7779a466a16575ec1e269e0bb
