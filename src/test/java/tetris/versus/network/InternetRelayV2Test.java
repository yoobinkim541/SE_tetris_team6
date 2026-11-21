package tetris.versus.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tetris.feature.network.relay.InternetRelay;
import tetris.feature.network.relay.RelayTransport;
import tetris.relay.LobbyCode;

/** 옛 서버가 v2에 ERR을 주면 예비 서버로 넘어가고, 목록은 두 서버를 합친다 */
@Timeout(30)
class InternetRelayV2Test {
    @Test
    void 메인이_v2를_모르면_예비_서버에서_방을_만든다() throws Exception {
        Recording oldServer = new Recording("{\"op\":\"error\",\"reason\":\"BAD_REQUEST\"}");
        Recording fallback = new Recording("{\"op\":\"room\",\"code\":\"4821\"}");
        InternetRelay relay = new InternetRelay(List.of(
                new InternetRelay.Route("메인", URI.create("tcp://old"), Duration.ofSeconds(1), oldServer,
                        LobbyCode::normalize, LobbyCode::display),
                InternetRelay.Route.fallback(URI.create("ws://fallback/relay"), fallback)), Runnable::run);
        String code = relay.openListedRoom(50123, "대전", true, "classic").toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals("4821", code);
        assertTrue(oldServer.sent.getFirst().contains("\"name\":\"대전\""), oldServer.sent.toString());
        assertTrue(fallback.sent.getFirst().contains("\"visibility\":\"public\""), fallback.sent.toString());
        relay.close();
    }

    @Test
    void 목록은_응답한_서버만_합치고_방을_그_서버에_묶어_둔다() throws Exception {
        Recording main = new Recording("{\"op\":\"rooms\",\"rooms\":[{\"id\":\"AAA\",\"name\":\"메인방\",\"mode\":\"classic\",\"private\":false,\"players\":1}]}");
        Recording broken = new Recording("{\"op\":\"error\",\"reason\":\"BAD_REQUEST\"}");
        List<InternetRelay.Route> routes = List.of(
                new InternetRelay.Route("메인", URI.create("tcp://main"), Duration.ofSeconds(1), main,
                        LobbyCode::normalize, LobbyCode::display),
                InternetRelay.Route.fallback(URI.create("ws://fallback/relay"), broken));
        List<InternetRelay.ListedRoom> rooms = InternetRelay.list(routes).toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals(1, rooms.size());
        assertEquals(0, rooms.getFirst().routeIndex());
        assertEquals("메인", rooms.getFirst().serverName());
        assertEquals("메인방", rooms.getFirst().name());
    }

    private static final class Recording implements RelayTransport {
        final String reply;
        final List<String> sent = new java.util.ArrayList<>();

        Recording(String reply) {
            this.reply = reply;
        }

        @Override
        public CompletableFuture<Channel> open(URI uri, Duration timeout, Listener listener) {
            return CompletableFuture.completedFuture(new Channel() {
                @Override public CompletableFuture<Void> sendText(String text) {
                    sent.add(text);
                    listener.onText(reply);
                    return CompletableFuture.completedFuture(null);
                }
                @Override public CompletableFuture<Void> sendBinary(byte[] bytes) {
                    return CompletableFuture.completedFuture(null);
                }
                @Override public void close() {
                }
            });
        }
    }
}
