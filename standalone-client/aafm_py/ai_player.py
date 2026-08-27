"""AI Player chat responder (port of aafm's com.mcai.chat.ChatResponder).

Auto-replies to in-game chat using an LLM. Supports trigger replies, auto
reply, scheduled replies, context window, message restriction, blocked
players and optional image generation.
"""
from __future__ import annotations

import re
import time
from collections import deque
from typing import Callable, Deque, Dict, List, Optional

from . import llm
from .config import AiPlayerConfig

MAX_LOG_LINES = 200

_LOG_CALLBACK = Callable[[str], None]


def _now_ms() -> int:
    return int(time.time() * 1000)


class AiPlayer:
    def __init__(self,
                 config: AiPlayerConfig,
                 on_send_chat: Callable[[str], None],
                 log: _LOG_CALLBACK = print,
                 error: _LOG_CALLBACK = None):
        self.config = config
        self.on_send_chat = on_send_chat
        self._log_cb = log
        self._error_cb = error if error is not None else log

        self._trigger_provider = None
        self._schedule_provider = None
        self._image_provider = None
        self._bot_username: Optional[str] = None

        self._trigger_reply_in_progress = False
        self._schedule_reply_in_progress = False
        self._scheduled_request_queued = False
        self._image_generating = False
        self._stopped = False

        self._last_player_message: Optional[str] = None
        self._last_player_message_time = 0
        self._last_handled_user_message_time = 0
        self._next_scheduled_reply_time = 0
        self._next_trigger_reply_time = 0
        self._next_image_cooldown_time = 0

        self._recent_ai_messages: Dict[str, int] = {}
        self._recent_player_messages: Dict[str, int] = {}
        self._context: Deque[Dict[str, str]] = deque()
        self._activity_log: Deque[str] = deque()

        self._trigger_pattern: Optional[re.Pattern] = None
        self._image_trigger_pattern: Optional[re.Pattern] = None
        self._blocked_patterns: List[re.Pattern] = []

        self._chunk_delay_ms = 600  # spacing between reply chunks

        self._build_providers()
        self._compile_patterns()
        self._next_scheduled_reply_time = _now_ms() + self.config.scheduleIntervalSeconds * 1000
        self._next_trigger_reply_time = _now_ms()
        self._next_image_cooldown_time = _now_ms()

    # ------------------------------------------------------------------
    # Lifecycle / reload
    # ------------------------------------------------------------------
    def _build_providers(self) -> None:
        cfg = self.config
        try:
            self._trigger_provider = llm.create_provider(cfg, cfg.apiKey)
        except Exception as e:  # noqa: BLE001
            self._error('创建聊天 AI 提供方失败: ' + str(e))
            self._trigger_provider = None
        try:
            self._schedule_provider = llm.create_provider(cfg, cfg.apiKey)
        except Exception as e:  # noqa: BLE001
            self._error('创建定时 AI 提供方失败: ' + str(e))
            self._schedule_provider = None
        if cfg.imageGenerationEnabled:
            self._image_provider = llm.create_image_provider(cfg)
        else:
            self._image_provider = None

    def _compile_patterns(self) -> None:
        cfg = self.config
        try:
            self._trigger_pattern = re.compile(cfg.triggerRegex)
        except re.error as e:
            self._trigger_pattern = None
            self._error(f"无效的触发正则: '{cfg.triggerRegex}' -> {e}")
        try:
            self._image_trigger_pattern = re.compile(cfg.imageTriggerRegex)
        except re.error as e:
            self._image_trigger_pattern = None
            self._error(f"无效的文生图触发正则: '{cfg.imageTriggerRegex}' -> {e}")
        patterns = []
        for pat in cfg.blockedRegexPatterns or []:
            try:
                patterns.append(re.compile(pat))
            except re.error as e:
                self._error(f"无效的拦截正则: '{pat}' -> {e}")
        self._blocked_patterns = patterns

    def reload(self, new_config: AiPlayerConfig) -> None:
        self.config = new_config
        self._build_providers()
        self._compile_patterns()
        self._context.clear()
        self._last_player_message = None
        self._last_player_message_time = 0
        self._last_handled_user_message_time = 0
        self._next_scheduled_reply_time = _now_ms() + new_config.scheduleIntervalSeconds * 1000
        self._next_trigger_reply_time = _now_ms()
        self._next_image_cooldown_time = _now_ms()
        self._recent_ai_messages.clear()
        self._recent_player_messages.clear()
        self._trigger_reply_in_progress = False
        self._schedule_reply_in_progress = False
        self._image_generating = False
        self._stopped = False

    def stop_now(self) -> None:
        self._stopped = True
        self._trigger_reply_in_progress = False
        self._schedule_reply_in_progress = False
        self._image_generating = False
        self._scheduled_request_queued = False

    def clear_context(self) -> None:
        self._context.clear()

    def get_activity_log(self) -> List[str]:
        return list(self._activity_log)

    # ------------------------------------------------------------------
    # Logging
    # ------------------------------------------------------------------
    def _log(self, msg: str) -> None:
        self._log_cb(msg)

    def _error(self, msg: str) -> None:
        self._error_cb(msg)

    def _log_activity(self, line: str) -> None:
        self._activity_log.append(line)
        while len(self._activity_log) > MAX_LOG_LINES:
            self._activity_log.popleft()

    # ------------------------------------------------------------------
    # Message processing
    # ------------------------------------------------------------------
    def on_player_message(self, player_name: Optional[str], text: str) -> None:
        cfg = self.config
        if text is None or not text.strip():
            return

        original = text
        if player_name is None:
            player_name = self._extract_player_name(original)

        if self._is_self(player_name):
            if cfg.debugLog:
                self._log(f"忽略自己的消息: '{player_name}'")
            return

        if self._is_player_blocked(player_name):
            if cfg.debugLog:
                self._log(f"忽略被屏蔽玩家的消息: '{player_name}'")
            return

        if player_name:
            text = self._strip_player_prefix(text, player_name)

        dedupe_key = (player_name or 'null') + '|' + text
        now = _now_ms()
        last_time = self._recent_player_messages.get(dedupe_key)
        if last_time is not None and now - last_time < 2000:
            if cfg.debugLog:
                self._log(f"忽略重复消息 (player='{player_name}', text='{text}')")
            return
        self._recent_player_messages[dedupe_key] = now

        if cfg.debugLog and original != text:
            self._log(f"剥离聊天前缀: '{original}' -> '{text}'")

        self._log_activity((player_name or '?') + ' > ' + text)

        # Image generation trigger
        if cfg.enabled and cfg.imageGenerationEnabled and self._matches_image_trigger(text):
            if now >= self._next_image_cooldown_time:
                self._next_image_cooldown_time = now + cfg.imageCooldownSeconds * 1000
                prompt = self._extract_image_prompt(text)
                if prompt is not None and prompt.strip():
                    self._last_handled_user_message_time = now
                    self._last_player_message = text
                    self._last_player_message_time = now
                    self._log('文生图已触发，提示词: ' + prompt)
                    self.generate_image(prompt)
            else:
                remaining = self._next_image_cooldown_time - now
                self._log(f'文生图冷却中 (剩余 {remaining} ms)。')
                self._last_handled_user_message_time = now
            return

        self._last_player_message = text
        self._last_player_message_time = now

        if not cfg.enabled:
            return

        if cfg.contextEnabled and cfg.contextLength > 0:
            self._add_to_context({'role': 'user', 'content': text})

        matches = cfg.triggerEnabled and self._matches_trigger(text)
        auto_reply = cfg.autoReplyEnabled
        should_reply = auto_reply or matches
        cooldown_passed = self._next_trigger_reply_time <= now

        if cfg.debugLog:
            self._log(f"消息='{text}', autoReply={auto_reply}, regexMatch={matches}, "
                      f"cooldownPassed={cooldown_passed}")

        if should_reply and cooldown_passed:
            self._next_trigger_reply_time = now + cfg.triggerCooldownSeconds * 1000
            self._last_handled_user_message_time = self._last_player_message_time
            self._log(f'回复已启动，冷却 {cfg.triggerCooldownSeconds} 秒。')
            self._start_ai_reply(text, self._last_player_message_time, True)
        elif should_reply:
            remaining = self._next_trigger_reply_time - now
            self._log(f'命中回复但冷却中 (剩余 {remaining} ms)。')
            self._last_handled_user_message_time = now

    def tick(self) -> None:
        """Called periodically (>=1s) to handle scheduled replies."""
        cfg = self.config
        if not cfg.enabled or not cfg.scheduleEnabled:
            return
        if (self._schedule_reply_in_progress or self._scheduled_request_queued
                or self._image_generating):
            return

        self._cleanup_recent_messages()
        now = _now_ms()
        if now < self._next_scheduled_reply_time:
            return

        msg = self._last_player_message
        if msg is None or not msg.strip():
            return
        if self._last_player_message_time <= self._last_handled_user_message_time:
            return
        if cfg.triggerEnabled and self._last_player_message_time == self._last_handled_user_message_time:
            return
        # Strict trigger gating: with trigger mode on, only messages that match the
        # trigger regex may produce a (scheduled) reply. Others stay silent.
        if cfg.triggerEnabled and not self._matches_trigger(msg):
            self._last_handled_user_message_time = self._last_player_message_time
            return

        if not self._scheduled_request_queued:
            self._scheduled_request_queued = True
            try:
                self._last_handled_user_message_time = self._last_player_message_time
                latest = self._last_player_message
                if latest and latest.strip():
                    self._log('自动回复已触发。')
                    self._start_ai_reply(latest, self._last_player_message_time, False)
            finally:
                self._scheduled_request_queued = False

    # ------------------------------------------------------------------
    # AI reply
    # ------------------------------------------------------------------
    def _start_ai_reply(self, user_message: str, user_message_time: int, is_trigger: bool) -> None:
        cfg = self.config
        flag_key = 'trigger' if is_trigger else 'schedule'
        flag = self._trigger_reply_in_progress if is_trigger else self._schedule_reply_in_progress
        if flag:
            self._log(('触发' if is_trigger else '定时') + '回复已在进行中，跳过。')
            return
        if is_trigger:
            self._trigger_reply_in_progress = True
        else:
            self._schedule_reply_in_progress = True
        try:
            provider = self._trigger_provider if is_trigger else self._schedule_provider
            if provider is None:
                self._error(('触发' if is_trigger else '定时') + 'AI 提供方为 null，请检查 AI API 设置。')
                return
            if not cfg.enabled:
                return
            if user_message is None or not user_message.strip():
                return
            messages = self._build_context(user_message)
            self._log(f'请求 AI (provider={cfg.provider}, model={cfg.model}, '
                      f'type={("trigger" if is_trigger else "schedule")})...')
            reply = provider.send(cfg.systemPrompt, messages)
            if reply is None or not reply.strip():
                return
            self._handle_ai_reply(reply)
        except Exception as e:  # noqa: BLE001
            self._error('AI 请求失败: ' + str(e))
        finally:
            if is_trigger:
                self._trigger_reply_in_progress = False
            else:
                self._schedule_reply_in_progress = False

    def _handle_ai_reply(self, reply: str) -> None:
        cfg = self.config
        if self._stopped or not cfg.enabled:
            return
        chunks = self._split_reply(reply)
        if not chunks:
            return
        sent = 0
        for i, chunk in enumerate(chunks):
            if self._stopped or not cfg.enabled:
                break
            if self._is_blocked(chunk):
                self._log('跳过被拦截的 AI 消息: ' + chunk)
                continue
            self.on_send_chat(chunk)
            self._recent_ai_messages[chunk] = _now_ms()
            self._log_activity('AI > ' + chunk)
            sent += 1
            if i < len(chunks) - 1 and self._chunk_delay_ms > 0:
                time.sleep(self._chunk_delay_ms / 1000.0)
        if sent > 0 and cfg.contextEnabled and cfg.contextLength > 0:
            self._add_to_context({'role': 'assistant', 'content': reply})
        self._next_scheduled_reply_time = _now_ms() + cfg.scheduleIntervalSeconds * 1000
        self._log(f'已发送 {sent} 条 AI 消息。')

    def _build_context(self, current_user_message: str) -> List[Dict[str, str]]:
        cfg = self.config
        if cfg.contextEnabled and cfg.contextLength > 0:
            messages = list(self._context)
            if not messages:
                messages.append({'role': 'user', 'content': current_user_message})
            while len(messages) > cfg.contextLength:
                messages.pop(0)
            return messages
        return [{'role': 'user', 'content': current_user_message}]

    def _add_to_context(self, msg: Dict[str, str]) -> None:
        cfg = self.config
        if cfg.contextLength <= 0:
            return
        self._context.append(msg)
        while len(self._context) > cfg.contextLength:
            self._context.popleft()

    # ------------------------------------------------------------------
    # Matching / filtering helpers
    # ------------------------------------------------------------------
    def _matches_trigger(self, text: str) -> bool:
        if self._trigger_pattern is None:
            self._log('触发正则表达式为 null。')
            return False
        return bool(self._trigger_pattern.search(text))

    def _matches_image_trigger(self, text: str) -> bool:
        if self._image_trigger_pattern is None:
            return False
        return bool(self._image_trigger_pattern.search(text))

    def _extract_image_prompt(self, text: str) -> Optional[str]:
        if self._image_trigger_pattern is None:
            return None
        m = self._image_trigger_pattern.search(text)
        if m:
            if m.groups():
                group = m.group(1)
                if group is not None and group.strip():
                    return group.strip()
            stripped = text[m.end():].strip()
            return stripped if stripped else None
        return None

    def _is_blocked(self, text: str) -> bool:
        if not self.config.restrictionEnabled:
            return False
        for pattern in self._blocked_patterns:
            if pattern.search(text):
                return True
        return False

    def set_bot_username(self, name: Optional[str]) -> None:
        self._bot_username = name

    def _is_self(self, player_name: Optional[str]) -> bool:
        if not player_name or not self._bot_username:
            return False
        return player_name.lower() == self._bot_username.lower()

    def _is_player_blocked(self, player_name: Optional[str]) -> bool:
        if not player_name or not self.config.blockedPlayers:
            return False
        for b in self.config.blockedPlayers:
            if b.get('enabled', True) and b.get('name') and \
                    str(b['name']).lower() == player_name.lower():
                return True
        return False

    def _extract_player_name(self, raw_text: str) -> Optional[str]:
        m = re.match(r'^\s*\|?\s*\[[^\]]+\]\s*([^\s»>]+)\s*[»>:]', raw_text)
        if m:
            return m.group(1).strip()
        m2 = re.match(r'^\s*[<\[\s]\s*([^\s\]><]+)\s*[>\]]\s*', raw_text)
        if m2:
            return m2.group(1).strip()
        return None

    def _strip_player_prefix(self, text: str, player_name: str) -> str:
        quoted = re.escape(player_name)
        prefix_pattern = re.compile(
            r'^<[^<>]*' + quoted + r'[^<>]*>\s*|^\[[^\[\]]*' + quoted + r'[^\[\]]*\]\s*')
        m = prefix_pattern.match(text)
        if m:
            stripped = text[m.end():].strip()
            if stripped:
                return stripped
        generic = re.compile(r'^\s*(?:[<\[]\s*[^\]<>]{1,32}\s*[>\]]|[|][^»>]{1,64}[»>])\s*:?\s*')
        m2 = generic.match(text)
        if m2:
            stripped = text[m2.end():].strip()
            if stripped:
                return stripped
        return text

    def _split_reply(self, reply: str) -> List[str]:
        result: List[str] = []
        if not reply:
            return result
        normalized = reply.replace('\r\n', '\n').strip()
        if not normalized:
            return result
        max_chars = max(1, self.config.maxCharsPerMessage)
        max_messages = max(1, self.config.maxReplyMessages)
        for paragraph in normalized.split('\n'):
            paragraph = paragraph.strip()
            if not paragraph:
                continue
            while len(paragraph) > max_chars and len(result) < max_messages:
                cut = max_chars
                space = paragraph.rfind(' ', 0, max_chars)
                if space > 0:
                    cut = space
                chunk = paragraph[:cut].strip()
                if not chunk:
                    break
                result.append(chunk)
                paragraph = paragraph[cut:].strip()
            if paragraph and len(result) < max_messages:
                result.append(paragraph)
            if len(result) >= max_messages:
                break
        return result

    def _cleanup_recent_messages(self) -> None:
        now = _now_ms()
        self._recent_ai_messages = {k: v for k, v in self._recent_ai_messages.items()
                                    if now - v <= 30000}
        self._recent_player_messages = {k: v for k, v in self._recent_player_messages.items()
                                        if now - v <= 5000}

    # ------------------------------------------------------------------
    # Image generation
    # ------------------------------------------------------------------
    def generate_image(self, prompt: str) -> None:
        if self._image_generating:
            self._log('文生图已在进行中。')
            return
        self._image_generating = True
        try:
            url = self._request_image_generation(prompt)
            if url:
                self.on_send_chat(url)
                self._log_activity('AI > [图] ' + url)
                self._log('图像生成完成，已发送 URL。')
            else:
                self._error('图像生成失败，请查看日志。')
        except Exception as e:  # noqa: BLE001
            self._error('文生图失败: ' + str(e))
        finally:
            self._image_generating = False

    def _request_image_generation(self, prompt: str) -> Optional[str]:
        cfg = self.config
        provider = self._image_provider
        if provider is None:
            provider = llm.create_image_provider(cfg)
            self._image_provider = provider
        if provider is None:
            self._error('文生图 API 未正确配置。')
            return None
        return provider.generate(prompt, cfg.imageModel, cfg.imageSize,
                                 getattr(cfg, 'imageRatio', '1:1') or '1:1')
