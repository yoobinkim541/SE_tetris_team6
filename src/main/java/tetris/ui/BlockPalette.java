package tetris.ui;

import java.awt.Color;
import tetris.component.block.BlockType;

/** 블록 색상 (RND-4~6). 기본/색맹 팔레트와 글자색 선택. */
public class BlockPalette {
    public Color color(BlockType type, boolean colorBlindMode) {
        throw new UnsupportedOperationException("TODO");
    }

    /** 배경색 밝기에 따라 흑/백 글자색 선택 */
    public Color textColor(Color background) {
        throw new UnsupportedOperationException("TODO");
    }
}
