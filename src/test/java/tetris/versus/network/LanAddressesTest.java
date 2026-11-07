package tetris.versus.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tetris.feature.network.LanAddresses;
import tetris.feature.network.LanAddresses.Candidate;

/** S8-C1: 호스트 화면에 표시할 LAN IPv4 후보 선별 (R3-HOST, R3-NETTEST) */
@Timeout(30)
class LanAddressesTest {

    private static InetAddress ipv4(int a, int b, int c, int d) {
        try {
            return InetAddress.getByAddress(new byte[] {(byte) a, (byte) b, (byte) c, (byte) d});
        } catch (UnknownHostException e) {
            throw new AssertionError(e);
        }
    }

    private static InetAddress ipv6() {
        try {
            byte[] bytes = new byte[16];
            bytes[0] = (byte) 0xfe;
            bytes[1] = (byte) 0x80;
            bytes[15] = 1;
            return InetAddress.getByAddress(bytes);
        } catch (UnknownHostException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void 비활성_루프백_가상_어댑터와_IPv6_링크로컬_주소를_제외한다() {
        List<Candidate> candidates = List.of(
                new Candidate("eth0", "Ethernet", false, false, false, List.of(ipv4(192, 168, 0, 2))),
                new Candidate("lo", "Loopback", true, true, false, List.of(ipv4(127, 0, 0, 1))),
                new Candidate("eth1", "Sub Interface", true, false, true, List.of(ipv4(192, 168, 0, 3))),
                new Candidate("wlan0", "Wi-Fi", true, false, false, List.of(
                        ipv6(),
                        ipv4(169, 254, 12, 34),
                        ipv4(127, 0, 0, 2),
                        ipv4(192, 168, 0, 10))));

        assertEquals(List.of("192.168.0.10"), LanAddresses.select(candidates));
    }

    @Test
    void 가상_또는_비물리_키워드가_이름이나_표시_이름에_있으면_대소문자_무시하고_제외한다() {
        String[] keywords = {
            "Virtual", "VMware", "VirtualBox", "VBox", "Hyper-V",
            "vEthernet", "DOCKER", "WSL", "Tap", "TUN", "Bluetooth"
        };
        for (int i = 0; i < keywords.length; i++) {
            String kw = keywords[i];
            Candidate inName = new Candidate("net-" + kw + "-0", "Adapter", true, false, false,
                    List.of(ipv4(192, 168, 1, 10 + i)));
            Candidate inDisplay = new Candidate("eth9", "My " + kw + " Adapter", true, false, false,
                    List.of(ipv4(10, 0, 1, 10 + i)));
            assertEquals(List.of(), LanAddresses.select(List.of(inName, inDisplay)), "제외되어야 하는 키워드: " + kw);
        }
    }

    @Test
    void 사설_대역을_먼저_두고_입력_순서를_유지하며_중복을_제거한다() {
        List<Candidate> example = List.of(
                new Candidate("eth0", "vEthernet (WSL)", true, false, false, List.of(ipv4(172, 20, 0, 1))),
                new Candidate("wlan0", "Wi-Fi", true, false, false, List.of(ipv4(8, 8, 4, 4), ipv4(192, 168, 0, 7))),
                new Candidate("lo", "Loopback", true, true, false, List.of(ipv4(127, 0, 0, 1))));

        assertEquals(List.of("192.168.0.7", "8.8.4.4"), LanAddresses.select(example));

        List<Candidate> orderingAndDedup = List.of(
                new Candidate("eth0", "LAN 1", true, false, false, List.of(
                        ipv4(172, 15, 0, 1),
                        ipv4(10, 1, 2, 3),
                        ipv4(172, 16, 0, 1),
                        ipv4(172, 32, 0, 1))),
                new Candidate("eth1", "LAN 2", true, false, false, List.of(
                        ipv4(10, 1, 2, 3),
                        ipv4(172, 31, 255, 254),
                        ipv4(192, 168, 1, 5),
                        ipv4(172, 15, 0, 1))));

        assertEquals(
                List.of("10.1.2.3", "172.16.0.1", "172.31.255.254", "192.168.1.5", "172.15.0.1", "172.32.0.1"),
                LanAddresses.select(orderingAndDedup));
    }

    @Test
    void current는_예외_없이_반환하고_루프백_주소를_포함하지_않는다() {
        List<String> current = LanAddresses.current();
        assertNotNull(current);
        for (String addr : current) {
            assertFalse(addr.startsWith("127."), "루프백 주소가 포함됨: " + addr);
            assertFalse(addr.startsWith("169.254."), "링크 로컬 주소가 포함됨: " + addr);
        }
    }
}
