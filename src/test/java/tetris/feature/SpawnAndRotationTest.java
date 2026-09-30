package tetris.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import tetris.component.block.Block;
import tetris.component.block.IBlock;
import tetris.component.block.JBlock;
import tetris.component.block.LBlock;
import tetris.component.block.OBlock;
import tetris.component.block.SBlock;
import tetris.component.block.TBlock;
import tetris.component.block.ZBlock;
import tetris.feature.data.Board;

/** SPN-1, SPN-4, ROT-2~4, ROT-6 (Spawn 앵커, 회전 4회 원복, O 불변, 벽 근처 회전 실패, Spawn 충돌) */
public class SpawnAndRotationTest {
    //#region Spawn
    @Test
    void Spawn_열은_행렬_크기_기준_가운데이고_왼쪽으로_내림() { // SPN-1
        Board board = new Board();
        for (Block block : new Block[] {new IBlock(), new JBlock(), new LBlock(), new SBlock(), new TBlock(), new ZBlock()}) {
            assertEquals(3, board.getSpawnColumn(block));
        }
        assertEquals(4, board.getSpawnColumn(new OBlock()));
    }
    //#endregion
}
