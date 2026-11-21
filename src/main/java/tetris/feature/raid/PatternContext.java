package tetris.feature.raid;

import java.util.Random;
import tetris.component.block.Block;
import tetris.feature.game.GameSession;
import tetris.feature.raid.api.PatternId;
import tetris.feature.versus.api.AttackRows;

/** 패턴이 보드·대기열·효과에 손을 댈 때 쓰는 자리. 엔진이 구현한다 */
public interface PatternContext {
    int level();

    boolean coop();

    /** 예고 대상. 두 명이면 -1 */
    int target();

    /** 막아주기. 0, 또는 2 이상(약화). 4 이상 무효는 엔진이 apply를 부르지 않는다 */
    int blockLines();

    Random random();

    int players();

    GameSession session(int player);

    void queueAttack(int player, AttackRows rows);

    void addEffect(int player, PatternId pattern, int remainingPieces, int[] area);

    void forceNext(int player, Block... pieces);

    void setGravity(int player, double multiplier);

    void split();
}
