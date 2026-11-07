/**
 * P2P 로비 계약 (C 소유, S7-C1). 로비 화면(B)이 {@link tetris.feature.network.lobby.LobbyClient}를 호출하고
 * {@link tetris.feature.network.lobby.LobbySnapshot}을 그린다. 소켓 구현은 S8-C1(접속)·S8-C2(준비·시작)에서 만든다.
 * 상태 흐름과 결정 근거는 {@code docs/req3/lobby-contract.md}.
 */
package tetris.feature.network.lobby;
