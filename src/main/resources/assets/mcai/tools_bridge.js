'use strict';

// aafm Node.js tool bridge
// Fast file read/write/edit/search and command execution for the agent.
// Communicates over stdio with one JSON object per line:
//   Request : {"id": <number>, "op": <string>, "params": {...}}
//   Response: {"id": <number>, "ok": <bool>, <result fields...> | "error": <string>}
// Usage: node tools_bridge.js <workspace-directory>
//
// ops:
//   run_command {command, timeout?}
//   write_file  {path, content}
//   read_file   {path}
//   edit_file   {path, find, replace, replaceAll?}
//   list_dir    {path}
//   grep        {path, pattern}
//   find_files  {path, glob}
//   ping

const fs = require('fs');
const fsp = require('fs/promises');
const path = require('path');
const readline = require('readline');
const { exec } = require('child_process');

const WORKSPACE = path.resolve(process.argv[2] || '.');
const MAX_OUTPUT = 8000;
const MAX_FILE_READ = 20000;
const MAX_FILE_SIZE = 512 * 1024;
const MAX_RESULTS = 200;
const MAX_GREP_MATCHES = 60;
const MAX_DEPTH = 12;

function truncate(s, max) {
  if (!s) return '';
  return s.length <= max ? s : s.substring(0, max) + '...(截断)';
}

function resolvePath(p) {
  if (!p) return null;
  if (path.isAbsolute(p)) return path.normalize(p);
  return path.normalize(path.join(WORKSPACE, p));
}

function walkFiles(dir, maxDepth, depth, out) {
  if (depth > maxDepth) return out;
  return fsp.readdir(dir, { withFileTypes: true }).then((entries) => {
    return entries.reduce((chain, e) => {
      const full = path.join(dir, e.name);
      if (e.isDirectory()) {
        if (e.name === 'node_modules' || e.name === '.git') return chain;
        return chain.then(() => walkFiles(full, maxDepth, depth + 1, out));
      } else if (e.isFile()) {
        out.push(full);
      }
      return chain;
    }, Promise.resolve()).then(() => out);
  }).catch(() => out);
}

function globToMatcher(glob) {
  let re = '^';
  const g = glob;
  let i = 0;
  while (i < g.length) {
    const c = g[i];
    if (c === '*') {
      if (g[i + 1] === '*') {
        if (g[i + 2] === '/') {
          re += '(?:[^/]*/)*';
          i += 3;
        } else {
          re += '.*';
          i += 2;
        }
      } else {
        re += '[^/]*';
        i++;
      }
    } else if (c === '?') {
      re += '[^/]';
      i++;
    } else if (c === '{') {
      const end = g.indexOf('}', i);
      if (end > i) {
        const inner = g.slice(i + 1, end).split(',')
          .map((x) => globToMatcher(x).source.replace(/^\^|\$$/g, ''));
        re += '(?:' + inner.join('|') + ')';
        i = end + 1;
      } else {
        re += '\\{';
        i++;
      }
    } else {
      re += c.replace(/[.+^${}()|[\]\\]/g, '\\$&');
      i++;
    }
  }
  return new RegExp(re + '$');
}

// ---------------------------------------------------------------------------
// Operations
// ---------------------------------------------------------------------------
function runCommand(params) {
  const command = String(params.command || '').trim();
  if (!command) return Promise.resolve({ ok: false, error: '命令为空' });
  const timeoutMs = Math.min(Math.max(parseInt(params.timeout, 10) || 30000, 1000), 120000);
  return new Promise((resolve) => {
    exec('cmd.exe /c ' + command, {
      windowsHide: true,
      timeout: timeoutMs,
      maxBuffer: 16 * 1024 * 1024,
    }, (err, stdout, stderr) => {
      let output = (stdout || '') + (stderr ? '\n[stderr]\n' + stderr : '');
      output = truncate(output, MAX_OUTPUT);
      if (err) {
        if (err.killed || err.signal) {
          resolve({ ok: false, error: '命令执行超时(' + Math.round(timeoutMs / 1000) + '秒)，已强制终止', output });
        } else if (err.code !== undefined && err.code !== null) {
          resolve({ ok: true, exit: err.code, output });
        } else {
          resolve({ ok: false, error: '命令执行失败: ' + err.message, output });
        }
      } else {
        resolve({ ok: true, exit: 0, output });
      }
    });
  });
}

async function writeFile(params) {
  const content = String(params.content === undefined || params.content === null ? '' : params.content);
  const target = resolvePath(params.path);
  if (!target) return { ok: false, error: '非法路径' };
  try {
    await fsp.mkdir(path.dirname(target), { recursive: true });
    await fsp.writeFile(target, content, 'utf8');
    return { ok: true, path: target, bytes: Buffer.byteLength(content, 'utf8') };
  } catch (e) {
    return { ok: false, error: '写入失败: ' + e.message };
  }
}

async function readFile(params) {
  const target = resolvePath(params.path);
  if (!target) return { ok: false, error: '非法路径' };
  let st;
  try {
    st = await fsp.stat(target);
  } catch (e) {
    return { ok: false, error: '文件不存在: ' + params.path };
  }
  if (!st.isFile()) return { ok: false, error: '不是文件: ' + params.path };
  if (st.size > MAX_FILE_SIZE) {
    return { ok: false, error: '文件过大(' + st.size + ' 字节)，拒绝读取' };
  }
  try {
    const buf = await fsp.readFile(target);
    if (buf.includes(0)) return { ok: false, error: '检测到二进制内容，拒绝以文本读取' };
    const content = buf.toString('utf8');
    return {
      ok: true,
      path: target,
      bytes: buf.length,
      lines: content.split('\n').length,
      content: truncate(content, MAX_FILE_READ),
    };
  } catch (e) {
    return { ok: false, error: '读取失败: ' + e.message };
  }
}

async function editFile(params) {
  const find = String(params.find || '');
  if (!find) return { ok: false, error: 'find 参数不能为空' };
  const replace = String(params.replace === undefined || params.replace === null ? '' : params.replace);
  const replaceAll = !!params.replaceAll;
  const target = resolvePath(params.path);
  if (!target) return { ok: false, error: '非法路径' };
  try {
    await fsp.access(target);
  } catch (e) {
    return { ok: false, error: '文件不存在: ' + params.path };
  }
  try {
    const content = await fsp.readFile(target, 'utf8');
    let newContent;
    let count;
    if (replaceAll) {
      const parts = content.split(find);
      count = parts.length - 1;
      if (count === 0) return { ok: false, error: '未在文件中找到要替换的文本，请先 read_file 确认内容后再试' };
      newContent = parts.join(replace);
    } else {
      const idx = content.indexOf(find);
      if (idx < 0) return { ok: false, error: '未在文件中找到要替换的文本，请先 read_file 确认内容后再试' };
      newContent = content.substring(0, idx) + replace + content.substring(idx + find.length);
      count = 1;
    }
    await fsp.writeFile(target, newContent, 'utf8');
    return { ok: true, path: target, replaced: truncate(find, 120), count };
  } catch (e) {
    return { ok: false, error: '编辑失败: ' + e.message };
  }
}

async function listDir(params) {
  const target = resolvePath(params.path);
  if (!target) return { ok: false, error: '非法路径' };
  let st;
  try {
    st = await fsp.stat(target);
  } catch (e) {
    return { ok: false, error: '目录不存在: ' + params.path };
  }
  if (!st.isDirectory()) return { ok: false, error: '不是目录: ' + params.path };
  try {
    const entries = await fsp.readdir(target, { withFileTypes: true });
    entries.sort((a, b) => a.name.toLowerCase().localeCompare(b.name.toLowerCase()));
    const lines = [];
    for (const d of entries) {
      if (lines.length >= MAX_RESULTS) break;
      lines.push((d.isDirectory() ? '[dir]  ' : '[file] ') + d.name);
    }
    return { ok: true, path: target, listing: lines.join('\n') };
  } catch (e) {
    return { ok: false, error: '浏览失败: ' + e.message };
  }
}

async function grep(params) {
  const pattern = String(params.pattern || '');
  if (!pattern) return { ok: false, error: 'pattern 参数不能为空' };
  let regex;
  try {
    regex = new RegExp(pattern);
  } catch (e) {
    return { ok: false, error: '正则表达式错误: ' + e.message };
  }
  const target = resolvePath(params.path);
  if (!target) return { ok: false, error: '非法路径' };
  let st;
  try {
    st = await fsp.stat(target);
  } catch (e) {
    return { ok: false, error: '路径不存在: ' + params.path };
  }
  const matches = [];

  async function grepFile(file, root) {
    try {
      const stf = await fsp.stat(file);
      if (!stf.isFile() || stf.size > MAX_FILE_SIZE) return;
      const buf = await fsp.readFile(file);
      if (buf.includes(0)) return;
      const text = buf.toString('utf8');
      const lines = text.split('\n');
      for (let i = 0; i < lines.length; i++) {
        if (matches.length >= MAX_GREP_MATCHES) return;
        regex.lastIndex = 0;
        if (regex.test(lines[i])) {
          const rel = root ? path.relative(root, file) : file;
          matches.push((root ? rel + ':' : '') + (i + 1) + ': ' + truncate(lines[i], 200));
        }
      }
    } catch (e) { /* skip unreadable */ }
  }

  try {
    if (st.isDirectory()) {
      const files = await walkFiles(target, MAX_DEPTH, 0, []);
      for (const f of files) {
        if (matches.length >= MAX_GREP_MATCHES) break;
        await grepFile(f, target);
      }
    } else {
      await grepFile(target, null);
    }
    if (matches.length === 0) return { ok: true, path: target, matches: '无匹配结果' };
    return { ok: true, path: target, matches: truncate(matches.join('\n'), MAX_OUTPUT) };
  } catch (e) {
    return { ok: false, error: '搜索失败: ' + e.message };
  }
}

async function findFiles(params) {
  const glob = String(params.glob || '**');
  let matcher;
  try {
    matcher = globToMatcher(glob);
  } catch (e) {
    return { ok: false, error: '无效的 glob 模式: ' + e.message };
  }
  const target = resolvePath(params.path);
  if (!target) return { ok: false, error: '非法路径' };
  let st;
  try {
    st = await fsp.stat(target);
  } catch (e) {
    return { ok: false, error: '目录不存在: ' + params.path };
  }
  if (!st.isDirectory()) return { ok: false, error: '不是目录: ' + params.path };
  try {
    const files = await walkFiles(target, MAX_DEPTH, 0, []);
    const out = [];
    for (const f of files) {
      if (out.length >= MAX_RESULTS) break;
      const rel = path.relative(target, f).split(path.sep).join('/');
      if (matcher.test(rel) || matcher.test(path.basename(rel))) {
        out.push(rel);
      }
    }
    if (out.length === 0) return { ok: true, path: target, files: '无匹配文件' };
    return { ok: true, path: target, files: out.join('\n') };
  } catch (e) {
    return { ok: false, error: '查找失败: ' + e.message };
  }
}

function dispatch(op, params) {
  switch (op) {
    case 'run_command': return runCommand(params);
    case 'write_file': return writeFile(params);
    case 'read_file': return readFile(params);
    case 'edit_file': return editFile(params);
    case 'list_dir': return listDir(params);
    case 'grep': return grep(params);
    case 'find_files': return findFiles(params);
    case 'ping': return Promise.resolve({ ok: true, node: process.version, workspace: WORKSPACE });
    default: return Promise.resolve({ ok: false, error: '未知操作: ' + op });
  }
}

// ---------------------------------------------------------------------------
// Main loop
// ---------------------------------------------------------------------------
const rl = readline.createInterface({ input: process.stdin, terminal: false });
rl.on('line', (line) => {
  line = line.trim();
  if (!line) return;
  let req;
  try {
    req = JSON.parse(line);
  } catch (e) {
    return;
  }
  if (typeof req.id !== 'number') return;
  dispatch(String(req.op || ''), (req.params || {}))
    .then((result) => {
      const out = Object.assign({ id: req.id }, result);
      process.stdout.write(JSON.stringify(out) + '\n');
    })
    .catch((e) => {
      process.stdout.write(JSON.stringify({ id: req.id, ok: false, error: '执行出错: ' + e.message }) + '\n');
    });
});
