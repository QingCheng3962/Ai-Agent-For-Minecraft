const mineflayer = require('mineflayer');
const yaml = require('js-yaml');
const fs = require('fs');
const path = require('path');
const utils = require('./utils');
const { mfAuth } = require('./3rdparty_auth');
const { initLogger, getLogger } = require('./logger');
const { QuizHandler } = require('./quiz');
const { AutoEat } = require('./autoeat');

// --- Load Config ---
function loadConfig() {
    const configPath = path.resolve('config.yml');
    if (!fs.existsSync(configPath)) {
        console.error('config.yml not found!');
        process.exit(1);
    }
    return yaml.load(fs.readFileSync(configPath, 'utf8'));
}

// --- Load Auth Profile ---
function loadAuth() {
    return utils.readConfig('auth_profile.json', undefined);
}

// --- Chat Parser ---
function parseChat(message) {
    // Private message: [playername -> me] content
    const privateMessageRegex = /^\[(.*?)\s*->\s*me\]\s*(.*)$/;
    let match = message.match(privateMessageRegex);
    if (match) {
        const [, player, content] = match;
        return {
            guild: null,
            player: player.trim(),
            content: content.trim(),
            isPrivate: true,
            time: utils.nowTimeMs(),
        };
    }
    // Public/guild message: |[guild] player » content  or  player » content
    const publicMessageRegex = /^(?:\|\[(.*?)\])?\s*(.*?)\s*»\s*(.*)$/;
    match = message.match(publicMessageRegex);
    if (match) {
        const [, guild, player, content] = match;
        return {
            guild: guild || null,
            player: player.trim().replace('|', ''),
            content: content.trim(),
            isPrivate: false,
            time: utils.nowTimeMs(),
        };
    }
    return null;
}

// --- Main ---
async function main() {
    await initLogger();
    const Log = getLogger("main");
    const ChatLog = getLogger("chat");

    Log.info("Loading config.yml...");
    const Config = loadConfig();

    Log.info("Loading auth_profile.json...");
    const Auth = loadAuth();

    if (!Auth || !Auth.session || !Auth.username) {
        Log.error("No valid auth_profile.json found. Run 'node 3rdparty_auth.js' first to login.");
        process.exit(1);
    }

    Log.info(`Connecting to server as ${Auth.username}...`);

    const bot = mineflayer.createBot({
        host: Config.server.host,
        port: Config.server.port,
        username: Auth.username,
        uuid: Auth.uuid,
        loginSession: Auth.session,
        accessToken: Auth.session.accessToken,
        clientToken: Auth.session.clientToken,
        brand: Config.server.brand,
        version: Config.server.version,
        viewDistance: Config.server.viewDistance,
        auth: mfAuth
    });

    // Initialize quiz handler
    const quiz = new QuizHandler(bot, ChatLog, Log, Config);

    // Initialize auto eat
    const autoEat = new AutoEat(bot, Log, Config);

    bot.on('message', (jsonMsg, position) => {
        if (position === 'game_info') return;
        const raw = jsonMsg.toString();
        ChatLog.info(raw);

        // Let quiz handler process chat messages (for session end detection)
        quiz.onChat(raw);

        const chat = parseChat(raw);
        if (chat) {
            if (chat.player === bot.username) return;
            Log.info(`[${chat.isPrivate ? 'PM' : 'Chat'}] ${chat.player}: ${chat.content}`);

            // Owner PM echo: #<message> -> bot says <message>
            if (chat.isPrivate && chat.player === Config.owner && chat.content.startsWith('#')) {
                const echo = chat.content.slice(1).trim();
                if (echo) {
                    bot.chat(echo);
                    Log.info(`Owner echo: ${echo}`);
                }
            }
        }
    });

    bot.once('spawn', () => {
        Log.info(`Bot spawned! Players: ${Object.keys(bot.players).join(', ')}`);
    });

    bot.on('kicked', (reason, loggedIn) => {
        const msg = typeof reason === 'string' ? reason : JSON.stringify(reason);
        if (!loggedIn) {
            Log.error(`Login failed: ${msg}`);
        } else {
            Log.error(`Kicked: ${msg}`);
        }
        process.exit(1);
    });

    bot.on('error', (e) => {
        Log.error(`Error: ${e.message}`);
        process.exit(1);
    });

    let lastActionBar = null;

    bot.on('actionBar', (jsonMsg) => {
        const text = jsonMsg.toString();
        if (text !== ' ' && text !== lastActionBar) {
            ChatLog.info(`ActionBar: ${text}`);
            lastActionBar = text;

            // Let quiz handler process action bar
            quiz.onActionBar(text);
        }
    });

    Log.info("Bot started. Press Ctrl+C to stop.");
}

main();
