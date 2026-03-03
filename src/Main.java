import java.util.Scanner;

/**
 * Entry point for running a level loaded from a text file.
 */
class Main{
    /**
     * Starts the game loop using a level file passed as argument.
     *
     * @param args command-line arguments, expected: &lt;level-file&gt;
     */
    public static void main(String[] args) {
        //TestWorld1.main();
        //TestWorld2.main();
        if (args.length!=1) {
            System.out.println("Usage: java Main <level-file>");
            return;
        }
        Player player =new Player();

        Level level1 = new Level();
        level1.randomPlacePlayer();
        level1.printGrid();
        Scanner input = new Scanner(System.in);
        while (true && level1.getCoinCounter()>0) {
            System.out.println("current score : " +player.getScore());
            System.out.println("coins remaining " +level1.getCoinCounter());
            System.out.print("Enter Your Choice: ");
            String choice = input.nextLine();
            if (choice.equals(""))
                continue;
            char c = Character.toLowerCase(choice.charAt(0));
            if (c == 'x') break;

            switch (c) {
                case 'z':
                    level1.movePlayer(Level.Direction.UP);
                    break;
                case 'q':
                    level1.movePlayer(Level.Direction.LEFT);
                    break;
                case 's':
                    level1.movePlayer(Level.Direction.DOWN);
                    break;
                case 'd':
                    level1.movePlayer(Level.Direction.RIGHT);
                    break;
                default:
                    break;
            }
        }

    }
}
