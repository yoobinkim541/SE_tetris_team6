package tetris.feature.raid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import tetris.component.block.Block;
import tetris.component.block.SBlock;
import tetris.component.block.ZBlock;
import tetris.feature.game.GameSession;
import tetris.feature.raid.api.PatternId;
import tetris.feature.versus.api.AttackRows;
import tetris.feature.versus.api.VersusRules;

/** 12패턴. 막아주기 2줄은 바위 −1, 소환 취소, 지속 조각 수를 절반으로 줄인다 */
public final class Patterns {
    private static final int GRAY = VersusRules.ATTACK_CELL;

    private Patterns() {
    }

    public static BossPattern of(PatternId id) {
        return switch (id) {
            case CLOUD -> new Cloud();
            case SUMMON_BLOCK -> new Summon();
            case ROCK_THROW -> new Rock();
            case PILLAR -> new Pillar();
            case CURSE_BLOCKS -> new Curse();
            case SEAL_PREVIEW -> new Seal();
            case OVERLOAD -> new Overload();
            case CHAIN -> new Chain();
            case REVERSE -> new Reverse();
            case SPLIT -> new Split();
            case EARTHQUAKE -> new Earthquake();
            case FIRE_BREATH -> new Fire();
        };
    }

    static int duration(PatternContext context, int full) {
        if (context.blockLines() >= 2) return Math.max(1, full / 2);
        return full;
    }

    static int[] garbageRow(Random random) {
        int[] row = new int[VersusRules.BOARD_COLS];
        Arrays.fill(row, GRAY);
        row[random.nextInt(VersusRules.BOARD_COLS)] = 0;
        return row;
    }

    static AttackRows garbage(Random random, int rows) {
        if (rows <= 0) return AttackRows.NONE;
        List<int[]> list = new ArrayList<>();
        for (int i = 0; i < rows; i++) list.add(garbageRow(random));
        return new AttackRows(list);
    }

    static void queue(PatternContext context, int player, int rows) {
        if (rows <= 0) return;
        AttackRows attack = garbage(context.random(), rows);
        if (player < 0) {
            for (int i = 0; i < context.players(); i++) context.queueAttack(i, attack);
        } else {
            context.queueAttack(player, attack);
        }
    }

    static int rockRows(PatternContext context) {
        int rows = context.level() >= 4 ? 3 : 2;
        if (context.blockLines() >= 2) rows--;
        return rows;
    }

    private static final class Cloud implements BossPattern {
        public PatternId id() { return PatternId.CLOUD; }

        public void apply(PatternContext context) {
            int row = context.random().nextInt(17);
            int col = context.random().nextInt(8);
            context.addEffect(Math.max(0, context.target()), PatternId.CLOUD, duration(context, 5), new int[] {row, col, 4, 3});
        }
    }

    private static final class Summon implements BossPattern {
        public PatternId id() { return PatternId.SUMMON_BLOCK; }

        public void apply(PatternContext context) {
            if (context.blockLines() >= 2) return;
            int player = Math.max(0, context.target());
            GameSession session = context.session(player);
            int[][] cells = session.snapshot().cells();
            List<int[]> empty = new ArrayList<>();
            for (int row = 10; row < VersusRules.BOARD_ROWS; row++) {
                for (int col = 0; col < VersusRules.BOARD_COLS; col++) {
                    if (cells[row][col] == 0) empty.add(new int[] {row, col});
                }
            }
            if (empty.isEmpty()) return;
            int size = 3 + context.random().nextInt(2);
            int[] start = empty.get(context.random().nextInt(empty.size()));
            boolean[][] taken = new boolean[VersusRules.BOARD_ROWS][VersusRules.BOARD_COLS];
            List<int[]> chunk = new ArrayList<>();
            chunk.add(start);
            taken[start[0]][start[1]] = true;
            int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            while (chunk.size() < size) {
                List<int[]> frontier = new ArrayList<>();
                for (int[] cell : chunk) {
                    for (int[] dir : dirs) {
                        int row = cell[0] + dir[0];
                        int col = cell[1] + dir[1];
                        if (row < 10 || row >= VersusRules.BOARD_ROWS || col < 0 || col >= VersusRules.BOARD_COLS) continue;
                        if (taken[row][col] || cells[row][col] != 0) continue;
                        boolean listed = false;
                        for (int[] already : frontier) if (already[0] == row && already[1] == col) listed = true;
                        if (!listed) frontier.add(new int[] {row, col});
                    }
                }
                if (frontier.isEmpty()) break;
                int[] next = frontier.get(context.random().nextInt(frontier.size()));
                taken[next[0]][next[1]] = true;
                chunk.add(next);
            }
            for (int[] cell : chunk) session.writeFixedCell(cell[0], cell[1], GRAY);
        }
    }

    private static final class Rock implements BossPattern {
        public PatternId id() { return PatternId.ROCK_THROW; }

        public void apply(PatternContext context) {
            queue(context, context.target(), rockRows(context));
        }
    }

    private static final class Pillar implements BossPattern {
        public PatternId id() { return PatternId.PILLAR; }

        public void apply(PatternContext context) {
            int player = Math.max(0, context.target());
            GameSession session = context.session(player);
            int col = context.random().nextInt(VersusRules.BOARD_COLS);
            int[][] cells = session.snapshot().cells();
            int filled = 0;
            for (int row = VersusRules.BOARD_ROWS - 1; row >= 0 && filled < 4; row--) {
                if (cells[row][col] == 0 && session.writeFixedCell(row, col, GRAY)) filled++;
            }
        }
    }

    private static final class Curse implements BossPattern {
        public PatternId id() { return PatternId.CURSE_BLOCKS; }

        public void apply(PatternContext context) {
            int player = Math.max(0, context.target());
            // 이미 보여 준 바로 다음은 forceUpcoming이 갈아끼우지 않는다. 그 뒤 2개만 S/Z.
            context.forceNext(player, curse(context.random()), curse(context.random()));
        }

        private static Block curse(Random random) {
            return random.nextBoolean() ? new SBlock() : new ZBlock();
        }
    }

    private static final class Seal implements BossPattern {
        public PatternId id() { return PatternId.SEAL_PREVIEW; }

        public void apply(PatternContext context) {
            context.addEffect(Math.max(0, context.target()), PatternId.SEAL_PREVIEW, duration(context, 4), new int[0]);
        }
    }

    private static final class Overload implements BossPattern {
        public PatternId id() { return PatternId.OVERLOAD; }

        public void apply(PatternContext context) {
            int player = Math.max(0, context.target());
            context.addEffect(player, PatternId.OVERLOAD, duration(context, 5), new int[0]);
            context.setGravity(player, 2.0);
        }
    }

    private static final class Chain implements BossPattern {
        public PatternId id() { return PatternId.CHAIN; }

        public void apply(PatternContext context) {
            if (!context.coop()) {
                queue(context, 0, 1);
                return;
            }
            queue(context, -1, 1);
            int pieces = duration(context, 8);
            for (int i = 0; i < context.players(); i++) {
                context.addEffect(i, PatternId.CHAIN, pieces, new int[0]);
            }
        }
    }

    private static final class Reverse implements BossPattern {
        public PatternId id() { return PatternId.REVERSE; }

        public void apply(PatternContext context) {
            context.addEffect(Math.max(0, context.target()), PatternId.REVERSE, duration(context, 3), new int[0]);
        }
    }

    private static final class Split implements BossPattern {
        public PatternId id() { return PatternId.SPLIT; }

        public void apply(PatternContext context) {
            context.split();
        }
    }

    private static final class Earthquake implements BossPattern {
        public PatternId id() { return PatternId.EARTHQUAKE; }

        public void apply(PatternContext context) {
            queue(context, context.target(), 1);
        }
    }

    private static final class Fire implements BossPattern {
        public PatternId id() { return PatternId.FIRE_BREATH; }

        public void apply(PatternContext context) {
            int rows = context.level() >= 4 ? 3 : 2;
            if (!context.coop()) rows *= 2;
            queue(context, context.coop() ? -1 : 0, rows);
        }
    }
}
