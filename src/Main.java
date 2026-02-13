import java.util.Scanner;

class Main{
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: java Main <level-file>");
            return;
        }
        Level level = Level.loadFromFile(args[0]);
        if (level == null) {
            return;
        }

        level.printGrid();
        Scanner input = new Scanner(System.in);
        while (true) {
            System.out.print("Enter Your Choice (z/q/s/d, x to quit): ");
            String choice = input.nextLine();
            if (choice.isEmpty()) {
                continue;
            }
            char c = Character.toLowerCase(choice.charAt(0));
            if (c == 'x') {
                break;
            }
            switch (c) {
                case 'z': level.movePlayer(Level.Direction.UP); break;
                case 'q': level.movePlayer(Level.Direction.LEFT); break;
                case 's': level.movePlayer(Level.Direction.DOWN); break;
                case 'd': level.movePlayer(Level.Direction.RIGHT); break;
                default:  break;
            }
        }
    }
}
