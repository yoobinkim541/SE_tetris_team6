package tetris.versus.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tetris.component.block.OBlock;
import tetris.feature.network.PeerEstablisher;
import tetris.feature.network.lobby.LobbySnapshot;
import tetris.feature.network.lobby.LobbySnapshot.State;
import tetris.feature.network.lobby.SocketLobbyClient;
import tetris.feature.network.match.ClientRaidClient;
import tetris.feature.network.match.HostBattleClient;
import tetris.feature.network.match.HostRaidClient;
import tetris.feature.network.match.MatchPulse;
import tetris.feature.network.protocol.FrameEncoder;
import tetris.feature.network.protocol.RaidPayloads;
import tetris.feature.network.protocol.WireFormat;
import tetris.feature.raid.api.RaidMode;
import tetris.feature.raid.api.RaidPhase;
import tetris.feature.raid.api.RaidSnapshot;
import tetris.feature.setting.ControlStyle;
import tetris.feature.setting.GameAction;
import tetris.feature.versus.LocalMatchEngine;
import tetris.feature.versus.api.MatchConfig;
import tetris.feature.versus.api.PlayerCommand;
import tetris.feature.versus.api.PlayerId;

/** 호스트 권위 레이드. 클라이언트 입력이 P2 보드에 반영되고 보스 HP·결과는 같다 */
@Timeout(30)
class RaidP2pMatchTest {
    private static final long WAIT = 5;

    static final class ManualPulse implements MatchPulse {
        Runnable tick;
        @Override public Handle start(int periodMillis, Runnable tick) {
            this.tick = tick;
            return () -> { };
        }
    }

    final AtomicLong now = new AtomicLong();
    final ManualPulse pulse = new ManualPulse();
    ExecutorService hostExec;
    ExecutorService clientExec;
    SocketLobbyClient host;
    SocketLobbyClient client;

    @BeforeEach
    void setUp() {
        hostExec = Executors.newSingleThreadExecutor();
        clientExec = Executors.newSingleThreadExecutor();
        host = new SocketLobbyClient(hostExec, new PeerEstablisher(hostExec),
                () -> new LocalMatchEngine(now::get, id -> OBlock::new), now::get, pulse);
        client = new SocketLobbyClient(clientExec);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (hostExec != null) run(hostExec, host::close);
        if (clientExec != null) run(clientExec, client::close);
        if (hostExec != null) hostExec.shutdownNow();
        if (clientExec != null) clientExec.shutdownNow();
        hostExec = null;
        clientExec = null;
    }

    @Test
    void coopInputsMoveP2AndBothSeeTheSameBoss() throws Exception {
        play(RaidMode.COOP);
    }

    @Test
    void raceInputsMoveP2AndBothSeeTheSameBoss() throws Exception {
        play(RaidMode.RACE);
    }

    private void play(RaidMode mode) throws Exception {
        var hs = lobbyQueue(hostExec, host);
        var cs = lobbyQueue(clientExec, client);
        start(hs, cs, MatchConfig.raid(mode, ControlStyle.CLASSIC));
        assertTrue(call(clientExec, client::raidClient).isPresent());
        assertTrue(call(clientExec, client::battleClient).isEmpty());
        assertFalse(call(clientExec, () -> client.raidClient().orElseThrow() instanceof HostRaidClient));
        assertTrue(call(clientExec, () -> client.raidClient().orElseThrow() instanceof ClientRaidClient));

        var hostRaid = raidQueue(hostExec, host);
        var clientRaid = raidQueue(clientExec, client);
        await(hs, snapshot -> snapshot.state() == State.PLAYING);
        now.set(3_000);
        tick();
        now.addAndGet(HostBattleClient.STATE_INTERVAL_MILLIS);
        tick();
        RaidSnapshot first = await(clientRaid, snapshot -> snapshot.phase() == RaidPhase.PLAYING);
        assertEquals(mode, first.mode());
        assertEquals(first.boss().hp(), call(hostExec, () -> ((HostRaidClient) host.raidClient().orElseThrow())
                .snapshot().boss().hp()));

        long before = call(hostExec, () -> ((HostRaidClient) host.raidClient().orElseThrow())
                .snapshot().players().get(1).board().score());
        run(clientExec, () -> client.raidClient().orElseThrow()
                .send(new PlayerCommand(PlayerId.ONE, GameAction.HARD_DROP, true, 0)));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT);
        long after = before;
        while (after == before && System.nanoTime() < deadline) {
            now.addAndGet(16);
            tick();
            after = call(hostExec, () -> ((HostRaidClient) host.raidClient().orElseThrow())
                    .snapshot().players().get(1).board().score());
        }
        assertTrue(after != before, mode + " client input must move P2");
        assertEquals(0L, call(hostExec, () -> ((HostRaidClient) host.raidClient().orElseThrow())
                .snapshot().players().get(0).board().score()));
        now.addAndGet(HostBattleClient.STATE_INTERVAL_MILLIS);
        tick();
        long expected = after;
        RaidSnapshot seen = await(clientRaid, snapshot -> snapshot.players().get(1).board().score() == expected);
        assertEquals(call(hostExec, () -> ((HostRaidClient) host.raidClient().orElseThrow()).snapshot().boss().hp()),
                seen.boss().hp());

        RaidSnapshot hostSnap = call(hostExec, () -> ((HostRaidClient) host.raidClient().orElseThrow()).snapshot());
        int body = new FrameEncoder().encode(RaidPayloads.stateMessage(hostSnap)).length
                - WireFormat.LENGTH_PREFIX_BYTES;
        assertTrue(body < WireFormat.MAX_FRAME_BYTES, mode + " frame " + body);

        run(hostExec, () -> host.raidClient().orElseThrow()
                .send(new PlayerCommand(PlayerId.ONE, GameAction.QUIT, true, 1)));
        RaidSnapshot hostEnd = await(hostRaid, snapshot -> snapshot.phase() == RaidPhase.FINISHED);
        RaidSnapshot clientEnd = await(clientRaid, snapshot -> snapshot.phase() == RaidPhase.FINISHED);
        assertNotNull(hostEnd.result());
        assertEquals(hostEnd.result(), clientEnd.result());
        assertEquals(hostEnd.boss().hp(), clientEnd.boss().hp());
        await(hs, snapshot -> snapshot.state() == State.RESULT);
        await(cs, snapshot -> snapshot.state() == State.RESULT);
        run(hostExec, host::returnToLobby);
        run(clientExec, client::returnToLobby);
        assertEquals(State.LOBBY, await(hs, snapshot -> snapshot.state() == State.LOBBY).state());
        assertEquals(State.LOBBY, await(cs, snapshot -> snapshot.state() == State.LOBBY).state());
    }

    private void start(BlockingQueue<LobbySnapshot> hs, BlockingQueue<LobbySnapshot> cs, MatchConfig config)
            throws Exception {
        CompletionStage<Void> hostStage = call(hostExec, () -> host.host(0));
        int port = call(hostExec, host::localPort);
        CompletionStage<Void> clientStage = call(clientExec, () -> client.connect("127.0.0.1", port));
        hostStage.toCompletableFuture().get(WAIT, TimeUnit.SECONDS);
        clientStage.toCompletableFuture().get(WAIT, TimeUnit.SECONDS);
        await(hs, snapshot -> snapshot.state() == State.LOBBY);
        await(cs, snapshot -> snapshot.state() == State.LOBBY);
        run(hostExec, () -> host.selectConfig(config));
        LobbySnapshot configured = await(cs, snapshot -> snapshot.config() != null
                && snapshot.config().raidMode() == config.raidMode());
        long rev = configured.configRevision();
        run(clientExec, () -> client.ready(rev, true));
        await(hs, LobbySnapshot::peerReady);
        run(hostExec, () -> host.ready(rev, true));
        await(hs, snapshot -> snapshot.state() == State.STARTING || snapshot.state() == State.PLAYING);
        await(cs, snapshot -> snapshot.state() == State.PLAYING);
    }

    private void tick() throws Exception {
        run(hostExec, () -> pulse.tick.run());
    }

    private BlockingQueue<LobbySnapshot> lobbyQueue(ExecutorService exec, SocketLobbyClient lobby) throws Exception {
        BlockingQueue<LobbySnapshot> queue = new LinkedBlockingQueue<>();
        call(exec, () -> lobby.subscribe(queue::add));
        return queue;
    }

    private BlockingQueue<RaidSnapshot> raidQueue(ExecutorService exec, SocketLobbyClient lobby) throws Exception {
        BlockingQueue<RaidSnapshot> queue = new LinkedBlockingQueue<>();
        call(exec, () -> lobby.raidClient().orElseThrow().subscribe(queue::add));
        return queue;
    }

    private static void run(ExecutorService exec, Runnable action) throws Exception {
        call(exec, () -> {
            action.run();
            return null;
        });
    }

    private static <T> T call(ExecutorService exec, Callable<T> action) throws Exception {
        try {
            return exec.submit(action).get(WAIT, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException re) throw re;
            if (e.getCause() instanceof Error err) throw err;
            throw e;
        }
    }

    private static <T> T await(BlockingQueue<T> queue, Predicate<T> predicate) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT);
        while (true) {
            long left = deadline - System.nanoTime();
            assertTrue(left > 0, "expected value did not arrive");
            T value = queue.poll(left, TimeUnit.NANOSECONDS);
            assertNotNull(value, "expected value did not arrive");
            if (predicate.test(value)) return value;
        }
    }
}
