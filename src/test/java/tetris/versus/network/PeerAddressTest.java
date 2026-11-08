package tetris.versus.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tetris.feature.network.EstablishException;
import tetris.feature.network.EstablishFailure;
import tetris.feature.network.NetworkDefaults;
import tetris.feature.network.PeerAddress;

/** S8-C1: 접속 주소 문자열·포트 검증과 네트워크 기본값 (R3-HOST, R3-NETTEST) */
@Timeout(30)
class PeerAddressTest {

    @Test
    void 앞뒤_공백을_제거하고_유효한_IPv4와_포트를_파싱한다() throws Exception {
        PeerAddress addr = PeerAddress.parse(" 192.168.0.7 ", 47123);
        assertEquals("192.168.0.7", addr.host());
        assertEquals(47123, addr.port());

        PeerAddress minPort = PeerAddress.parse("127.0.0.1", 1);
        assertEquals("127.0.0.1", minPort.host());
        assertEquals(1, minPort.port());

        PeerAddress maxPort = PeerAddress.parse("10.0.0.1", 65535);
        assertEquals("10.0.0.1", maxPort.host());
        assertEquals(65535, maxPort.port());
    }

    @Test
    void 잘못된_주소_형식은_모두_INVALID_ADDRESS로_거부한다() {
        String[] invalidHosts = {
            null,
            "",
            "   ",
            "abc",
            "localhost",
            "::1",
            "1.2.3",
            "1.2.3.4.5",
            "1.2.3.4.",
            ".1.2.3.4",
            "256.1.1.1",
            "01.2.3.4",
            "0.0.0.0",
            "255.255.255.255",
            "1.2.3.-4",
            "+1.2.3.4"
        };
        for (String host : invalidHosts) {
            EstablishException ex = assertThrows(
                    EstablishException.class,
                    () -> PeerAddress.parse(host, NetworkDefaults.DEFAULT_PORT),
                    "거부되어야 하는 주소: " + host);
            assertEquals(EstablishFailure.INVALID_ADDRESS, ex.reason());
        }
    }

    @Test
    void 범위를_벗어난_포트는_INVALID_ADDRESS로_거부한다() {
        int[] invalidPorts = {0, 65536, -1};
        for (int port : invalidPorts) {
            EstablishException ex = assertThrows(
                    EstablishException.class,
                    () -> PeerAddress.parse("127.0.0.1", port),
                    "거부되어야 하는 포트: " + port);
            assertEquals(EstablishFailure.INVALID_ADDRESS, ex.reason());
        }
    }

    @Test
    void 네트워크_기본값은_5초_타임아웃과_유효한_사용자_포트를_가진다() {
        assertEquals(5000L, NetworkDefaults.CONNECT_TIMEOUT.toMillis());
        assertEquals(47123, NetworkDefaults.DEFAULT_PORT);
        assertTrue(NetworkDefaults.DEFAULT_PORT >= 1024 && NetworkDefaults.DEFAULT_PORT <= 65535);
    }
}
