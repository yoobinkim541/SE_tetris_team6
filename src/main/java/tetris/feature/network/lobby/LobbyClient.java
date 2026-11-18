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
    /** Idempotently cancels work, releases resources and removes subscriptions. */
    @Override void close();

    @FunctionalInterface
    interface Subscription extends AutoCloseable {
        /** Idempotent; no subsequent callbacks after returning. */
        @Override void close();
    }
}
