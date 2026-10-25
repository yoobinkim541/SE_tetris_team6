package tetris.feature.item;

import java.util.Set;

/** 아이템이 지우려는 행과 칸. GameSession이 완성 행과 합쳐(R ∪ C) CLEARING 뒤에 삭제한다 (§5 L2). */
public record ItemResult(Set<Integer> rows, Set<Cell> cells) {
    public ItemResult {
        rows = Set.copyOf(rows);
        cells = Set.copyOf(cells);
    }

    public static ItemResult none() {
        return new ItemResult(Set.of(), Set.of());
    }
}
