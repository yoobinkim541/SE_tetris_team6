package tetris.feature.versus.api;

import java.util.Objects;
import tetris.feature.setting.GameAction;

/**
 * 한 참가자의 키 입력. 모양은 B의 임시 DTO(ui.versus.PlayerCommand)와 같아 import만 바꾸면 된다.
 * PAUSE는 누구나 전체 Pause/Resume을 요청하고 엔진(호스트)이 순서를 정해 적용한다. QUIT 확정은 기권 (D-03).
 * 반복 가능한 이동은 OS 반복 press마다 한 번, ROTATE·HARD_DROP·PAUSE·QUIT은 press→release 사이 한 번 (D-10).
 *
 * @param pressed  true=누름, false=뗌. 반복 이동은 누른 동안만 유지한다
 * @param sequence 보낸 쪽 기준 0부터 증가. 중복·역순 판정용
 */
public record PlayerCommand(PlayerId playerId, GameAction action, boolean pressed, long sequence) {
    public PlayerCommand {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(action, "action");
        if (sequence < 0) throw new IllegalArgumentException("negative sequence");
    }
}
