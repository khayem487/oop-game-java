/**
 * Models a player with a stable identity and a score that never goes negative.
 */
public class Player {
    /** Fixed identity used for comparison and display. */
    final private String name;
    /** Current score, always kept non-negative. */
    private int score = 0;
    /** Counts how many Player instances were created. */
    static private int totalPlayers = 0;

    /**
     * Creates a player with a given name and an initial score of 0.
     *
     * @param name player name
     */
    public Player(String name) {
        this.name = name;
        totalPlayers += 1;
    }

    /**
     * Creates a player with an auto-generated name to keep IDs unique.
     */
    public Player() {
        this("Player" + (totalPlayers + 1));
    }

    /**
     * Returns the global number of created players for world-level tracking.
     *
     * @return total players created
     */
    public static int getTotalPlayers() {
        return totalPlayers;
    }

    /**
     * Returns the player's identity used in comparisons and output.
     *
     * @return player name
     */
    public String getName() {
        return this.name;
    }

    /**
     * Returns the current score for display or game logic.
     *
     * @return current score
     */
    public int getScore() {
        return score;
    }

    /**
     * Formats the player for display with the singular/plural point suffix.
     *
     * @return formatted player string
     */
    public String toString() {
        String point = "pts";
        if (this.score == 1 || this.score == 0 )
            point = "pt";
        return this.name + " : " + this.score + point;
    }

    /**
     * Adds points when a reward is collected; ignores non-positive inputs.
     *
     * @param points points to add
     */
    public void addPoints(int points) {
        if (points<=0)
            return;
        this.score += points;
    }

    /**
     * Removes points on penalties and clamps the score at zero.
     *
     * @param points points to remove
     */
    public void removePoints(int points) {
        if (points<=0)
            return;
        this.score -= points;
        if (this.score < 0)
            this.score = 0;
    }

    /**
     * Compares players by name (case-insensitive) to keep identity stable.
     *
     * @param o object to compare
     * @return true if equal, false otherwise
     */
    public boolean equals(Object o) {
        if (o == null)
            return false;
        if (this == o)
            return true;
        if (!(o instanceof Player))
            return false;
        Player obj = (Player) o;
        if (this.name == null || obj.name == null)
            if (this.name == null && obj.name == null)
                return true;
            else
                return false;
        return this.name.equalsIgnoreCase(obj.name);
    }

    /**
     * Hash code consistent with case-insensitive name equality.
     *
     * @return hash code
     */
    public int hashCode() {
        return (name == null) ? 0 : name.toLowerCase().hashCode();
    }

    /**
     * Runs basic tests for World 1.
     */
    public void test(){
        System.out.println("player count : "+Player.getTotalPlayers());
        Player bob = new Player("Bob");
        Player alice = new Player("Alice");
        System.out.println(bob);
        System.out.println(alice);
        System.out.println(alice.equals("Alice")); // should be false
        System.out.println(alice.equals(bob));     // should be false
        Player bob2 = new Player("BOB");
        System.out.println(bob.equals(bob2));      // should be true
        System.out.println(bob == bob2);           // should be false
        Player bobRef = bob;
        System.out.println(bob == bobRef);         // should be true
        Player p4 = new Player();
        Player p5 = new Player();
        System.out.println(p4);
        System.out.println(p5);
        System.out.println("player count : "+Player.getTotalPlayers());
        bobRef=null;
    }
}
