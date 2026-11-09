package tetris.ui.versus;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import tetris.component.block.BlockType;
import tetris.ui.BlockPalette;
import tetris.feature.versus.api.VersusRules;

/** Shared local/P2P board drawing. Attack is a display value, never a generated BlockType. */
public final class BoardRenderer {
    private final BlockPalette palette = new BlockPalette();
    private final boolean colorBlind;
    public BoardRenderer(boolean colorBlind) { this.colorBlind = colorBlind; }
    public void paint(Graphics2D g, int[][] cells, int x, int y, int size) {
        Graphics2D board=(Graphics2D) g.create();
        try {
            for (int r=0;r<cells.length;r++) for (int c=0;c<cells[r].length;c++)
                paintCell(board,cells[r][c],x+c*size,y+r*size,size);
        } finally { board.dispose(); }
    }
    /** items: cells와 같은 크기. 0이 아닌 칸은 아이템 문자(L·B·D·S)를 흰 바탕에 그린다 (R3-ITEM) */
    public void paint(Graphics2D g, int[][] cells, int[][] items, int x, int y, int size) {
        paint(g, cells, x, y, size);
        Graphics2D board=(Graphics2D) g.create();
        try {
            for (int r=0;r<items.length;r++) for (int c=0;c<items[r].length;c++)
                if (items[r][c] != 0) paintItem(board,(char) items[r][c],x+c*size,y+r*size,size);
        } finally { board.dispose(); }
    }
    private void paintItem(Graphics2D g, char symbol, int x, int y, int size) {
        int inset = Math.max(1, size/8);
        g.setColor(Color.WHITE); g.fillRect(x+inset,y+inset,size-2*inset,size-2*inset);
        g.setColor(Color.BLACK); g.drawRect(x+inset,y+inset,size-2*inset-1,size-2*inset-1);
        if (size < 9) return;
        g.setFont(new Font(Font.MONOSPACED,Font.BOLD,Math.max(8,size*2/3)));
        String text = String.valueOf(symbol);
        g.drawString(text,x+(size-g.getFontMetrics().stringWidth(text))/2,y+(size+g.getFontMetrics().getAscent())/2-2);
    }
    public void paintCell(Graphics2D g, int value, int x, int y, int size) {
        if (size <= 0) return;
        if (value < 0 || value > VersusRules.ATTACK_CELL) throw new IllegalArgumentException("unknown cell");
        boolean attack = value == VersusRules.ATTACK_CELL;
        Color fill = value == 0 ? new Color(0x262A33) : attack ? new Color(0x929AA5)
            : palette.color(BlockType.values()[value-1],colorBlind);
        g.setColor(fill); g.fillRect(x,y,size,size);
        g.setColor(new Color(0x323744)); g.drawRect(x,y,size-1,size-1);
        if (attack) {
            g.setColor(Color.BLACK);
            // A cross remains visible at small sizes and distinguishes gray W without color.
            int inset = Math.max(2,size/4);
            g.drawLine(x+inset,y+inset,x+size-inset-1,y+size-inset-1);
            g.drawLine(x+size-inset-1,y+inset,x+inset,y+size-inset-1);
        } else if (value != 0 && size >= 11) {
            g.setColor(palette.textColor(fill)); g.setFont(new Font(Font.MONOSPACED,Font.BOLD,Math.max(9,size*2/3)));
            String letter = BlockType.values()[value-1].name();
            g.drawString(letter,x+(size-g.getFontMetrics().stringWidth(letter))/2,y+(size+g.getFontMetrics().getAscent())/2-2);
        }
    }
}
