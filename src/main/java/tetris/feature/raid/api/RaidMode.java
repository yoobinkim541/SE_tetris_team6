package tetris.feature.raid.api;

/** 보스 레이드 모드 (challenge-plan-v2 §2) */
public enum RaidMode {
    /** 1인. 보스 HP는 협동의 55%, 패턴은 항상 나에게 */
    SOLO,
    /** 2인 협동. 보스 HP 하나를 함께 깎고 보스는 어그로 규칙으로 한 명을 노린다 */
    COOP;

    public int players() {
        return this == SOLO ? 1 : 2;
    }
}
