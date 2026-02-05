/**
 * Simple test class for Player.
 */
class Main{
    /**
     * Runs basic tests for World 1.
     */
    public static void main() {
        System.out.println("player count : "+Player.getTotalPlayers());
        Player bob = new Player("Bob");
        Player alice = new Player("Alice");
        System.out.println(bob);
        System.out.println(alice);
        // Tests required by Level 5
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
