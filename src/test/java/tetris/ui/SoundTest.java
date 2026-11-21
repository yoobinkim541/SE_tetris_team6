package tetris.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tetris.feature.raid.api.RaidEvent;
import tetris.ui.fx.SoundEffects;
import tetris.ui.fx.SoundId;

/** 효과음 자원과, 장치·파일이 없어도 예외가 나지 않는 재생기 */
@Timeout(30)
class SoundTest {
    @Test
    void everySoundIdHasAPcmWavOnTheClasspath() throws Exception {
        long total = 0;
        assertEquals(30, SoundId.values().length);
        for (SoundId id : SoundId.values()) {
            assertEquals("/sfx/" + id.stem() + ".wav", id.resourcePath());
            try (InputStream in = SoundTest.class.getResourceAsStream(id.resourcePath())) {
                assertNotNull(in, id.resourcePath());
                byte[] data = in.readAllBytes();
                total += data.length;
                try (AudioInputStream audio = AudioSystem.getAudioInputStream(new ByteArrayInputStream(data))) {
                    AudioFormat format = audio.getFormat();
                    assertEquals(AudioFormat.Encoding.PCM_SIGNED, format.getEncoding(), id.stem());
                    assertEquals(1, format.getChannels(), id.stem());
                    assertEquals(16, format.getSampleSizeInBits(), id.stem());
                    float expectedRate = id.loops() ? 16000f : 22050f;
                    assertEquals(expectedRate, format.getSampleRate(), 0.1f, id.stem());
                    assertTrue(audio.getFrameLength() > (long) (0.02 * expectedRate), id.stem());
                    double seconds = audio.getFrameLength() / format.getSampleRate();
                    double limit = id == SoundId.BGM_RAID ? 60.0 : id == SoundId.BGM_MENU ? 40.0 : 2.5;
                    assertTrue(seconds <= limit, id.stem() + " " + seconds);
                    if (id.loops()) assertSeam(id, audio.readAllBytes());
                }
            }
        }
        assertTrue(total <= 1_800_000L, "sfx " + total);
        assertTrue(SoundId.BGM_RAID.loops());
        assertTrue(SoundId.BGM_MENU.loops());
        assertFalse(SoundId.MOVE.loops());
    }

    @Test
    void missingMixerAndAbsentFileDoNotThrow() {
        int[] opens = {0};
        SoundEffects missingMixer = new SoundEffects(SoundEffects.class::getResourceAsStream, wav -> {
            opens[0]++;
            throw new LineUnavailableException("no mixer");
        });
        assertFalse(missingMixer.play(SoundId.MOVE));
        assertFalse(missingMixer.play(SoundId.ROTATE));
        assertFalse(missingMixer.loop(SoundId.BGM_RAID));
        missingMixer.stopLoop();
        missingMixer.close();
        assertEquals(1, opens[0]);
        assertNull(missingMixer.looping());

        int[] opensAfterMiss = {0};
        SoundEffects absent = new SoundEffects(path -> null, wav -> {
            opensAfterMiss[0]++;
            throw new LineUnavailableException("should not open");
        });
        assertFalse(absent.play(SoundId.LOCK));
        assertFalse(absent.loop(SoundId.BGM_MENU));
        absent.stopLoop();
        assertEquals(0, opensAfterMiss[0]);
        assertFalse(absent.play(null));
        assertFalse(absent.loop(null));
    }

    @Test
    void badFileDoesNotDisableTheDevice() {
        int[] opens = {0};
        SoundEffects fx = new SoundEffects(SoundEffects.class::getResourceAsStream, wav -> {
            opens[0]++;
            throw new IllegalArgumentException("bad format");
        });
        assertFalse(fx.play(SoundId.MOVE));
        assertFalse(fx.play(SoundId.ROTATE));
        assertFalse(fx.loop(SoundId.BGM_MENU));
        assertEquals(3, opens[0]);
    }

    @Test
    void volumeClampsAndMuteDoesNotOpenTheDevice() {
        int[] opens = {0};
        SoundEffects fx = new SoundEffects(SoundEffects.class::getResourceAsStream, wav -> {
            opens[0]++;
            throw new LineUnavailableException("no mixer");
        });
        assertEquals(100, fx.volume());
        assertFalse(fx.muted());

        fx.setVolume(40);
        assertEquals(40, fx.volume());
        fx.setVolume(-10);
        assertEquals(0, fx.volume());
        fx.setVolume(250);
        assertEquals(100, fx.volume());
        fx.setVolume(0);
        assertEquals(0, fx.volume());
        assertFalse(fx.play(SoundId.MOVE));
        assertFalse(fx.loop(SoundId.BGM_MENU));

        fx.setVolume(70);
        fx.setMuted(true);
        assertTrue(fx.muted());
        assertEquals(70, fx.volume());
        assertFalse(fx.play(SoundId.GO));
        assertFalse(fx.loop(SoundId.BGM_MENU));
        fx.stopLoop();
        fx.setMuted(false);
        assertFalse(fx.muted());
        assertEquals(70, fx.volume());
        assertEquals(0, opens[0]);
    }

    @Test
    void propertyOffIsANoOp() {
        String previous = System.getProperty(SoundEffects.DISABLE_PROPERTY);
        System.setProperty(SoundEffects.DISABLE_PROPERTY, "off");
        try {
            SoundEffects fx = new SoundEffects(path -> {
                fail("꺼진 상태에서는 파일을 열지 않는다");
                return null;
            }, wav -> {
                fail("꺼진 상태에서는 믹서를 열지 않는다");
                return null;
            });
            assertFalse(fx.play(SoundId.COUNTDOWN));
            assertFalse(fx.loop(SoundId.BGM_MENU));
            fx.stopLoop();
            fx.setVolume(15);
            assertEquals(15, fx.volume());
            fx.setMuted(true);
            assertTrue(fx.muted());
            fx.close();
        } finally {
            if (previous == null) System.clearProperty(SoundEffects.DISABLE_PROPERTY);
            else System.setProperty(SoundEffects.DISABLE_PROPERTY, previous);
        }
    }

    @Test
    void raidEventsMapToSounds() {
        assertNull(SoundEffects.forRaidEvent(null));
        assertEquals(SoundId.BOSS_HIT, sound(RaidEvent.Type.DAMAGE));
        assertEquals(SoundId.CRITICAL, sound(RaidEvent.Type.CRITICAL));
        assertEquals(SoundId.COMBO, sound(RaidEvent.Type.COMBO));
        assertEquals(SoundId.TSPIN, sound(RaidEvent.Type.TSPIN));
        assertEquals(SoundId.ATTACK_SEND, sound(RaidEvent.Type.SYNC));
        assertEquals(SoundId.BOSS_ATTACK_WARN, sound(RaidEvent.Type.BOSS_ATTACK));
        assertEquals(SoundId.LOCK, sound(RaidEvent.Type.BLOCKED));
        assertEquals(SoundId.HOLD, sound(RaidEvent.Type.SHIELDED));
        assertEquals(SoundId.REVIVE, sound(RaidEvent.Type.CLEANSED));
        assertEquals(SoundId.DEFEAT, sound(RaidEvent.Type.PLAYER_DOWN));
        assertEquals(SoundId.REVIVE, sound(RaidEvent.Type.REVIVED));
        assertEquals(SoundId.ULTIMATE, sound(RaidEvent.Type.ULTIMATE));
        assertEquals(SoundId.BOSS_SPAWN, sound(RaidEvent.Type.BOSS_SPAWN));
        assertEquals(SoundId.BOSS_DEFEAT, sound(RaidEvent.Type.BOSS_DEFEATED));
        assertEquals(SoundId.ATTACK_RECEIVE, sound(RaidEvent.Type.BOSS_ESCAPED));
        assertEquals(SoundId.CARD_PICK, sound(RaidEvent.Type.CARD_PICKED));
        for (RaidEvent.Type type : RaidEvent.Type.values()) {
            assertNotNull(sound(type), type.name());
        }
    }

    private static SoundId sound(RaidEvent.Type type) {
        return SoundEffects.forRaidEvent(new RaidEvent(type, -1, 0));
    }

    /** 루프의 끝과 처음 차이가 곡 안의 보통 샘플 간격보다 크면 클릭이다. */
    private static void assertSeam(SoundId id, byte[] pcm) {
        int frames = pcm.length / 2;
        assertTrue(frames > 2, id.stem());
        int first = sample(pcm, 0);
        int last = sample(pcm, frames - 1);
        int maxStep = 0;
        int prev = first;
        for (int i = 1; i < frames; i++) {
            int cur = sample(pcm, i);
            maxStep = Math.max(maxStep, Math.abs(cur - prev));
            prev = cur;
        }
        int boundary = Math.abs(first - last);
        assertTrue(boundary <= maxStep + 1, id.stem() + " seam " + boundary + " > " + maxStep);
    }

    private static int sample(byte[] pcm, int index) {
        int at = index * 2;
        return (short) ((pcm[at] & 0xff) | (pcm[at + 1] << 8));
    }
}
