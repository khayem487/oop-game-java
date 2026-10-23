package game.model;

import java.util.List;

/** Six playable maps. These maps illustrate the course worlds; they are not its exercises. */
public final class Campaign {
    private Campaign() {}
    public static List<LevelDefinition> levels() {
        return List.of(
            LevelDefinition.of("Le jardin des premières pièces", "Ramasser les six pièces. Les murs bloquent, les pièces donnent 10 points.", List.of(
                "#########",
                "#1 . . .#",
                "# ### # #",
                "# . . . #",
                "#########"), false, LevelDefinition.Goal.COLLECT_COINS),
            LevelDefinition.of("Le détour verrouillé", "Ramasser toutes les pièces. Au-delà de 100 points, Crochetage ouvre le passage D.", List.of(
                "###############",
                "#1 . . .#  .  #",
                "# # * # D     #",
                "# . . . #     #",
                "###############"), false, LevelDefinition.Goal.COLLECT_COINS),
            LevelDefinition.of("La salle des sentinelles", "Vaincre les trois sentinelles. Ramasser les armes ; le sablier peut immobiliser les ennemis.", List.of(
                "#############",
                "#1 W W W H..#",
                "# ######### #",
                "# R R R     #",
                "#############"), false, LevelDefinition.Goal.DEFEAT_ENEMIES),
            LevelDefinition.of("La chasse circulaire", "Ramasser les trois pièces. Les bords se rejoignent ; le fantôme traverse les murs et le chasseur poursuit.", List.of(
                "  ## W   ",
                "1 H .   G",
                "  ## # H ",
                " W  C  . ",
                " .  # W  "), true, LevelDefinition.Goal.COLLECT_COINS),
            LevelDefinition.of("Le trésor gardé", "Ramasser le trophée T. Cet objet de quête est consommé immédiatement, sans occuper l'inventaire.", List.of(
                "#############",
                "#1 H W#  T  #",
                "# ### # ### #",
                "# . D   G W #",
                "# W # C     #",
                "#############"), false, LevelDefinition.Goal.FIND_TROPHY),
            LevelDefinition.of("La dernière garde", "Vaincre les trois gardiens. Le chasseur a trois vies : chaque arme n'inflige qu'un dégât.", List.of(
                "#############",
                "#1 W H W W  #",
                "# ### # ### #",
                "# W R # G W #",
                "# ### # ### #",
                "# W   C W H #",
                "#############"), false, LevelDefinition.Goal.DEFEAT_ENEMIES)
        );
    }
}
