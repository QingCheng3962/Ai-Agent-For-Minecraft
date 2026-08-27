const fs = require("fs");
const path = require('path');
const readline = require('readline');

function formatUUID(uuid) {
    if (typeof uuid !== 'string' || uuid.length !== 32) {
        throw new Error('Invalid UUID');
    }
    return [
        uuid.slice(0, 8),
        uuid.slice(8, 12),
        uuid.slice(12, 16),
        uuid.slice(16, 20),
        uuid.slice(20)
    ].join('-');
}

async function getInput(prompt) {
    const rl = readline.createInterface({
        input: process.stdin,
        output: process.stdout,
    });
    return new Promise((resolve) => {
        rl.question(prompt, (answer) => {
            rl.close();
            resolve(answer);
        });
    });
}

function readConfig(filePath, key, defaultConfig = {}) {
    try {
        const absolutePath = path.resolve(filePath);
        if (!fs.existsSync(absolutePath)) {
            console.warn(`Config not found: ${absolutePath}, creating default.`);
            if (!writeConfig(filePath, defaultConfig)) {
                console.error('Failed to create default config!');
                return null;
            }
        }
        const data = fs.readFileSync(absolutePath, 'utf8');
        const config = JSON.parse(data);
        if (key) {
            return config[key] !== undefined ? config[key] : null;
        } else {
            return config;
        }
    } catch (error) {
        console.error(`Failed to read config: ${error.message}`);
        return null;
    }
}

function writeConfig(filePath, config, pretty = false) {
    try {
        const absolutePath = path.resolve(filePath);
        const jsonString = pretty ? JSON.stringify(config, null, 2) : JSON.stringify(config);
        fs.writeFileSync(absolutePath, jsonString, 'utf8');
        return true;
    } catch (error) {
        console.error(`Failed to write config: ${error.message}`);
        return false;
    }
}

function nowTime() {
    return Math.floor(Date.now() / 1000);
}

function nowTimeMs() {
    return Math.floor(Date.now());
}

module.exports = {
    formatUUID,
    getInput,
    readConfig,
    writeConfig,
    nowTime,
    nowTimeMs
};
