package tetris.feature.versus.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 소켓 없이 즉시 상태가 바뀌는 로비 가짜 (B의 S8 로비 화면·테스트용). EDT 한 스레드에서만 쓴다.
 * 상대 행동은 {@link #peerConnected()}, {@link #peerReady()}, {@link #fail(String)}로 흉내 낸다.
 */
public final class FakeLobbyClient implements LobbyClient {
    private final List<Consumer<LobbySnapshot>> listeners = new ArrayList<>();
    private final Supplier<BattleClient> battleFactory;
    private LobbySnapshot current;
    private BattleClient battle;
    private boolean closed;

    public FakeLobbyClient(List<String> recentHosts, Supplier<BattleClient> battleFactory) {
        this.battleFactory = Objects.requireNonNull(battleFactory, "battleFactory");
        this.current = LobbySnapshot.idle(recentHosts);
    }

    @Override
    public void host() {
        requirePhase(LobbyPhase.IDLE, LobbyPhase.FAILED);
        publish(with(LobbyPhase.HOSTING, true, "127.0.0.1", null, 0, false, false, "접속을 기다리는 중"));
    }

    @Override
    public void connect(String address) {
        Objects.requireNonNull(address, "address");
        if (address.isBlank()) throw new IllegalArgumentException("blank address");
        requirePhase(LobbyPhase.IDLE, LobbyPhase.FAILED);
        List<String> recent = new ArrayList<>(current.recentHosts());
        recent.remove(address);
        recent.add(0, address);
        current = new LobbySnapshot(current.phase(), false, null, null, 0, false, false, recent, "");
        publish(with(LobbyPhase.CONNECTING, false, address, null, 0, false, false, address + " 에 접속 중"));
    }

    /** 상대가 접속했거나 접속을 받아 줌 */
    public void peerConnected() {
        requirePhase(LobbyPhase.HOSTING, LobbyPhase.CONNECTING);
        publish(with(LobbyPhase.CONNECTED, current.host(), current.hostAddress(), null, 0, false, false, "연결됨"));
    }

    @Override
    public void cancel() {
        ensureOpen();
        closeBattle();
        publish(LobbySnapshot.idle(current.recentHosts()));
    }

    @Override
    public void configure(MatchConfig config) {
        Objects.requireNonNull(config, "config");
        requirePhase(LobbyPhase.CONNECTED);
        if (!current.host()) throw new IllegalStateException("only the host chooses the mode");
        publish(with(LobbyPhase.CONNECTED, true, current.hostAddress(), config,
                current.configRevision() + 1, false, false, config.mode() + " 선택됨"));
    }

    @Override
    public void ready() {
        requireConfigured();
        advanceReady(true, current.peerReady());
    }

    /** 상대가 게임 시작을 누름 */
    public void peerReady() {
        requireConfigured();
        advanceReady(current.selfReady(), true);
    }

    /** 경기가 끝나 연결 직후 대기 상태로 돌아감 (R3-P2P-END) */
    public void matchFinished() {
        requirePhase(LobbyPhase.IN_MATCH);
        closeBattle();
        publish(with(LobbyPhase.CONNECTED, current.host(), current.hostAddress(), current.config(),
                current.configRevision(), false, false, "경기 종료"));
    }

    /** 연결 실패·끊김 */
    public void fail(String message) {
        ensureOpen();
        closeBattle();
        publish(with(LobbyPhase.FAILED, current.host(), null, null, 0, false, false, message));
    }

    @Override
    public BattleClient battle() {
        requirePhase(LobbyPhase.IN_MATCH);
        return battle;
    }

    @Override
    public BattleClient.Subscription subscribe(Consumer<LobbySnapshot> listener) {
        ensureOpen();
        Objects.requireNonNull(listener, "listener");
        listeners.add(listener);
        listener.accept(current);
        return () -> listeners.remove(listener);
    }

    public LobbySnapshot current() {
        return current;
    }

    @Override
    public void close() {
        if (closed) return;
        closeBattle();
        closed = true;
        listeners.clear();
    }

    private void advanceReady(boolean self, boolean peer) {
        if (self && peer) {
            battle = battleFactory.get();
            publish(with(LobbyPhase.IN_MATCH, current.host(), current.hostAddress(), current.config(),
                    current.configRevision(), true, true, "경기 시작"));
        } else {
            publish(with(LobbyPhase.CONNECTED, current.host(), current.hostAddress(), current.config(),
                    current.configRevision(), self, peer, self ? "상대를 기다리는 중" : "상대가 준비됨"));
        }
    }

    private LobbySnapshot with(LobbyPhase phase, boolean host, String address, MatchConfig config, int revision,
                               boolean selfReady, boolean peerReady, String message) {
        return new LobbySnapshot(phase, host, address, config, revision, selfReady, peerReady,
                current.recentHosts(), message);
    }

    private void publish(LobbySnapshot next) {
        current = next;
        for (Consumer<LobbySnapshot> listener : List.copyOf(listeners)) listener.accept(next);
    }

    private void closeBattle() {
        if (battle != null) {
            battle.close();
            battle = null;
        }
    }

    private void requireConfigured() {
        requirePhase(LobbyPhase.CONNECTED);
        if (current.config() == null) throw new IllegalStateException("no config yet");
    }

    private void requirePhase(LobbyPhase... allowed) {
        ensureOpen();
        for (LobbyPhase phase : allowed) {
            if (current.phase() == phase) return;
        }
        throw new IllegalStateException("not allowed in " + current.phase());
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("lobby is closed");
    }
}
