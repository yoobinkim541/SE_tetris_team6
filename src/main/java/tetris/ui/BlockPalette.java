package tetris.ui;

import java.awt.Color;
import java.util.EnumMap;
import java.util.Map;
import tetris.component.block.BlockType;

/** 블록 색상 (RND-4~6). 기본/색맹 팔레트와 글자색 선택. */
public class BlockPalette {
    private static final Map<BlockType, Color> DEFAULT = new EnumMap<>(BlockType.class);
    private static final Map<BlockType, Color> COLOR_BLIND = new EnumMap<>(BlockType.class); // Okabe-Ito 계열
    private static final double LIGHT_BACKGROUND_LUMA = 140; // 이 밝기 이상이면 검은 글자

    static {
        DEFAULT.put(BlockType.I, new Color(0x00BCD4));
        DEFAULT.put(BlockType.J, new Color(0x1E4FD8));
        DEFAULT.put(BlockType.L, new Color(0xF57C00));
        DEFAULT.put(BlockType.O, new Color(0xFBC02D));
        DEFAULT.put(BlockType.S, new Color(0x2E9E44));
        DEFAULT.put(BlockType.T, new Color(0x8E24AA));
        DEFAULT.put(BlockType.Z, new Color(0xD32F2F));

        COLOR_BLIND.put(BlockType.I, new Color(0x56B4E9));
        COLOR_BLIND.put(BlockType.J, new Color(0x0072B2));
        COLOR_BLIND.put(BlockType.L, new Color(0xE69F00));
        COLOR_BLIND.put(BlockType.O, new Color(0xF0E442));
        COLOR_BLIND.put(BlockType.S, new Color(0x009E73));
        COLOR_BLIND.put(BlockType.T, new Color(0xCC79A7));
        COLOR_BLIND.put(BlockType.Z, new Color(0xD55E00));
    }

    public Color color(BlockType type, boolean colorBlindMode) {
        if (type == null) throw new IllegalArgumentException("type cannot be null");
        return (colorBlindMode ? COLOR_BLIND : DEFAULT).get(type);
    }

    /** 배경색 밝기에 따라 흑/백 글자색 선택 */
    public Color textColor(Color background) {
        double luma = 0.299 * background.getRed() + 0.587 * background.getGreen() + 0.114 * background.getBlue();
        return luma >= LIGHT_BACKGROUND_LUMA ? Color.BLACK : Color.WHITE;
    }
}
