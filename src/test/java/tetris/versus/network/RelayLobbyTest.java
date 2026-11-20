package tetris.versus.network;

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tetris.feature.network.HeartbeatPolicy;
import tetris.feature.network.PeerEstablisher;
import tetris.feature.network.lobby.LobbySnapshot;
import tetris.feature.network.lobby.LobbySnapshot.Role;
import tetris.feature.network.lobby.LobbySnapshot.State;
import tetris.feature.network.lobby.SocketLobbyClient;
import tetris.feature.network.match.DispatcherPulse;
import tetris.feature.save.RecentPeers;
import tetris.feature.versus.LocalMatchEngine;
import tetris.feature.versus.api.BattleMode;
import tetris.feature.versus.api.MatchClock;
import tetris.feature.versus.api.MatchConfig;
import tetris.relay.LobbyCode;
import tetris.relay.RelayLimits;
import tetris.relay.RelayServer;

/** challenge: 로비 코드로 인터넷 방을 만들고 들어가 기존 로비·경기 흐름을 그대로 탄다 */
@Timeout(30)
class RelayLobbyTest {
    private static final RelayLimits RELAXED = new RelayLimits(300, 64, 10_000, Duration.ofSeconds(5), 100,
            Duration.ofMinutes(10), 100, Duration.ofSeconds(60), Duration.ofSeconds(10), Duration.ofHours(3), 10_000);

    /** 기록 호출을 모으는 최근 접속 저장소 */
    private static final class RecordingPeers implements RecentPeers {
        final List<String> recorded = new CopyOnWriteArrayList<>();
        @Override public List<String> load() { return List.copyOf(recorded); }
        @Override public void record(String address) { recorded.add(address); }
    }

    private RelayServer relay;
    private ExecutorService hostExec;
    private ExecutorService clientExec;
    private SocketLobbyClient host;
    private SocketLobbyClient client;
    private final RecordingPeers hostPeers = new RecordingPeers();
    private final RecordingPeers clientPeers = new RecordingPeers();
    private final BlockingQueue<LobbySnapshot> hostSnaps = new LinkedBlockingQueue<>();
    private final BlockingQueue<LobbySnapshot> clientSnaps = new LinkedBlockingQueue<>();

    private SocketLobbyClient lobby(ExecutorService exec, RecentPeers peers, InetSocketAddress relayAddress) {
        return new SocketLobbyClient(exec, new PeerEstablisher(exec, Duration.ofSeconds(5)), LocalMatchEngine::new,
                MatchClock.system(), new DispatcherPulse(exec), HeartbeatPolicy.DEFAULT, peers, Runnable::run,
                relayAddress);
    }

    @BeforeEach
    void setUp() throws Exception {
        relay = new RelayServer(0, RELAXED, System::nanoTime, new Random(5));
        relay.start();
        InetSocketAddress address = new InetSocketAddress(InetAddress.getLoopbackAddress(), relay.localPort());
        hostExec = Executors.newSingleThreadExecutor();
        clientExec = Executors.newSingleThreadExecutor();
        host = lobby(hostExec, hostPeers, address);
        client = lobby(clientExec, clientPeers, address);
        on(hostExec, () -> host.subscribe(hostSnaps::add));
        on(clientExec, () -> client.subscribe(clientSnaps::add));
    }

    @AfterEach
    void tearDown() throws Exception {
        on(hostExec, () -> { host.close(); return null; });
        on(clientExec, () -> { client.close(); return null; });
        relay.close();
        hostExec.shutdownNow();
        clientExec.shutdownNow();
    }

    private static <T> T on(ExecutorService exec, Callable<T> work) throws Exception {
        return exec.submit(work).get(10, TimeUnit.SECONDS);
    }

    private static LobbySnapshot await(BlockingQueue<LobbySnapshot> q, Predicate<LobbySnapshot> p, String what)
            throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < end) {
            LobbySnapshot s = q.poll(100, TimeUnit.MILLISECONDS);
            if (s != null && p.test(s)) return s;
        }
        fail("timed out waiting for " + what);
        return null;
    }

    private String hostAndGetCode() throws Exception {
        BlockingQueue<String> codes = new LinkedBlockingQueue<>();
        on(hostExec, () -> host.subscribeRoomCode(c -> c.ifPresent(codes::add)));
        on(hostExec, host::hostOnline);
        await(hostSnaps, s -> s.state() == State.HOSTING, "HOSTING");
        String code = codes.poll(10, TimeUnit.SECONDS);
        assertNotNull(code, "로비 코드를 받지 못했다");
        assertEquals(Optional.of(code), on(hostExec, host::roomCode));
        return code;
    }

    @Test
    void 코드로_들어오면_양쪽이_LOBBY에_들어가고_상대는_인터넷_코드로_보인다() throws Exception {
        String code = hostAndGetCode();
        on(clientExec, () -> client.joinOnline(LobbyCode.display(code)));

        LobbySnapshot h = await(hostSnaps, s -> s.state() == State.LOBBY, "host LOBBY");
        LobbySnapshot c = await(clientSnaps, s -> s.state() == State.LOBBY, "client LOBBY");
        assertEquals(Role.HOST, h.role());
        assertEquals(Role.CLIENT, c.role());
        assertTrue(h.peerIdentity().startsWith("인터넷 · " + LobbyCode.display(code)), h.peerIdentity());
        assertTrue(c.peerIdentity().startsWith("인터넷 · " + LobbyCode.display(code)), c.peerIdentity());
        assertEquals(Optional.empty(), on(hostExec, host::roomCode), "LOBBY에서는 대기 코드가 없다");
    }

    @Test
    void 인터넷_방에서도_설정_준비_시작까지_기존_흐름을_탄다() throws Exception {
        String code = hostAndGetCode();
        on(clientExec, () -> client.joinOnline(code));
        await(hostSnaps, s -> s.state() == State.LOBBY, "host LOBBY");
        await(clientSnaps, s -> s.state() == State.LOBBY, "client LOBBY");

        on(hostExec, () -> { host.selectConfig(MatchConfig.of(BattleMode.CLASSIC)); return null; });
        LobbySnapshot configured = await(clientSnaps, s -> s.config() != null, "client got config");
        long revision = configured.configRevision();
        on(clientExec, () -> { client.ready(revision, true); return null; });
        on(hostExec, () -> { host.ready(revision, true); return null; });

        await(hostSnaps, s -> s.state() == State.STARTING || s.state() == State.PLAYING, "host started");
        await(clientSnaps, s -> s.state() == State.STARTING || s.state() == State.PLAYING, "client started");
        assertTrue(on(clientExec, () -> client.battleClient().isPresent()));
    }

    @Test
    void 인터넷_연결은_최근_접속_IP에_기록하지_않는다() throws Exception {
        String code = hostAndGetCode();
        on(clientExec, () -> client.joinOnline(code));
        await(clientSnaps, s -> s.state() == State.LOBBY, "client LOBBY");
        assertTrue(clientPeers.recorded.isEmpty(), clientPeers.recorded.toString());
        assertTrue(hostPeers.recorded.isEmpty(), hostPeers.recorded.toString());
    }

    @Test
    void 대기_중_취소하면_INITIAL이고_서버의_방도_사라진다() throws Exception {
        hostAndGetCode();
        on(hostExec, () -> { host.cancel(); return null; });
        await(hostSnaps, s -> s.state() == State.INITIAL, "INITIAL");
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (relay.waitingRooms() != 0 && System.nanoTime() < end) Thread.sleep(5);
        assertEquals(0, relay.waitingRooms());
        assertEquals(Optional.empty(), on(hostExec, host::roomCode));
    }

    @Test
    void 없는_코드면_INITIAL로_돌아오고_단계는_실패한다() throws Exception {
        var stage = on(clientExec, () -> client.joinOnline("AAAAAA"));
        await(clientSnaps, s -> s.state() == State.CONNECTING, "CONNECTING");
        await(clientSnaps, s -> s.state() == State.INITIAL, "INITIAL");
        var e = assertThrows(java.util.concurrent.ExecutionException.class,
                () -> stage.toCompletableFuture().get(5, TimeUnit.SECONDS));
        assertEquals(tetris.feature.network.EstablishFailure.NO_ROOM,
                ((tetris.feature.network.EstablishException) e.getCause()).reason());
    }

    @Test
    void 상대가_나가면_INITIAL로_돌아온다() throws Exception {
        String code = hostAndGetCode();
        on(clientExec, () -> client.joinOnline(code));
        await(hostSnaps, s -> s.state() == State.LOBBY, "host LOBBY");
        await(clientSnaps, s -> s.state() == State.LOBBY, "client LOBBY");
        on(clientExec, () -> { client.leave(); return null; });
        await(hostSnaps, s -> s.state() == State.INITIAL, "host INITIAL");
    }
}
