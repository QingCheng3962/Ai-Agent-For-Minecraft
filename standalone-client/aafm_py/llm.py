"""OpenAI-compatible chat client (port of aafm's ChatProvider).

Supports OpenAI-compatible endpoints (OpenAI, DeepSeek, Ollama, ...) and the
Anthropic Messages API, with retry on failure.
"""
from __future__ import annotations

import json
from typing import List, Optional, Tuple

import httpx

from .config import DEFAULT_BASE_URLS

DEFAULT_TIMEOUT = 30.0


class LLMError(Exception):
    pass


def normalize_base_url(base_url: str) -> str:
    s = (base_url or '').strip()
    while s.endswith('/'):
        s = s[:-1]
    return s + '/'


def _message_pairs(messages: List[dict]) -> List[Tuple[str, str]]:
    """Normalize role/content pairs, defaulting missing content to ''. """
    out = []
    for m in messages:
        role = m.get('role') or 'user'
        content = m.get('content')
        if content is None:
            content = ''
        out.append((role, str(content)))
    return out


class _BaseProvider:
    def __init__(self, cfg, api_key: str):
        self.cfg = cfg
        self.api_key = api_key or cfg.apiKey or ''
        self.model = cfg.model or 'gpt-4o-mini'
        self.temperature = cfg.temperature
        self.max_tokens = cfg.maxTokens
        self.timeout = max(cfg.timeoutSeconds or DEFAULT_TIMEOUT, 5)
        self.retry_count = cfg.retryCount or 0
        self._client = httpx.Client(timeout=self.timeout)

    def _post(self, endpoint: str, headers: dict, body: dict) -> str:
        json_str = json.dumps(body, ensure_ascii=False)
        last_err: Optional[Exception] = None
        for attempt in range(self.retry_count + 1):
            try:
                resp = self._client.post(endpoint, headers=headers, content=json_str)
                if 200 <= resp.status_code < 300:
                    return resp.text
                last_err = LLMError(f'HTTP {resp.status_code}: {resp.text[:500]}')
            except Exception as e:  # noqa: BLE001
                last_err = e
        raise last_err if last_err else LLMError('unknown failure')

    def close(self) -> None:
        try:
            self._client.close()
        except Exception:
            pass


class OpenAIProvider(_BaseProvider):
    def __init__(self, cfg, base_url: str, api_key: str):
        super().__init__(cfg, api_key)
        self.endpoint = normalize_base_url(base_url) + 'chat/completions'

    def send(self, system_prompt: str, messages: List[dict]) -> str:
        body: dict = {
            'model': self.model,
            'temperature': self.temperature,
            'max_tokens': self.max_tokens,
            'messages': [],
        }
        if system_prompt:
            body['messages'].append({'role': 'system', 'content': system_prompt})
        for role, content in _message_pairs(messages):
            body['messages'].append({'role': role, 'content': content})
        headers = {
            'Content-Type': 'application/json',
            'Authorization': 'Bearer ' + self.api_key,
        }
        text = self._post(self.endpoint, headers, body)
        try:
            data = json.loads(text)
            choices = data.get('choices') or []
            if not choices:
                raise LLMError('No choices in response: ' + text[:300])
            message = (choices[0].get('message') or {})
            content = message.get('content')
            return content if content is not None else ''
        except json.JSONDecodeError:
            raise LLMError('Invalid JSON response: ' + text[:300])


class AnthropicProvider(_BaseProvider):
    def __init__(self, cfg, base_url: str, api_key: str):
        super().__init__(cfg, api_key)
        self.endpoint = normalize_base_url(base_url) + 'v1/messages'

    def send(self, system_prompt: str, messages: List[dict]) -> str:
        body: dict = {
            'model': self.model,
            'max_tokens': self.max_tokens,
            'temperature': self.temperature,
            'messages': [],
        }
        if system_prompt:
            body['system'] = system_prompt
        for role, content in _message_pairs(messages):
            body['messages'].append({
                'role': 'assistant' if role == 'assistant' else 'user',
                'content': content,
            })
        headers = {
            'Content-Type': 'application/json',
            'x-api-key': self.api_key,
            'anthropic-version': '2023-06-01',
        }
        text = self._post(self.endpoint, headers, body)
        try:
            data = json.loads(text)
            content = data.get('content') or []
            if not content:
                raise LLMError('No content in Anthropic response: ' + text[:300])
            first = content[0]
            value = first.get('text')
            return value if value is not None else ''
        except json.JSONDecodeError:
            raise LLMError('Invalid JSON response: ' + text[:300])


class ImageProvider(_BaseProvider):
    """OpenAI-compatible image generation (images/generations)."""

    def __init__(self, cfg, base_url: str, api_key: str):
        super().__init__(cfg, api_key)
        self.timeout = max(getattr(cfg, 'imageTimeoutSeconds', 120), 30)
        self._client = httpx.Client(timeout=self.timeout)
        self.endpoint = base_url
        base = base_url
        if not base.endswith('images/generations'):
            self.endpoint = normalize_base_url(base) + 'images/generations'
        self.retry_count = getattr(cfg, 'imageRetryCount', 0)

    def generate(self, prompt: str, model: str, size: str, ratio: str) -> str:
        body = {
            'model': model,
            'prompt': prompt,
            'size': size,
            'ratio': ratio,
            'extra_body': {'response_format': 'url'},
        }
        headers = {
            'Content-Type': 'application/json',
            'Authorization': 'Bearer ' + self.api_key,
        }
        text = self._post(self.endpoint, headers, body)
        try:
            data = json.loads(text)
            items = data.get('data') or []
            if not items:
                raise LLMError('No data in image response')
            return items[0].get('url') or ''
        except json.JSONDecodeError:
            raise LLMError('Invalid JSON image response')


def create_provider(cfg, api_key_override: Optional[str] = None) -> _BaseProvider:
    provider = (cfg.provider or 'openai').lower()
    base_url = (cfg.baseUrl or '').strip()
    if not base_url:
        base_url = DEFAULT_BASE_URLS.get(provider, '')
    if not base_url:
        raise LLMError('聊天 AI 需要 baseUrl，请在设置中填写 provider 或 baseUrl。')
    api_key = api_key_override if api_key_override is not None else cfg.apiKey
    if provider == 'anthropic':
        return AnthropicProvider(cfg, base_url, api_key or '')
    return OpenAIProvider(cfg, base_url, api_key or '')


def create_image_provider(cfg, api_key: Optional[str] = None) -> Optional[ImageProvider]:
    base_url = (cfg.baseUrl or '').strip()
    if not base_url:
        base_url = DEFAULT_BASE_URLS.get((cfg.provider or 'openai').lower(), '')
    if not base_url:
        return None
    key = api_key if api_key is not None else cfg.apiKey
    try:
        return ImageProvider(cfg, base_url, key or '')
    except Exception:
        return None
