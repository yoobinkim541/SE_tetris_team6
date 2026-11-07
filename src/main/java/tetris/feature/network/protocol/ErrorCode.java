package tetris.feature.network.protocol;

/** 받은 프레임을 엔진에 넘기지 않고 연결을 끊어야 하는 이유. ERROR 메시지 payload에도 그대로 쓴다 */
public enum ErrorCode {
    /** 길이 접두가 0 이하이거나 본문이 JSON 객체가 아니다 */
    MALFORMED_FRAME,
    /** 본문이 {@link WireFormat#MAX_FRAME_BYTES}를 넘는다 */
    FRAME_TOO_LARGE,
    /** {@link WireFormat#VERSION}과 다른 버전 */
    UNSUPPORTED_VERSION,
    /** {@link MessageType}에 없는 종류 */
    UNKNOWN_TYPE,
    /** 필드가 없거나 타입·값이 규칙에 맞지 않다 */
    INVALID_FIELD,
    /** 순번이 직전 순번 + 1이 아니다 (누락·중복·역순) */
    SEQUENCE_GAP
}
