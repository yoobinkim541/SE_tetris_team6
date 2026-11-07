package tetris.feature.versus.api;

import java.util.Objects;

/**
 * 한 번의 Lock이 정산된 결과 (R3-ATTACK, R3-ORDER). 줄 삭제가 확정된 뒤(CLEARING 150 ms가 끝난 뒤)
 * 한 번만 발행한다. CLEARING 시작 시점이나 Pause 뒤 CLEARING 재시작 때는 발행하지 않는다.
 *
 * @param clearedRows 삭제된 행 수. 자연 완성 행 + L 아이템 행의 중복 없는 합집합 (D-09)
 * @param attack      상대에게 보낼 줄. clearedRows가 2 미만이면 {@link AttackRows#NONE}
 */
public record LockResult(int clearedRows, AttackRows attack) {
    public LockResult {
        Objects.requireNonNull(attack, "attack");
        if (clearedRows < 0) throw new IllegalArgumentException("negative clearedRows");
        if (clearedRows < VersusRules.MIN_ATTACK_LINES && !attack.isEmpty()) {
            throw new IllegalArgumentException("fewer than " + VersusRules.MIN_ATTACK_LINES + " rows cannot attack");
        }
        if (attack.size() > clearedRows) throw new IllegalArgumentException("attack larger than cleared rows");
    }
}
