/**
 * P2P 전송 계약 (S7-C1, R3-NETTEST). TCP 위의 4바이트 big-endian 길이 접두 + UTF-8 JSON 프레임이다.
 *
 * <p>이 패키지는 소켓·스레드·게임 엔진을 모른다. 바이트와 {@link tetris.feature.network.protocol.Message}
 * 사이의 변환과 검증만 맡고, 연결은 {@code tetris.feature.network}가 담당한다.
 * 형식과 오류 정책은 {@code docs/req3/network-protocol.md}에 기록한다.
 */
package tetris.feature.network.protocol;
