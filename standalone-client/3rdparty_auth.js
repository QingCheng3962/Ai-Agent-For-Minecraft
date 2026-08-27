const http = require('http');
const url = require('url');
const fs = require('fs');
const path = require('path');
const utils = require('./utils');

async function refresh_session(refreshToken) {
    const tokenEndpoint = 'https://littleskin.cn/oauth/token';
    const requestBody = new URLSearchParams();
    requestBody.append('grant_type', 'refresh_token');
    requestBody.append('client_id', 1153);
    requestBody.append('client_secret', '3C1p27R9qxnH4lBrJjM9NsWVjYG2UuAluag95ydS');
    requestBody.append('refresh_token', refreshToken);
    let response = await fetch(tokenEndpoint, {
        method: 'POST',
        headers: {
            'Accept': 'application/json',
            'Content-Type': 'application/x-www-form-urlencoded'
        },
        body: requestBody.toString()
    });
    let newToken = await response.json();
    return newToken;
}

const cf = 'auth_profile.json';
var Config = {
    session: {},
    refreshToken: '',
    expireTime: -1,
    uuid: '',
    username: ''
};
Config = utils.readConfig(cf, undefined, Config);

async function main() {
    if (Config.refreshToken != '' && Config.expireTime < Math.floor(Date.now() / 1000)) {
        console.log("Refreshing session...");
        let ls_token = await refresh_session(Config.refreshToken);
        Config.refreshToken = ls_token.refresh_token;
        Config.expireTime = Math.floor(Date.now() / 1000) + ls_token.expires_in;

        const rolesEndpoint = 'https://littleskin.cn/api/yggdrasil/sessionserver/session/minecraft/profile';
        const headers = new Headers();
        headers.append("Content-Type", "application/json");
        headers.append("Authorization", 'Bearer ' + ls_token.access_token);
        headers.append("Accept", "application/json");

        let retries = 0, roles, uuid;
        while (retries < 3) {
            try {
                const response = await fetch(rolesEndpoint, { headers, timeout: 5000 });
                if (!response.ok) throw new Error(`Failed to fetch roles: ${response.status}`);
                roles = await response.json();
                if (JSON.stringify(roles).indexOf("\"error\":") !== -1) throw new Error("No response from Roles API");
                break;
            } catch (error) {
                console.error(`Attempt ${retries + 1} failed:`, error.message);
                retries++;
                await new Promise(resolve => setTimeout(resolve, 5000));
            }
        }
        for (const item of roles) {
            if (item.name === Config.username) uuid = item.id;
        }
        const mctokenEndpoint = 'https://littleskin.cn/api/yggdrasil/authserver/oauth';
        let response = await fetch(mctokenEndpoint, {
            method: 'POST',
            headers,
            body: JSON.stringify({ "uuid": uuid })
        });
        Config.session = await response.json();
        Config.uuid = utils.formatUUID(uuid);
        console.log("Session refreshed.");
    } else if (Config.refreshToken != '' && Config.expireTime > Math.floor(Date.now() / 1000)) {
        console.log("Session valid, skipping refresh.");
    } else {
        // First-time login via OAuth
        var logined = false;
        let server = http.createServer(async function (req, res) {
            const littleskin_code = url.parse(req.url, true).query.code;
            const tokenEndpoint = 'https://littleskin.cn/oauth/token';
            const requestBody = new URLSearchParams();
            requestBody.append('grant_type', 'authorization_code');
            requestBody.append('client_id', 1153);
            requestBody.append('client_secret', '3C1p27R9qxnH4lBrJjM9NsWVjYG2UuAluag95ydS');
            requestBody.append('redirect_uri', 'http://localhost:7322/auth');
            requestBody.append('code', littleskin_code);

            let response = await fetch(tokenEndpoint, {
                method: 'POST',
                headers: { 'Accept': 'application/json', 'Content-Type': 'application/x-www-form-urlencoded' },
                body: requestBody.toString()
            });
            let ls_token = await response.json();
            Config.refreshToken = ls_token.refresh_token;
            Config.expireTime = Math.floor(Date.now() / 1000) + ls_token.expires_in;

            await new Promise(resolve => setTimeout(resolve, 1000));
            const rolesEndpoint = 'https://littleskin.cn/api/yggdrasil/sessionserver/session/minecraft/profile';
            const headers = new Headers();
            headers.append("Content-Type", "application/json");
            headers.append("Authorization", 'Bearer ' + ls_token.access_token);
            headers.append("Accept", "application/json");

            let retries = 0, roles;
            while (retries < 3) {
                try {
                    const response = await fetch(rolesEndpoint, { headers, timeout: 3000 });
                    if (!response.ok) throw new Error(`Failed to fetch roles: ${response.status}`);
                    roles = await response.json();
                    if (JSON.stringify(roles).indexOf("\"error\":") !== -1) throw new Error("No response from Roles API");
                    break;
                } catch (error) {
                    console.error(`Attempt ${retries + 1} failed:`, error.message);
                    retries++;
                    await new Promise(resolve => setTimeout(resolve, 5000));
                }
            }

            const roleNames = roles.map(role => role.name);
            console.log("\nAvailable profiles:");
            roleNames.forEach((name, index) => console.log(`[${index}] ${name}`));

            const choice = await utils.getInput("\nChoose profile (index): ");
            const selectedIndex = parseInt(choice);
            if (isNaN(selectedIndex) || selectedIndex < 0 || selectedIndex >= roles.length) {
                throw new Error("Invalid profile selection");
            }
            const selectedRole = roles[selectedIndex];
            console.log(`Selected: ${selectedRole.name}`);

            const mctokenEndpoint = 'https://littleskin.cn/api/yggdrasil/authserver/oauth';
            response = await fetch(mctokenEndpoint, {
                method: 'POST',
                headers,
                body: JSON.stringify({ "uuid": selectedRole.id })
            });
            await new Promise(resolve => setTimeout(resolve, 500));
            Config.session = await response.json();
            Config.username = selectedRole.name;
            Config.uuid = utils.formatUUID(selectedRole.id);

            logined = true;
            res.writeHead(200, { "Content-Type": "text/html;charset=UTF-8" });
            res.end("Done Login.");
            server.close();
        });

        server.listen(7322, "127.0.0.1");
        console.log("Click to login: https://littleskin.cn/oauth/authorize?client_id=1153&redirect_uri=http%3a%2f%2flocalhost%3a7322%2fauth&response_type=code&scope=Yggdrasil.PlayerProfiles.Read%20Yggdrasil.MinecraftToken.Create");
        while (!logined) await new Promise(resolve => setTimeout(resolve, 1000));
        console.log("Login done.");
    }

    utils.writeConfig(cf, Config);
    console.log("Config saved.");
}

function mfAuth(client, options) {
    client.username = options.username;
    client.uuid = options.uuid;
    options.sessionServer = "https://littleskin.cn/api/yggdrasil/sessionserver";
    options.auth = "mojang";
    client.session = options.loginSession;
    options.haveCredentials = true;
    options.connect(client);
}

module.exports = {
    main,
    mfAuth
};

main();
