"""Bridge to the headless mineflayer bot engine subprocess (bot_engine.js).

Communicates over stdio with one JSON object per line:
  Python -> Node: {"type": "chat" | "setAutoeat" | "setQuiz" | "ping" | "quit", ...}
  Node -> Python: {"event": ...}
"""
from __future__ import annotations

import json
import os
import subprocess
import threading
from typing import Callable, Dict, Any, Optional

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RUNTIME_CONFIG_FILE = os.path.join(BASE_DIR, 'runtime_config.json')

_EVENT_CALLBACK = Callable[[Dict[str, Any]], None]


class BotEngine:
    def __init__(self, node_bin: str = 'node', cwd: Optional[str] = None,
                 on_event: Optional[_EVENT_CALLBACK] = None):
        self.node_bin = node_bin
        self.cwd = cwd or BASE_DIR
        self.on_event = on_event
        self.proc: Optional[subprocess.Popen] = None
        self._write_lock = threading.Lock()
        self._running = False

    @property
    def running(self) -> bool:
        return self._running and self.proc is not None and self.proc.poll() is None

    def start(self, runtime_config: Dict[str, Any]) -> None:
        if self.proc is not None and self.proc.poll() is None:
            self.stop()

        with open(RUNTIME_CONFIG_FILE, 'w', encoding='utf-8') as f:
            json.dump(runtime_config, f, ensure_ascii=False, indent=2)

        kwargs = {}
        if os.name == 'nt':
            kwargs['creationflags'] = subprocess.CREATE_NO_WINDOW
        self.proc = subprocess.Popen(
            [self.node_bin, 'bot_engine.js', RUNTIME_CONFIG_FILE],
            cwd=self.cwd,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            encoding='utf-8',
            errors='replace',
            bufsize=1,
            **kwargs,
        )
        self._running = True
        threading.Thread(target=self._read_stdout, daemon=True).start()
        threading.Thread(target=self._read_stderr, daemon=True).start()

    def _read_stdout(self) -> None:
        try:
            for line in self.proc.stdout:
                if not line:
                    continue
                line = line.strip()
                if not line:
                    continue
                try:
                    ev = json.loads(line)
                except json.JSONDecodeError:
                    ev = {'event': 'log', 'level': 'info', 'message': line}
                if self.on_event:
                    try:
                        self.on_event(ev)
                    except Exception:  # noqa: BLE001
                        pass
        except Exception:  # noqa: BLE001
            pass
        finally:
            self._running = False

    def _read_stderr(self) -> None:
        try:
            for line in self.proc.stderr:
                if not line:
                    continue
                line = line.strip()
                if not line:
                    continue
                if self.on_event:
                    try:
                        self.on_event({'event': 'log', 'level': 'error',
                                       'message': '[engine] ' + line})
                    except Exception:  # noqa: BLE001
                        pass
        except Exception:  # noqa: BLE001
            pass

    def send(self, cmd: Dict[str, Any]) -> bool:
        if not self.running or self.proc is None or self.proc.stdin is None:
            return False
        try:
            with self._write_lock:
                self.proc.stdin.write(json.dumps(cmd, ensure_ascii=False) + '\n')
                self.proc.stdin.flush()
            return True
        except Exception:  # noqa: BLE001
            return False

    def say(self, text: str) -> bool:
        return self.send({'type': 'chat', 'text': text})

    def set_autoeat(self, enabled: bool) -> bool:
        return self.send({'type': 'setAutoeat', 'enabled': bool(enabled)})

    def set_quiz(self, enabled: bool) -> bool:
        return self.send({'type': 'setQuiz', 'enabled': bool(enabled)})

    def ping(self) -> bool:
        return self.send({'type': 'ping'})

    def stop(self) -> None:
        try:
            self.send({'type': 'quit'})
        except Exception:  # noqa: BLE001
            pass
        if self.proc is not None:
            try:
                self.proc.wait(timeout=3)
            except Exception:  # noqa: BLE001
                try:
                    self.proc.terminate()
                except Exception:  # noqa: BLE001
                    pass
        self._running = False
