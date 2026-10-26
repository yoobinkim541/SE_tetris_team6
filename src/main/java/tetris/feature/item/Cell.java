package tetris.feature.item;

/** 보드 위의 한 칸. 논리 좌표를 쓴다: row 0..19(위->아래), 그 위의 숨은 줄은 -2..-1, col 0..9 */
public record Cell(int row, int col) {
}
