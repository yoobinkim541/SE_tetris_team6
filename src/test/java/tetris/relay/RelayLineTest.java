package tetris.relay;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** 중계 핸드셰이크 줄: \n까지 바이트 단위로 읽고 뒤의 바이트는 남긴다 */
class RelayLineTest {
    @Test
    void 줄_뒤에_붙은_바이트는_스트림에_그대로_남는다() throws IOException {
        byte[] data = {'O', 'K', '\n', 0, 0, 0, 5, 1, 2};
        ByteArrayInputStream in = new ByteArrayInputStream(data);

        assertEquals("OK", RelayLine.read(in));
        assertArrayEquals(new byte[]{0, 0, 0, 5, 1, 2}, in.readAllBytes());
    }

    @Test
    void 쓰기는_줄바꿈을_붙인_UTF8이다() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        RelayLine.write(out, "ROOM K7QM2X");
        assertEquals("ROOM K7QM2X\n", out.toString(StandardCharsets.UTF_8));
    }

    @Test
    void 최대_길이를_넘는_줄은_거부한다() {
        byte[] longLine = ("A".repeat(RelayLine.MAX_BYTES + 1) + "\n").getBytes(StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> RelayLine.read(new ByteArrayInputStream(longLine)));
    }

    @Test
    void 정확히_최대_길이는_받는다() throws IOException {
        String line = "A".repeat(RelayLine.MAX_BYTES);
        assertEquals(line, RelayLine.read(new ByteArrayInputStream((line + "\n").getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void 줄바꿈_전에_끝나면_거부한다() {
        assertThrows(IOException.class, () -> RelayLine.read(new ByteArrayInputStream("OK".getBytes(StandardCharsets.UTF_8))));
    }
}
