(() => {
  "use strict";
  const $ = (id) => document.getElementById(id);
  const storageKey = "oop-game-java-save-v2";
  const oldStorageKey = "oop-game-java-history-v1";
  const board = $("board");
  const message = $("message");
  const feedback = $("feedback");
  const moveButtons = [...document.querySelectorAll("[data-move]")];
  const dialogs = [...document.querySelectorAll("dialog")];
  let saved = { version: 2, name: "Explorateur", seed: "1337", history: "" };
  let current = null;
  let busy = false;
  let paused = false;
  let migrationNotice = "";
  let storageAvailable = true;
  let heldDirection = null;
  let holdTimer = null;
  const pressedDirections = new Map();
  try {
    if (localStorage.getItem(oldStorageKey) !== null) {
      localStorage.removeItem(oldStorageKey);
      migrationNotice = "La campagne a évolué : votre ancienne sauvegarde à trois cartes a été retirée. Une nouvelle aventure commence.";
    }
    const raw = localStorage.getItem(storageKey);
    if (raw) {
      const candidate = JSON.parse(raw);
      if (candidate.version === 2 && typeof candidate.name === "string" && candidate.name.length <= 64 && /^\d{1,19}$/.test(String(candidate.seed)) && /^[ULDRN1-5S]{0,2048}$/.test(candidate.history))
        saved = { version: 2, name: candidate.name, seed: String(candidate.seed), history: candidate.history };
      else migrationNotice = "La sauvegarde n’était plus compatible. Une nouvelle aventure commence.";
    }
  } catch (_) { storageAvailable = false; }

  const tileTypes = { "#": "wall", ".": "coin", "*": "trap", "D": "door", "W": "weapon", "H": "hourglass", "T": "trophy", " ": "floor" };
  const tileNames = { "#": "mur", ".": "pièce", "*": "piège", "D": "porte verrouillée", "W": "arme", "H": "sablier", "T": "trésor", " ": "sol" };
  const tileMarks = { "D": "▥", "W": "⚔", "H": "⌛", "T": "♜" };
  const enemyMarks = { random: "R", ghost: "G", hunter: "C" };

  function stopMovement() { heldDirection = null; clearInterval(holdTimer); holdTimer = null; }
  function stopHolding() { pressedDirections.clear(); stopMovement(); }
  function canPlay() { return current?.status === "playing" && !paused && !dialogs.some((dialog) => dialog.open); }

  function renderBoard(state) {
    const enemies = new Map();
    for (const enemy of state.enemies) {
      const key = `${enemy.row},${enemy.col}`;
      if (!enemies.has(key)) enemies.set(key, []);
      enemies.get(key).push(enemy);
    }
    const fragment = document.createDocumentFragment();
    for (const [rowIndex, row] of state.terrain.entries()) {
      const rowNode = document.createElement("div");
      rowNode.className = "board-row";
      for (const [colIndex, symbol] of [...row].entries()) {
        const tile = document.createElement("div");
        tile.className = `tile ${tileTypes[symbol] || "floor"}`;
        tile.setAttribute("aria-hidden", "true");
        const marks = [];
        if (tileMarks[symbol]) {
          const item = document.createElement("span");
          item.className = "terrain-mark";
          item.textContent = tileMarks[symbol];
          tile.appendChild(item);
        }
        for (const enemy of enemies.get(`${rowIndex},${colIndex}`) || []) {
          const marker = document.createElement("span");
          marker.className = `actor enemy ${Object.hasOwn(enemyMarks, enemy.type) ? enemy.type : "random"}`;
          marker.textContent = enemyMarks[enemy.type] || "R";
          const lives = document.createElement("small");
          lives.className = "enemy-lives";
          lives.textContent = String(enemy.lives);
          marker.appendChild(lives);
          tile.appendChild(marker);
          marks.push(`${enemy.name}, ${enemy.lives} vie${enemy.lives > 1 ? "s" : ""}`);
        }
        if (rowIndex === state.playerRow && colIndex === state.playerCol) {
          const player = document.createElement("span");
          player.className = "actor player";
          player.textContent = "••";
          tile.appendChild(player);
          marks.push(state.playerName);
        }
        tile.title = marks.length ? `${tileNames[symbol] || "sol"} · ${marks.join(" · ")}` : tileNames[symbol] || "sol";
        rowNode.appendChild(tile);
      }
      fragment.appendChild(rowNode);
    }
    board.replaceChildren(fragment);
    board.classList.toggle("toroidal", state.toroidal);
    board.setAttribute("aria-label", `${state.levelName}. ${state.objectiveText} ${state.lives} vies. Position ligne ${state.playerRow + 1}, colonne ${state.playerCol + 1}. ${state.enemiesRemaining} ennemis. Utilisez les flèches ou les boutons de direction.`);
  }

  function inventoryIcon(entry) {
    const name = `${entry.id} ${entry.name}`.toLowerCase();
    if (/sablier|hourglass/.test(name)) return "⌛";
    if (/arme|weapon/.test(name)) return "⚔";
    if (/crochet|lockpick/.test(name)) return "⚿";
    if (/téléport|teleport/.test(name)) return "✧";
    if (/trésor|trophy/.test(name)) return "♜";
    return entry.kind === "skill" ? "✧" : "◇";
  }

  function renderInventory(state) {
    const fragment = document.createDocumentFragment();
    for (let index = 0; index < 5; index++) {
      const entry = state.inventory[index];
      const slot = document.createElement("button");
      slot.className = `inventory-slot ${entry?.kind === "skill" ? "skill" : ""}`;
      slot.dataset.slot = String(index + 1);
      slot.disabled = !entry || !canPlay() || busy;
      const key = document.createElement("small");
      key.className = "slot-key";
      key.textContent = String(index + 1);
      const icon = document.createElement("span");
      icon.className = "slot-icon";
      icon.textContent = entry ? inventoryIcon(entry) : "·";
      const name = document.createElement("span");
      name.className = "slot-name";
      name.textContent = entry?.name || "Libre";
      slot.append(key, icon, name);
      slot.setAttribute("aria-label", entry ? `Emplacement ${index + 1} : ${entry.name}. ${entry.description}` : `Emplacement ${index + 1} libre`);
      if (entry) {
        slot.title = `${entry.name} · ${entry.description}`;
        slot.addEventListener("click", () => { if (canPlay()) request(`USE_${index + 1}`); });
      }
      fragment.appendChild(slot);
    }
    $("inventory").replaceChildren(fragment);
    $("inventory-count").textContent = `${state.inventory.length} / 5 emplacements`;
  }

  function renderJourney(state) {
    const fragment = document.createDocumentFragment();
    // Display labels only. The server determines the campaign and its progression.
    const labels = ["Jardin des premières pièces", "Le détour verrouillé", "La salle des sentinelles", "La chasse circulaire", "Le trésor gardé", "La dernière garde"];
    for (let index = 0; index < state.levelCount; index++) {
      const complete = index < state.levelIndex || state.status === "won";
      const active = index === state.levelIndex && !complete;
      const item = document.createElement("li");
      item.className = complete ? "complete" : active ? "current" : "";
      const number = document.createElement("span");
      number.textContent = String(index + 1).padStart(2, "0");
      const name = document.createElement("strong");
      name.textContent = index === state.levelIndex ? state.levelName : labels[index] || `Carte ${index + 1}`;
      const mark = document.createElement("i");
      mark.setAttribute("aria-hidden", "true");
      mark.textContent = complete ? "✓" : active ? "●" : "○";
      item.append(number, name, mark);
      fragment.appendChild(item);
    }
    $("journey").replaceChildren(fragment);
  }

  function render(state) {
    current = state;
    saved = { version: 2, name: state.playerName, seed: state.seed, history: state.history };
    try { localStorage.setItem(storageKey, JSON.stringify(saved)); } catch (_) { storageAvailable = false; }
    $("save-note").textContent = storageAvailable ? "Sauvegarde automatique dans ce navigateur." : "Stockage indisponible : gardez cet onglet ouvert pour continuer.";
    $("score").textContent = state.score;
    $("lives").textContent = state.lives;
    $("coins").textContent = state.coinsRemaining;
    $("enemies").textContent = state.enemiesRemaining;
    $("level-number").textContent = `CARTE ${String(state.levelIndex + 1).padStart(2, "0")} / ${String(state.levelCount).padStart(2, "0")}`;
    $("level-name").textContent = state.levelName;
    $("player-name").textContent = `${state.playerName} · ${state.defeatedEnemies} sentinelle${state.defeatedEnemies > 1 ? "s" : ""} vaincue${state.defeatedEnemies > 1 ? "s" : ""}`;
    $("objective").textContent = state.objectiveText;
    $("steps").textContent = `${state.turns} tour${state.turns > 1 ? "s" : ""}`;
    $("topology").textContent = state.toroidal ? "↔ Carte circulaire" : "Bords fermés";
    $("freeze").hidden = state.freezeTurns <= 0;
    $("freeze").textContent = `❄ ${state.freezeTurns} tours`;
    renderBoard(state);
    renderInventory(state);
    renderJourney(state);
    $("next").hidden = state.status !== "level-complete";
    $("replay").hidden = !["won", "game-over"].includes(state.status);
    $("retry").hidden = true;
    feedback.classList.toggle("success", ["level-complete", "won"].includes(state.status));
    feedback.classList.toggle("danger", state.status === "game-over");
    if (state.status === "won") message.textContent = `Les jardins sont à vous, ${state.playerName} ! ${state.score} points et ${state.lives} vie${state.lives > 1 ? "s" : ""} restante${state.lives > 1 ? "s" : ""}.`;
    else if (state.status === "game-over") message.textContent = "Vos vies sont épuisées. Rejouez pour tenter un autre chemin.";
    else if (state.status === "level-complete") message.textContent = "Objectif accompli. La prochaine carte vous attend !";
    else message.textContent = state.messages.join(" ") || "Observez les sentinelles, puis choisissez votre prochain pas.";
    if (migrationNotice) { message.textContent = migrationNotice; migrationNotice = ""; }
    $("loading").hidden = true;
    if (state.status !== "playing") stopHolding();
  }

  function updateControls() {
    for (const button of moveButtons) button.disabled = busy || !canPlay();
    for (const slot of document.querySelectorAll("[data-slot]")) slot.disabled = busy || !canPlay() || !current?.inventory[Number(slot.dataset.slot) - 1];
    $("sort").disabled = busy || !canPlay() || !current?.inventory.length;
    $("next").disabled = busy;
    $("restart").disabled = busy || !current;
    $("confirm-restart").disabled = busy;
    $("retry").disabled = busy;
    $("replay").disabled = busy;
    $("menu").disabled = busy;
    $("pause").disabled = !current;
    board.setAttribute("aria-busy", String(busy));
    board.classList.toggle("busy", busy);
    $("pause-overlay").hidden = !paused;
    $("pause").textContent = paused ? "▷" : "Ⅱ";
    $("pause").setAttribute("aria-label", paused ? "Reprendre le jeu" : "Mettre le jeu en pause");
  }

  async function request(command = "STATE", replacement = null) {
    if (busy) return false;
    busy = true;
    updateControls();
    let completed = false;
    try {
      const outgoing = replacement || saved;
      const response = await fetch("/api/game", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8" },
        body: new URLSearchParams({ name: outgoing.name, seed: outgoing.seed, history: outgoing.history, command }),
        signal: AbortSignal.timeout(20000),
      });
      const data = await response.json();
      if (!response.ok) throw Object.assign(new Error(data.error || "Le jeu ne répond pas pour le moment."), { apiError: true });
      if (data.version !== 2 || !Array.isArray(data.terrain)) throw new Error("Version de jeu incompatible.");
      render(data);
      completed = true;
    } catch (error) {
      $("loading").hidden = true;
      feedback.classList.add("danger");
      message.textContent = error.apiError ? error.message : error.name === "TimeoutError" ? "Le réveil du jeu prend un peu de temps. Réessayez la connexion." : "Connexion interrompue. Votre dernière action confirmée est conservée.";
      $("retry").hidden = Boolean(error.apiError);
      if (command === "STATE" && error.apiError && saved.history) message.textContent += " Ouvrez le menu pour commencer une nouvelle aventure.";
      $("menu-error").textContent = error.apiError ? error.message : "La connexion a échoué. Réessayez.";
      stopHolding();
    } finally { busy = false; updateControls(); }
    return completed;
  }

  function move(direction) { if (canPlay()) request(direction); }
  function startHolding(direction) {
    if (heldDirection === direction || !canPlay()) return;
    stopMovement(); heldDirection = direction; move(direction);
    holdTimer = setInterval(() => { if (heldDirection) move(heldDirection); }, 180);
  }
  function setPaused(value) { paused = value; stopHolding(); updateControls(); }
  function openDialog(dialog) { stopHolding(); dialog.showModal(); updateControls(); }
  for (const dialog of dialogs) dialog.addEventListener("close", updateControls);
  for (const button of moveButtons) {
    button.addEventListener("pointerdown", (event) => {
      if (event.button !== 0) return;
      event.preventDefault(); stopHolding(); button.setPointerCapture(event.pointerId); startHolding(button.dataset.move);
    });
    button.addEventListener("pointerup", stopHolding);
    button.addEventListener("pointercancel", stopHolding);
    button.addEventListener("lostpointercapture", stopHolding);
    button.addEventListener("click", (event) => { if (event.detail === 0) move(button.dataset.move); });
  }
  $("next").addEventListener("click", () => request("NEXT"));
  $("sort").addEventListener("click", () => request("SORT"));
  $("retry").addEventListener("click", () => request("STATE"));
  $("pause").addEventListener("click", () => setPaused(!paused));
  $("resume").addEventListener("click", () => { setPaused(false); board.focus(); });
  $("restart").addEventListener("click", () => openDialog($("restart-dialog")));
  $("replay").addEventListener("click", () => openDialog($("restart-dialog")));
  $("cancel-restart").addEventListener("click", () => $("restart-dialog").close());
  $("confirm-restart").addEventListener("click", async () => {
    if (await request("RESTART")) { $("restart-dialog").close(); setPaused(false); board.focus(); }
  });
  $("menu").addEventListener("click", () => {
    $("name-input").value = saved.name; $("seed-input").value = saved.seed; $("menu-error").textContent = "";
    openDialog($("menu-dialog"));
  });
  $("close-menu").addEventListener("click", () => $("menu-dialog").close());
  $("new-game-form").addEventListener("submit", async (event) => {
    event.preventDefault(); $("menu-error").textContent = "";
    const replacement = { name: $("name-input").value.trim(), seed: $("seed-input").value, history: "" };
    if (await request("RESTART", replacement)) { $("menu-dialog").close(); setPaused(false); board.focus(); }
  });
  $("credits").addEventListener("click", () => openDialog($("credits-dialog")));
  $("close-credits").addEventListener("click", () => $("credits-dialog").close());
  const keys = { ArrowUp: "UP", ArrowLeft: "LEFT", ArrowDown: "DOWN", ArrowRight: "RIGHT", z: "UP", w: "UP", q: "LEFT", a: "LEFT", s: "DOWN", d: "RIGHT" };
  document.addEventListener("keydown", (event) => {
    if (event.altKey || event.ctrlKey || event.metaKey || /^(INPUT|TEXTAREA|SELECT)$/.test(event.target.tagName) || dialogs.some((dialog) => dialog.open)) return;
    const direction = keys[event.key] || keys[event.key.toLowerCase()];
    if (direction) {
      event.preventDefault();
      if (!event.repeat && canPlay()) {
        const key = event.code || event.key;
        pressedDirections.delete(key);
        pressedDirections.set(key, direction);
        startHolding(direction);
      }
    }
    else if (/^[1-5]$/.test(event.key) && !event.repeat && canPlay()) { event.preventDefault(); request(`USE_${event.key}`); }
    else if (event.key === "Escape" && !event.repeat) { event.preventDefault(); setPaused(!paused); }
  });
  document.addEventListener("keyup", (event) => {
    if (!pressedDirections.delete(event.code || event.key)) return;
    const remaining = [...pressedDirections.values()].at(-1);
    if (remaining) startHolding(remaining);
    else stopMovement();
  });
  window.addEventListener("blur", stopHolding);
  document.addEventListener("visibilitychange", () => { if (document.hidden && current) setPaused(true); });
  updateControls(); request();
})();
