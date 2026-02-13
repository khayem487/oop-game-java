import java.util.Random;
public class Level {
    private char[][] grid;
    private int playerRow;
    private int playerCol;
    private boolean hasPlayer;

    public Level(char[][] grid) {
        if (grid == null || grid.length == 0 || grid[0].length == 0) {
            throw new IllegalArgumentException("Grid must not be empty");
        }
        this.grid = new char[grid.length][grid[0].length];
        for (int i = 0; i < grid.length; i++) {
            for (int j = 0; j < grid[0].length; j++) {
                this.grid[i][j] = grid[i][j];
            }
        }
    }

    public Level() {
        Random rand = new Random();
        int minSize = 5;
        int maxSize = 20;
        int rows = rand.nextInt(maxSize - minSize + 1) + minSize;
        int cols = rand.nextInt(maxSize - minSize + 1) + minSize;

        this.grid = new char[rows][cols];

        double wallChance = 0.2; // 20% walls inside the borders
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                if (i == 0 || j == 0 || i == rows - 1 || j == cols - 1) {
                    this.grid[i][j] = '#';
                } else {
                    if (rand.nextDouble() < wallChance)
                        this.grid[i][j] = '#';
                    else
                        this.grid[i][j] = ' ';

                }
            }
        }
    }

    public void printGrid() {
        for (int i = 0; i < grid.length; i++) {
            StringBuilder line = new StringBuilder();
            for (int j = 0; j < grid[i].length; j++) {
                char c = grid[i][j];
                if (c == '#') {
                    line.append("⬛");
                } else if (c == '1') {
                    line.append("🧍");
                } else {
                    line.append("  ");
                }
            }
            System.out.println(line);
        }
        if (hasPlayer) {
            System.out.println("player position: (" + playerRow + "," + playerCol + ")");
        }
    }


    // wall: #, player: 1, empty: space
    public void placePlayer(int row, int col) {
        if (row < 0 || col < 0 || row >= grid.length || col >= grid[0].length) {
            throw new IllegalArgumentException("Player position out of bounds");
        }
        if (this.grid[row][col] != ' ') {
            throw new IllegalArgumentException("Player must be placed on empty space");
        }
        if (hasPlayer)
            return;
        this.grid[row][col] = '1';
        playerRow = row;
        playerCol = col;
        hasPlayer = true;
    }

    public void randomPlacePlayer(){
        if (hasPlayer)
            return;
        Random rand = new Random();
        int row;
        int col;
        do {
            row = rand.nextInt(grid.length-1)+1;
            col = rand.nextInt(grid[0].length-1)+1;
        }while (grid[row][col]!=' ');
        grid[row][col] = '1';
        playerRow = row;
        playerCol = col;
        hasPlayer = true;
    }

    public void movePlayer()

}
