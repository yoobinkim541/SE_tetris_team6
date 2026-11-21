package tetris.relay;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 중계 핸드셰이크 한 줄. 게임 프레임이 바로 뒤에 붙어 올 수 있으므로 미리 읽기 버퍼 없이 {@code \n}까지
 * 한 바이트씩 읽고, 그 뒤 바이트는 스트림에 남긴다.
 */
public final class RelayLine {
    public static final int MAX_BYTES = 64;

    private RelayLine() {
    }

    /** 줄바꿈 전까지 읽는다. 64바이트 초과·줄바꿈 전 종료는 {@link IOException} */
    public static String read(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream(MAX_BYTES);
        while (true) {
            int b = in.read();
            if (b < 0) throw new IOException("relay line ended before newline");
            if (b == '\n') return line.toString(StandardCharsets.UTF_8);
            if (line.size() >= MAX_BYTES) throw new IOException("relay line longer than " + MAX_BYTES + " bytes");
            line.write(b);
        }
    }

    public static void write(OutputStream out, String line) throws IOException {
        out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }
}
