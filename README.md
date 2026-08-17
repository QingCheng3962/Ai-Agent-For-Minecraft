# AI Agent for Minecraft

> Minecraft 1.21.11 / Fabric AI 智能体模组 | **中文** · [English](#en)

Minecraft 1.21.11 / Fabric AI agent mod | [中文](#zh) · **English**

---

<a id="zh"></a>

# 中文版

## 简介

Minecraft 1.21.11 / Fabric 内置 AI 智能体模组，自带**独立窗口**。可以把 AI 接入任意 **OpenAI 兼容** API（OpenAI、DeepSeek、Ollama、vLLM、SiliconFlow、通义/智谱兼容端点等），让它在游戏里和你对话，读取你的位置、血量、背包和周围环境。

> ⚠️ **重要**：AI **操控玩家**的功能默认**关闭**。如需让 AI 移动角色/攻击/使用物品，请到设置界面手动开启「允许操控玩家」。

## 功能

- **独立窗口**：按 `K` 键打开游戏内 AI 窗口（滚动对话记录 + 输入框 + 发送/设置/清空/停止/任务模式/任务/会话按钮）
- **完全可自定义 API**：游戏内设置界面可配置 Base URL、API Key、Model、Temperature、Max Tokens、Max History、System Prompt；配置保存为 `.minecraft/config/mcai.json`
- **多会话**：会话历史独立保存（`.minecraft/config/mcai_sessions/`），可新建 / 切换 / 删除
- **任务系统**：每条指令生成一个任务，可在任务列表中暂停 / 继续 / 取消；任务模式自动按步骤工作直到完成
- **感知世界**：`get_state` 读取位置、朝向、维度、血量、饥饿、经验、物品栏（热栏）、准星目标方块、附近实体与方块
- **操控玩家**（默认禁用，需开启）：`move`/`jump`/`sneak`/`sprint`/`stop` 移动、`look` 瞄准、`use_held_item` 使用/放置、`attack` 攻击/挖掘、`select_hotbar` 切栏、`drop_held_item` 丢弃、`chat` 发言
- **电脑工具**（默认禁用，需开启）：执行系统命令、读写/编辑文件、目录浏览、grep、find_files、复制到剪贴板
- **事件感知**（可开关）：死亡/重生、维度切换、低血量、附近敌对生物、天黑、监听他人聊天
- **后台运行**：窗口打开时世界不暂停（非暂停界面）；HUD 显示"思考中"状态
- **纯手动构建**：不使用 Gradle，一键脚本用 `javac` + `tiny-remapper` 直接编译打包

## 使用

1. 安装 **Fabric Loader ≥ 0.16** 与 **Fabric API**（1.21.11 版本），Java ≥ 21
2. 把 `dist/mcai-1.0.0.jar` 复制到 `.minecraft/mods/`
3. 启动游戏进入世界，按 `K` 打开 AI 窗口
4. 点 `设置`，填好 `API 地址`（如 `https://api.deepseek.com/v1` 或 `http://localhost:11434/v1`）、`API 密钥`、`模型`，点 `保存并重载`
5. 在输入框提问，例如：
   - "告诉我现在我在哪里，周围有什么"
   - "分析一下我的背包"
   - （开启操控玩家后）"向前走两步"、"把我面前那个方块打掉"

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
| `maxToolIterations` | 单轮最多工具调用轮数 | 25 |
| `enabled` | 智能体总开关 | true |
| `taskMode` | 自主任务模式 | true |
| `allowPlayerControl` | **允许 AI 操控玩家**（移动/攻击等） | **false** |
| `allowShell` | 允许执行系统命令 | false |
| `allowFileWrite` | 允许读写文件/目录 | false |
| `autoRespondEvents` | 自动响应游戏事件 | false |
| `watchChat` | 监听他人聊天 | false |
| `windowMove` | AI 窗口打开时可用键盘移动角色 | true |
| `systemPrompt` | 系统提示词 | 内置 |

## 工具列表

- **感知**：`get_state` 读取玩家与世界状态（始终可用）
- **交流**：`chat` 在游戏内发言（始终可用）
- **操控玩家**（`allowPlayerControl` 开启后可用）：`move`/`jump`/`sneak`/`sprint`/`stop`、`look`、`use_held_item`、`attack`、`select_hotbar`、`drop_held_item`
- **电脑类**（`allowShell` / `allowFileWrite` 开启后可用）：
  - `run_command` 在电脑上执行系统命令（cmd.exe，30 秒超时）
  - `write_file` / `read_file` / `edit_file` / `list_dir` / `grep` / `find_files` 处理文档、代码、目录（相对路径存到 `.minecraft/mcai_workspace/`）
  - `copy_text` 把文本复制到系统剪贴板

## 安全开关

以下能力有风险，默认全部关闭，需在**设置**界面手动开启：

- **允许操控玩家**：控制 `move` / `attack` 等游戏动作
- **允许命令**：控制 `run_command`
- **允许文件**：控制 `read_file` / `write_file` / `edit_file` / `list_dir` / `grep` / `find_files`

设置保存在 `config/mcai.json` 中。开启后请谨慎使用，系统提示词也会要求 AI 评估命令安全性。

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
  config/                 配置模型与读写（mcai.json / 会话）
  llm/                    OpenAI 兼容客户端 + DTO + 工具规格
  agent/                  状态读取 / 动作执行 / 智能体循环 / 工具定义
  gui/                    AI 独立窗口 + 设置 / 任务 / 会话界面
src/main/resources/       fabric.mod.json / 语言文件 / 图标
tools/                    映射合并与图标生成工具
test/smoke/               LLM 客户端离线冒烟测试
build.ps1                 一键手动构建脚本
```

## 说明与限制

- 1.21.11 的输入系统已重构为 `KeyEvent`/`MouseButtonEvent` 记录，GUI 已适配
- 智能体动作通过客户端 tick 队列在渲染线程执行，LLM 请求在工作线程执行，线程安全
- 目前为**非流式**响应（更稳定）；后续版本可加流式与更多版本支持

## 免责声明

**请在使用本模组前仔细阅读：**

1. **AI 内容不作保证**：本模组输出的所有内容（对话、建议、动作、命令）均由第三方 AI 模型生成，可能出现错误、偏差或不安全内容，使用前请自行判断与核实。
2. **操控玩家风险**：开启「允许操控玩家」后，AI 可能执行移动、攻击、挖掘、丢弃物品等操作，可能导致角色死亡、掉落物品、破坏地形等后果。请自行承担风险。
3. **系统命令与文件风险**：开启「允许命令」/「允许文件」后，AI 可在你的电脑上执行命令、读写文件。这可能导致数据丢失、系统损坏或安全风险。**默认关闭，非必要请勿开启**；开启后请始终监控 AI 的行为。
4. **网络与费用**：本模组会向你所配置的 API 端点发送你的对话、游戏状态（位置/血量/背包等）与文件内容。请注意隐私，自行承担相关 API 费用。
5. **非官方**：本项目与 Mojang、Microsoft 无任何关联，非官方作品，仅供学习与研究使用。
6. **按现状提供**：本项目按"现状"提供，不提供任何形式的明示或暗示保证。因使用本模组造成的任何直接或间接损失，作者不承担责任。

---

<a id="en"></a>

# English Version

## Introduction

An in-game AI agent mod for Minecraft 1.21.11 / Fabric with a **standalone window**. Connect the AI to any **OpenAI-compatible** API (OpenAI, DeepSeek, Ollama, vLLM, SiliconFlow, etc.), chat with it in-game, and let it read your position, health, inventory, and surroundings.

> ⚠️ **Note**: The AI's ability to **control the player** is **disabled by default**. To let the AI move your character / attack / use items, manually enable "Allow Player Control" in the Settings screen.

## Features

- **Standalone window**: press `K` to open the in-game AI window (scrollable chat + input box + Send / Settings / Clear / Stop / Task Mode / Tasks / Sessions buttons)
- **Fully customizable API**: configure Base URL, API Key, Model, Temperature, Max Tokens, Max History, System Prompt in-game; saved to `.minecraft/config/mcai.json`
- **Multiple sessions**: independent chat histories (`.minecraft/config/mcai_sessions/`), create / switch / delete sessions
- **Task system**: every command becomes a task that can be paused / resumed / cancelled; task mode works autonomously step by step until done
- **World perception**: `get_state` reads position, facing, dimension, health, hunger, XP, hotbar, targeted block, nearby entities and blocks
- **Player control** (disabled by default): `move`/`jump`/`sneak`/`sprint`/`stop`, `look`, `use_held_item`, `attack`, `select_hotbar`, `drop_held_item`, `chat`
- **Computer tools** (disabled by default): run system commands, read/write/edit files, list directories, grep, find files, copy text to clipboard
- **Event awareness** (togglable): death/rebirth, dimension change, low health, nearby hostiles, nightfall, other players' chat
- **Runs in background**: the world keeps running while the window is open (non-pause screen); HUD shows a "thinking" indicator
- **Manual build**: no Gradle — one script compiles and packages with `javac` + `tiny-remapper`

## Usage

1. Install **Fabric Loader ≥ 0.16** and **Fabric API** (1.21.11), Java ≥ 21
2. Copy `dist/mcai-1.0.0.jar` into `.minecraft/mods/`
3. Launch the game, join a world, press `K` to open the AI window
4. Open `Settings`, fill in `API Base URL` (e.g. `https://api.deepseek.com/v1` or `http://localhost:11434/v1`), `API Key`, `Model`, then click `Save & Reload`
5. Ask the AI, for example:
   - "Tell me where I am and what's around me"
   - "Analyze my inventory"
   - (with player control enabled) "Walk forward two steps", "Break the block in front of me"

> For local models (Ollama etc.) leave the API Key empty.

## Configuration

Config file: `.minecraft/config/mcai.json`

| Field | Meaning | Default |
| --- | --- | --- |
| `baseUrl` | OpenAI-compatible endpoint (without `/chat/completions`) | `https://api.openai.com/v1` |
| `apiKey` | API key | empty |
| `model` | Model name | `gpt-4o-mini` |
| `temperature` | Sampling temperature 0~2 | 0.7 |
| `maxTokens` | Max output tokens per response | 1024 |
| `maxHistory` | History messages kept | 24 |
| `maxToolIterations` | Max tool-call rounds per turn | 25 |
| `enabled` | Master agent switch | true |
| `taskMode` | Autonomous task mode | true |
| `allowPlayerControl` | **Allow AI to control the player** (move/attack etc.) | **false** |
| `allowShell` | Allow running system commands | false |
| `allowFileWrite` | Allow reading/writing files and directories | false |
| `autoRespondEvents` | Auto-respond to game events | false |
| `watchChat` | Watch other players' chat | false |
| `windowMove` | Allow keyboard movement while the AI window is open | true |
| `systemPrompt` | System prompt | built-in |

## Tools

- **Perception**: `get_state` reads the player and world state (always available)
- **Communication**: `chat` speaks in-game (always available)
- **Player control** (available when `allowPlayerControl` is on): `move`/`jump`/`sneak`/`sprint`/`stop`, `look`, `use_held_item`, `attack`, `select_hotbar`, `drop_held_item`
- **Computer tools** (available when `allowShell` / `allowFileWrite` are on):
  - `run_command` executes a system command (cmd.exe, 30 s timeout)
  - `write_file` / `read_file` / `edit_file` / `list_dir` / `grep` / `find_files` for documents, code, directories (relative paths are stored under `.minecraft/mcai_workspace/`)
  - `copy_text` copies text to the system clipboard

## Safety Switches

The following capabilities are risky and are **all off by default**; enable them manually in the Settings screen:

- **Allow Player Control**: controls `move`, `attack`, etc.
- **Allow Commands**: controls `run_command`
- **Allow Files**: controls `read_file` / `write_file` / `edit_file` / `list_dir` / `grep` / `find_files`

Settings are saved in `config/mcai.json`. Use them with caution; the system prompt also asks the AI to evaluate command safety.

## Manual Build (no Gradle)

```powershell
powershell -ExecutionPolicy Bypass -File build.ps1
```

The script automatically: downloads required dependencies (Mojang client, Yarn mappings, Mojang official mappings, Fabric API/Loader, tiny-remapper, cached under `lib/`) → generates merged mappings → remaps Fabric API to Mojang names → compiles sources with `javac` → remaps the output back to **intermediary** names with tiny-remapper → packages `dist/mcai-1.0.0.jar` (tens of KB).

### How the build works

Minecraft 1.21.x ships a client.jar with readable Mojang mappings, while Fabric requires mod jars named with **intermediary**. This project:

1. Merges Yarn tiny + Mojang `client.txt` into a `mojang ↔ official ↔ intermediary` two-way mapping (`tools/MergeMappings.java`)
2. Writes sources directly in **Mojang names**, compiled against the official client.jar (no client remap needed)
3. Remaps Fabric API from intermediary to Mojang names for compilation
4. Remaps the output back to intermediary for distribution; Fabric Loader resolves names at runtime

## Project Structure

```
src/main/java/com/mcai/   mod sources (Mojang names)
  McAiClient.java         entry point + hotkey + HUD
  config/                 config model and I/O (mcai.json / sessions)
  llm/                    OpenAI-compatible client + DTOs + tool specs
  agent/                  state reading / action execution / agent loop / tool definitions
  gui/                    AI window + settings / tasks / sessions screens
src/main/resources/       fabric.mod.json / language files / icon
tools/                    mapping-merge and icon generator
test/smoke/               offline LLM client smoke test
build.ps1                 one-click manual build script
```

## Notes & Limitations

- 1.21.11 refactored the input system to `KeyEvent`/`MouseButtonEvent` records; the GUI is adapted
- Agent actions run on the client tick queue (render thread); LLM requests run on a worker thread — thread-safe
- Currently **non-streaming** responses (more stable); streaming and more versions may come later

## Disclaimer

**Please read carefully before using this mod:**

1. **No guarantee on AI content**: all content generated by this mod (conversation, suggestions, actions, commands) comes from third-party AI models and may be wrong, biased, or unsafe. Verify before relying on it.
2. **Player-control risks**: with "Allow Player Control" enabled, the AI may move, attack, mine, or drop items, which can cause death, item loss, or terrain damage. Use at your own risk.
3. **System-command and file risks**: with "Allow Commands"/"Allow Files" enabled, the AI can run commands and read/write files on your computer. This may cause data loss, system damage, or security issues. **Off by default — keep it off unless necessary**; always monitor the AI when enabled.
4. **Network & costs**: this mod sends your conversation, game state (position/health/inventory), and file contents to the configured API endpoint. Mind your privacy; you are responsible for any API costs.
5. **Unofficial**: this project is not affiliated with or endorsed by Mojang or Microsoft. It is an unofficial work for learning and research purposes.
6. **As-is**: this project is provided "as is" without any express or implied warranty. The author is not liable for any direct or indirect loss caused by using this mod.
