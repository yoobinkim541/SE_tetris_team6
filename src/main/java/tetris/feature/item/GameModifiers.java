package tetris.feature.item;

import java.util.ArrayList;
import java.util.List;

/**
 * 한 판 동안 이어지는 아이템 지속 효과의 상태. 효과마다 남은 블록 수를 따로 세고, 두 효과는 동시에 켜질 수 있다.
 * 아이템 효과(ItemEffect)는 상태를 갖지 않고 여기에 효과를 켜기만 하며, GameSession은 여기서 배율을 읽는다.
 *
 * 점수 2배: 켜진 블록 자신의 Lock부터 그 뒤 블록 10개의 Lock까지(총 11번) 줄 삭제 점수가 2배다.
 * 슬로우: 켜진 뒤 새로 나오는 블록 10개의 낙하 간격이 1.5배다. Level·점수·카운터에는 영향이 없다.
 * 둘 다 효과 중에 다시 켜면 남은 수를 10으로 되돌리고, 배율은 겹치지 않는다(4배, 2.25배가 되지 않는다).
 *
 * 가정(팀 합의 필요): GameSession은 다음 순서로 부른다.
 * - Lock: 아이템 효과 실행(onLock) → 줄 삭제 점수 × scoreMultiplier() + takeBonusScore() → onLockFinished()
 * - Spawn: onBlockSpawned() → 낙하 간격 × fallMultiplier()로 타이머 시작
 * - 새 게임과 Restart에서 reset()
 */
public class GameModifiers {
    private static final int DOUBLE_SCORE_MULTIPLIER = 2;
    private static final double SLOW_MULTIPLIER = 1.5;
    private static final int EFFECT_BLOCKS = 10;

    private int doubleScoreRemaining;
    private boolean doubleScoreJustActivated; // 켜진 블록 자신의 Lock은 남은 수에서 빼지 않는다
    private int slowRemaining;                // 앞으로 나올 블록 중 슬로우를 받을 블록 수
    private boolean currentBlockSlow;         // 지금 떨어지는 블록이 슬로우 대상인지
    private long bonusScore;                  // 이번 Lock에서 아이템이 준 추가 점수 (폭탄의 칸 점수 등)

    /** 줄 삭제 점수에 곱하는 배율. 점수 2배가 켜져 있으면 2, 아니면 1 */
    public int scoreMultiplier() {
        return doubleScoreRemaining > 0 ? DOUBLE_SCORE_MULTIPLIER : 1;
    }

    /** 지금 떨어지는 블록의 낙하 간격에 곱하는 배율. 슬로우 대상이면 1.5, 아니면 1 */
    public double fallMultiplier() {
        return currentBlockSlow ? SLOW_MULTIPLIER : 1.0;
    }

    // 아래 activate·addBonusScore는 아이템 효과만 부르도록 같은 패키지로 막는다. GameSession과 UI는 읽기·진행 메서드만 쓴다

    /** 점수 2배를 켠다. 이 호출이 일어난 Lock의 줄 삭제 점수부터 2배가 된다 */
    void activateDoubleScore() {
        doubleScoreRemaining = EFFECT_BLOCKS;
        doubleScoreJustActivated = true;
    }

    /** 슬로우를 켠다. 다음에 나오는 블록부터 10개가 대상이다. 이미 떨어지고 있는 블록의 상태는 바꾸지 않는다 */
    void activateSlow() {
        slowRemaining = EFFECT_BLOCKS;
    }

    /**
     * 아이템이 주는 추가 점수를 쌓는다. 줄 삭제 점수와 달리 점수 2배·Level의 영향을 받지 않는다.
     * 가정(팀 합의 필요): ItemResult에 점수 칸이 없어서 계약을 바꾸지 않고 여기로 전달한다.
     * GameSession은 Lock마다 takeBonusScore()로 꺼내 점수에 그대로 더한다.
     */
    void addBonusScore(long points) {
        if (points < 0) throw new IllegalArgumentException("points should be 0 or more. Value : " + points);
        bonusScore += points;
    }

    /**
     * 쌓인 추가 점수를 꺼내고 0으로 비운다. Lock마다 onLockFinished()보다 먼저 불러야 한다.
     * 부르지 않으면 점수가 다음 Lock으로 넘어간다.
     */
    public long takeBonusScore() {
        long points = bonusScore;
        bonusScore = 0;
        return points;
    }

    /**
     * 블록 하나의 Lock 처리(점수 더하기)가 끝났을 때 부른다. 점수 2배의 남은 블록 수를 하나 줄인다.
     * Lock된 블록은 더 이상 떨어지지 않으므로 슬로우 대상 표시도 지운다(다음 Spawn에서 다시 정한다).
     */
    public void onLockFinished() {
        currentBlockSlow = false;
        if (doubleScoreJustActivated) {
            doubleScoreJustActivated = false;
            return;
        }
        if (doubleScoreRemaining > 0) doubleScoreRemaining--;
    }

    /** 새 블록이 나올 때 부른다. 슬로우가 남아 있으면 이 블록을 슬로우 대상으로 정하고 남은 수를 하나 줄인다 */
    public void onBlockSpawned() {
        currentBlockSlow = slowRemaining > 0;
        if (currentBlockSlow) slowRemaining--;
    }

    /**
     * 화면 표시용: 지금 켜져 있는 지속 효과와 각각의 남은 블록 수.
     * 가정(팀 합의 필요): 남은 블록 수는 지금 떨어지는 블록을 포함해 앞으로 효과를 받을 블록 수다.
     * 마지막 대상 블록이 떨어지는 동안 1로 보이고, 그다음 블록부터 목록에서 빠진다.
     */
    public List<ActiveEffect> effects() {
        List<ActiveEffect> active = new ArrayList<>();
        if (doubleScoreRemaining > 0) active.add(new ActiveEffect(ItemType.DOUBLE_SCORE, doubleScoreRemaining));
        int slowBlocks = slowRemaining + (currentBlockSlow ? 1 : 0);
        if (slowBlocks > 0) active.add(new ActiveEffect(ItemType.SLOW, slowBlocks));
        return List.copyOf(active);
    }

    /** 모든 지속 효과와 쌓인 추가 점수를 지운다 */
    public void reset() {
        doubleScoreRemaining = 0;
        doubleScoreJustActivated = false;
        slowRemaining = 0;
        currentBlockSlow = false;
        bonusScore = 0;
    }
}
