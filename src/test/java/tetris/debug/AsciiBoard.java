package tetris.debug;

import tetris.component.block.Block;
import tetris.component.block.BlockType;
import tetris.feature.data.Board;
import tetris.feature.game.GameSnapshot;

/**
 * 스냅샷을 텍스트 그림으로 바꾸는 디버그 도구. UI 없이 보드 상태를 눈으로 확인하는 용도.
 *
 * 고정 칸은 대문자(I..Z), 현재 블록은 소문자, 빈칸은 '.', 숨은 줄의 빈칸은 ':'.
 * 현재 블록이 고정 칸과 겹치면 '!'로 표시해 판정 버그를 바로 드러낸다.
 * 숨은 줄은 copyCells가 보이는 20줄만 주기 때문에 현재 블록만 보인다.
 */
public final class AsciiBoard {
    private static final char EMPTY = '.';
    private static final char HIDDEN_EMPTY = ':';
    private static final char OVERLAP = '!';
    private static final char UNKNOWN = '#';

    private AsciiBoard() {
    }

    /** 숨은 줄·보이는 20줄 전체와 상태 한 줄 */
    public static String render(GameSnapshot snapshot) {
        StringBuilder sb = new StringBuilder();
        sb.append("    ").append("0123456789".substring(0, Board.WIDTH)).append('\n');
        for (int r = -Board.MARGIN; r < Board.HEIGHT; r++) {
            if (r == 0) sb.append("   +").append("-".repeat(Board.WIDTH)).append("+\n");
            sb.append(String.format("%3d|", r)).append(row(snapshot, r)).append("|\n");
        }
        sb.append("   +").append("-".repeat(Board.WIDTH)).append("+\n");
        sb.append(String.format("state=%s score=%d level=%d lines=%d next=%s",
                snapshot.state(), snapshot.score(), snapshot.level(), snapshot.lines(),
                snapshot.nextBlock() == null ? "-" : String.valueOf(letter(snapshot.nextBlock()))));
        return sb.toString();
    }

    /** 논리 행 하나(-MARGIN..HEIGHT-1)를 WIDTH 글자로. 테스트에서 줄 단위로 비교할 때 쓴다 */
    public static String row(GameSnapshot snapshot, int row) {
        char[] line = new char[Board.WIDTH];
        for (int col = 0; col < Board.WIDTH; col++) {
            line[col] = row < 0 ? HIDDEN_EMPTY : lockedChar(snapshot.cells()[row][col]);
        }
        overlayCurrent(line, snapshot.currentBlock(), row);
        return new String(line);
    }

    public static void print(GameSnapshot snapshot) {
        System.out.println(render(snapshot));
        System.out.println();
    }

    private static char lockedChar(int value) {
        if (value == Board.EMPTY) return EMPTY;
        BlockType[] types = BlockType.values();
        return value >= 1 && value <= types.length ? types[value - 1].letter() : UNKNOWN;
    }

    private static void overlayCurrent(char[] line, Block current, int row) {
        if (current == null) return;
        int[][] shape = current.getShape();
        int shapeRow = row - current.getRow();
        if (shapeRow < 0 || shapeRow >= shape.length) return;

        char mark = Character.toLowerCase(letter(current));
        for (int c = 0; c < shape[shapeRow].length; c++) {
            if (shape[shapeRow][c] == 0) continue;
            int col = current.getCol() + c;
            if (col < 0 || col >= Board.WIDTH) continue;
            boolean occupied = line[col] != EMPTY && line[col] != HIDDEN_EMPTY;
            line[col] = occupied ? OVERLAP : mark;
        }
    }

    private static char letter(Block block) {
        try {
            return block.getType().letter();
        } catch (UnsupportedOperationException e) {
            return '@'; // 종류가 없는 테스트용 익명 블록
        }
    }
}
