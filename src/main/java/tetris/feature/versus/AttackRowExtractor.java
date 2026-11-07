package tetris.feature.versus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.TreeSet;
import tetris.component.block.Block;
import tetris.feature.versus.api.AttackRows;
import tetris.feature.versus.api.VersusRules;

/** Captures the final deletion-row union before the board is changed. */
public final class AttackRowExtractor {
    private AttackRowExtractor() { }
    public static AttackRows extract(int[][] cells, Collection<Integer> deletedRows, Block locked) {
        Objects.requireNonNull(cells); Objects.requireNonNull(deletedRows); Objects.requireNonNull(locked);
        if(cells.length!=VersusRules.BOARD_ROWS) throw new IllegalArgumentException("board height");
        for(int[] row:cells) {
            if(row==null || row.length!=VersusRules.BOARD_COLS) throw new IllegalArgumentException("board width");
            for(int value:row) if(value<0 || value>VersusRules.ATTACK_CELL) throw new IllegalArgumentException("board cell");
        }
        var visible=new TreeSet<Integer>();
        for(int row:deletedRows) if(row>=0 && row<VersusRules.BOARD_ROWS) visible.add(row);
        if(visible.size()<VersusRules.MIN_ATTACK_LINES) return AttackRows.NONE;
        var result=new ArrayList<int[]>(); int[][] shape=locked.getShape();
        for(int row:visible) {
            int[] attack=new int[VersusRules.BOARD_COLS];
            for(int col=0;col<attack.length;col++) {
                int localRow=row-locked.getRow(), localCol=col-locked.getCol();
                boolean footprint=localRow>=0 && localRow<shape.length && localCol>=0
                    && localCol<shape[localRow].length && shape[localRow][localCol]!=0;
                attack[col]=cells[row][col]==0 || footprint ? 0 : VersusRules.ATTACK_CELL;
            }
            result.add(attack);
        }
        return new AttackRows(result);
    }
}
