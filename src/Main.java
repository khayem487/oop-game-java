import java.util.Scanner;

/**
 * Simple test class for Player.
 */
class Main{
    /**
     * main of the projects
     */
    public static void main(String[] args) {
        //TestWorld1.main();
        //TestWorld2.main();
        Level level1 = new Level("C:\\Users\\khaye\\OneDrive\\Documents\\GL1\\java\\app\\src\\level1.txt");
        level1.printGrid();
        Scanner input = new Scanner(System.in);
        while (true) {
            System.out.print("Enter Your Choice: ");
            String choice = input.nextLine();
            if (choice.equals(""))
                continue;
            char c = Character.toLowerCase(choice.charAt(0));
            if (c == 'x') break;

            switch (c) {
                case 'z': level1.movePlayer(Level.Direction.UP); break;
                case 'q': level1.movePlayer(Level.Direction.LEFT); break;
                case 's': level1.movePlayer(Level.Direction.DOWN); break;
                case 'd': level1.movePlayer(Level.Direction.RIGHT); break;
                default:  break;
            }
        }

    }
}
