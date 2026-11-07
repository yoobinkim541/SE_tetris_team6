package tetris.feature.network;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import tetris.feature.network.protocol.Message;
import tetris.feature.network.protocol.PeerRole;

/**
 * HELLO 핸드셰이크를 마친 P2P 연결 한 세대 (S8-C1).
 *
 * <p>핸드셰이크 직후부터 상위 계층이 {@link #attach(PeerListener)}를 부르기 전까지 도착한
 * 메시지와 종료 통지를 순서대로 버퍼에 보관했다가, {@code attach} 시점에 빠짐없이 넘긴다.
 * {@code attach}와 콜백 전달은 모두 동일한 dispatcher 스레드에서 실행된다.
 */
public final class PeerLink {
    private record ClosedEvent(CloseReason reason, String detail) {
    }

    private final PeerRole role;
    private final String peerAddress;
    private final PeerConnection connection;
    private final List<Message> bufferedMessages = new ArrayList<>();
    private ClosedEvent bufferedClose;
    private PeerListener listener;
    private volatile boolean closedLocally;

    PeerLink(PeerRole role, String peerAddress, PeerConnection connection) {
        this.role = Objects.requireNonNull(role, "role");
        this.peerAddress = Objects.requireNonNull(peerAddress, "peerAddress");
        this.connection = Objects.requireNonNull(connection, "connection");
    }

    public PeerRole role() {
        return role;
    }

    public String peerAddress() {
        return peerAddress;
    }

    public boolean send(Message m) {
        return connection.send(m);
    }

    /** 리스너를 연결하고 그 전에 도착해 있던 메시지·종료 통지를 순서대로 전달한다 (dispatcher 스레드) */
    public void attach(PeerListener l) {
        Objects.requireNonNull(l, "listener");
        if (this.listener != null) {
            throw new IllegalStateException("listener already attached");
        }
        this.listener = l;
        if (!bufferedMessages.isEmpty()) {
            List<Message> snapshot = List.copyOf(bufferedMessages);
            bufferedMessages.clear();
            for (Message message : snapshot) {
                if (closedLocally) break;
                l.onMessage(message);
            }
        }
        if (bufferedClose != null) {
            ClosedEvent close = bufferedClose;
            bufferedClose = null;
            l.onClosed(close.reason(), close.detail());
        }
    }

    public void close() {
        closedLocally = true;
        bufferedMessages.clear();
        connection.close();
    }

    public boolean isOpen() {
        return connection.isOpen();
    }

    public boolean awaitTermination(Duration d) throws InterruptedException {
        return connection.awaitTermination(d);
    }

    void deliverMessage(Message message) {
        if (closedLocally) return;
        if (listener != null) {
            listener.onMessage(message);
        } else if (bufferedClose == null) {
            bufferedMessages.add(message);
        }
    }

    void deliverClosed(CloseReason reason, String detail) {
        if (listener != null) {
            listener.onClosed(reason, detail);
        } else if (bufferedClose == null) {
            bufferedClose = new ClosedEvent(reason, detail);
        }
    }
}
