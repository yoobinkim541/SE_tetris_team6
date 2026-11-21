package tetris.feature.network.lobby;

import java.util.Optional;
import tetris.feature.network.tools.LatencySample;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import tetris.feature.versus.api.BattleClient;
import tetris.feature.versus.api.MatchConfig;

/**
 * S7 consumer port, not a socket implementation. All calls return promptly on the EDT.
 * Implementations dispatch snapshot callbacks and asynchronous stage completion onto
 * Swing's EDT through their injected dispatcher.
 */
public interface LobbyClient extends AutoCloseable {
    /** Completes on entering LOBBY; fails exceptionally on connection failure. */
    CompletionStage<Void> host(int port);
    CompletionStage<Void> connect(String address, int port);
    /**
     * rememberPeer가 false면 접속에 성공해도 최근 접속 주소에 남기지 않는다.
     * 인터넷 대전은 이 PC의 중계 루프백 주소로 접속하므로 남길 주소가 아니다
     */
    default CompletionStage<Void> connect(String address, int port, boolean rememberPeer) {
        return connect(address, port);
    }
    /** Idempotently cancels pending establishment and returns to INITIAL. */
    void cancel();
    /** Host-only in LOBBY; increments revision and clears both votes. */
    void selectConfig(MatchConfig config);
    /** Rejects a vote referencing an outdated config revision. */
    void ready(long configRevision, boolean ready);
    /** Idempotently releases the lobby/match and returns to INITIAL. */
    void leave();
    /**
     * After START: the match handoff. The host's engine starts on the first call (STARTING -> PLAYING);
     * the client's receiver already exists. Empty outside STARTING/PLAYING/RESULT.
     */
    Optional<BattleClient> battleClient();
    /** 레이드 CONFIG면 호스트 엔진 또는 클라이언트 수신기. 대전이면 비어 있다 */
    default Optional<tetris.feature.raid.api.RaidClient> raidClient() {
        return Optional.empty();
    }
    void returnToLobby();
    /** Delivers the current snapshot first, then ordered updates until unsubscribed. */
    Subscription subscribe(Consumer<LobbySnapshot> listener);
    /** Delivers the current link health first, then updates whenever link health status changes. */
    default Subscription subscribeHealth(Consumer<LinkHealth> listener) {
        return () -> { };
    }
    /** 개발자 모드: 연결된 상대와 주고받은 PING/PONG 왕복 표본을 받는다. heartbeat와 probeRtt 응답이 모두 온다 */
    default Subscription subscribeRtt(Consumer<LatencySample> listener) {
        return () -> { };
    }
    /** 개발자 모드: heartbeat와 별도로 intervalMillis마다 PING을 count번 보낸다. 연결이 없으면 아무것도 하지 않는다 */
    default void probeRtt(int count, int intervalMillis) {
    }

    /**
     * 개발자 모드 연결 상태 표시용 통계 (dev-net-overlay). 활성 연결(LOBBY 이후)이 없으면 비어 있다.
     * 수집·조회는 모두 dispatcher 스레드에서 한다.
     */
    default Optional<LinkStats> linkStats() {
        return Optional.empty();
    }
    /** Idempotently cancels work, releases resources and removes subscriptions. */
    @Override void close();

    @FunctionalInterface
    interface Subscription extends AutoCloseable {
        /** Idempotent; no subsequent callbacks after returning. */
        @Override void close();
    }
}
