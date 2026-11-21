package tetris.relay;

import java.util.Optional;

/** 인터넷 방 목록에 실리는 모드 토큰. 줄 프로토콜은 공백으로 나누므로 토큰에 공백이 없다. */
public enum RelayRoomMode {
    CLASSIC("classic", "일반"),
    ITEM("item", "아이템"),
    TIMED("timed", "시간제한"),
    RAID_COOP("raid-coop", "협동 레이드"),
    RAID_RACE("raid-race", "레이스 레이드");

    private final String token;
    private final String label;

    RelayRoomMode(String token, String label) {
        this.token = token;
        this.label = label;
    }

    public String token() {
        return token;
    }

    public String label() {
        return label;
    }

    public static Optional<RelayRoomMode> fromToken(String token) {
        if (token == null) return Optional.empty();
        for (RelayRoomMode mode : values()) {
            if (mode.token.equals(token)) return Optional.of(mode);
        }
        return Optional.empty();
    }
}
