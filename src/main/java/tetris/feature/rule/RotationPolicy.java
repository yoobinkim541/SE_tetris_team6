package tetris.feature.rule;

import tetris.component.block.Block;
import tetris.feature.data.Board;

/** 회전 정책 (ROT-5). 1차: BasicRotationPolicy(Wall Kick 없음). 현재 기본: SrsRotationPolicy(SRS Wall Kick, ROT-8). */
public interface RotationPolicy {
    /**
     * 시계방향 90° 회전을 시도한다.
     * 유효하면 block의 shape를 갱신하고 true, 충돌이면 상태를 바꾸지 않고 false.
     */
    boolean rotate(Board board, Block block);

    /**
     * rotate와 같지만 성공했을 때 쓰인 킥 번호(0 = 제자리, 1..4 = 대체 위치)를, 실패하면 -1을 돌려준다.
     * T-스핀 판정에서 마지막 킥(4번)으로 들어간 회전은 미니가 아닌 T-스핀으로 본다 (TSP-3).
     */
    default int tryRotate(Board board, Block block) {
        return rotate(board, block) ? 0 : -1;
    }

    /**
     * 반시계 90° 회전. 성공하면 킥 번호(0 = 제자리), 실패하면 -1.
     * 1차 규칙(BasicRotationPolicy)도 제자리 반시계는 가능하지만, 클래식 조작에서는 세션이 호출하지 않는다 (ROT-9).
     */
    default int tryRotateCounterClockwise(Board board, Block block) {
        return -1;
    }
}
