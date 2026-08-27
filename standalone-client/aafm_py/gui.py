"""tkinter GUI for the headless AI-player bot (Task 2: original idea).

Allows configuring the Minecraft server address, LittleSkin/offline auth, the
AI API (OpenAI-compatible), the AI player chat settings, quiz/autoeat extras,
plus a live log and chat input to send messages as the bot.
"""
from __future__ import annotations

import os
import subprocess
import sys
import tkinter as tk
from tkinter import ttk, messagebox

from .config import (AiPlayerConfig, AppConfig, AUTH_PROFILE_FILE, DEFAULT_BASE_URLS,
                     TEMPLATE_ASSISTANT, TEMPLATE_BUDDY, PromptTemplateManager,
                     load_auth_profile)
from .controller import Controller, BASE_DIR

TEMPLATE_BUILTINS = {
    '聊天搭子': TEMPLATE_BUDDY,
    '助手': TEMPLATE_ASSISTANT,
}

def _build_templates() -> dict:
    tpls = dict(TEMPLATE_BUILTINS)
    for t in PromptTemplateManager.get_all():
        if t.get('name') and t.get('prompt'):
            tpls[t['name']] = t['prompt']
    return tpls

_PROVIDERS = ['openai', 'deepseek', 'moonshot', 'anthropic', 'ollama']


class AiPlayerGUI:
    def __init__(self, root: tk.Tk):
        self.root = root
        self.controller = Controller()
        self.root.title('Aafm AI 玩家 - 无客户端机器人')
        self.root.geometry('860x640')
        self.root.minsize(760, 560)

        self._build_toolbar()
        self._build_notebook()
        self._load_config_into_widgets()

        self.root.protocol('WM_DELETE_WINDOW', self._on_close)
        self.root.after(100, self._poll_ui)

    # ------------------------------------------------------------------
    # UI construction
    # ------------------------------------------------------------------
    def _build_toolbar(self) -> None:
        bar = ttk.Frame(self.root, padding=(8, 6))
        bar.pack(side='top', fill='x')

        self.status_label = ttk.Label(bar, text='未连接', font=('Segoe UI', 10, 'bold'),
                                      foreground='#999999')
        self.status_label.pack(side='left')

        self.connect_btn = ttk.Button(bar, text='连接', command=self._on_connect)
        self.connect_btn.pack(side='left', padx=(16, 4))
        self.disconnect_btn = ttk.Button(bar, text='断开', command=self._on_disconnect,
                                         state='disabled')
        self.disconnect_btn.pack(side='left', padx=4)

        ttk.Separator(bar, orient='vertical').pack(side='left', fill='y', padx=12)

        self.ai_enabled = tk.BooleanVar(value=False)
        ttk.Checkbutton(bar, text='AI 玩家开关', variable=self.ai_enabled,
                        command=self._on_ai_enabled).pack(side='left', padx=4)
        ttk.Button(bar, text='重开上下文', command=self._on_clear_context).pack(side='left', padx=4)
        ttk.Button(bar, text='保存设置', command=self._on_save).pack(side='left', padx=4)

    def _build_notebook(self) -> None:
        self.notebook = ttk.Notebook(self.root)
        self.notebook.pack(fill='both', expand=True, padx=6, pady=(0, 6))

        self._build_server_tab()
        self._build_api_tab()
        self._build_ai_player_tab()
        self._build_log_tab()

    def _build_server_tab(self) -> None:
        tab = ttk.Frame(self.notebook, padding=10)
        self.notebook.add(tab, text=' 服务器 ')

        frm = ttk.LabelFrame(tab, text='Minecraft 服务器', padding=8)
        frm.pack(fill='x', pady=(0, 8))

        self.var_host = tk.StringVar()
        self.var_port = tk.StringVar()
        self.var_version = tk.StringVar()
        self.var_brand = tk.StringVar()
        self.var_view = tk.StringVar()

        row = 0
        self._entry(frm, row, '地址', self.var_host); row += 1
        self._entry(frm, row, '端口', self.var_port); row += 1
        self._entry(frm, row, '版本', self.var_version); row += 1
        self._entry(frm, row, '品牌', self.var_brand); row += 1
        self._entry(frm, row, '视距', self.var_view); row += 1

        auth = ttk.LabelFrame(tab, text='登录方式（LittleSkin 需先运行 npm run auth）', padding=8)
        auth.pack(fill='x', pady=(0, 8))

        self.var_auth_type = tk.StringVar(value='offline')
        ttk.Radiobutton(auth, text='离线模式', variable=self.var_auth_type, value='offline').grid(
            row=0, column=0, sticky='w', padx=4, pady=2)
        ttk.Radiobutton(auth, text='LittleSkin 正版', variable=self.var_auth_type,
                        value='littleskin').grid(row=0, column=1, sticky='w', padx=4)
        ttk.Button(auth, text='LittleSkin 登录', command=self._on_littleskin_login).grid(
            row=0, column=2, sticky='e', padx=8)

        self.var_username = tk.StringVar()
        ttk.Label(auth, text='用户名').grid(row=1, column=0, sticky='w', padx=4, pady=4)
        ttk.Entry(auth, textvariable=self.var_username, width=28).grid(
            row=1, column=1, columnspan=2, sticky='we', padx=4, pady=4)
        self.auth_profile_label = ttk.Label(auth, text='未登录', foreground='#888888')
        self.auth_profile_label.grid(row=2, column=0, columnspan=3, sticky='w', padx=4)
        auth.columnconfigure(1, weight=1)

        extra = ttk.LabelFrame(tab, text='辅助功能', padding=8)
        extra.pack(fill='x')
        self.var_quiz = tk.BooleanVar(value=True)
        self.var_autoeat = tk.BooleanVar(value=True)
        ttk.Checkbutton(extra, text='答题（Quiz 自动答题）', variable=self.var_quiz,
                        command=lambda: self._on_extra('quiz', self.var_quiz.get())).pack(side='left')
        ttk.Checkbutton(extra, text='自动进食（AutoEat）', variable=self.var_autoeat,
                        command=lambda: self._on_extra('autoeat', self.var_autoeat.get())).pack(
            side='left', padx=16)

    def _build_api_tab(self) -> None:
        tab = ttk.Frame(self.notebook, padding=10)
        self.notebook.add(tab, text=' AI API ')

        frm = ttk.LabelFrame(tab, text='OpenAI 兼容端点（含 Anthropic）', padding=8)
        frm.pack(fill='x')

        self.var_provider = tk.StringVar()
        self.var_base_url = tk.StringVar()
        self.var_api_key = tk.StringVar()
        self.var_model = tk.StringVar()
        self.var_temperature = tk.StringVar()
        self.var_max_tokens = tk.StringVar()
        self.var_timeout = tk.StringVar()
        self.var_retry = tk.StringVar()

        row = 0
        ttk.Label(frm, text='提供方').grid(row=row, column=0, sticky='w', padx=4, pady=3)
        self.provider_box = ttk.Combobox(frm, textvariable=self.var_provider, values=_PROVIDERS)
        self.provider_box.grid(row=row, column=1, columnspan=2, sticky='we', padx=4, pady=3)
        self.provider_box.bind('<<ComboboxSelected>>', lambda e: self._on_provider_changed())
        row += 1
        self._entry(frm, row, 'API 地址', self.var_base_url, hint='留空则使用提供方默认'); row += 1
        self._entry(frm, row, 'API 密钥', self.var_api_key, show='*'); row += 1
        self._entry(frm, row, '模型', self.var_model); row += 1
        self._entry(frm, row, '温度 (0-2)', self.var_temperature); row += 1
        self._entry(frm, row, '最大 Token', self.var_max_tokens); row += 1
        self._entry(frm, row, '超时秒', self.var_timeout); row += 1
        self._entry(frm, row, '重试次数', self.var_retry); row += 1

        hint = ttk.Label(tab, text='提示：Ollama 本地模型可留空 API 密钥；baseUrl 形如 '
                                   'https://api.deepseek.com/v1', foreground='#888888')
        hint.pack(fill='x', padx=6, pady=(8, 0))

    def _build_ai_player_tab(self) -> None:
        tab = ttk.Frame(self.notebook, padding=10)
        self.notebook.add(tab, text=' AI 玩家 ')

        self._build_prompt_frame(tab)
        self._build_trigger_frame(tab)
        self._build_block_frame(tab)

    def _build_prompt_frame(self, parent) -> None:
        frm = ttk.LabelFrame(parent, text='提示词与上下文', padding=8)
        frm.pack(fill='x', pady=(0, 8))

        self.var_template = tk.StringVar(value='聊天搭子')
        ttk.Label(frm, text='模板').grid(row=0, column=0, sticky='w', padx=4, pady=3)
        self.template_combo = ttk.Combobox(frm, textvariable=self.var_template,
                     values=list(_build_templates().keys()),
                     state='readonly')
        self.template_combo.grid(row=0, column=1, sticky='w', padx=4, pady=3)
        ttk.Button(frm, text='应用模板', command=self._on_apply_template).grid(
            row=0, column=2, padx=4)

        self.var_new_tpl_name = tk.StringVar()
        ttk.Entry(frm, textvariable=self.var_new_tpl_name, width=16).grid(
            row=0, column=3, sticky='w', padx=(8, 2), pady=3)
        ttk.Button(frm, text='保存为模板', command=self._on_save_template).grid(
            row=0, column=4, padx=2)
        ttk.Button(frm, text='删除模板', command=self._on_delete_template).grid(
            row=0, column=5, padx=2)

        self.var_system_prompt = tk.StringVar()
        ttk.Label(frm, text='系统提示词').grid(row=1, column=0, sticky='nw', padx=4, pady=3)
        prompt_txt = tk.Text(frm, height=6, width=70, wrap='word')
        prompt_txt.grid(row=1, column=1, columnspan=2, sticky='we', padx=4, pady=3)
        self.system_prompt_widget = prompt_txt
        prompt_scroll = ttk.Scrollbar(frm, orient='vertical', command=prompt_txt.yview)
        prompt_scroll.grid(row=1, column=3, sticky='ns')
        prompt_txt.configure(yscrollcommand=prompt_scroll.set)

        self.var_context = tk.BooleanVar(value=True)
        self.var_context_len = tk.StringVar()
        ttk.Checkbutton(frm, text='启用上下文', variable=self.var_context).grid(
            row=2, column=0, sticky='w', padx=4, pady=3)
        self._entry(frm, 2, '长度', self.var_context_len, col=1)
        self.var_max_msgs = tk.StringVar()
        self.var_max_chars = tk.StringVar()
        self._entry(frm, 2, '最多消息', self.var_max_msgs, col=2)
        self._entry(frm, 3, '单条字数', self.var_max_chars, col=1)

    def _build_trigger_frame(self, parent) -> None:
        frm = ttk.LabelFrame(parent, text='触发回复（仅当聊天消息匹配触发正则才回复，非触发保持静默）', padding=8)
        frm.pack(fill='x', pady=(0, 8))

        self.var_trigger = tk.BooleanVar(value=True)
        self.var_trigger_regex = tk.StringVar()
        self.var_cooldown = tk.StringVar()
        self.var_auto = tk.BooleanVar(value=False)
        self.var_schedule = tk.BooleanVar(value=False)
        self.var_schedule_interval = tk.StringVar()

        ttk.Checkbutton(frm, text='启用触发回复', variable=self.var_trigger).grid(
            row=0, column=0, sticky='w', padx=4, pady=3)
        ttk.Label(frm, text='触发正则').grid(row=0, column=1, sticky='w', padx=4, pady=3)
        ttk.Entry(frm, textvariable=self.var_trigger_regex, width=46).grid(
            row=0, column=2, columnspan=3, sticky='we', padx=4, pady=3)
        self._entry(frm, 1, '冷却秒', self.var_cooldown, col=1)

        ttk.Checkbutton(frm, text='自动回复（对每条消息回复）', variable=self.var_auto).grid(
            row=2, column=0, sticky='w', padx=4, pady=3)
        ttk.Checkbutton(frm, text='定时回复（需有匹配消息）', variable=self.var_schedule).grid(
            row=2, column=1, sticky='w', padx=4)
        self._entry(frm, 2, '间隔秒', self.var_schedule_interval, col=2)

        hint = ttk.Label(frm, text='例：仅当消息以 @Helper / [Helper] / !ai 开头时回复。留空=不回复。',
                         foreground='#888888')
        hint.grid(row=3, column=0, columnspan=6, sticky='w', padx=4, pady=(4, 0))

    def _build_block_frame(self, parent) -> None:
        frm = ttk.LabelFrame(parent, text='消息过滤 / 黑名单', padding=8)
        frm.pack(fill='both', expand=True)

        self.var_restrict = tk.BooleanVar(value=True)
        ttk.Checkbutton(frm, text='启用消息过滤', variable=self.var_restrict).grid(
            row=0, column=0, columnspan=3, sticky='w', padx=4, pady=3)

        self.var_block_regex = tk.StringVar()
        ttk.Label(frm, text='拦截正则（用 ; 分隔）').grid(row=1, column=0, sticky='w', padx=4, pady=3)
        ttk.Entry(frm, textvariable=self.var_block_regex, width=50).grid(
            row=1, column=1, columnspan=3, sticky='we', padx=4, pady=3)

        ttk.Label(frm, text='黑名单玩家').grid(row=2, column=0, sticky='nw', padx=4, pady=3)
        self.blocked_list = tk.Listbox(frm, height=6)
        self.blocked_list.grid(row=2, column=1, columnspan=3, sticky='we', padx=4, pady=3)
        self.var_new_block = tk.StringVar()
        ttk.Entry(frm, textvariable=self.var_new_block, width=28).grid(
            row=3, column=1, sticky='w', padx=4, pady=3)
        ttk.Button(frm, text='添加', command=self._on_add_blocked).grid(row=3, column=2, padx=2)
        ttk.Button(frm, text='删除', command=self._on_remove_blocked).grid(row=3, column=3, sticky='w', padx=2)

        img = ttk.LabelFrame(parent, text='文生图（可选）', padding=8)
        img.pack(fill='x')
        self.var_image = tk.BooleanVar(value=False)
        self.var_image_model = tk.StringVar()
        ttk.Checkbutton(img, text='启用文生图', variable=self.var_image).pack(side='left', padx=4)
        ttk.Label(img, text='模型').pack(side='left', padx=(12, 2))
        ttk.Entry(img, textvariable=self.var_image_model, width=22).pack(side='left')

    def _build_log_tab(self) -> None:
        tab = ttk.Frame(self.notebook, padding=6)
        self.notebook.add(tab, text=' 日志/聊天 ')

        self.log_text = tk.Text(tab, height=20, wrap='word', state='disabled',
                                font=('Consolas', 9))
        self.log_text.pack(fill='both', expand=True)
        log_scroll = ttk.Scrollbar(tab, orient='vertical', command=self.log_text.yview)
        log_scroll.pack(side='right', fill='y')
        self.log_text.configure(yscrollcommand=log_scroll.set)

        self.log_text.tag_configure('info', foreground='#888888')
        self.log_text.tag_configure('error', foreground='#ff6b6b')
        self.log_text.tag_configure('ai', foreground='#8fb7ff')
        self.log_text.tag_configure('sent', foreground='#7ddb7d')
        self.log_text.tag_configure('quiz', foreground='#ffd54f')
        self.log_text.tag_configure('chat', foreground='#e9ecf1')
        self.log_text.tag_configure('private', foreground='#ff9ad8')

        bottom = ttk.Frame(tab)
        bottom.pack(fill='x', pady=(6, 0))
        self.chat_var = tk.StringVar()
        self.chat_entry = ttk.Entry(bottom, textvariable=self.chat_var)
        self.chat_entry.pack(side='left', fill='x', expand=True, padx=(0, 6))
        self.chat_entry.bind('<Return>', lambda e: self._on_send_chat())
        ttk.Button(bottom, text='发送', command=self._on_send_chat).pack(side='right')

    # ------------------------------------------------------------------
    # Helpers
    # ------------------------------------------------------------------
    def _entry(self, parent, row, label, var, col=0, hint=None, show=None):
        ttk.Label(parent, text=label).grid(row=row, column=col, sticky='w', padx=4, pady=3)
        e = ttk.Entry(parent, textvariable=var, show=show or '', width=30)
        e.grid(row=row, column=col + 1, sticky='we', padx=4, pady=3)
        if hint:
            ttk.Label(parent, text=hint, foreground='#888888').grid(
                row=row, column=col + 2, sticky='w', padx=4)
        return e

    def _append_log(self, text: str, tag: str = 'info') -> None:
        self.log_text.configure(state='normal')
        self.log_text.insert('end', text + '\n', tag)
        self.log_text.see('end')
        self.log_text.configure(state='disabled')

    def _load_config_into_widgets(self) -> None:
        cfg = self.controller.config
        ai = self.controller.ai_config

        self.var_host.set(cfg.server.host)
        self.var_port.set(str(cfg.server.port))
        self.var_version.set(cfg.server.version)
        self.var_brand.set(cfg.server.brand)
        self.var_view.set(cfg.server.viewDistance)
        self.var_auth_type.set(cfg.auth.type)
        self.var_username.set(cfg.auth.username)
        self.ai_enabled.set(ai.enabled)

        self.var_provider.set(ai.provider or 'openai')
        self.var_base_url.set(ai.baseUrl or '')
        self.var_api_key.set(ai.apiKey or '')
        self.var_model.set(ai.model)
        self.var_temperature.set(str(ai.temperature))
        self.var_max_tokens.set(str(ai.maxTokens))
        self.var_timeout.set(str(ai.timeoutSeconds))
        self.var_retry.set(str(ai.retryCount))

        self.system_prompt_widget.delete('1.0', 'end')
        self.system_prompt_widget.insert('1.0', ai.systemPrompt)
        self.var_template.set(ai.templateName if ai.templateName in _build_templates() else '')
        self.var_context.set(ai.contextEnabled)
        self.var_context_len.set(str(ai.contextLength))
        self.var_max_msgs.set(str(ai.maxReplyMessages))
        self.var_max_chars.set(str(ai.maxCharsPerMessage))

        self.var_trigger.set(ai.triggerEnabled)
        self.var_trigger_regex.set(ai.triggerRegex)
        self.var_cooldown.set(str(ai.triggerCooldownSeconds))
        self.var_auto.set(ai.autoReplyEnabled)
        self.var_schedule.set(ai.scheduleEnabled)
        self.var_schedule_interval.set(str(ai.scheduleIntervalSeconds))

        self.var_restrict.set(ai.restrictionEnabled)
        self.var_block_regex.set(';'.join(ai.blockedRegexPatterns))
        self._refresh_blocked_list()

        self.var_image.set(ai.imageGenerationEnabled)
        self.var_image_model.set(ai.imageModel)

        self.var_quiz.set(cfg.quiz.enabled)
        self.var_autoeat.set(cfg.autoeat.enabled)

        self._refresh_auth_label()

    def _refresh_blocked_list(self) -> None:
        self.blocked_list.delete(0, 'end')
        for b in self.controller.ai_config.blockedPlayers:
            mark = '●' if b.get('enabled', True) else '○'
            self.blocked_list.insert('end', f"{mark} {b.get('name', '')}")

    def _refresh_auth_label(self) -> None:
        profile = load_auth_profile()
        if profile:
            self.auth_profile_label.config(
                text=f"已登录: {profile.get('username')}  (UUID {profile.get('uuid', '')[:8]}...)",
                foreground='#7ddb7d')
        else:
            self.auth_profile_label.config(
                text='未找到 auth_profile.json，请点「LittleSkin 登录」或使用离线模式。', foreground='#999999')

    # ------------------------------------------------------------------
    # Actions
    # ------------------------------------------------------------------
    def _on_provider_changed(self):
        provider = self.var_provider.get()
        if provider in DEFAULT_BASE_URLS and not self.var_base_url.get().strip():
            self.var_base_url.set(DEFAULT_BASE_URLS[provider])

    def _on_apply_template(self):
        name = self.var_template.get()
        prompt = _build_templates().get(name)
        if prompt:
            self.system_prompt_widget.delete('1.0', 'end')
            self.system_prompt_widget.insert('1.0', prompt)

    def _on_save_template(self):
        name = self.var_new_tpl_name.get().strip()
        if not name:
            messagebox.showwarning('aafm', '请输入模板名称。')
            return
        prompt = self.system_prompt_widget.get('1.0', 'end').strip()
        if not prompt:
            messagebox.showwarning('aafm', '提示词内容为空。')
            return
        PromptTemplateManager.save(name, prompt)
        self.var_new_tpl_name.set('')
        self._refresh_template_combobox()
        self._append_log(f'已保存模板: {name}', 'info')

    def _on_delete_template(self):
        name = self.var_template.get()
        if name in TEMPLATE_BUILTINS:
            messagebox.showwarning('aafm', '内置模板不可删除。')
            return
        PromptTemplateManager.delete(name)
        self._refresh_template_combobox()
        self._append_log(f'已删除模板: {name}', 'info')

    def _refresh_template_combobox(self):
        tpls = list(_build_templates().keys())
        self.template_combo['values'] = tpls
        if self.var_template.get() not in tpls:
            self.var_template.set(tpls[0] if tpls else '')

    def _gather_config(self) -> None:
        cfg = self.controller.config
        ai = self.controller.ai_config

        cfg.server.host = self.var_host.get().strip() or 'localhost'
        try:
            cfg.server.port = int(self.var_port.get().strip())
        except ValueError:
            cfg.server.port = 25565
        cfg.server.version = self.var_version.get().strip()
        cfg.server.brand = self.var_brand.get().strip() or 'aafm-python'
        cfg.server.viewDistance = self.var_view.get().strip() or 'tiny'

        cfg.auth.type = self.var_auth_type.get()
        cfg.auth.username = self.var_username.get().strip() or 'AI_Bot'

        ai.provider = self.var_provider.get().strip() or 'openai'
        ai.baseUrl = self.var_base_url.get().strip()
        ai.apiKey = self.var_api_key.get()
        ai.model = self.var_model.get().strip() or 'gpt-4o-mini'
        ai.temperature = self._parse_float(self.var_temperature.get(), 0.7)
        ai.maxTokens = self._parse_int(self.var_max_tokens.get(), 1024)
        ai.timeoutSeconds = self._parse_int(self.var_timeout.get(), 30)
        ai.retryCount = self._parse_int(self.var_retry.get(), 1)

        ai.systemPrompt = self.system_prompt_widget.get('1.0', 'end').strip()
        ai.templateName = self.var_template.get()
        ai.contextEnabled = self.var_context.get()
        ai.contextLength = self._parse_int(self.var_context_len.get(), 20)
        ai.maxReplyMessages = self._parse_int(self.var_max_msgs.get(), 3)
        ai.maxCharsPerMessage = self._parse_int(self.var_max_chars.get(), 100)

        ai.triggerEnabled = self.var_trigger.get()
        ai.triggerRegex = self.var_trigger_regex.get().strip()
        ai.triggerCooldownSeconds = self._parse_int(self.var_cooldown.get(), 10)
        ai.autoReplyEnabled = self.var_auto.get()
        ai.scheduleEnabled = self.var_schedule.get()
        ai.scheduleIntervalSeconds = self._parse_int(self.var_schedule_interval.get(), 30)
        if ai.autoReplyEnabled:
            ai.triggerEnabled = False
            ai.scheduleEnabled = False

        ai.restrictionEnabled = self.var_restrict.get()
        raw_regex = self.var_block_regex.get().strip()
        ai.blockedRegexPatterns = [x.strip() for x in raw_regex.split(';') if x.strip()]

        ai.imageGenerationEnabled = self.var_image.get()
        ai.imageModel = self.var_image_model.get().strip() or 'gpt-image-1'

        cfg.quiz.enabled = self.var_quiz.get()
        cfg.autoeat.enabled = self.var_autoeat.get()

        cfg.save()
        ai.save()

    def _on_save(self):
        self._gather_config()
        self.controller.ai_config = self.controller.ai_config.__class__.load()
        self._append_log('设置已保存。', 'info')
        messagebox.showinfo('aafm AI 玩家', '设置已保存。')

    def _on_connect(self):
        self._gather_config()
        self.controller.connect()
        self._set_connected_state(True)
        self._append_log('正在连接...', 'info')

    def _on_disconnect(self):
        self.controller.disconnect()
        self._set_connected_state(False)
        self._append_log('已断开连接。', 'info')

    def _set_connected_state(self, connected: bool):
        if connected:
            self.connect_btn.config(state='disabled')
            self.disconnect_btn.config(state='normal')
        else:
            self.connect_btn.config(state='normal')
            self.disconnect_btn.config(state='disabled')

    def _on_ai_enabled(self):
        self._gather_config()
        self.controller.set_ai_enabled(self.ai_enabled.get())

    def _on_clear_context(self):
        self.controller.clear_ai_context()
        self._append_log('上下文已清空。', 'info')

    def _on_send_chat(self):
        text = self.chat_var.get().strip()
        if not text:
            return
        self.controller.send_chat(text)
        self.chat_var.set('')
        self._append_log(f'» {text}', 'sent')

    def _on_add_blocked(self):
        name = self.var_new_block.get().strip()
        if not name:
            return
        self.controller.add_blocked_player(name)
        self.var_new_block.set('')
        self._refresh_blocked_list()

    def _on_remove_blocked(self):
        sel = self.blocked_list.curselection()
        if not sel:
            return
        idx = sel[0]
        players = self.controller.ai_config.blockedPlayers
        if 0 <= idx < len(players):
            players.pop(idx)
            self.controller.ai_config.save()
            if self.controller.ai_player:
                self.controller.ai_player.config = self.controller.ai_config
                self.controller.ai_player._compile_patterns()
            self._refresh_blocked_list()
            self._append_log('已删除黑名单玩家。', 'info')

    def _on_extra(self, which, enabled):
        self._gather_config()
        self.controller.set_extra_enabled(which, enabled)
        self._append_log(f'{which} {"已启用" if enabled else "已禁用"}。', 'info')

    def _on_littleskin_login(self):
        self._gather_config()
        try:
            if os.name == 'nt':
                kwargs = {'creationflags': subprocess.CREATE_NEW_CONSOLE}
            else:
                kwargs = {}
            subprocess.Popen(['node', '3rdparty_auth.js'], cwd=BASE_DIR, **kwargs)
        except Exception as e:  # noqa: BLE001
            messagebox.showerror('aafm AI 玩家', f'无法启动登录: {e}')
            return
        self._append_log('已启动 LittleSkin 登录窗口，请在弹出的控制台中完成登录，然后重启本程序读取 auth_profile.json。', 'info')

    # ------------------------------------------------------------------
    # Polling
    # ------------------------------------------------------------------
    def _poll_ui(self):
        for ev in self.controller.drain_ui():
            self._handle_ui_event(ev)
        self.root.after(100, self._poll_ui)

    def _handle_ui_event(self, ev):
        kind = ev.get('kind')
        if kind == 'status':
            self.status_label.config(text=ev.get('text', ''), foreground='#4fc3f7')
        elif kind == 'log':
            self._append_log(ev.get('message', ''), self._log_tag(ev.get('level', 'info')))
        elif kind == 'chat':
            prefix = '[私聊] ' if ev.get('private') else ''
            tag = 'private' if ev.get('private') else 'chat'
            self._append_log(f'{prefix}{ev.get("player", "?")}: {ev.get("content", "")}', tag)
        elif kind == 'actionBar':
            self._append_log(f'[ActionBar] {ev.get("text", "")}', 'info')
        elif kind == 'health':
            self._append_log(f'生命值 {ev.get("health")}  饥饿 {ev.get("food")}', 'info')
        elif kind == 'spawn':
            self._set_connected_state(True)
        elif kind == 'disconnected':
            self._set_connected_state(False)
            self.status_label.config(text='已断开', foreground='#999999')

    @staticmethod
    def _log_tag(level):
        return {'error': 'error', 'ai': 'ai', 'quiz': 'quiz', 'sent': 'sent'}.get(level, 'info')

    @staticmethod
    def _parse_int(s, default):
        try:
            return int(str(s).strip())
        except (TypeError, ValueError):
            return default

    @staticmethod
    def _parse_float(s, default):
        try:
            return float(str(s).strip())
        except (TypeError, ValueError):
            return default

    # ------------------------------------------------------------------
    # Close
    # ------------------------------------------------------------------
    def _on_close(self):
        try:
            self._gather_config()
            self.controller.disconnect()
        finally:
            self.root.destroy()


def main() -> None:
    root = tk.Tk()
    AiPlayerGUI(root)
    root.mainloop()


if __name__ == '__main__':
    main()
