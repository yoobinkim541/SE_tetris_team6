package tetris.feature;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import tetris.feature.save.JsonRecentPeerRepository;
import tetris.feature.save.RecentPeers;

/** S10-C2: recent-peers.json 최근 접속 주소 5개 저장소 검증 (R3-RECENT) */
@Timeout(30)
class RecentPeerRepositoryTest {
    @TempDir
    Path dir;

    private Path peerFile() {
        return dir.resolve("recent-peers.json");
    }

    @Test
    void 파일이_없으면_빈_목록() {
        RecentPeers repo = new JsonRecentPeerRepository(peerFile());
        assertEquals(List.of(), repo.load());
        assertEquals(List.of(), RecentPeers.NONE.load());
        assertEquals("recent-peers.json", JsonRecentPeerRepository.defaultFile().getFileName().toString());
    }

    @Test
    void 기록하면_최신순이고_같은_주소는_맨_앞으로_한_번만() {
        RecentPeers repo = new JsonRecentPeerRepository(peerFile());
        repo.record("192.168.0.1");
        repo.record("192.168.0.2");
        repo.record("192.168.0.3");
        assertEquals(List.of("192.168.0.3", "192.168.0.2", "192.168.0.1"), repo.load());

        repo.record("  192.168.0.1  ");
        assertEquals(List.of("192.168.0.1", "192.168.0.3", "192.168.0.2"), repo.load());
    }

    @Test
    void _6개째를_기록하면_가장_오래된_주소가_빠진다() {
        RecentPeers repo = new JsonRecentPeerRepository(peerFile());
        repo.record("10.0.0.1");
        repo.record("10.0.0.2");
        repo.record("10.0.0.3");
        repo.record("10.0.0.4");
        repo.record("10.0.0.5");
        repo.record("10.0.0.6");

        assertEquals(
                List.of("10.0.0.6", "10.0.0.5", "10.0.0.4", "10.0.0.3", "10.0.0.2"),
                repo.load());
    }

    @Test
    void 손상된_JSON이면_빈_목록이고_corrupt로_옮긴_뒤_다음_기록이_정상_저장된다() throws IOException {
        Path file = peerFile();
        Files.writeString(file, "{ \"peers\": [ \"192.168.0.1\"", StandardCharsets.UTF_8);

        RecentPeers repo = new JsonRecentPeerRepository(file);
        assertEquals(List.of(), repo.load());
        assertFalse(Files.exists(file));
        assertTrue(Files.exists(dir.resolve("recent-peers.json.corrupt")));

        repo.record("192.168.0.7");
        assertEquals(List.of("192.168.0.7"), repo.load());
        assertTrue(Files.exists(file));
    }

    @Test
    void 형식이_틀린_항목은_무시한다() throws IOException {
        Path file = peerFile();
        Files.writeString(
                file,
                "{\"schemaVersion\":1,\"peers\":[\"999.1.1.1\",\"abc\",3,\"10.0.0.2\"]}",
                StandardCharsets.UTF_8);

        RecentPeers repo = new JsonRecentPeerRepository(file);
        assertEquals(List.of("10.0.0.2"), repo.load());
    }

    @Test
    void 잘못된_주소는_기록하지_않는다() {
        Path file = peerFile();
        RecentPeers repo = new JsonRecentPeerRepository(file);
        repo.record(null);
        repo.record("");
        repo.record("localhost");
        repo.record("999.1.1.1");
        repo.record("0.0.0.0");
        repo.record("255.255.255.255");

        assertEquals(List.of(), repo.load());
        assertFalse(Files.exists(file));
    }

    @Test
    void 쓰기에_실패해도_예외를_던지지_않는다() throws IOException {
        Path directoryInPlaceOfFile = Files.createDirectory(peerFile());
        Files.writeString(directoryInPlaceOfFile.resolve("child"), "x", StandardCharsets.UTF_8);

        RecentPeers repo = new JsonRecentPeerRepository(directoryInPlaceOfFile);
        assertEquals(List.of(), repo.load());
        assertDoesNotThrow(() -> repo.record("192.168.0.10"));
    }

    @Test
    void 다시_만든_저장소가_같은_목록을_읽는다() throws IOException {
        Path file = peerFile();
        new JsonRecentPeerRepository(file).record("192.168.1.10");
        new JsonRecentPeerRepository(file).record("192.168.1.20");

        RecentPeers reloaded = new JsonRecentPeerRepository(file);
        assertEquals(List.of("192.168.1.20", "192.168.1.10"), reloaded.load());
        assertTrue(Files.readString(file, StandardCharsets.UTF_8).contains("\"schemaVersion\" : 1"));
    }
}
