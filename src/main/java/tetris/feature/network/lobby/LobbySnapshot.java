package tetris.feature.network.lobby;

import java.util.Objects;
import tetris.feature.versus.api.MatchConfig;

/** Immutable view. Null config/peerIdentity means not negotiated/not connected. */
public record LobbySnapshot(Role role, State state, MatchConfig config, long configRevision,
        boolean localReady, boolean peerReady, String peerIdentity) {
    public enum Role { NONE, HOST, CLIENT }
    public enum State { INITIAL, HOSTING, CONNECTING, LOBBY, STARTING, PLAYING, RESULT }

    public LobbySnapshot {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(state, "state");
        if (configRevision < 0 || configRevision > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("config revision outside wire int range");
        }
        if ((localReady || peerReady) && (configRevision == 0 || config == null || peerIdentity == null
                || role == Role.NONE || state == State.INITIAL || state == State.HOSTING
                || state == State.CONNECTING)) {
            throw new IllegalArgumentException("READY requires a connected, configured lobby");
        }
        if (peerIdentity != null && peerIdentity.isBlank()) {
            throw new IllegalArgumentException("blank peer identity");
        }
    }

    /** An accepted selection clears both votes, even when selecting identical settings. */
    public LobbySnapshot selectConfig(MatchConfig next) {
        if (role != Role.HOST || state != State.LOBBY) {
            throw new IllegalStateException("only the host in LOBBY selects config");
        }
        return new LobbySnapshot(role, state, Objects.requireNonNull(next, "config"),
                Math.incrementExact(configRevision), false, false, peerIdentity);
    }

    public LobbySnapshot readyAtRevision(long revision, boolean ready) {
        if (state != State.LOBBY || role == Role.NONE || config == null || peerIdentity == null
                || configRevision == 0) {
            throw new IllegalStateException("READY requires a connected, configured LOBBY");
        }
        if (revision != configRevision) throw new IllegalArgumentException("stale config revision");
        return new LobbySnapshot(role, state, config, configRevision, ready, peerReady, peerIdentity);
    }
}
