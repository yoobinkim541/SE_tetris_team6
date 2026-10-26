package tetris.feature.item;

import java.util.ArrayList;
import java.util.List;

/**
 * 한 판 동안 이어지는 아이템 지속 효과의 상태. 효과마다 남은 블록 수를 따로 센다.
 * 아이템 효과(ItemEffect)는 상태를 갖지 않고 여기에 효과를 켜기만 하며, GameSession은 여기서 배율을 읽는다.
 *
 * 점수 2배: 켜진 블록 자신의 Lock부터 그 뒤 블록 10개의 Lock까지(총 11번) 줄 삭제 점수가 2배다.
 * 효과 중에 다시 켜면 남은 수를 10으로 되돌리고, 배율은 겹치지 않는다(4배가 되지 않는다).
 *
 * 가정(팀 합의 필요): GameSession은 다음 순서로 부른다.
 * - Lock: 아이템 효과 실행(onLock) → 줄 삭제 점수 × scoreMultiplier() → onLockFinished()
 * - 새 게임과 Restart에서 reset()
 */
public class GameModifiers {
    private static final int DOUBLE_SCORE_MULTIPLIER = 2;
    private static final int EFFECT_BLOCKS = 10;

    private int doubleScoreRemaining;
    private boolean doubleScoreJustActivated; // 켜진 블록 자신의 Lock은 남은 수에서 빼지 않는다

    /** 줄 삭제 점수에 곱하는 배율. 점수 2배가 켜져 있으면 2, 아니면 1 */
    public int scoreMultiplier() {
        return doubleScoreRemaining > 0 ? DOUBLE_SCORE_MULTIPLIER : 1;
    }

    /** 낙하 간격에 곱하는 배율 (슬로우가 켜져 있으면 1.5) */
    public double fallMultiplier() {
        return 1.0;
    }

    // 아래 activate는 아이템 효과만 부르도록 같은 패키지로 막는다. GameSession과 UI는 읽기·진행 메서드만 쓴다

    /** 점수 2배를 켠다. 이 호출이 일어난 Lock의 줄 삭제 점수부터 2배가 된다 */
    void activateDoubleScore() {
        doubleScoreRemaining = EFFECT_BLOCKS;
        doubleScoreJustActivated = true;
    }

    /** 블록 하나의 Lock 처리(점수 더하기)가 끝났을 때 부른다. 점수 2배의 남은 블록 수를 하나 줄인다 */
    public void onLockFinished() {
        if (doubleScoreJustActivated) {
            doubleScoreJustActivated = false;
            return;
        }
        if (doubleScoreRemaining > 0) doubleScoreRemaining--;
    }

    /**
     * 화면 표시용: 지금 켜져 있는 지속 효과와 각각의 남은 블록 수.
     * 가정(팀 합의 필요): 남은 블록 수는 지금 떨어지는 블록을 포함해 앞으로 효과를 받을 블록 수다.
     */
    public List<ActiveEffect> effects() {
        List<ActiveEffect> active = new ArrayList<>();
        if (doubleScoreRemaining > 0) active.add(new ActiveEffect(ItemType.DOUBLE_SCORE, doubleScoreRemaining));
        return List.copyOf(active);
    }

    /** 모든 지속 효과를 지운다 */
    public void reset() {
        doubleScoreRemaining = 0;
        doubleScoreJustActivated = false;
    }
}
