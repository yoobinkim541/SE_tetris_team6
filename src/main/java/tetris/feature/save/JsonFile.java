package tetris.feature.save;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** 저장 파일 하나를 JSON 객체로 읽고 쓴다 (PER-3~5). Jackson은 이 계층에서만 쓴다 */
final class JsonFile {
    enum ReadStatus { OK, MISSING, UNREADABLE, MALFORMED }

    /** root는 status가 OK일 때만 있고 항상 JSON 객체다 */
    record ReadResult(ReadStatus status, JsonNode root) {
    }

    private static final int SCHEMA_VERSION = 1;
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, true);

    private final Path path;

    JsonFile(Path path) {
        this.path = path;
    }

    /** schemaVersion이 들어 있는 저장용 최상위 객체 */
    static ObjectNode newRoot() {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("schemaVersion", SCHEMA_VERSION);
        return root;
    }

    ReadResult read() {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(path);
        } catch (NoSuchFileException e) {
            return new ReadResult(ReadStatus.MISSING, null);
        } catch (IOException e) {
            return new ReadResult(ReadStatus.UNREADABLE, null);
        }

        try {
            JsonNode root = MAPPER.readTree(bytes);
            if (root != null && root.isObject()) return new ReadResult(ReadStatus.OK, root);
        } catch (IOException e) {
            // 구문 손상은 아래에서 MALFORMED로 돌려준다
        }
        return new ReadResult(ReadStatus.MALFORMED, null);
    }

    /** 임시 파일에 쓴 뒤 교체한다 (PER-4). 실패하면 false */
    boolean write(ObjectNode root) {
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.createDirectories(path.toAbsolutePath().getParent());
            Files.write(temp, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(root));
            replaceWithTemp(temp);
            return true;
        } catch (IOException | RuntimeException e) {
            deleteQuietly(temp);
            return false;
        }
    }

    /** 손상 파일을 *.corrupt로 옮겨 둔다. 실패해도 다음 정상 저장이 덮어쓴다 */
    void moveAsideAsCorrupt() {
        try {
            Files.move(path, path.resolveSibling(path.getFileName() + ".corrupt"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // 보존은 권장 사항일 뿐이다
        }
    }

    private void replaceWithTemp(Path temp) throws IOException {
        try {
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // 임시 파일 정리 실패는 저장 실패 결과를 바꾸지 않는다
        }
    }
}
