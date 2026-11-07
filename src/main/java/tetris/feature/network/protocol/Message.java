package tetris.feature.network.protocol;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Objects;

/**
 * 프레임 하나의 내용. 버전과 순번은 {@link FrameEncoder}가 붙이고 {@link FrameDecoder}가 검사한 뒤 떼어 낸다.
 *
 * @param matchId        경기 식별자. {@link MessageType#requiresMatchId()}이면 필수, 아니면 없어도 된다. 빈 문자열은 금지
 * @param configRevision 로비 설정 revision. 0은 설정 전. {@link MessageType#requiresConfigRevision()}이면 1 이상
 * @param payload        종류별 내용. 생성 시 복사하므로 넘긴 객체를 나중에 바꿔도 메시지는 바뀌지 않는다
 */
public record Message(MessageType type, String matchId, int configRevision, ObjectNode payload) {
    public Message {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(payload, "payload");
        if (matchId != null && matchId.isBlank()) {
            throw new IllegalArgumentException("matchId must not be blank");
        }
        if (type.requiresMatchId() && matchId == null) {
            throw new IllegalArgumentException(type + " requires matchId");
        }
        if (configRevision < 0) {
            throw new IllegalArgumentException("configRevision must not be negative");
        }
        if (type.requiresConfigRevision() && configRevision < 1) {
            throw new IllegalArgumentException(type + " requires configRevision >= 1");
        }
        payload = payload.deepCopy();
    }
}
