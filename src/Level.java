import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

/**
 * Models a game level as a character grid and player position state.
 */
public class Level {
    /** Grid containing walls, empty cells, and the player marker. */
    private char[][] grid;
    /** Current player row index. */
    private int playerRow;
    /** Current player column index. */
    private int playerCol;
    /** Indicates whether the player is currently placed in the grid. */
    private boolean hasPlayer;
    /** Indicates how many coins are remaining to collect */
    private int coinCounter=0;
    private int playerStartRow;
    private int playerStartCol;

    /**
     * Creates a level from an existing grid.
     *
     * @param grid source grid to copy
     */
    public Level(char[][] grid) {
        if (grid == null || grid.length == 0 || grid[0].length == 0) {
            throw new IllegalArgumentException("Grid must not be empty");
        }
        this.grid = new char[grid.length][grid[0].length];
        for (int i = 0; i < grid.length; i++) {
            for (int j = 0; j < grid[0].length; j++) {
                this.grid[i][j] = grid[i][j];
                if (this.grid[i][j] == '.') {
                    coinCounter++;
                }
            }
        }
    }

    public int getCoinCounter() {
        return coinCounter;
    }

    /**
     * Creates a random level with walls on borders and random internal walls.
     */
    public Level() {
        Random rand = new Random();
        int minSize = 5;
        int maxSize = 20;
        int rows = rand.nextInt(maxSize - minSize + 1) + minSize;
        int cols = rand.nextInt(maxSize - minSize + 1) + minSize;

        this.grid = new char[rows][cols];

        double wallChance = 0.2; // 20% walls inside the borders
        double coinChance = 0.1; // 10% coins inside the borders
        double trapChance = 0.025; // 2.5% traps inside the borders
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                if (i == 0 || j == 0 || i == rows - 1 || j == cols - 1) {
                    this.grid[i][j] = '#';
                } else {
                    if (rand.nextDouble() < wallChance)
                        this.grid[i][j] = '#';
                    else if (rand.nextDouble()<coinChance) {
                        this.grid[i][j] = '.';
                        coinCounter++;
                    } else if (rand.nextDouble()<trapChance) {
                        this.grid[i][j] = '*';
                    } else
                        this.grid[i][j] = ' ';

                }
            }
        }
    }

    /**
     * Creates a level from a text file and initializes player state from marker '1'.
     *
     * @param path path to the level text file
     */
    public Level(String path){
        this(readGridFromFile(path));
        int i =0;
        int j = 0;
        while(i<this.grid.length && !this.hasPlayer){
            j=0;
            while(j < this.grid[0].length && !this.hasPlayer) {
                if (this.grid[i][j]=='1') {
                    this.hasPlayer = true;
                    this.playerRow=i;
                    this.playerCol=j;
                }
                j++;
            }
            i++;
        }
    }

    /**
     * Reads a grid from a text file.
     *
     * @param path path to the level text file
     * @return loaded grid, or {@code null} when file cannot be read
     */
    private static char[][] readGridFromFile(String path){
        try{
            List<String> lines = Files.readAllLines(Path.of(path));
            char [][] tempGrid = new char [lines.size()][lines.get(0).length()];
            for (int i = 0; i < tempGrid.length; i++) {
                for (int j = 0; j < tempGrid[0].length; j++) {
                    tempGrid[i][j]=lines.get(i).charAt(j);
                }
            }
            return tempGrid;
        } catch (IOException e) {
            System.err.println("Cannot read level file: " + path);
        }
        return null;
    }

    /**
     * Prints the current level state in the console.
     */
    public void printGrid() {
        for (int i = 0; i < grid.length; i++) {
            StringBuilder line = new StringBuilder();
            for (int j = 0; j < grid[i].length; j++) {
                char c = grid[i][j];
                if (c == '#') {
                    line.append("\uD83D\uDFEB");//⬛⬜◼️⏹️❎🟦🟪🟫🟩🟥
                } else if (c == '1') {
                    line.append("🧙‍♂️");
                } else if (c == '.') {
                    line.append("🪙");
                } else if (c == '*') {
                    line.append("💣");
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


    /**
     * Places the player on a valid empty cell.
     *
     * @param row target row
     * @param col target column
     */
    public void placePlayer(int row, int col) {
        if (row < 0 || col < 0 || row >= grid.length || col >= grid[0].length) {
            throw new IllegalArgumentException("Player position out of bounds");
        }
        if (this.grid[row][col] == '#') {
            throw new IllegalArgumentException("Player must be placed on empty space");
        }
        if (hasPlayer)
            return;
        this.grid[row][col] = '1';
        playerRow = row;
        playerCol = col;
        hasPlayer = true;
    }

    /**
     * Places the player randomly on an empty cell.
     */
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

    /**
     * Moves the player one cell in the given direction.
     *
     * @param dir move direction
     */
    public MoveResult movePlayer(Direction dir) {
        int cRow = 0, cCol = 0;
        switch (dir) {
            case UP:
                cRow--;
                break;
            case DOWN:
                cRow++;
                break;
            case LEFT:
                cCol--;
                break;
            case RIGHT:
                cCol++;
                break;
        }
        MoveResult result = new MoveResult(0,0 );
        try {
            hasPlayer = false;
            grid[playerRow][playerCol] = ' ';
            if (grid[playerRow + cRow][playerCol + cCol] == '.') {
                this.coinCounter--;
                result = new MoveResult(1,0 );
            }
            if (grid[playerRow + cRow][playerCol + cCol] == '*') {
                result = new MoveResult(0,2 );
            }
            placePlayer(playerRow + cRow, playerCol + cCol);
        } catch (IllegalArgumentException e) {
            hasPlayer = true;
            grid[playerRow][playerCol] = '1';

        }
        this.printGrid();
        return result;
    }

    public void saveStartPosition() {
        this.playerStartRow = this.playerRow;
        this.playerStartCol = this.playerCol;
    }

    public void resetPlayerPosition() {
        this.grid[playerRow][playerCol] = ' ';
        this.playerRow = this.playerStartRow;
        this.playerCol = this.playerStartCol;
        this.grid[playerRow][playerCol] = '1';
    }

    /**
     * Allowed movement directions.
     */
    public enum Direction {
        UP,
        LEFT,
        DOWN,
        RIGHT
    }

    public static final class MoveResult {
        private final int coinsCollected;
        private final int dammageDone;

        public MoveResult(int coinsCollected, int dammageDone) {
            this.coinsCollected=Math.max(0,coinsCollected);
            this.dammageDone =Math.max(0,dammageDone) ;
        }

        public int getCoinsCollected() {
            return coinsCollected;
        }

        public int getDammageDone() {
            return dammageDone;
        }
    }
}
