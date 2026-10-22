(() => {
  "use strict";
  const storageKey = "oop-game-java-history-v1";
  const board = document.getElementById("board");
  const message = document.getElementById("message");
  const feedback = document.querySelector(".game-feedback");
  const next = document.getElementById("next");
  const retry = document.getElementById("retry");
  const loading = document.getElementById("loading");
  const restart = document.getElementById("restart");
  const movementButtons = [...document.querySelectorAll("[data-move]")];
  let current = null;
  let busy = false;
  let savedHistory = "";
  try {
    savedHistory = localStorage.getItem(storageKey) || "";
  } catch (_) {
    /* Storage is optional. */
  }

  const messages = {
    start: "Ramassez toutes les pièces pour ouvrir le prochain monde.",
    move: "Explorez le jardin. Chaque pièce rapporte 10 points.",
    wall: "Un mur bloque ce passage. Essayez un autre chemin.",
    coin: "Une pièce de plus. +10 points !",
    trap: "Un piège ! −2 vies et retour au départ. Vos pièces restent acquises.",
    next: "Nouveau monde, même aventure. À vous de trouver le chemin.",
  };

  function render(state) {
    current = state;
    savedHistory = state.history;
    try {
      localStorage.setItem(storageKey, state.history);
    } catch (_) {
      /* Continue without persistence. */
    }
    document.getElementById("score").textContent = state.score;
    document.getElementById("lives").textContent = state.lives;
    document.getElementById("coins").textContent = state.coinsRemaining;
    document.getElementById("world-number").textContent =
      `MONDE ${String(state.world).padStart(2, "0")} / ${String(state.worldCount).padStart(2, "0")}`;
    document.getElementById("world-name").textContent = state.name;
    document.getElementById("world-subtitle").textContent = state.subtitle;
    document.getElementById("steps").textContent =
      `${state.steps} déplacement${state.steps > 1 ? "s" : ""}`;
    board.className = `board cols-${state.grid[0].length}`;
    board.setAttribute(
      "aria-label",
      `${state.name}. ${state.coinsRemaining} pièces restantes, ${state.lives} vies. Utilisez les flèches ou les boutons de direction.`,
    );
    const fragment = document.createDocumentFragment();
    const types = {
      "#": "wall",
      1: "player",
      ".": "coin",
      "*": "trap",
      " ": "floor",
    };
    for (const row of state.grid) {
      for (const cell of row) {
        const tile = document.createElement("div");
        tile.className = `tile ${types[cell] || "floor"}`;
        tile.setAttribute("aria-hidden", "true");
        fragment.appendChild(tile);
      }
    }
    board.replaceChildren(fragment);
    for (const item of document.querySelectorAll("[data-world]")) {
      const number = Number(item.dataset.world);
      const complete = number < state.world || state.status === "won";
      item.classList.toggle("current", number === state.world && !complete);
      item.classList.toggle("complete", complete);
      item.querySelector("i").textContent = complete
        ? "✓"
        : number === state.world
          ? "●"
          : "○";
    }
    next.hidden = state.status !== "level-complete";
    retry.hidden = true;
    feedback.classList.toggle(
      "success",
      state.status === "level-complete" || state.status === "won",
    );
    feedback.classList.toggle(
      "danger",
      state.event === "trap" || state.status === "game-over",
    );
    if (state.status === "level-complete")
      message.textContent =
        "Toutes les pièces sont à vous. Le prochain monde vous attend !";
    else if (state.status === "won")
      message.textContent = `Aventure terminée ! ${state.score} points, ${state.lives} vie${state.lives > 1 ? "s" : ""} restante${state.lives > 1 ? "s" : ""}. Rejouez pour affiner votre parcours.`;
    else if (state.status === "game-over")
      message.textContent =
        "Vos vies sont épuisées. Recommencez l’aventure pour tenter un autre chemin.";
    else message.textContent = messages[state.event] || messages.start;
    restart.textContent =
      state.status === "won" || state.status === "game-over"
        ? "↻ Rejouer l’aventure"
        : "↻ Recommencer l’aventure";
    loading.hidden = true;
  }

  function updateControls() {
    for (const button of movementButtons)
      button.disabled = busy || !current || current.status !== "playing";
    next.disabled = busy;
    restart.disabled = busy;
    retry.disabled = busy;
    board.setAttribute("aria-busy", String(busy));
    board.classList.toggle("busy", busy);
  }

  async function request(command = "STATE") {
    if (busy) return;
    busy = true;
    updateControls();
    try {
      const response = await fetch("/api/game", {
        method: "POST",
        headers: {
          "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8",
        },
        body: new URLSearchParams({ history: savedHistory, command }),
        signal: AbortSignal.timeout(15000),
      });
      const data = await response.json();
      if (!response.ok) {
        if (command === "STATE" && response.status === 400 && savedHistory) {
          savedHistory = "";
          busy = false;
          await request("RESTART");
          message.textContent =
            "Votre ancienne sauvegarde n’était plus compatible. Une nouvelle aventure commence.";
          return;
        }
        throw Object.assign(
          new Error(data.error || "Le jeu ne répond pas pour le moment."),
          { apiError: true },
        );
      }
      render(data);
    } catch (error) {
      loading.hidden = true;
      feedback.classList.add("danger");
      message.textContent = error.apiError
        ? error.message
        : error.name === "TimeoutError"
          ? "Le réveil du jeu prend un peu de temps. Réessayez la connexion."
          : "Connexion interrompue. Votre dernier déplacement confirmé est conservé.";
      retry.hidden = Boolean(error.apiError);
    } finally {
      busy = false;
      updateControls();
    }
  }

  function move(direction) {
    if (current?.status === "playing") request(direction);
  }
  for (const button of movementButtons)
    button.addEventListener("click", () => move(button.dataset.move));
  next.addEventListener("click", () => request("NEXT"));
  restart.addEventListener("click", () => request("RESTART"));
  retry.addEventListener("click", () => request("STATE"));
  const keys = {
    ArrowUp: "UP",
    ArrowLeft: "LEFT",
    ArrowDown: "DOWN",
    ArrowRight: "RIGHT",
    z: "UP",
    w: "UP",
    q: "LEFT",
    a: "LEFT",
    s: "DOWN",
    d: "RIGHT",
  };
  document.addEventListener("keydown", (event) => {
    if (
      event.altKey ||
      event.ctrlKey ||
      event.metaKey ||
      /^(INPUT|TEXTAREA|SELECT)$/.test(event.target.tagName)
    )
      return;
    const direction = keys[event.key] || keys[event.key.toLowerCase()];
    if (direction) {
      event.preventDefault();
      move(direction);
    }
  });
  updateControls();
  request();
})();
