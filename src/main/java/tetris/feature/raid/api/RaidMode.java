package tetris.feature.raid.api;

/** 보스 레이드 모드 (challenge-plan-v2 §2) */
public enum RaidMode {
    /** 1인. 보스 HP는 협동의 55%, 패턴은 항상 나에게 */
    SOLO,
    /** 2인 협동. 보스 HP 하나를 함께 깎고 보스는 어그로 규칙으로 한 명을 노린다 */
    COOP,
    /**
     * 1v1 레이스. 둘은 같은 시드의 보스 순서를 각자 상대한다.
     * 먼저 잡은 사람이 상대에게 쓰레기 2줄을 보내고, 그 순간 둘 다 다음 보스로 넘어간다.
     * 보상 카드·어그로·막아주기·부활·합체 필살기는 없다. 필살 게이지는 각자 채운다.
     */
    RACE;

    public int players() {
        return this == SOLO ? 1 : 2;
    }
}
