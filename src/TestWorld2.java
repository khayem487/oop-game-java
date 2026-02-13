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
    }
}
