package tetris.relay;

import java.net.InetSocketAddress;

/**
 * 게임이 접속할 중계 서버 주소 (challenge). 기본은 Oracle Cloud VM이고, 시스템 속성
 * {@code -Dtetris.relay=host:port}로 바꿀 수 있다. 화면 스레드에서 DNS 조회를 하지 않도록 해석하지 않은 주소를 돌려준다.
 */
public final class RelayEndpoint {
    public static final String PROPERTY = "tetris.relay";
    public static final String DEFAULT = "158.179.174.70:" + RelayServer.DEFAULT_PORT;

    private RelayEndpoint() {
    }

    public static InetSocketAddress configured() {
        String value = System.getProperty(PROPERTY);
        InetSocketAddress parsed = value == null ? null : parse(value);
        return parsed != null ? parsed : parse(DEFAULT);
    }

    /** "host:port"를 해석하지 않은 주소로 바꾼다. 형식이 틀리면 null */
    static InetSocketAddress parse(String value) {
        String text = value.trim();
        int colon = text.lastIndexOf(':');
        if (colon <= 0 || colon == text.length() - 1) return null;
        int port;
        try {
            port = Integer.parseInt(text.substring(colon + 1));
        } catch (NumberFormatException e) {
            return null;
        }
        if (port < 1 || port > 65_535) return null;
        return InetSocketAddress.createUnresolved(text.substring(0, colon), port);
    }
}
