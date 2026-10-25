package tetris.feature.item;

/** Board의 읽기 전용 모습 (ARC-4). 아이템 효과가 보드를 직접 바꾸지 못하게 해, 삭제 순서는 GameSession 한 곳에서 관리한다. */
public interface BoardView {
    /** 논리 좌표 (row, col)가 비어 있는지. 숨은 줄 포함, 보드 밖이면 false */
    boolean isEmpty(int row, int col);

    /** 한 행의 10칸이 모두 채워졌는지 (row: 0..19). 범위 밖이면 false */
    boolean isRowFull(int row);
}
