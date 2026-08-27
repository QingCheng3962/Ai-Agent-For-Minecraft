'use strict';

// Headless mineflayer bot engine controlled by the Python app over stdio.
// Usage: node bot_engine.js <runtime-config.json>
// Events (stdout, one JSON per line):
//   {"event":"log","level":..., "message":...}
//   {"event":"spawn","username":..., "players":[...]}
//   {"event":"chat","player":..., "content":..., "isPrivate":bool, "guild":...}
//   {"event":"actionBar","text":...}
//   {"event":"health","health":..., "food":...}
//   {"event":"kicked","reason":..., "loggedIn":bool}
//   {"event":"error","message":...}
//   {"event":"end","reason":...}
//   {"event":"chatSent","text":...}
// Commands (stdin, one JSON per line):
//   {"type":"chat","text":...}
//   {"type":"setAutoeat","enabled":bool}
//   {"type":"setQuiz","enabled":bool}
//   {"type":"ping"}
//   {"type":"quit"}

const fs = require('fs');
const readline = require('readline');
const mineflayer = require('mineflayer');

function emitEvent(obj) {
    process.stdout.write(JSON.stringify(obj) + '\n');
}

function log(level, message) {
    emitEvent({ event: 'log', level, message: String(message) });
}

function logError(message) {
    emitEvent({ event: 'log', level: 'error', message: String(message) });
}

function makeLogger() {
    return {
        info: (m) => log('info', m),
        warn: (m) => log('warn', m),
        error: (m) => log('error', m)
    };
}

// --- Chat parsing (server-specific formats + standard vanilla) ---
function parseChat(message) {
    if (typeof message !== 'string' || !message) return null;

    // Private message: [player -> me] content
    let match = message.match(/^\[(.*?)\s*->\s*me\]\s*(.*)$/);
    if (match) {
        return { player: match[1].trim(), content: match[2].trim(), isPrivate: true, guild: null };
    }

    // Guild / public with » or : separator, optional |[guild] prefix
    match = message.match(/^(?:\|\[(.*?)\])?\s*([A-Za-z0-9_]{1,16})\s*[»>:]\s*(.+)$/);
    if (match) {
        return { player: match[2].trim().replace('|', ''), content: match[3].trim(), isPrivate: false, guild: match[1] || null };
    }

    // Standard vanilla: <player> content
    match = message.match(/^<([^>]{1,32})>\s*(.+)$/);
    if (match) {
        return { player: match[1].trim(), content: match[2].trim(), isPrivate: false, guild: null };
    }

    // [player] content  (player-like name to avoid matching system tags)
    match = message.match(/^\[([A-Za-z0-9_]{1,16})\]\s*(.+)$/);
    if (match) {
        return { player: match[1].trim(), content: match[2].trim(), isPrivate: false, guild: null };
    }

    return null;
}

// --- Config ---
const configPath = process.argv[2];
if (!configPath) {
    console.error('usage: node bot_engine.js <runtime-config.json>');
    process.exit(1);
}

let config;
try {
    config = JSON.parse(fs.readFileSync(configPath, 'utf8'));
} catch (e) {
    console.error('Failed to read runtime config: ' + e.message);
    process.exit(1);
}

const server = config.server || {};
const auth = config.auth || { type: 'offline' };
const quizCfg = config.quiz || {};
const autoEatCfg = config.autoeat || {};

// Mutable runtime toggles shared with quiz / autoeat
const runtime = {
    autoeat: { enabled: !!autoEatCfg.enabled },
    quiz: { enabled: !!quizCfg.enabled }
};

// --- Auth: littleskin (mirrors 3rdparty_auth.js mfAuth) ---
function mfAuth(client, options) {
    client.username = options.username;
    client.uuid = options.uuid;
    options.sessionServer = 'https://littleskin.cn/api/yggdrasil/sessionserver';
    options.auth = 'mojang';
    client.session = options.loginSession;
    options.haveCredentials = true;
    options.connect(client);
}

const botOptions = {
    host: server.host,
    port: server.port || 25565,
    username: auth.username || 'AI_Bot',
    version: server.version || undefined,
    viewDistance: server.viewDistance || 'tiny',
    brand: server.brand || 'aafm-python',
    hideErrors: true,
    logErrors: false,
    auth: 'offline'
};

if (auth.type === 'littleskin' && auth.session && auth.uuid) {
    botOptions.uuid = auth.uuid;
    botOptions.auth = mfAuth;
    botOptions.sessionServer = 'https://littleskin.cn/api/yggdrasil/sessionserver';
    botOptions.loginSession = auth.session;
    botOptions.accessToken = auth.session.accessToken;
    botOptions.clientToken = auth.session.clientToken;
}

let bot;
try {
    bot = mineflayer.createBot(botOptions);
} catch (e) {
    logError('createBot failed: ' + (e && e.message));
    process.exit(1);
}

// --- Optional handlers (quiz / autoeat from the original small project) ---
let QuizHandler = null;
let AutoEat = null;
let quiz = null;
let autoEat = null;

try {
    QuizHandler = require('./quiz').QuizHandler;
} catch (e) {
    logError('quiz module not available: ' + e.message);
}
try {
    AutoEat = require('./autoeat').AutoEat;
} catch (e) {
    logError('autoeat module not available: ' + e.message);
}

const engineLogger = makeLogger();

if (QuizHandler) {
    const quizConfig = {
        quiz: {
            enabled: runtime.quiz.enabled,
            minDelay: quizCfg.minDelay,
            questionBank: quizCfg.questionBank
        },
        llm: config.llm || {}
    };
    const chatLog = { info: (m) => emitEvent({ event: 'quizLog', message: String(m) }) };
    try {
        quiz = new QuizHandler(bot, chatLog, engineLogger, quizConfig);
    } catch (e) {
        logError('quiz init: ' + e.message);
    }
}

if (AutoEat) {
    try {
        // Construct with enabled=false so the engine drives the health check itself.
        autoEat = new AutoEat(bot, engineLogger, { autoeat: { enabled: false } });
        autoEat.enabled = runtime.autoeat.enabled;
    } catch (e) {
        logError('autoeat init: ' + e.message);
    }
}

// --- Bot events ---
bot.on('message', (jsonMsg, position) => {
    if (position === 'game_info') return;
    const raw = jsonMsg.toString();
    emitEvent({ event: 'message', raw });

    if (quiz) quiz.onChat(raw);

    const chat = parseChat(raw);
    if (chat) {
        if (chat.player && bot.username && chat.player.toLowerCase() === bot.username.toLowerCase()) return;
        emitEvent({
            event: 'chat',
            player: chat.player,
            content: chat.content,
            isPrivate: !!chat.isPrivate,
            guild: chat.guild
        });
    }
});

bot.on('actionBar', (jsonMsg) => {
    const text = jsonMsg.toString();
    if (!text || text === ' ') return;
    emitEvent({ event: 'actionBar', text });
    if (quiz) quiz.onActionBar(text);
});

bot.once('spawn', () => {
    emitEvent({ event: 'spawn', username: bot.username, players: Object.keys(bot.players) });
});

bot.on('kicked', (reason, loggedIn) => {
    const msg = typeof reason === 'string' ? reason : JSON.stringify(reason);
    emitEvent({ event: 'kicked', reason: msg, loggedIn: !!loggedIn });
});

bot.on('error', (e) => {
    emitEvent({ event: 'error', message: e && e.message ? e.message : String(e) });
});

bot.on('end', (reason) => {
    emitEvent({ event: 'end', reason: reason == null ? '' : String(reason) });
});

bot.on('health', () => {
    emitEvent({ event: 'health', health: bot.health, food: bot.food });
    if (autoEat) autoEat._check();
});

// --- Stdin commands ---
const rl = readline.createInterface({ input: process.stdin, terminal: false });

function handleCommand(cmd) {
    switch (cmd.type) {
        case 'chat': {
            const text = String(cmd.text || '');
            if (!text) return;
            if (bot && typeof bot.chat === 'function') {
                bot.chat(text);
                emitEvent({ event: 'chatSent', text });
            }
            break;
        }
        case 'setAutoeat':
            runtime.autoeat.enabled = !!cmd.enabled;
            if (autoEat) autoEat.enabled = runtime.autoeat.enabled;
            log('info', 'AutoEat ' + (runtime.autoeat.enabled ? 'enabled' : 'disabled'));
            break;
        case 'setQuiz':
            runtime.quiz.enabled = !!cmd.enabled;
            if (quiz && quiz.config) quiz.config.enabled = runtime.quiz.enabled;
            log('info', 'Quiz ' + (runtime.quiz.enabled ? 'enabled' : 'disabled'));
            break;
        case 'ping':
            emitEvent({ event: 'pong' });
            break;
        case 'quit':
            try { if (bot) bot.end('quit'); } catch (e) { /* ignore */ }
            process.exit(0);
            break;
        default:
            log('warn', 'Unknown command: ' + cmd.type);
    }
}

rl.on('line', (line) => {
    line = line.trim();
    if (!line) return;
    let cmd;
    try {
        cmd = JSON.parse(line);
    } catch (e) {
        logError('Bad command: ' + line);
        return;
    }
    try {
        handleCommand(cmd);
    } catch (e) {
        logError('Command error: ' + e.message);
    }
});

log('info', 'Bot engine started. Waiting for spawn...');
