#!/usr/bin/env python3
"""Aafm AI 玩家 - 无客户端 Minecraft AI 玩家机器人

任务二（最初想法）：不开 Minecraft，用 Python GUI 控制 mineflayer 引擎进入服务器做 AI。
可配置服务器地址、AI API、AI 玩家触发规则等。

用法:
    pip install -r requirements.txt
    python main.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from aafm_py.gui import main  # noqa: E402

if __name__ == '__main__':
    main()
