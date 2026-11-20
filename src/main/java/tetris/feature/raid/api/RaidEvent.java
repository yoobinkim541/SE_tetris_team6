package tetris.feature.raid.api;

import java.util.Objects;

/**
 * 화면 연출용 사건. 스냅샷마다 직전 스냅샷 뒤에 생긴 것만 담긴다.
 *
 * @param player 관련 참가자 번호(0, 1). 없으면 -1
 * @param value  데미지·콤보 수 등. 없으면 0
 */
public record RaidEvent(Type type, int player, int value) {
    public enum Type {
        /** 줄 삭제로 보스에게 데미지. value=데미지 */
        DAMAGE,
        /** 럭키 블록 크리티컬. value=데미지 */
        CRITICAL,
        /** 콤보. value=콤보 수(2부터) */
        COMBO,
        /** T-스핀 삭제 */
        TSPIN,
        /** 협동 동시 공격. value=추가 데미지 */
        SYNC,
        /** 보스가 패턴을 썼다. value=PatternId ordinal */
        BOSS_ATTACK,
        /** 막아주기 성공(player=막은 사람). value=1 약화, 2 완전 무효 */
        BLOCKED,
        /** 방패 카드로 패턴 무효 */
        SHIELDED,
        /** 정화로 팀원 효과 단축(player=정화한 사람) */
        CLEANSED,
        /** 한 명이 쓰러졌다 */
        PLAYER_DOWN,
        /** 부활(player=부활한 사람) */
        REVIVED,
        /** 합체 필살기 발동. value=데미지 */
        ULTIMATE,
        /** 보스 등장. value=BossId ordinal */
        BOSS_SPAWN,
        /** 보스 처치. value=BossId ordinal */
        BOSS_DEFEATED,
        /** 황금 테트로가 도망갔다 */
        BOSS_ESCAPED,
        /** 보상 카드 선택. value=CardId ordinal */
        CARD_PICKED
    }

    public RaidEvent {
        Objects.requireNonNull(type, "type");
        if (player < -1 || player > 1) throw new IllegalArgumentException("player must be -1..1");
    }
}
