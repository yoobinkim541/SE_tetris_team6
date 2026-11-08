package tetris.versus.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import tetris.component.block.*;
import tetris.feature.versus.AttackRowExtractor;

class AttackRowAdapterTest {
    @Test void rotatedIAndLKeepOriginalHolesAndRemoveEveryLockedCell() {
        for(Block block:List.of(new IBlock(),new LBlock())) {
            block.rotateClockwise(); block.setPosition(15,3);
            int[][] cells=new int[20][10];
            for(int[] row:cells) Arrays.fill(row,2);
            cells[15][0]=0;
            var rows=new TreeSet<Integer>();
            for(int r=0;r<block.getShape().length;r++) for(int c=0;c<block.getShape()[r].length;c++)
                if(block.getShape()[r][c]!=0) rows.add(15+r);
            var attack=AttackRowExtractor.extract(cells,rows,block);
            assertEquals(rows.size(),attack.size());
            int index=0;
            for(int row:rows) {
                int[] expected=new int[10];
                for(int col=0;col<10;col++) expected[col]=cells[row][col]==0?0:9;
                int r=row-block.getRow();
                for(int c=0;c<block.getShape()[r].length;c++) if(block.getShape()[r][c]!=0) expected[block.getCol()+c]=0;
                assertArrayEquals(expected,attack.rows().get(index++));
            }
            cells[15][1]=0;
            assertEquals(9,attack.rows().getFirst()[1]);
        }
    }
    @Test void deduplicatesSortsAndCountsOnlyVisibleRowsForAttackThreshold() {
        int[][] cells=new int[20][10]; cells[18][0]=1; cells[19][1]=8;
        var block=new OBlock(); block.setPosition(0,4);
        assertTrue(AttackRowExtractor.extract(cells,List.of(-1,19,19,20),block).isEmpty());
        var attack=AttackRowExtractor.extract(cells,List.of(19,18,19,-1,20),block);
        assertEquals(2,attack.size());
        assertEquals(9,attack.rows().get(0)[0]); assertEquals(9,attack.rows().get(1)[1]);
        assertEquals(0,attack.rows().get(0)[1]);
    }
}
