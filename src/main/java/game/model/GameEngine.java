package game.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * Complete turn-based game rules. CLI, JavaFX and HTTP adapters all use this model.
 * A seed and a sequence of commands reconstruct exactly the same game state.
 */
public final class GameEngine {
    private final String name;
    private final long seed;
    private final List<LevelDefinition> definitions;
    private SplittableRandom random;
    private Player player;
    private Level level;
    private int levelIndex;
    private int turns;
    private int freezeTurns;
    private String status;
    private final List<String> messages = new ArrayList<>();

    private GameEngine(String name, long seed, List<LevelDefinition> levels) {
        if (levels == null || levels.isEmpty() || levels.size() > 24)
            throw new IllegalArgumentException("Une campagne contient entre 1 et 24 cartes.");
        this.definitions = List.copyOf(levels);
        this.seed = seed;
        // Validate the name once before storing its canonical form.
        this.player = new Player(name);
        this.name = player.getName();
        reset(false);
    }
    public static GameEngine campaign(String name, long seed) { return ofLevels(name, seed, Campaign.levels()); }
    public static GameEngine ofLevels(String name, long seed, List<LevelDefinition> levels) {
        return new GameEngine(name, seed, levels);
    }
    public GameSnapshot snapshot() {
        LevelDefinition definition = definitions.get(levelIndex);
        Position position = player.getPosition();
        return new GameSnapshot(player.getName(), player.getScore(), player.getLives(), player.getDefeatedEnemies(),
                levelIndex, definitions.size(), turns, definition.name(), definition.objective(), status, definition.toroidal(),
                position.row(), position.col(), level.terrain(),
                level.getEnemies().stream().map(Enemy::snapshot).toList(),
                player.inventory().stream().map(InventoryElement::snapshot).toList(),
                level.coinsRemaining(), level.getEnemies().size(), freezeTurns, messages);
    }

    /** Every attempted move is a turn, including a collision with a wall or door. */
    public void move(Direction direction) {
        requirePlaying();
        if (direction == null) throw new IllegalArgumentException("Direction absente.");
        messages.clear();
        turns++;
        Set<String> collided = new HashSet<>();
        Position target = level.neighbour(player.getPosition(), direction).orElse(null);
        boolean lostLife = false;
        if (target == null) messages.add("Le bord de cette carte est fermé.");
        else if (!level.getCell(target).canEnter(player)) {
            messages.add(level.getCell(target) instanceof LockedDoorCell
                    ? "Porte verrouillée : Crochetage s'obtient au-delà de 100 points."
                    : "Un mur bloque le passage.");
        } else {
            player.moveTo(target);
            Cell cell = level.getCell(target);
            if (cell.isTrap()) {
                level.consumeTrap(target);
                loseLives(2, "Piège détruit : vous perdez 2 vies.");
                lostLife = true;
            } else collectAtPlayer();
        }
        if (!lostLife) lostLife = resolveCollisions(collided);
        boolean frozen = freezeTurns > 0;
        if (frozen) {
            freezeTurns--;
            messages.add("Ennemis immobilisés : " + freezeTurns + " tour(s) restant(s).");
        }
        if (!lostLife && !frozen && player.getLives() > 0) {
            for (Enemy enemy : level.getEnemies()) enemy.takeTurn(level, player, random);
            resolveCollisions(collided);
        }
        learnSkills();
        updateStatus();
    }

    /** Activates an item/skill without spending a movement turn. Indexes start at zero. */
    public boolean useInventory(int index) {
        requirePlaying();
        InventoryElement entry = player.entry(index);
        messages.clear();
        if (entry instanceof Item item) {
            if (item.type() == Item.Type.HOURGLASS) {
                player.removeEntry(index);
                freezeTurns = 10;
                messages.add("Sablier consommé : les ennemis restent immobiles pendant les 10 prochains déplacements.");
                learnSkills();
                return true;
            }
            messages.add(item.type() == Item.Type.WEAPON
                    ? "L'arme est utilisée automatiquement au contact d'un ennemi, puis consommée."
                    : "Le trophée est conservé pour l'objectif de la carte.");
            return false;
        }
        Skill skill = (Skill) entry;
        if (skill.type() == Skill.Type.LOCKPICK) {
            messages.add("Crochetage agit automatiquement lorsque vous traversez une porte D.");
            return false;
        }
        List<Position> destinations = level.emptyTeleportDestinations();
        if (destinations.isEmpty()) {
            messages.add("Aucune autre case vide n'est disponible pour se téléporter.");
            return false;
        }
        player.moveTo(destinations.get(random.nextInt(destinations.size())));
        messages.add("Téléportation : nouvelle position, compétence conservée.");
        return true;
    }

    /** Sorts items before skills, then by their French display name. */
    public void sortInventory() {
        requirePlaying();
        messages.clear();
        player.sortInventory();
        messages.add("Inventaire trié : objets, puis compétences ; ordre alphabétique.");
    }
    public void nextLevel() {
        if (!status.equals("level-complete")) throw new IllegalArgumentException("Terminez d'abord l'objectif de la carte.");
        loadLevel(levelIndex + 1);
    }
    /** Starts from zero, preserving the selected name, seed and campaign definitions. */
    public void restart() { reset(true); }

    private void reset(boolean replacePlayer) {
        if (replacePlayer) player = new Player(name);
        random = new SplittableRandom(seed);
        turns = 0;
        loadLevel(0);
    }
    private void loadLevel(int index) {
        levelIndex = index;
        level = new Level(definitions.get(index), player, index, random);
        freezeTurns = 0;
        status = "playing";
        messages.clear();
        messages.add("Carte " + (index + 1) + " : " + definitions.get(index).objective());
    }
    private void requirePlaying() {
        if (!status.equals("playing")) throw new IllegalArgumentException("Cette carte est terminée : passez à la suivante ou recommencez.");
    }
    private void collectAtPlayer() {
        Cell cell = level.getCell(player.getPosition());
        if (cell.hasCoin()) {
            cell.removeCoin();
            player.addPoints(10);
            messages.add("Pièce ramassée : +10 points.");
        }
        Item item = cell.item();
        if (item != null) {
            if (item.type() == Item.Type.TROPHY) {
                cell.removeItem();
                level.collectedTrophy();
                messages.add("Trophée ramassé et consommé pour l'objectif, sans place d'inventaire.");
            } else if (player.collect(item)) {
                cell.removeItem();
                messages.add(item.name() + " ramassé(e).");
            } else messages.add("Inventaire plein (5 places) : " + item.name() + " reste au sol.");
        }
        learnSkills();
    }
    /** A particular enemy can resolve at most one contact in a movement turn. */
    private boolean resolveCollisions(Set<String> collided) {
        List<Enemy> contacts = level.getEnemies().stream()
                .filter(enemy -> enemy.getPosition().equals(player.getPosition()))
                .filter(enemy -> !collided.contains(enemy.getId())).toList();
        int damage = 0;
        List<String> attackers = new ArrayList<>();
        for (Enemy enemy : contacts) {
            collided.add(enemy.getId());
            if (player.takeWeapon() != null) {
                enemy.takeDamage(1);
                messages.add("Arme consommée contre " + enemy.getName() + " : aucun dégât reçu.");
                if (enemy.getLives() == 0) {
                    level.removeEnemy(enemy);
                    player.defeatedEnemy();
                    messages.add(enemy.getName() + " vaincu.");
                } else {
                    enemy.resetPosition();
                    messages.add(enemy.getName() + " retourne à son départ (" + enemy.getLives() + " vie(s)).");
                }
            } else {
                damage += enemy.collisionDamage();
                attackers.add(enemy.getName());
            }
        }
        if (damage == 0) return false;
        loseLives(damage, "Collision avec " + String.join(", ", attackers) + " : -" + damage + " vie(s).");
        return true;
    }
    private void loseLives(int amount, String message) {
        player.takeDamage(amount);
        player.resetPosition();
        level.resetEnemies();
        messages.add(message);
        messages.add("Le joueur et les ennemis survivants retournent à leur départ.");
    }
    private void learnSkills() {
        if (player.getScore() > 100) learn(Skill.Type.LOCKPICK);
        if (player.getDefeatedEnemies() >= 3) learn(Skill.Type.TELEPORT);
    }
    private void learn(Skill.Type type) {
        if (player.hasSkill(type)) return;
        Skill skill = new Skill(type);
        if (player.collect(skill)) messages.add("Compétence gagnée : " + skill.name() + ".");
        else {
            String text = "Inventaire plein : libérez une place pour apprendre " + skill.name() + ".";
            if (!messages.contains(text)) messages.add(text);
        }
    }
    private void updateStatus() {
        if (player.getLives() == 0) {
            status = "game-over";
            messages.add("GAME OVER. Recommencez de zéro ou quittez.");
        } else if (level.goalReached()) {
            status = levelIndex == definitions.size() - 1 ? "won" : "level-complete";
            messages.add(status.equals("won") ? "Victoire ! Merci d'avoir joué." : "NIVEAU TERMINÉ. La carte suivante est disponible.");
        }
    }
}
