package tetris.feature.network.lobby;

import java.util.Optional;
import tetris.feature.network.tools.LatencySample;
import java.util.concurrent.CompletableFuture;
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
     * challenge: 중계 서버에 인터넷 방을 만들고 HOSTING에서 친구를 기다린다. 받은 로비 코드는
     * {@link #subscribeRoomCode}로 알린다. 친구가 들어오면 LOBBY로 완료된다.
     */
    default CompletionStage<Void> hostOnline() {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("internet lobby"));
    }
    /** challenge: 로비 코드로 인터넷 방에 들어간다. LOBBY에 들어가면 완료된다 */
    default CompletionStage<Void> joinOnline(String code) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("internet lobby"));
    }
    /** challenge: 인터넷 방을 만들고 친구를 기다리는 동안의 로비 코드. 그 밖에는 비어 있다 */
    default Optional<String> roomCode() {
        return Optional.empty();
    }
    /** challenge: 현재 로비 코드를 먼저 보내고, 코드가 생기거나 사라질 때마다 알린다 */
    default Subscription subscribeRoomCode(Consumer<Optional<String>> listener) {
        listener.accept(Optional.empty());
        return () -> { };
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
    /** RESULT -> LOBBY on the same connection (F-07). Keeps config and the peer's vote; clears the local vote. */
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
