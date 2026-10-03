package tetris.feature.save;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** 두 저장소가 함께 쓰는 JSON 읽기·원자적 쓰기. Jackson은 save 패키지 밖으로 나가지 않는다 (PER-3) */
final class JsonFiles {
    static final int SCHEMA_VERSION = 1; // PER-5

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private JsonFiles() {
    }

    static ObjectNode newObject() {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("schemaVersion", SCHEMA_VERSION);
        return root;
    }

    /** 구문이 깨졌으면 IOException */
    static JsonNode read(Path file) throws IOException {
        return MAPPER.readTree(file.toFile());
    }

    /** 같은 폴더의 임시 파일에 다 쓴 뒤 교체한다. 쓰다가 종료돼도 원본이 반쯤 쓰인 상태로 남지 않는다 (PER-4) */
    static void writeAtomically(Path file, JsonNode root) throws IOException {
        Path dir = file.toAbsolutePath().getParent();
        Files.createDirectories(dir);
        Path temp = Files.createTempFile(dir, file.getFileName().toString(), ".tmp");
        try {
            MAPPER.writeValue(temp.toFile(), root);
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /** 손상 파일을 *.corrupt로 옮겨 다음 정상 저장에 덮이지 않게 둔다 (명세 12.2 권장). 실패해도 무시 */
    static void moveToCorrupt(Path file) {
        try {
            Files.move(file, file.resolveSibling(file.getFileName() + ".corrupt"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // 보관은 선택 기능이라 실패해도 로드는 계속한다
        }
    }
}
