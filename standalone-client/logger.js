const log4js = require('log4js');
const fs = require('fs');
const path = require('path');

async function createLogsFolder() {
    const logsFolderPath = path.join(__dirname, 'logs');
    try {
        await fs.promises.access(logsFolderPath, fs.constants.F_OK);
    } catch (error) {
        if (error.code === 'ENOENT') {
            try {
                await fs.promises.mkdir(logsFolderPath, { recursive: true });
            } catch (mkdirError) {
                console.error('Failed to create logs folder:', mkdirError);
                process.exit(1);
            }
        } else {
            console.error('Failed to check logs folder:', error);
            process.exit(1);
        }
    }
}

async function initLogger() {
    await createLogsFolder();
    log4js.configure({
        appenders: {
            file: {
                type: "dateFile",
                filename: "logs/log.log",
                pattern: "yyyy-MM-dd",
                compress: true,
                layout: {
                    type: "pattern",
                    pattern: "[%d{yyyy.MM.dd hh:mm:ss}] [%p] (%c): %m"
                }
            },
            console: {
                type: "stdout",
                layout: {
                    type: "pattern",
                    pattern: "%[[%d{yyyy.MM.dd hh:mm:ss}] [%p] (%c): %]%m"
                }
            }
        },
        categories: {
            default: {
                appenders: ["file", "console"],
                level: "debug"
            }
        },
    });
}

function getLogger(name) {
    return log4js.getLogger(name);
}

module.exports = {
    initLogger,
    getLogger
};
