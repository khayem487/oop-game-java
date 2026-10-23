"use strict";

// Executes the shipped browser program unchanged. The small DOM/transport doubles
// expose event handlers and confirmed API commands, not an alternative game engine.
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const test = require("node:test");

const root = path.resolve(__dirname, "..");
const browserSource = fs.readFileSync(path.join(root, "web/public/game.js"), "utf8");
const browserHtml = fs.readFileSync(path.join(root, "web/public/index.html"), "utf8");
const flush = () => new Promise((resolve) => setImmediate(resolve));

class NodeDouble {
  constructor(tagName = "DIV") {
    this.tagName = tagName.toUpperCase();
    this.children = [];
    this.dataset = {};
    this.attributes = {};
    this.listeners = new Map();
    this.value = "";
    this.open = false;
    this.hidden = false;
    this.disabled = false;
    this.classes = new Set();
    this.classList = {
      add: (...classes) => classes.forEach((value) => this.classes.add(value)),
      remove: (...classes) => classes.forEach((value) => this.classes.delete(value)),
      contains: (value) => this.classes.has(value),
      toggle: (value, force = !this.classes.has(value)) => {
        if (force) this.classes.add(value); else this.classes.delete(value);
        return force;
      },
    };
  }
  addEventListener(name, listener) {
    if (!this.listeners.has(name)) this.listeners.set(name, []);
    this.listeners.get(name).push(listener);
  }
  emit(name, details = {}) {
    const event = {
      target: this, key: "", code: "", repeat: false,
      altKey: false, ctrlKey: false, metaKey: false,
      button: 0, pointerId: 1, detail: 1, defaultPrevented: false,
      preventDefault() { this.defaultPrevented = true; },
      ...details,
    };
    for (const listener of this.listeners.get(name) || []) listener(event);
    return event;
  }
  setAttribute(name, value) { this.attributes[name] = String(value); }
  append(...nodes) {
    for (const node of nodes) this.children.push(...(node.fragment ? node.children : [node]));
  }
  appendChild(node) { this.append(node); return node; }
  replaceChildren(...nodes) { this.children = []; this.append(...nodes); }
  showModal() { this.open = true; }
  close() { this.open = false; this.emit("close"); }
  focus() { this.focused = true; }
  setPointerCapture(pointerId) { this.capturedPointer = pointerId; }
}

async function browser() {
  const elements = new Map([...browserHtml.matchAll(/\bid="([^"]+)"/g)].map((match) => [match[1], new NodeDouble()]));
  const moveButtons = ["UP", "LEFT", "RIGHT", "DOWN"].map((direction) => {
    const button = new NodeDouble("BUTTON"); button.dataset.move = direction; return button;
  });
  const dialogs = ["menu-dialog", "restart-dialog", "credits-dialog"].map((id) => elements.get(id));
  const doc = new NodeDouble("BODY");
  doc.hidden = false;
  doc.getElementById = (id) => { assert.ok(elements.has(id), `Shipped HTML contains #${id}`); return elements.get(id); };
  doc.createElement = (tag) => new NodeDouble(tag);
  doc.createDocumentFragment = () => Object.assign(new NodeDouble(), { fragment: true });
  doc.querySelectorAll = (selector) => {
    if (selector === "[data-move]") return moveButtons;
    if (selector === "dialog") return dialogs;
    if (selector === "[data-slot]") return elements.get("inventory").children.filter((node) => node.dataset.slot);
    throw new Error(`Unexpected selector ${selector}`);
  };
  const win = new NodeDouble("WINDOW");
  const storage = new Map();
  const commands = [];
  const timers = new Map();
  let nextTimer = 1;
  const context = vm.createContext({
    document: doc, window: win, URLSearchParams,
    localStorage: { getItem: (key) => storage.get(key) ?? null, setItem: (key, value) => storage.set(key, value), removeItem: (key) => storage.delete(key) },
    AbortSignal: { timeout: (milliseconds) => ({ milliseconds }) },
    setInterval(callback, milliseconds) { assert.equal(milliseconds, 180); const id = nextTimer++; timers.set(id, callback); return id; },
    clearInterval(id) { timers.delete(id); },
    async fetch(url, options) {
      assert.equal(url, "/api/game"); assert.equal(options.method, "POST");
      const submitted = new URLSearchParams(options.body);
      commands.push(submitted.get("command"));
      // A confirmed, fixed snapshot lets this test observe input scheduling only.
      return { ok: true, json: async () => ({
        version: 2, playerName: submitted.get("name"), seed: submitted.get("seed"), history: submitted.get("history"),
        score: 0, lives: 5, coinsRemaining: 1, enemiesRemaining: 0,
        levelIndex: 0, levelCount: 6, levelName: "Carte de contrôle", objectiveText: "Ramasser la pièce", status: "playing",
        toroidal: false, playerRow: 1, playerCol: 1, terrain: ["####", "#1.#", "####"],
        enemies: [], inventory: [], defeatedEnemies: 0, turns: 0, freezeTurns: 0, messages: [],
      }) };
    },
  });
  vm.runInContext(browserSource, context, { filename: "web/public/game.js", timeout: 1000 });
  await flush();
  assert.deepEqual(commands, ["STATE"], "Actual browser program completes its initial request");
  assert.equal(elements.get("feedback").classList.contains("danger"), false, "DOM double supports the actual render path");
  assert.equal(elements.get("board").children.length, 3);
  commands.length = 0;
  return {
    commands, timers, elements, doc, win, moveButtons,
    async down(key, code = key, repeat = false) { doc.emit("keydown", { key, code, repeat }); await flush(); },
    async up(key, code = key) { doc.emit("keyup", { key, code }); await flush(); },
    async click(id) { elements.get(id).emit("click"); await flush(); },
    async tick() { for (const callback of [...timers.values()]) callback(); await flush(); },
  };
}

test("one keyboard press/release sends one action, without browser-autorepeat duplication", async () => {
  const app = await browser();
  await app.down("ArrowRight"); await app.down("ArrowRight", "ArrowRight", true);
  await app.up("ArrowRight"); await app.tick();
  assert.deepEqual(app.commands, ["RIGHT"]);
  assert.equal(app.timers.size, 0);
});

test("releasing an older direction keeps the newer direction moving", async () => {
  const app = await browser();
  await app.down("ArrowRight"); await app.down("ArrowDown"); await app.up("ArrowRight"); await app.tick();
  assert.deepEqual(app.commands, ["RIGHT", "DOWN", "DOWN"]);
  assert.equal(app.timers.size, 1);
  await app.up("ArrowDown"); await app.tick();
  assert.deepEqual(app.commands, ["RIGHT", "DOWN", "DOWN"]);
});

test("releasing the newest direction restores the older key still held", async () => {
  const app = await browser();
  await app.down("ArrowRight"); await app.down("ArrowDown"); await app.up("ArrowDown"); await app.tick();
  assert.deepEqual(app.commands, ["RIGHT", "DOWN", "RIGHT", "RIGHT"]);
  await app.up("ArrowRight"); assert.equal(app.timers.size, 0);
});

test("two physical keys for the same direction keep one timer and no duplicate initial action", async () => {
  const app = await browser();
  await app.down("ArrowRight"); await app.down("d", "KeyD"); await app.up("ArrowRight"); await app.tick();
  assert.deepEqual(app.commands, ["RIGHT", "RIGHT"]);
  assert.equal(app.timers.size, 1);
  await app.up("d", "KeyD"); assert.equal(app.timers.size, 0);
});

test("blur stops all movement and clears keys without resurrecting stale direction on release", async () => {
  const app = await browser();
  await app.down("ArrowRight"); await app.down("ArrowDown"); app.win.emit("blur");
  await app.up("ArrowDown"); await app.tick();
  assert.deepEqual(app.commands, ["RIGHT", "DOWN"]);
  assert.equal(app.timers.size, 0);
  await app.down("ArrowLeft"); assert.equal(app.commands.at(-1), "LEFT");
});

test("pause and resume stop held movement and require a fresh press", async () => {
  const app = await browser();
  await app.down("ArrowRight"); await app.click("pause"); await app.tick();
  await app.down("ArrowDown"); await app.up("ArrowRight");
  assert.deepEqual(app.commands, ["RIGHT"]);
  await app.click("resume"); await app.tick();
  assert.deepEqual(app.commands, ["RIGHT"]);
  await app.down("ArrowLeft"); assert.equal(app.commands.at(-1), "LEFT");
});

for (const [open, close] of [["menu", "close-menu"], ["credits", "close-credits"], ["restart", "cancel-restart"]]) {
  test(`${open} dialog stops movement; closing it does not restore held keys`, async () => {
    const app = await browser();
    await app.down("ArrowRight"); await app.click(open); await app.tick();
    await app.down("ArrowDown"); await app.up("ArrowRight");
    assert.deepEqual(app.commands, ["RIGHT"]);
    await app.click(close); await app.tick();
    assert.deepEqual(app.commands, ["RIGHT"]);
    await app.down("ArrowLeft"); assert.equal(app.commands.at(-1), "LEFT");
  });
}

test("a pointer tap sends once and the ensuing real click does not send a duplicate", async () => {
  const app = await browser();
  const right = app.moveButtons.find((button) => button.dataset.move === "RIGHT");
  right.emit("pointerdown"); await flush(); right.emit("pointerup"); right.emit("click", { detail: 1 }); await app.tick();
  assert.deepEqual(app.commands, ["RIGHT"]);
  assert.equal(app.timers.size, 0);
});

test("pointer hold repeats, loss of capture stops it, and keyboard activation sends once", async () => {
  const app = await browser();
  const right = app.moveButtons.find((button) => button.dataset.move === "RIGHT");
  right.emit("pointerdown"); await flush(); await app.tick(); await app.tick();
  assert.deepEqual(app.commands, ["RIGHT", "RIGHT", "RIGHT"]);
  right.emit("lostpointercapture"); await app.tick();
  assert.equal(app.commands.length, 3);
  right.emit("click", { detail: 0 }); await flush();
  assert.deepEqual(app.commands, ["RIGHT", "RIGHT", "RIGHT", "RIGHT"]);
});

test("hiding the document pauses and clears held keys", async () => {
  const app = await browser();
  await app.down("ArrowRight"); app.doc.hidden = true; app.doc.emit("visibilitychange");
  await app.tick(); await app.up("ArrowRight");
  app.doc.hidden = false; app.doc.emit("visibilitychange"); await app.tick();
  assert.deepEqual(app.commands, ["RIGHT"]);
  await app.click("resume"); await app.down("ArrowDown");
  assert.deepEqual(app.commands, ["RIGHT", "DOWN"]);
});
