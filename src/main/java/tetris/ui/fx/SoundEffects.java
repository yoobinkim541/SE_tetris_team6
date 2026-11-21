package tetris.ui.fx;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import tetris.feature.raid.api.RaidEvent;

/**
 * WAV 효과음. 클래스패스에서 처음 쓸 때 읽고, 같은 소리를 최대 4개까지 겹쳐 튼다.
 * 재생이 끝나기를 기다리지 않는다.
 *
 * <p>믹서가 없거나 파일이 없어도 예외를 던지지 않는다. 파일 형식이 나빠도
 * 그 소리만 건너뛰고, 장치 전체를 끄지 않는다. 시스템 속성
 * {@code tetris.sound=off} 이면 재생 메서드는 아무 것도 하지 않는다.
 */
public final class SoundEffects {
    /** 이 값이 {@code off}이면 소리를 열지 않는다 */
    public static final String DISABLE_PROPERTY = "tetris.sound";

    /** 화면이 공유하는 재생기. 테스트는 따로 만든다 */
    public static final SoundEffects SHARED = new SoundEffects();

    private static final int VOICES = 4;

    private final ResourceLoader loader;
    private final ClipOpener opener;
    private final Map<SoundId, byte[]> cache = new HashMap<>();
    private final Map<SoundId, Boolean> missing = new HashMap<>();
    private final Map<SoundId, List<Clip>> pool = new HashMap<>();
    private Clip loopClip;
    private SoundId loopId;
    private boolean deviceDown;
    private int volume = 100;
    private boolean muted;

    public SoundEffects() {
        this(SoundEffects.class::getResourceAsStream, SoundEffects::openClip);
    }

    /**
     * 자원·장치 접근을 갈아 끼운다. 헤드리스 테스트가 없는 파일과 믹서 없음을 흉내 낸다.
     */
    public SoundEffects(ResourceLoader loader, ClipOpener opener) {
        this.loader = loader;
        this.opener = opener;
    }

    /** 한 번 재생. 음소거·꺼짐·없는 파일·장치 없음·풀이 가득 차면 false */
    public synchronized boolean play(SoundId id) {
        if (id == null || blocked() || silenced()) return false;
        byte[] wav = load(id);
        if (wav == null) return false;
        Clip clip;
        try {
            clip = takeVoice(id, wav);
        } catch (Exception ex) {
            if (isMixerFailure(ex)) deviceDown = true;
            return false;
        }
        if (clip == null) return false;
        try {
            applyGain(clip);
            clip.setFramePosition(0);
            clip.start();
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * 배경음을 이어 튼다. 이미 틀고 있던 루프는 멈춘다.
     * 음소거·볼륨 0이면 시작하지 않는다. 끄면 알아서 다시 틀지 않으니 화면이 다시 {@code loop} 한다.
     */
    public synchronized boolean loop(SoundId id) {
        if (id == null || blocked()) return false;
        stopLoopClip();
        if (silenced()) return false;
        byte[] wav = load(id);
        if (wav == null) return false;
        try {
            Clip clip = opener.open(wav);
            if (clip == null) return false;
            applyGain(clip);
            clip.setFramePosition(0);
            clip.loop(Clip.LOOP_CONTINUOUSLY);
            loopClip = clip;
            loopId = id;
            return true;
        } catch (Exception ex) {
            if (isMixerFailure(ex)) deviceDown = true;
            return false;
        }
    }

    public synchronized void stopLoop() {
        stopLoopClip();
    }

    /** 지금 루프 중인 소리. 없으면 null */
    public synchronized SoundId looping() {
        return loopId;
    }

    /** 0–100. 범위를 벗어나면 잘라 넣는다 */
    public synchronized void setVolume(int volume) {
        this.volume = Math.max(0, Math.min(100, volume));
        applyGainAll();
    }

    public synchronized int volume() {
        return volume;
    }

    public synchronized void setMuted(boolean muted) {
        this.muted = muted;
        applyGainAll();
    }

    public synchronized boolean muted() {
        return muted;
    }

    /** 연 라인을 닫는다. 다음 재생은 다시 연다 */
    public synchronized void close() {
        stopLoopClip();
        for (List<Clip> voices : pool.values()) {
            for (Clip clip : voices) closeQuietly(clip);
        }
        pool.clear();
    }

    /**
     * 레이드 사건을 가장 가까운 효과음으로 옮긴다.
     * 줄 삭제 단수(clear1~tetris)는 사건이 아니라 게임 쪽에서 고른다.
     * 사건이 없으면 null.
     */
    public static SoundId forRaidEvent(RaidEvent event) {
        if (event == null) return null;
        return switch (event.type()) {
            case DAMAGE -> SoundId.BOSS_HIT;
            case CRITICAL -> SoundId.CRITICAL;
            case COMBO -> SoundId.COMBO;
            case TSPIN -> SoundId.TSPIN;
            case SYNC -> SoundId.ATTACK_SEND;
            case BOSS_ATTACK -> SoundId.BOSS_ATTACK_WARN;
            case BLOCKED -> SoundId.LOCK;
            case SHIELDED -> SoundId.HOLD;
            case CLEANSED, REVIVED -> SoundId.REVIVE;
            case PLAYER_DOWN -> SoundId.DEFEAT;
            case ULTIMATE -> SoundId.ULTIMATE;
            case BOSS_SPAWN -> SoundId.BOSS_SPAWN;
            case BOSS_DEFEATED -> SoundId.BOSS_DEFEAT;
            case BOSS_ESCAPED -> SoundId.ATTACK_RECEIVE;
            case CARD_PICKED -> SoundId.CARD_PICK;
        };
    }

    @FunctionalInterface
    public interface ResourceLoader {
        InputStream open(String resourcePath);
    }

    @FunctionalInterface
    public interface ClipOpener {
        Clip open(byte[] wav) throws Exception;
    }

    private boolean blocked() {
        return deviceDown || "off".equalsIgnoreCase(System.getProperty(DISABLE_PROPERTY));
    }

    private boolean silenced() {
        return muted || volume <= 0;
    }

    private byte[] load(SoundId id) {
        if (Boolean.TRUE.equals(missing.get(id))) return null;
        byte[] cached = cache.get(id);
        if (cached != null) return cached;
        try (InputStream in = loader.open(id.resourcePath())) {
            if (in == null) {
                missing.put(id, Boolean.TRUE);
                return null;
            }
            byte[] data = in.readAllBytes();
            if (data.length < 44) {
                missing.put(id, Boolean.TRUE);
                return null;
            }
            cache.put(id, data);
            return data;
        } catch (Exception ex) {
            missing.put(id, Boolean.TRUE);
            return null;
        }
    }

    private Clip takeVoice(SoundId id, byte[] wav) throws Exception {
        List<Clip> voices = pool.computeIfAbsent(id, key -> new ArrayList<>(VOICES));
        for (Clip clip : voices) {
            if (clip.isOpen() && !clip.isRunning()) return clip;
        }
        if (voices.size() >= VOICES) return null;
        Clip clip = opener.open(wav);
        if (clip == null) return null;
        voices.add(clip);
        return clip;
    }

    private void applyGainAll() {
        for (List<Clip> voices : pool.values()) {
            for (Clip clip : voices) applyGain(clip);
        }
        applyGain(loopClip);
    }

    private void applyGain(Clip clip) {
        if (clip == null || !clip.isOpen()) return;
        try {
            if (!clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) return;
            FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            gain.setValue(volumeDb(gain.getMinimum(), gain.getMaximum()));
        } catch (Exception ignored) {
            // 게인을 못 건드리면 장치 기본 음량으로 둔다
        }
    }

    private float volumeDb(float min, float max) {
        if (muted || volume <= 0) return min;
        float db = (float) (20.0 * Math.log10(volume / 100.0));
        if (Float.isNaN(db) || db < min) return min;
        if (db > max) return max;
        return db;
    }

    private void stopLoopClip() {
        loopId = null;
        Clip clip = loopClip;
        loopClip = null;
        closeQuietly(clip);
    }

    private static void closeQuietly(Clip clip) {
        if (clip == null) return;
        try {
            clip.stop();
            clip.close();
        } catch (Exception ignored) {
            // 이미 닫힌 라인
        }
    }

    /** 라인을 못 여는 경우만 장치 고장이다. 파일 형식 오류는 그 소리만 건너뛴다. */
    private static boolean isMixerFailure(Exception ex) {
        return ex instanceof LineUnavailableException || ex instanceof SecurityException;
    }

    private static Clip openClip(byte[] wav) throws Exception {
        Clip clip = acquireClip();
        try (AudioInputStream in = AudioSystem.getAudioInputStream(
                new BufferedInputStream(new ByteArrayInputStream(wav)))) {
            clip.open(in);
            return clip;
        } catch (Exception ex) {
            closeQuietly(clip);
            if (ex instanceof LineUnavailableException || ex instanceof SecurityException) throw ex;
            throw new javax.sound.sampled.UnsupportedAudioFileException(
                    ex.getMessage() == null ? "bad audio" : ex.getMessage());
        }
    }

    /** 믹서가 없으면 IllegalArgumentException이 난다. 장치 없음으로 바꾼다. */
    private static Clip acquireClip() throws LineUnavailableException, SecurityException {
        try {
            return AudioSystem.getClip();
        } catch (IllegalArgumentException ex) {
            LineUnavailableException wrapped = new LineUnavailableException(
                    ex.getMessage() == null ? "mixer" : ex.getMessage());
            wrapped.initCause(ex);
            throw wrapped;
        }
    }
}
