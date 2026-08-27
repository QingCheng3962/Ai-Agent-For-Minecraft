const FOODS = [
    'bread', 'apple', 'golden_carrot', 'cooked_beef', 'cooked_porkchop',
    'cooked_chicken', 'cooked_cod', 'cooked_salmon', 'cookie',
    'melon_slice', 'carrot', 'potato', 'baked_potato', 'beetroot',
    'cooked_rabbit', 'mutton', 'cooked_mutton', 'chorus_fruit',
    'dried_kelp', 'sweet_berries', 'glow_berries'
];

const FOOD_THRESHOLD = 7; // 饱食度低于此值时进食

class AutoEat {
    constructor(bot, Log, Config) {
        this.bot = bot;
        this.Log = Log;
        this.enabled = Config.autoeat?.enabled ?? true;
        this.eating = false;
        this.originalSlot = null;

        if (this.enabled) {
            this.bot.on('health', () => this._check());
            this.Log.info("AutoEat enabled");
        }
    }

    _findFood() {
        for (let i = 9; i < 45; i++) {
            const item = this.bot.inventory.slots[i];
            if (item && FOODS.includes(item.name)) {
                return { slot: i, item };
            }
        }
        return null;
    }

    async _check() {
        if (this.eating || !this.enabled) return;
        if ((this.bot.food || 0) > FOOD_THRESHOLD) return;

        const food = this._findFood();
        if (!food) {
            this.Log.warn("AutoEat: no food in inventory");
            return;
        }

        this.eating = true;
        this.originalSlot = this.bot.quickBarSlot;

        try {
            await this.bot.moveSlotItem(food.slot, this.bot.quickBarSlot + 36);
            await new Promise(r => setTimeout(r, 100));
            this.bot.activateItem();
            this.Log.info(`AutoEat: eating ${food.item.name}`);

            const interval = setInterval(() => {
                if (this.bot.food >= 20 || !this.bot.heldItem) {
                    clearInterval(interval);
                    this._finish(food.slot);
                } else {
                    this.bot.activateItem();
                }
            }, 500);
        } catch (err) {
            this.Log.error(`AutoEat error: ${err.message}`);
            this.eating = false;
        }
    }

    async _finish(foodSlot) {
        try {
            if (this.bot.heldItem && foodSlot !== null) {
                await this.bot.moveSlotItem(this.bot.quickBarSlot + 36, foodSlot);
            }
            if (this.originalSlot !== null) {
                this.bot.setQuickBarSlot(this.originalSlot);
            }
        } catch (err) {
            this.Log.error(`AutoEat finish error: ${err.message}`);
        }
        this.eating = false;
        this.originalSlot = null;
        this.Log.info("AutoEat: done");
    }

    toggle() {
        this.enabled = !this.enabled;
        if (this.enabled) {
            this.bot.on('health', () => this._check());
            this.Log.info("AutoEat enabled");
        }
        return this.enabled;
    }
}

module.exports = { AutoEat };
