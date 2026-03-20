import java.util.Scanner;

class TestWorld2 {
    public static void main() {
        char[][] grid = new char[][]{
                {'#', '#', '#', '#', '#'},
                {'#', ' ', ' ', ' ', '#'},
                {'#', ' ', '#', ' ', '#'},
                {'#', ' ', ' ', ' ', '#'},
                {'#', '#', '#', '#', '#'}
        };
        Level fixedLevel = new Level(grid);
        fixedLevel.placePlayer(1, 1);
        fixedLevel.printGrid();

        System.out.println();

        Level randomLevel = new Level();
        randomLevel.randomPlacePlayer();
        randomLevel.printGrid();
        System.out.println();
        Scanner input = new Scanner(System.in);
        while (true) {
            System.out.print("Enter Your Choice: ");
            String choice = input.nextLine();
            if (choice.equals(""))
                continue;
            char c = Character.toLowerCase(choice.charAt(0));
            if (c == 'x') break;

            switch (c) {
                case 'z': randomLevel.movePlayer(Level.Direction.UP); break;
                case 'q': randomLevel.movePlayer(Level.Direction.LEFT); break;
                case 's': randomLevel.movePlayer(Level.Direction.DOWN); break;
                case 'd': randomLevel.movePlayer(Level.Direction.RIGHT); break;
                default:  break;
            }
        }
    }
}
