package tetris.feature.save;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import tetris.feature.network.EstablishException;
import tetris.feature.network.PeerAddress;

/**
 * recent-peers.json 기반 최근 접속 성공 주소(최대 5개, 최신순, 중복 제거) 저장소 (S10-C2, R3-RECENT).
 */
public final class JsonRecentPeerRepository implements RecentPeers {
    private static final String FILE_NAME = "recent-peers.json";

    private final JsonFile file;

    public JsonRecentPeerRepository(Path file) {
        this.file = new JsonFile(Objects.requireNonNull(file, "file"));
    }

    public static Path defaultFile() {
        return DataDirectory.resolve().resolve(FILE_NAME);
    }

    @Override
    public synchronized List<String> load() {
        JsonFile.ReadResult read = file.read();
        return switch (read.status()) {
            case MISSING, UNREADABLE -> List.of();
            case MALFORMED -> emptyAfterMovingAsideCorruptFile();
            case OK -> parsePeers(read.root().path("peers"));
        };
    }

    @Override
    public synchronized void record(String address) {
        String normalized = normalize(address);
        if (normalized == null) {
            return;
        }
        List<String> updated = new ArrayList<>(load());
        updated.remove(normalized);
        updated.addFirst(normalized);
        if (updated.size() > MAX) {
            updated = new ArrayList<>(updated.subList(0, MAX));
        }
        ObjectNode root = JsonFile.newRoot();
        ArrayNode peers = root.putArray("peers");
        for (String peer : updated) {
            peers.add(peer);
        }
        file.write(root);
    }

    private List<String> parsePeers(JsonNode peersNode) {
        if (!peersNode.isArray()) {
            return emptyAfterMovingAsideCorruptFile();
        }
        List<String> valid = new ArrayList<>();
        for (JsonNode item : peersNode) {
            if (!item.isTextual()) {
                continue;
            }
            String normalized = normalize(item.asText());
            if (normalized != null && !valid.contains(normalized)) {
                valid.add(normalized);
                if (valid.size() >= MAX) {
                    break;
                }
            }
        }
        return List.copyOf(valid);
    }

    private List<String> emptyAfterMovingAsideCorruptFile() {
        file.moveAsideAsCorrupt();
        return List.of();
    }

    private static String normalize(String address) {
        if (address == null) {
            return null;
        }
        try {
            return PeerAddress.parse(address, 1).host();
        } catch (EstablishException e) {
            return null;
        }
    }
}
