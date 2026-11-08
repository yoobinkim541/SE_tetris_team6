package tetris.feature.network;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 호스트 대기 화면에 안내할 LAN IPv4 주소 후보를 선별한다 (R3-HOST).
 *
 * <p>루프백·비활성·가상·터널 어댑터와 IPv6·링크 로컬 주소를 제외하고,
 * 사설 IPv4 대역({@code 10/8}, {@code 172.16/12}, {@code 192.168/16})을 앞세워 돌려준다.
 */
public final class LanAddresses {
    private static final List<String> EXCLUDED_KEYWORDS = List.of(
            "virtual",
            "vmware",
            "virtualbox",
            "vbox",
            "hyper-v",
            "vethernet",
            "docker",
            "wsl",
            "tap",
            "tun",
            "bluetooth");

    private LanAddresses() {
    }

    /** 네트워크 인터페이스 하나에서 읽은 판정 입력 */
    public record Candidate(
            String name,
            String displayName,
            boolean up,
            boolean loopback,
            boolean virtual,
            List<InetAddress> addresses) {
        public Candidate {
            addresses = List.copyOf(Objects.requireNonNull(addresses, "addresses"));
        }
    }

    /** 주어진 인터페이스 목록에서 안내할 IPv4 문자열 목록을 고른다 */
    public static List<String> select(List<Candidate> interfaces) {
        Objects.requireNonNull(interfaces, "interfaces");
        Set<String> privateAddresses = new LinkedHashSet<>();
        Set<String> publicAddresses = new LinkedHashSet<>();

        for (Candidate candidate : interfaces) {
            if (!isEligibleInterface(candidate)) continue;
            for (InetAddress address : candidate.addresses()) {
                if (!(address instanceof Inet4Address ipv4)) continue;
                if (ipv4.isLoopbackAddress() || ipv4.isLinkLocalAddress()
                        || ipv4.isAnyLocalAddress() || ipv4.isMulticastAddress()) {
                    continue;
                }
                String text = ipv4.getHostAddress();
                if (isPrivateIpv4(ipv4.getAddress())) {
                    privateAddresses.add(text);
                } else {
                    publicAddresses.add(text);
                }
            }
        }

        List<String> ordered = new ArrayList<>(privateAddresses.size() + publicAddresses.size());
        ordered.addAll(privateAddresses);
        for (String pub : publicAddresses) {
            if (!privateAddresses.contains(pub)) {
                ordered.add(pub);
            }
        }
        return List.copyOf(ordered);
    }

    /** 현재 머신의 네트워크 인터페이스를 조회해 후보 IPv4 목록을 돌려준다 */
    public static List<String> current() {
        List<NetworkInterface> rawInterfaces;
        try (Stream<NetworkInterface> stream = NetworkInterface.networkInterfaces()) {
            rawInterfaces = stream.toList();
        } catch (SocketException e) {
            return List.of();
        }

        List<Candidate> candidates = new ArrayList<>(rawInterfaces.size());
        for (NetworkInterface ni : rawInterfaces) {
            try {
                boolean up = ni.isUp();
                boolean loopback = ni.isLoopback();
                boolean virtual = ni.isVirtual();
                List<InetAddress> addrs = ni.inetAddresses().toList();
                candidates.add(new Candidate(ni.getName(), ni.getDisplayName(), up, loopback, virtual, addrs));
            } catch (SocketException ignored) {
                // 조회 도중 내려간 인터페이스는 건너뛴다
            }
        }
        return select(candidates);
    }

    private static boolean isEligibleInterface(Candidate candidate) {
        if (candidate == null || !candidate.up() || candidate.loopback() || candidate.virtual()) {
            return false;
        }
        return !containsExcludedKeyword(candidate.name()) && !containsExcludedKeyword(candidate.displayName());
    }

    private static boolean containsExcludedKeyword(String text) {
        if (text == null || text.isEmpty()) return false;
        String lower = text.toLowerCase(Locale.ROOT);
        for (String keyword : EXCLUDED_KEYWORDS) {
            if (lower.contains(keyword)) return true;
        }
        return false;
    }

    private static boolean isPrivateIpv4(byte[] bytes) {
        int b0 = bytes[0] & 0xFF;
        int b1 = bytes[1] & 0xFF;
        return b0 == 10
                || (b0 == 172 && b1 >= 16 && b1 <= 31)
                || (b0 == 192 && b1 == 168);
    }
}
