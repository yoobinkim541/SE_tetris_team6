package tetris.feature.network;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

/**
 * 클라이언트가 접속할 상대 IPv4 주소와 포트 (R3-HOST).
 *
 * <p>EDT에서 DNS 조회로 멈추지 않도록 호스트 이름이나 IPv6는 받지 않고,
 * 앞자리 불필요한 0이 없는 점 4개 10진수 IPv4 문자열만 허용한다.
 */
public record PeerAddress(String host, int port) {
    public PeerAddress {
        Objects.requireNonNull(host, "host");
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port out of range: " + port);
        }
    }

    /** 주소 문자열과 포트를 검증해 돌려준다. 형식 오류면 {@link EstablishFailure#INVALID_ADDRESS} */
    public static PeerAddress parse(String text, int port) throws EstablishException {
        if (port < 1 || port > 65535) {
            throw new EstablishException(EstablishFailure.INVALID_ADDRESS, "port " + port);
        }
        if (text == null) {
            throw new EstablishException(EstablishFailure.INVALID_ADDRESS, "address is null");
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            throw new EstablishException(EstablishFailure.INVALID_ADDRESS, "address is empty");
        }
        parseIpv4Bytes(trimmed);
        return new PeerAddress(trimmed, port);
    }

    /** DNS 조회 없이 숫자 바이트로 바로 {@link InetAddress}를 만든다 */
    InetAddress toInetAddress() throws EstablishException {
        byte[] bytes = parseIpv4Bytes(host);
        try {
            return InetAddress.getByAddress(host, bytes);
        } catch (UnknownHostException e) {
            throw new EstablishException(EstablishFailure.INVALID_ADDRESS, host, e);
        }
    }

    private static byte[] parseIpv4Bytes(String trimmed) throws EstablishException {
        String[] parts = trimmed.split("\\.", -1);
        if (parts.length != 4) {
            throw new EstablishException(EstablishFailure.INVALID_ADDRESS, trimmed);
        }
        int[] octets = new int[4];
        for (int i = 0; i < 4; i++) {
            octets[i] = parseOctet(parts[i], trimmed);
        }
        if (octets[0] == 0 && octets[1] == 0 && octets[2] == 0 && octets[3] == 0) {
            throw new EstablishException(EstablishFailure.INVALID_ADDRESS, "unspecified address 0.0.0.0");
        }
        if (octets[0] == 255 && octets[1] == 255 && octets[2] == 255 && octets[3] == 255) {
            throw new EstablishException(EstablishFailure.INVALID_ADDRESS, "broadcast address 255.255.255.255");
        }
        return new byte[] {
            (byte) octets[0],
            (byte) octets[1],
            (byte) octets[2],
            (byte) octets[3]
        };
    }

    private static int parseOctet(String part, String full) throws EstablishException {
        if (part.isEmpty() || part.length() > 3) {
            throw new EstablishException(EstablishFailure.INVALID_ADDRESS, full);
        }
        if (part.length() > 1 && part.charAt(0) == '0') {
            throw new EstablishException(EstablishFailure.INVALID_ADDRESS, full);
        }
        int value = 0;
        for (int i = 0; i < part.length(); i++) {
            char ch = part.charAt(i);
            if (ch < '0' || ch > '9') {
                throw new EstablishException(EstablishFailure.INVALID_ADDRESS, full);
            }
            value = value * 10 + (ch - '0');
        }
        if (value > 255) {
            throw new EstablishException(EstablishFailure.INVALID_ADDRESS, full);
        }
        return value;
    }
}
