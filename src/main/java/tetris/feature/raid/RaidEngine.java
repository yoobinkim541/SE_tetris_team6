package tetris.feature.raid;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.function.IntFunction;
import tetris.component.block.Block;
import tetris.feature.game.Difficulty;
import tetris.feature.game.GameListener;
import tetris.feature.game.GameMode;
import tetris.feature.game.GameSession;
import tetris.feature.game.GameSnapshot;
import tetris.feature.game.GameState;
import tetris.feature.item.Cell;
import tetris.feature.raid.api.BossId;
import tetris.feature.raid.api.CardId;
import tetris.feature.raid.api.PatternId;
import tetris.feature.raid.api.RaidConfig;
import tetris.feature.raid.api.RaidEvent;
import tetris.feature.raid.api.RaidMode;
import tetris.feature.raid.api.RaidPhase;
import tetris.feature.raid.api.RaidSnapshot;
import tetris.feature.rule.BlockGenerator;
import tetris.feature.rule.TSpinType;
import tetris.feature.rule.WeightedBlockGenerator;
import tetris.feature.setting.AutoRepeat;
import tetris.feature.setting.GameAction;
import tetris.feature.versus.DueTimeClock;
import tetris.feature.versus.PendingAttackQueue;
import tetris.feature.versus.api.AttackRows;
import tetris.feature.versus.api.LockResult;
import tetris.feature.versus.api.MatchClock;
import tetris.feature.versus.api.MatchSnapshot;
import tetris.feature.versus.api.PlayerCommand;
import tetris.feature.versus.api.PlayerId;
import tetris.feature.versus.api.SpawnBoundary;
import tetris.feature.versus.api.VersusRules;

/**
 * 솔로·로컬 협동 레이드 권위. Swing 없음. 같은 시드와 같은 입력이면 같은 스냅샷 순서다.
 * 줄 삭제는 Lock 정산(CLEARING 150ms 뒤)에서 데미지로 바꾸고, 쓰레기 줄은 다음 Spawn 전에 넣는다.
 */
public final class RaidEngine {
    private final MatchClock time;
    private final RaidConfig config;
    private final Random raidRandom;
    private final PatternSource patternSource;
    private final int count;
    private final boolean modern;
    private final GameSession[] sessions;
    private final Fighter[] fighters;
    private final ArrayDeque<PlayerCommand> commands = new ArrayDeque<>();
    private final List<RaidEvent> events = new ArrayList<>();
    private final List<long[]> recentDamage = new ArrayList<>();
    private final List<BossId> bag = new ArrayList<>();
    private final String raidId;

    private long wall;
    private long playClock;
    private long elapsed;
    private long revision;
    private RaidPhase phase = RaidPhase.COUNTDOWN;
    private RaidPhase pausedFrom = RaidPhase.COUNTDOWN;
    private long countdownLeft = RaidRules.COUNTDOWN_MILLIS;
    private RaidSnapshot snapshot;
    private boolean closed;

    private int level = 1;
    private int kills;
    private long totalDamage;
    private int gauge;
    private int sharpStacks;
    private int shieldCharges;
    private int ultimateCharges;
    private int timeWarp;
    private boolean luckNext;
    private boolean luckDoubled;
    private boolean goldenUsed;
    private boolean split;
    private int[] splitHp = new int[0];
    private BossId bossId = BossId.JELLY_KING;
    private int bossHp;
    private int bossMax;
    private PatternId nextPattern;
    private PatternId previousPattern;
    private PatternId beforePrevious;
    private int target;
    private int piecesLeft;
    private int windowPieces;
    private boolean openingUsed;
    private int openingBonus;
    private int blockBest;
    private int lastTarget = -1;
    private long fleeLeft = -1;
    private boolean chainActive;
    private int chainLeft;
    private boolean[] chainCleared = new boolean[2];
    private int reviveCost = RaidRules.REVIVE_LINES;
    private int pendingOffers;
    private int offerSerial;
    private int chooser;
    private long offerLeft;
    private List<CardId> offer;
    private RaidSnapshot.Result result;
    private Lane[] lanes;
    private int patternPlayer = -1;
    private boolean raceAdvanced;

    public RaidEngine(RaidConfig config, MatchClock clock) {
        this(config, clock, null, null);
    }

    /** generators·patterns가 null이면 시드 가중 생성기와 가중 패턴을 쓴다 */
    public RaidEngine(RaidConfig config, MatchClock clock, IntFunction<BlockGenerator> generators, PatternSource patterns) {
        this.config = Objects.requireNonNull(config, "config");
        this.time = Objects.requireNonNull(clock, "clock");
        this.patternSource = patterns;
        this.count = config.mode().players();
        this.modern = config.controlStyle().modern();
        this.raidId = "raid-" + config.seed();
        Random root = new Random(config.seed());
        long[] pieceSeeds = new long[count];
        for (int i = 0; i < count; i++) pieceSeeds[i] = root.nextLong();
        this.raidRandom = new Random(root.nextLong());
        this.sessions = new GameSession[count];
        this.fighters = new Fighter[count];
        this.wall = time.nowMillis();
        for (int i = 0; i < count; i++) {
            int index = i;
            DueTimeClock due = new DueTimeClock(() -> wall);
            BlockGenerator generator = generators == null
                    ? new WeightedBlockGenerator(Difficulty.NORMAL.weights(), new Random(pieceSeeds[i]))
                    : Objects.requireNonNull(generators.apply(i), "generator");
            GameSession session = new GameSession(GameMode.CLASSIC, Difficulty.NORMAL, generator, due, quiet(),
                    boundary(index), config.controlStyle());
            fighters[i] = new Fighter(session, due);
            sessions[i] = session;
            session.start();
            fighters[i].luckyFalling = RaidLuck.roll(raidRandom, false);
            session.pause();
        }
        rollBoss(false);
        if (config.mode() == RaidMode.RACE) initRaceLanes();
        phase = RaidPhase.COUNTDOWN;
        publish();
    }

    public void submit(PlayerCommand command) {
        Objects.requireNonNull(command, "command");
        if (closed || phase == RaidPhase.FINISHED) return;
        int i = command.playerId().ordinal();
        if (i >= count) return;
        Fighter fighter = fighters[i];
        if (command.sequence() <= fighter.lastSequence) return;
        fighter.lastSequence = command.sequence();
        if (!command.pressed()) {
            fighter.held.remove(command.action());
            fighter.repeat.release(command.action());
        } else if (modern && command.action().isRepeatable()) {
            if (!fighter.held.add(command.action())) return;
            fighter.repeat.press(command.action(), wall);
        } else if (!command.action().isRepeatable() && !fighter.held.add(command.action())) return;
        commands.addLast(command);
    }

    public void ultimate(PlayerId player) {
        if (closed || phase != RaidPhase.PLAYING) return;
        int i = index(player);
        if (i < 0 || !fighters[i].alive) return;
        if (config.mode() == RaidMode.RACE) {
            if (lanes[i].gauge < RaidRules.GAUGE_MAX) return;
            fireRaceUltimate(i);
            if (phase != RaidPhase.FINISHED) bump();
            return;
        }
        if (gauge < RaidRules.GAUGE_MAX) return;
        fighters[i].ultimateAt = playClock;
        tryFireUltimate();
        if (phase != RaidPhase.FINISHED) bump();
    }

    public void pickCard(PlayerId player, int cardIndex) {
        if (closed) return;
        if (!applyPick(index(player), cardIndex)) return;
        if (phase != RaidPhase.FINISHED) bump();
    }

    public void advance(long now) {
        if (closed || phase == RaidPhase.FINISHED) return;
        if (now < wall) throw new IllegalArgumentException("raid time must be monotonic");
        long dt = now - wall;
        wall = now;
        if (phase != RaidPhase.PAUSED && dt > 0) {
            if (phase == RaidPhase.PLAYING) {
                playClock += dt;
                elapsed += dt;
                if (bossId == BossId.GOLDEN_TETRO) fleeLeft -= dt;
            } else if (phase == RaidPhase.COUNTDOWN) {
                countdownLeft -= dt;
            } else if (phase == RaidPhase.CARD_PICK) {
                offerLeft -= dt;
            }
        }
        if (phase == RaidPhase.PLAYING && config.mode() == RaidMode.RACE && lanes != null && dt > 0
                && phase != RaidPhase.PAUSED) {
            for (Lane lane : lanes) if (lane.id == BossId.GOLDEN_TETRO) lane.fleeLeft -= dt;
            if (elapsed >= RaidRules.RACE_LIMIT_MILLIS) {
                finishRace(tiebreak());
                return;
            }
        }
        if (phase == RaidPhase.COUNTDOWN && countdownLeft <= 0) {
            phase = RaidPhase.PLAYING;
            resumePlaying();
        }
        if (phase == RaidPhase.CARD_PICK && offerLeft <= 0) applyPick(chooser, 1);
        if (phase == RaidPhase.PLAYING) runDue(now);
        drainCommands();
        if (phase == RaidPhase.FINISHED) return;
        if (phase == RaidPhase.PLAYING) {
            settleSpawns();
            noteDowns();
            if (phase == RaidPhase.FINISHED) return;
            if (config.mode() == RaidMode.RACE) {
                if (lanes != null && lanes[0].id == BossId.GOLDEN_TETRO && lanes[0].fleeLeft <= 0) raceFlee();
            } else if (bossId == BossId.GOLDEN_TETRO && fleeLeft <= 0) flee();
            tryFireUltimate();
        }
        if (phase != RaidPhase.FINISHED) bump();
    }

    public RaidSnapshot snapshot() {
        if (snapshot == null) throw new IllegalStateException("raid not started");
        return snapshot;
    }

    public void close() {
        if (closed) return;
        closed = true;
        commands.clear();
        for (GameSession session : sessions) if (session != null) session.end();
    }

    private void runDue(long now) {
        while (phase == RaidPhase.PLAYING) {
            long due = Long.MAX_VALUE;
            for (Fighter fighter : fighters) {
                if (fighter.alive && fighter.clock.isDue(now)) due = Math.min(due, fighter.clock.dueAt());
            }
            if (due == Long.MAX_VALUE) break;
            wall = due;
            for (int i = 0; i < count; i++) {
                if (phase != RaidPhase.PLAYING) return;
                Fighter fighter = fighters[i];
                if (!fighter.alive || !fighter.clock.isDue(due)) continue;
                long fired = fighter.clock.dueAt();
                fighter.session.tick();
                fighter.clock.onTicked(fired);
            }
        }
        wall = now;
    }

    private void drainCommands() {
        var deferred = new ArrayDeque<PlayerCommand>();
        boolean[] blocked = new boolean[count];
        while (!commands.isEmpty()) {
            if (phase == RaidPhase.FINISHED) return;
            PlayerCommand command = commands.removeFirst();
            int i = command.playerId().ordinal();
            if (i >= count) continue;
            GameAction action = command.action();
            if (!command.pressed()) {
                if (blocked[i]) deferred.addLast(command);
                continue;
            }
            if (action == GameAction.PAUSE || action == GameAction.QUIT) {
                deferred.clear();
                Arrays.fill(blocked, false);
                if (action == GameAction.QUIT) {
                    finish();
                    return;
                }
                togglePause();
                continue;
            }
            if (phase != RaidPhase.PLAYING || !fighters[i].alive) continue;
            if (blocked[i] || sessions[i].isSpawnPending()) {
                blocked[i] = true;
                deferred.addLast(command);
                continue;
            }
            if (sessions[i].getState() == GameState.CLEARING && modern && isBuffered(action)) {
                apply(i, action);
                continue;
            }
            if (sessions[i].getState() != GameState.PLAYING) continue;
            apply(i, action);
        }
        commands.addAll(deferred);
        if (modern && phase == RaidPhase.PLAYING) repeatHeld();
    }

    private void apply(int player, GameAction action) {
        if (reversed(player)) {
            if (action == GameAction.MOVE_LEFT) action = GameAction.MOVE_RIGHT;
            else if (action == GameAction.MOVE_RIGHT) action = GameAction.MOVE_LEFT;
        }
        Fighter fighter = fighters[player];
        if (action == GameAction.HOLD) {
            Block before = fighter.session.snapshot().currentBlock();
            Block held = fighter.session.snapshot().holdBlock();
            boolean falling = fighter.luckyFalling;
            boolean inHold = fighter.luckyHold;
            fighter.session.hold();
            if (fighter.session.snapshot().currentBlock() == before) return;
            if (held == null) {
                fighter.luckyHold = falling;
                fighter.luckyFalling = RaidLuck.roll(raidRandom, luckDoubled);
            } else {
                fighter.luckyHold = falling;
                fighter.luckyFalling = inHold;
            }
            return;
        }
        switch (action) {
            case MOVE_LEFT -> fighter.session.moveLeft();
            case MOVE_RIGHT -> fighter.session.moveRight();
            case SOFT_DROP -> fighter.session.moveDown();
            case ROTATE -> fighter.session.rotateRight();
            case ROTATE_CCW -> fighter.session.rotateLeft();
            case HARD_DROP -> fighter.session.hardDrop();
            default -> { }
        }
    }

    private void repeatHeld() {
        for (int i = 0; i < count; i++) {
            boolean ready = fighters[i].alive && sessions[i].getState() == GameState.PLAYING && !sessions[i].isSpawnPending();
            for (GameAction action : fighters[i].repeat.poll(wall)) if (ready) apply(i, action);
        }
    }

    private void settleSpawns() {
        for (int i = 0; i < count; i++) {
            if (!sessions[i].isSpawnPending() || sessions[i].getState() != GameState.PLAYING) continue;
            AttackRows attack = fighters[i].pending.drain();
            if (!attack.isEmpty()) fighters[i].lucky = LuckyGrid.shiftUp(fighters[i].lucky, attack.size());
            sessions[i].applyPendingAttack(attack);
        }
        for (int i = 0; i < count; i++) {
            if (!sessions[i].isSpawnPending() || sessions[i].getState() != GameState.PLAYING) continue;
            sessions[i].resumeSpawn();
            if (sessions[i].getState() == GameState.PLAYING) {
                fighters[i].luckyFalling = RaidLuck.roll(raidRandom, luckDoubled);
            }
        }
    }

    private void onLock(int player, LockResult lock) {
        if (phase != RaidPhase.PLAYING || !fighters[player].alive) return;
        Fighter fighter = fighters[player];
        int lines = lock.clearedRows();
        int[] cleared = fighter.session.lastClearedRows();
        if (fighter.luckyFalling) {
            for (Cell cell : fighter.session.lastPlacedCells()) LuckyGrid.mark(fighter.lucky, cell.row(), cell.col());
            fighter.luckyFalling = false;
        }
        boolean crit = false;
        for (int row : cleared) if (LuckyGrid.rowHas(fighter.lucky, row)) crit = true;
        if (cleared.length > 0) fighter.lucky = LuckyGrid.dropRows(fighter.lucky, cleared);
        boolean tSpin = lines > 0 && fighter.session.getLastTSpin() != TSpinType.NONE;
        if (fighter.session.getLastTSpin().isTSpin()) fighter.tSpinLocks++;
        if (tSpin) events.add(new RaidEvent(RaidEvent.Type.TSPIN, player, 0));
        if (lines > 0) {
            fighter.combo++;
            if (fighter.combo >= 2) events.add(new RaidEvent(RaidEvent.Type.COMBO, player, fighter.combo));
        } else {
            fighter.combo = 0;
        }
        boolean coop = config.mode() == RaidMode.COOP;
        boolean race = config.mode() == RaidMode.RACE;
        boolean sync = false;
        if (coop && lines > 0) {
            long other = fighters[1 - player].lastClearAt;
            sync = other >= 0 && playClock - other <= RaidRules.SYNC_MILLIS;
        }
        if (lines > 0) fighter.lastClearAt = playClock;
        int damage = lines == 0 ? 0 : RaidDamage.of(lines, tSpin, fighter.combo, crit, sync, sharpStacks);
        if (crit) events.add(new RaidEvent(RaidEvent.Type.CRITICAL, player, damage));
        if (sync) {
            int plain = RaidDamage.of(lines, tSpin, fighter.combo, crit, false, sharpStacks);
            events.add(new RaidEvent(RaidEvent.Type.SYNC, player, Math.max(0, damage - plain)));
            gauge = Math.min(RaidRules.GAUGE_MAX, gauge + RaidRules.GAUGE_SYNC);
        }
        if (damage > 0) {
            events.add(new RaidEvent(RaidEvent.Type.DAMAGE, player, damage));
            fighter.damageDealt += damage;
            totalDamage += damage;
            recentDamage.add(new long[] {playClock, player, damage});
            if (race) {
                lanes[player].gauge = Math.min(RaidRules.GAUGE_MAX,
                        lanes[player].gauge + lines * RaidRules.GAUGE_PER_LINE);
                hurtLane(player, damage);
            } else {
                hurt(player, damage);
                gauge = Math.min(RaidRules.GAUGE_MAX, gauge + lines * RaidRules.GAUGE_PER_LINE);
            }
        }
        if (coop && lines > 0) {
            cleanse(player);
            noteBlock(player, lines);
            noteRevive(player, lines);
        }
        if (!race && chainActive && lines > 0) chainCleared[player] = true;
        decrementEffects(player);
        if (!race) tickChain();
        if (phase != RaidPhase.PLAYING) return;
        if (race) {
            if (!raceAdvanced) tickRacePattern(player);
            raceAdvanced = false;
            return;
        }
        if (target == player || target < 0) {
            piecesLeft--;
            if (piecesLeft <= 0) firePattern();
        }
    }

    private void hurt(int player, int amount) {
        if (amount <= 0 || bossHp <= 0 || phase != RaidPhase.PLAYING) return;
        if (split) {
            int left = amount;
            int[] order = count == 1 ? new int[] {0} : new int[] {player, 1 - player};
            for (int i : order) {
                if (i < 0 || i >= splitHp.length || splitHp[i] <= 0) continue;
                int hit = Math.min(splitHp[i], left);
                splitHp[i] -= hit;
                left -= hit;
                if (left == 0) break;
            }
            int sum = 0;
            for (int hp : splitHp) sum += hp;
            bossHp = Math.min(bossMax, sum);
            if (sum == 0) defeat();
            return;
        }
        bossHp = Math.max(0, bossHp - amount);
        if (bossId == BossId.JELLY_KING && bossHp > 0 && bossHp * 2L <= bossMax) splitNow(true);
        if (bossHp == 0) defeat();
    }

    private void splitNow(boolean announce) {
        if (split || bossHp <= 0) return;
        split = true;
        splitHp = count == 1 ? new int[] {bossHp} : new int[] {bossHp / 2, bossHp - bossHp / 2};
        if (announce) events.add(new RaidEvent(RaidEvent.Type.BOSS_ATTACK, -1, PatternId.SPLIT.ordinal()));
    }

    private void defeat() {
        kills++;
        events.add(new RaidEvent(RaidEvent.Type.BOSS_DEFEATED, -1, bossId.ordinal()));
        luckDoubled = false;
        fleeLeft = -1;
        pendingOffers = bossId == BossId.GOLDEN_TETRO ? 2 : 1;
        beginOffer();
    }

    private void flee() {
        events.add(new RaidEvent(RaidEvent.Type.BOSS_ESCAPED, -1, 0));
        luckDoubled = false;
        rollBoss(false);
    }

    private void firePattern() {
        PatternId firing = nextPattern;
        int savedTarget = target;
        int weaken = savedTarget >= 0 ? blockBest : 0;
        blockBest = 0;
        if (firing == null) {
            telegraph();
            return;
        }
        if (shieldCharges > 0 || ultimateCharges > 0) {
            if (shieldCharges > 0) shieldCharges--;
            else ultimateCharges--;
            events.add(new RaidEvent(RaidEvent.Type.SHIELDED, savedTarget, 0));
            telegraph();
            return;
        }
        if (weaken >= 4) {
            events.add(new RaidEvent(RaidEvent.Type.BLOCKED, other(savedTarget), 2));
            telegraph();
            return;
        }
        if (weaken >= 2) events.add(new RaidEvent(RaidEvent.Type.BLOCKED, other(savedTarget), 1));
        Patterns.of(firing).apply(new EngineContext(savedTarget, weaken));
        events.add(new RaidEvent(RaidEvent.Type.BOSS_ATTACK, savedTarget, firing.ordinal()));
        telegraph();
    }

    private void telegraph() {
        PatternId scripted = patternSource == null ? null
                : patternSource.pick(bossId, level, previousPattern, beforePrevious, raidRandom);
        nextPattern = scripted != null ? scripted
                : PatternPicker.pick(bossId, level, split, previousPattern, beforePrevious, raidRandom);
        beforePrevious = previousPattern;
        previousPattern = nextPattern;
        if (nextPattern == PatternId.FIRE_BREATH && count == 2 || nextPattern == PatternId.CHAIN && count == 2) {
            target = -1;
        } else if (count == 1) {
            target = 0;
        } else {
            target = chooseTarget();
        }
        int pieces = RaidRules.countdown(level);
        if (!openingUsed) {
            pieces += openingBonus;
            openingBonus = 0;
            openingUsed = true;
        }
        piecesLeft = pieces;
        windowPieces = pieces;
        blockBest = 0;
    }

    private int chooseTarget() {
        List<Integer> alive = aliveIndexes();
        if (alive.size() == 1) {
            lastTarget = alive.get(0);
            return lastTarget;
        }
        long d1 = recent(0);
        long d2 = recent(1);
        int picked;
        if (d1 + d2 == 0) picked = lastTarget == 0 ? 1 : 0;
        else picked = raidRandom.nextDouble() < AggroPolicy.chanceP1(d1, d2) ? 0 : 1;
        if (!alive.contains(picked)) picked = alive.get(0);
        lastTarget = picked;
        return picked;
    }

    private long recent(int player) {
        long sum = 0;
        for (long[] hit : recentDamage) {
            if (hit[1] == player && playClock - hit[0] <= RaidRules.AGGRO_WINDOW_MILLIS) sum += hit[2];
        }
        return sum;
    }

    private void noteBlock(int player, int lines) {
        if (target >= 0 && player != target) blockBest += lines;
    }

    private void noteRevive(int player, int lines) {
        int mate = 1 - player;
        if (fighters[mate].alive) return;
        fighters[mate].reviveProgress += lines;
        if (fighters[mate].reviveProgress >= reviveCost) revive(mate);
    }

    private void revive(int player) {
        Fighter fighter = fighters[player];
        fighter.session.reviveFromTopOut();
        fighter.alive = true;
        fighter.lucky = LuckyGrid.empty();
        fighter.luckyHold = false;
        fighter.luckyFalling = RaidLuck.roll(raidRandom, luckDoubled);
        fighter.combo = 0;
        fighter.reviveProgress = 0;
        fighter.effects.clear();
        fighter.pending.clear();
        reviveCost += RaidRules.REVIVE_STEP;
        events.add(new RaidEvent(RaidEvent.Type.REVIVED, player, 0));
    }



    private void cleanse(int player) {
        int mate = 1 - player;
        boolean shortened = reduce(mate, PatternId.CLOUD) | reduce(mate, PatternId.SEAL_PREVIEW) | reduce(mate, PatternId.REVERSE);
        if (shortened) events.add(new RaidEvent(RaidEvent.Type.CLEANSED, player, 0));
    }

    private boolean reduce(int player, PatternId pattern) {
        for (EffectState effect : fighters[player].effects) {
            if (effect.pattern != pattern || effect.remaining <= 0) continue;
            effect.remaining--;
            if (effect.remaining == 0) fighters[player].effects.remove(effect);
            return true;
        }
        return false;
    }

    private void decrementEffects(int player) {
        var it = fighters[player].effects.iterator();
        while (it.hasNext()) {
            EffectState effect = it.next();
            if (effect.pattern == PatternId.CHAIN || effect.remaining <= 0) continue;
            effect.remaining--;
            if (effect.remaining == 0) {
                it.remove();
                if (effect.pattern == PatternId.OVERLOAD) sessions[player].setGravityMultiplier(1.0);
            }
        }
    }

    private void tickChain() {
        if (!chainActive) return;
        if (count == 2 && chainCleared[0] && chainCleared[1]) {
            endChain(false);
            return;
        }
        chainLeft--;
        for (Fighter fighter : fighters) {
            for (EffectState effect : fighter.effects) {
                if (effect.pattern == PatternId.CHAIN) effect.remaining = Math.max(0, chainLeft);
            }
        }
        if (chainLeft <= 0) endChain(true);
    }

    private void endChain(boolean punish) {
        chainActive = false;
        for (Fighter fighter : fighters) fighter.effects.removeIf(effect -> effect.pattern == PatternId.CHAIN);
        if (!punish) return;
        for (int i = 0; i < count; i++) fighters[i].pending.append(Patterns.garbage(raidRandom, 1));
    }

    private void tryFireUltimate() {
        if (config.mode() == RaidMode.RACE || phase != RaidPhase.PLAYING || gauge < RaidRules.GAUGE_MAX) return;
        if (count == 1) {
            if (fighters[0].ultimateAt < 0) return;
        } else {
            long a = fighters[0].ultimateAt;
            long b = fighters[1].ultimateAt;
            if (a < 0 || b < 0 || Math.abs(a - b) > RaidRules.ULTIMATE_WINDOW_MILLIS) return;
        }
        for (Fighter fighter : fighters) fighter.ultimateAt = -1;
        gauge = 0;
        ultimateCharges++;
        events.add(new RaidEvent(RaidEvent.Type.ULTIMATE, -1, RaidRules.ULTIMATE_DAMAGE));
        totalDamage += RaidRules.ULTIMATE_DAMAGE;
        hurt(0, RaidRules.ULTIMATE_DAMAGE);
    }

    private boolean applyPick(int player, int cardIndex) {
        if (phase != RaidPhase.CARD_PICK || offer == null) return false;
        if (player != chooser || cardIndex < 0 || cardIndex > 2) return false;
        CardId card = offer.get(cardIndex);
        events.add(new RaidEvent(RaidEvent.Type.CARD_PICKED, player, card.ordinal()));
        applyCard(player, card);
        offer = null;
        pendingOffers--;
        if (pendingOffers > 0) beginOffer();
        else finishCards();
        return true;
    }

    private void applyCard(int player, CardId card) {
        switch (card) {
            case SHARP_BLOCKS -> sharpStacks++;
            case SHIELD -> shieldCharges++;
            case TIME_WARP -> timeWarp += 4;
            case CHEER -> gauge = Math.min(RaidRules.GAUGE_MAX, gauge + 50);
            case TELESCOPE -> sessions[player].expandPreview(2, 5);
            case LUCK -> luckNext = true;
            case SWEEP -> {
                sessions[player].removeVisibleRows(18, 19);
                fighters[player].lucky = LuckyGrid.dropRows(fighters[player].lucky, new int[] {18, 19});
            }
        }
    }

    private void beginOffer() {
        List<CardId> deck = new ArrayList<>(List.of(CardId.values()));
        Collections.shuffle(deck, raidRandom);
        offer = List.of(deck.get(0), deck.get(1), deck.get(2));
        chooser = count == 1 ? 0 : Math.floorMod(offerSerial, 2);
        offerSerial++;
        offerLeft = RaidRules.CARD_PICK_MILLIS;
        phase = RaidPhase.CARD_PICK;
        for (GameSession session : sessions) {
            if (session.getState() == GameState.PLAYING || session.getState() == GameState.CLEARING) session.pause();
        }
    }

    private void finishCards() {
        rollBoss(true);
        phase = RaidPhase.PLAYING;
        resumePlaying();
        settleSpawns();
    }

    private void rollBoss(boolean killedPrevious) {
        if (killedPrevious) level++;
        clearFightEffects();
        for (Fighter fighter : fighters) fighter.pending.clear();
        split = false;
        splitHp = new int[0];
        chainActive = false;
        previousPattern = null;
        beforePrevious = null;
        openingUsed = false;
        openingBonus = timeWarp;
        timeWarp = 0;
        luckDoubled = luckNext;
        luckNext = false;
        boolean golden = !goldenUsed && raidRandom.nextInt(100) < 5;
        if (golden) {
            goldenUsed = true;
            bossId = BossId.GOLDEN_TETRO;
            bossMax = RaidRules.goldenHp(config.mode(), level);
            bossHp = bossMax;
            nextPattern = null;
            target = count == 1 ? 0 : -1;
            piecesLeft = 0;
            fleeLeft = RaidRules.GOLDEN_FLEE_MILLIS;
        } else {
            if (bag.isEmpty()) refillBag();
            bossId = bag.remove(0);
            bossMax = RaidRules.hp(config.mode(), level);
            bossHp = bossMax;
            fleeLeft = -1;
            telegraph();
        }
        events.add(new RaidEvent(RaidEvent.Type.BOSS_SPAWN, -1, bossId.ordinal()));
    }

    private void refillBag() {
        bag.clear();
        bag.add(BossId.JELLY_KING);
        bag.add(BossId.STONE_GOLEM);
        bag.add(BossId.NIGHT_WITCH);
        bag.add(BossId.MECHA_DRAGON);
        Collections.shuffle(bag, raidRandom);
    }

    private void clearFightEffects() {
        for (int i = 0; i < count; i++) {
            fighters[i].effects.clear();
            sessions[i].setGravityMultiplier(1.0);
        }
    }

    private void noteDowns() {
        for (int i = 0; i < count; i++) {
            if (!fighters[i].alive || sessions[i].getState() != GameState.GAME_OVER) continue;
            fighters[i].alive = false;
            fighters[i].effects.clear();
            fighters[i].pending.clear();
            events.add(new RaidEvent(RaidEvent.Type.PLAYER_DOWN, i, 0));
        }
        if (config.mode() == RaidMode.RACE) {
            int alive = 0;
            int survivor = -1;
            for (int i = 0; i < count; i++) if (fighters[i].alive) {
                alive++;
                survivor = i;
            }
            if (alive < count) finishRace(alive == 1 ? survivor : tiebreak());
            return;
        }
        if (aliveIndexes().isEmpty()) {
            finish();
            return;
        }
        if (phase == RaidPhase.PLAYING && target >= 0 && !fighters[target].alive) {
            target = count == 1 ? 0 : chooseTarget();
            piecesLeft = windowPieces;
        }
    }

    private void finish() {
        phase = RaidPhase.FINISHED;
        offer = null;
        if (result == null) {
            if (config.mode() == RaidMode.RACE && lanes != null) {
                result = new RaidSnapshot.Result(lanes[0].kills + lanes[1].kills, totalDamage, elapsed, null);
            } else {
                result = new RaidSnapshot.Result(kills, totalDamage, elapsed);
            }
        }
        commands.clear();
        for (Fighter fighter : fighters) {
            fighter.clock.stop();
            fighter.held.clear();
            fighter.repeat.clear();
        }
        revision++;
        publish();
    }

    private void togglePause() {
        for (Fighter fighter : fighters) {
            fighter.held.removeIf(action -> action != GameAction.PAUSE && action != GameAction.QUIT);
            fighter.repeat.clear();
        }
        if (phase == RaidPhase.PAUSED) {
            phase = pausedFrom;
            if (phase == RaidPhase.PLAYING) resumePlaying();
            return;
        }
        pausedFrom = phase;
        if (phase == RaidPhase.PLAYING) {
            for (GameSession session : sessions) {
                if (session.getState() == GameState.PLAYING || session.getState() == GameState.CLEARING) session.pause();
            }
        }
        phase = RaidPhase.PAUSED;
    }

    private void resumePlaying() {
        for (int i = 0; i < count; i++) {
            if (fighters[i].alive && sessions[i].getState() == GameState.PAUSED) sessions[i].resume();
        }
    }

    private void bump() {
        revision++;
        publish();
    }

    private void publish() {
        snapshot = build();
        events.clear();
    }

    private RaidSnapshot build() {
        List<RaidSnapshot.PlayerView> players = new ArrayList<>();
        for (int i = 0; i < count; i++) players.add(view(i));
        RaidSnapshot.CardOffer cardOffer = phase == RaidPhase.CARD_PICK && offer != null
                ? new RaidSnapshot.CardOffer(offer, chooser, Math.max(0, offerLeft)) : null;
        RaidPhase shown = phase;
        if (shown == RaidPhase.CARD_PICK && cardOffer == null) shown = RaidPhase.PLAYING;
        RaidSnapshot.BossView boss = bossView();
        int shownKills = kills;
        int shownGauge = Math.min(RaidRules.GAUGE_MAX, Math.max(0, gauge));
        List<RaidSnapshot.BossView> raceBosses = List.of();
        int[] raceKills = new int[0];
        int[] raceGauges = new int[0];
        if (config.mode() == RaidMode.RACE && lanes != null) {
            raceBosses = List.of(laneView(0), laneView(1));
            raceKills = new int[] {lanes[0].kills, lanes[1].kills};
            raceGauges = new int[] {lanes[0].gauge, lanes[1].gauge};
            boss = raceBosses.get(0);
            shownKills = raceKills[0] + raceKills[1];
            shownGauge = 0;
        }
        return new RaidSnapshot(raidId, revision, config.mode(), shown, boss, players, shownKills, elapsed,
                shownGauge, cardOffer, List.copyOf(events), shown == RaidPhase.FINISHED ? result : null,
                raceBosses, raceKills, raceGauges);
    }

    private RaidSnapshot.BossView bossView() {
        long flee = bossId == BossId.GOLDEN_TETRO ? Math.max(0, fleeLeft) : -1;
        return new RaidSnapshot.BossView(bossId, Math.max(1, level), Math.max(0, Math.min(bossHp, bossMax)),
                Math.max(1, bossMax), nextPattern, target, Math.max(0, piecesLeft), splitHp, flee);
    }

    private RaidSnapshot.PlayerView view(int i) {
        Fighter fighter = fighters[i];
        GameSnapshot state = fighter.session.snapshot();
        int[][] cells = state.cells();
        Block block = state.currentBlock();
        if (block != null) paint(cells, block);
        List<int[][]> previews = new ArrayList<>();
        for (Block preview : state.nextBlocks()) previews.add(shapeCells(preview));
        List<int[][]> shown = previews.size() <= 3 ? previews : new ArrayList<>(previews.subList(0, 3));
        List<int[][]> extra = previews.size() <= 3 ? List.of() : new ArrayList<>(previews.subList(3, previews.size()));
        int[][] next = shown.isEmpty() ? shapeCells(state.nextBlock()) : shown.get(0);
        int[][] items = new int[VersusRules.BOARD_ROWS][VersusRules.BOARD_COLS];
        MatchSnapshot.PlayerView board = new MatchSnapshot.PlayerView(cells, next, state.score(), fighter.pending.rows(),
                items, shapeCells(state.holdBlock()), ghostCells(state.ghostBlock()), shown, fighter.tSpinLocks);
        List<RaidSnapshot.Effect> effects = new ArrayList<>();
        for (EffectState effect : fighter.effects) {
            if (effect.remaining <= 0 && effect.pattern != PatternId.CLOUD) continue;
            effects.add(new RaidSnapshot.Effect(effect.pattern, effect.remaining, effect.area));
        }
        int progress = fighter.alive ? 0 : fighter.reviveProgress;
        return new RaidSnapshot.PlayerView(board, fighter.alive, fighter.lucky, effects, progress, reviveCost,
                fighter.damageDealt, extra);
    }

    private SpawnBoundary boundary(int player) {
        return new SpawnBoundary() {
            @Override
            public void onLockResolved(LockResult lock) {
                onLock(player, lock);
            }

            @Override
            public boolean deferSpawn() {
                return true;
            }
        };
    }

    private static GameListener quiet() {
        return new GameListener() {
            @Override public void onChanged(GameSnapshot state) { }
            @Override public void onGameOver(GameSnapshot state) { }
        };
    }

    private boolean reversed(int player) {
        for (EffectState effect : fighters[player].effects) {
            if (effect.pattern == PatternId.REVERSE && effect.remaining > 0) return true;
        }
        return false;
    }

    private List<Integer> aliveIndexes() {
        List<Integer> alive = new ArrayList<>();
        for (int i = 0; i < count; i++) if (fighters[i].alive) alive.add(i);
        return alive;
    }

    private static int other(int player) {
        return player == 0 ? 1 : 0;
    }

    private static int index(PlayerId player) {
        return player == null ? -1 : player.ordinal();
    }

    private static boolean isBuffered(GameAction action) {
        return action == GameAction.ROTATE || action == GameAction.ROTATE_CCW
                || action == GameAction.HOLD || action == GameAction.HARD_DROP;
    }

    private static void paint(int[][] cells, Block block) {
        int[][] shape = block.getShape();
        for (int r = 0; r < shape.length; r++) for (int c = 0; c < shape[r].length; c++) {
            int row = block.getRow() + r;
            int col = block.getCol() + c;
            if (shape[r][c] != 0 && row >= 0 && row < cells.length && col >= 0 && col < cells[row].length) {
                cells[row][col] = block.getType().cellValue();
            }
        }
    }

    private static int[][] shapeCells(Block block) {
        if (block == null) return new int[0][];
        int[][] shape = block.getShape();
        int[][] cells = new int[shape.length][];
        for (int r = 0; r < shape.length; r++) {
            cells[r] = new int[shape[r].length];
            for (int c = 0; c < shape[r].length; c++) cells[r][c] = shape[r][c] == 0 ? 0 : block.getType().cellValue();
        }
        return cells;
    }

    private static int[][] ghostCells(Block ghost) {
        int[][] grid = new int[VersusRules.BOARD_ROWS][VersusRules.BOARD_COLS];
        if (ghost == null) return grid;
        int[][] shape = ghost.getShape();
        for (int r = 0; r < shape.length; r++) for (int c = 0; c < shape[r].length; c++) {
            int row = ghost.getRow() + r;
            int col = ghost.getCol() + c;
            if (shape[r][c] != 0 && row >= 0 && row < grid.length && col >= 0 && col < grid[row].length) {
                grid[row][col] = ghost.getType().cellValue();
            }
        }
        return grid;
    }

    private final class EngineContext implements PatternContext {
        private final int patternTarget;
        private final int weaken;

        private EngineContext(int patternTarget, int weaken) {
            this.patternTarget = patternTarget;
            this.weaken = weaken;
        }

        public int level() { return level; }
        public boolean coop() { return config.mode() == RaidMode.COOP; }
        public int target() { return patternTarget; }
        public int blockLines() { return weaken; }
        public Random random() {
            return patternPlayer >= 0 ? lanes[patternPlayer].patternRandom : raidRandom;
        }
        public int players() { return count; }
        public GameSession session(int player) { return sessions[player]; }

        public void queueAttack(int player, AttackRows rows) {
            if (player >= 0 && player < count) fighters[player].pending.append(rows);
        }

        public void addEffect(int player, PatternId pattern, int remainingPieces, int[] area) {
            if (player < 0 || player >= count) return;
            fighters[player].effects.add(new EffectState(pattern, remainingPieces, area));
            if (pattern == PatternId.CHAIN && !chainActive) {
                chainActive = true;
                chainLeft = remainingPieces;
                chainCleared = new boolean[count];
            }
        }

        public void forceNext(int player, Block... pieces) {
            sessions[player].forceUpcoming(pieces);
        }

        public void setGravity(int player, double multiplier) {
            sessions[player].setGravityMultiplier(multiplier);
        }

        public void split() {
            if (patternPlayer >= 0) splitLane(patternPlayer, false);
            else splitNow(false);
        }
    }

    private void initRaceLanes() {
        long patternSeed = raidRandom.nextLong();
        lanes = new Lane[] {new Lane(new Random(patternSeed)), new Lane(new Random(patternSeed))};
        copySharedTo(lanes[0]);
        copySharedTo(lanes[1]);
    }

    private void copySharedTo(Lane lane) {
        lane.id = bossId;
        lane.hp = bossHp;
        lane.max = bossMax;
        lane.next = nextPattern;
        lane.previous = previousPattern;
        lane.beforePrevious = beforePrevious;
        lane.piecesLeft = piecesLeft;
        lane.windowPieces = windowPieces;
        lane.split = split;
        lane.splitHp = splitHp.clone();
        lane.fleeLeft = fleeLeft;
        lane.openingUsed = openingUsed;
        lane.openingBonus = openingBonus;
    }

    private void tickRacePattern(int player) {
        Lane lane = lanes[player];
        if (lane.id == BossId.GOLDEN_TETRO || lane.hp <= 0) return;
        lane.piecesLeft--;
        if (lane.piecesLeft <= 0) fireRacePattern(player);
    }

    private void fireRacePattern(int player) {
        Lane lane = lanes[player];
        PatternId firing = lane.next;
        if (firing == null) {
            telegraphLane(player);
            return;
        }
        if (lane.ultimateCharges > 0) {
            lane.ultimateCharges--;
            events.add(new RaidEvent(RaidEvent.Type.SHIELDED, player, 0));
            telegraphLane(player);
            return;
        }
        patternPlayer = player;
        try {
            Patterns.of(firing).apply(new EngineContext(player, 0));
        } finally {
            patternPlayer = -1;
        }
        events.add(new RaidEvent(RaidEvent.Type.BOSS_ATTACK, player, firing.ordinal()));
        telegraphLane(player);
    }

    private void telegraphLane(int player) {
        Lane lane = lanes[player];
        PatternId scripted = patternSource == null ? null
                : patternSource.pick(lane.id, level, lane.previous, lane.beforePrevious, lane.patternRandom);
        lane.next = scripted != null ? scripted
                : PatternPicker.pick(lane.id, level, lane.split, lane.previous, lane.beforePrevious, lane.patternRandom);
        lane.beforePrevious = lane.previous;
        lane.previous = lane.next;
        int pieces = RaidRules.countdown(level);
        if (!lane.openingUsed) {
            pieces += lane.openingBonus;
            lane.openingBonus = 0;
            lane.openingUsed = true;
        }
        lane.piecesLeft = pieces;
        lane.windowPieces = pieces;
    }

    private void hurtLane(int player, int amount) {
        Lane lane = lanes[player];
        if (amount <= 0 || lane.hp <= 0 || phase != RaidPhase.PLAYING) return;
        if (lane.split) {
            if (lane.splitHp.length > 0) {
                lane.splitHp[0] = Math.max(0, lane.splitHp[0] - amount);
                lane.hp = Math.max(0, Math.min(lane.max, lane.splitHp[0]));
            }
            if (lane.hp == 0) raceKill(player);
            return;
        }
        lane.hp = Math.max(0, lane.hp - amount);
        if (lane.id == BossId.JELLY_KING && lane.hp > 0 && lane.hp * 2L <= lane.max) splitLane(player, true);
        if (lane.hp == 0) raceKill(player);
    }

    private void splitLane(int player, boolean announce) {
        Lane lane = lanes[player];
        if (lane.split || lane.hp <= 0) return;
        lane.split = true;
        lane.splitHp = new int[] {lane.hp};
        if (announce) events.add(new RaidEvent(RaidEvent.Type.BOSS_ATTACK, player, PatternId.SPLIT.ordinal()));
    }

    private void raceKill(int player) {
        if (raceAdvanced || phase != RaidPhase.PLAYING) return;
        raceAdvanced = true;
        Lane killer = lanes[player];
        killer.kills++;
        events.add(new RaidEvent(RaidEvent.Type.BOSS_DEFEATED, player, killer.id.ordinal()));
        int slower = 1 - player;
        advanceRaceBosses();
        fighters[slower].pending.append(Patterns.garbage(raidRandom, RaidRules.RACE_STEAL_ROWS));
    }

    private void raceFlee() {
        if (phase != RaidPhase.PLAYING || raceAdvanced) return;
        raceAdvanced = true;
        events.add(new RaidEvent(RaidEvent.Type.BOSS_ESCAPED, -1, 0));
        advanceRaceBosses();
        raceAdvanced = false;
    }

    private void advanceRaceBosses() {
        level++;
        clearFightEffects();
        for (Fighter fighter : fighters) fighter.pending.clear();
        split = false;
        splitHp = new int[0];
        chainActive = false;
        previousPattern = null;
        beforePrevious = null;
        openingUsed = false;
        openingBonus = 0;
        luckDoubled = false;
        luckNext = false;
        boolean golden = !goldenUsed && raidRandom.nextInt(100) < 5;
        if (golden) {
            goldenUsed = true;
            bossId = BossId.GOLDEN_TETRO;
            bossMax = RaidRules.goldenHp(RaidMode.RACE, level);
            bossHp = bossMax;
            nextPattern = null;
            target = -1;
            piecesLeft = 0;
            fleeLeft = RaidRules.GOLDEN_FLEE_MILLIS;
        } else {
            if (bag.isEmpty()) refillBag();
            bossId = bag.remove(0);
            bossMax = RaidRules.hp(RaidMode.RACE, level);
            bossHp = bossMax;
            fleeLeft = -1;
            nextPattern = null;
            target = -1;
            piecesLeft = 0;
        }
        long patternSeed = raidRandom.nextLong();
        for (int i = 0; i < lanes.length; i++) {
            lanes[i].patternRandom.setSeed(patternSeed);
            lanes[i].ultimateCharges = 0;
            copySharedTo(lanes[i]);
            if (golden) {
                lanes[i].next = null;
                lanes[i].piecesLeft = 0;
                lanes[i].fleeLeft = RaidRules.GOLDEN_FLEE_MILLIS;
            } else {
                telegraphLane(i);
            }
        }
        events.add(new RaidEvent(RaidEvent.Type.BOSS_SPAWN, -1, bossId.ordinal()));
    }

    private void fireRaceUltimate(int player) {
        Lane lane = lanes[player];
        if (lane.gauge < RaidRules.GAUGE_MAX || !fighters[player].alive) return;
        lane.gauge = 0;
        lane.ultimateCharges++;
        fighters[player].ultimateAt = -1;
        events.add(new RaidEvent(RaidEvent.Type.ULTIMATE, player, RaidRules.ULTIMATE_DAMAGE));
        totalDamage += RaidRules.ULTIMATE_DAMAGE;
        fighters[player].damageDealt += RaidRules.ULTIMATE_DAMAGE;
        hurtLane(player, RaidRules.ULTIMATE_DAMAGE);
        raceAdvanced = false;
    }

    private void finishRace(Integer winner) {
        if (phase == RaidPhase.FINISHED) return;
        int shown = winner == null ? lanes[0].kills : lanes[winner].kills;
        result = new RaidSnapshot.Result(shown, totalDamage, elapsed, winner);
        finish();
    }

    private Integer tiebreak() {
        int left = lanes[0].kills;
        int right = lanes[1].kills;
        if (left != right) return left > right ? 0 : 1;
        long leftProgress = progress(lanes[0]);
        long rightProgress = progress(lanes[1]);
        if (leftProgress != rightProgress) return leftProgress > rightProgress ? 0 : 1;
        return null;
    }

    private static long progress(Lane lane) {
        int max = Math.max(1, lane.max);
        int left = Math.max(0, Math.min(lane.hp, max));
        return (long) (max - left) * 1_000_000L / max;
    }

    private RaidSnapshot.BossView laneView(int player) {
        Lane lane = lanes[player];
        long flee = lane.id == BossId.GOLDEN_TETRO ? Math.max(0, lane.fleeLeft) : -1;
        int hp = Math.max(0, Math.min(lane.hp, Math.max(1, lane.max)));
        return new RaidSnapshot.BossView(lane.id, Math.max(1, level), hp, Math.max(1, lane.max),
                lane.next, player, Math.max(0, lane.piecesLeft), lane.splitHp, flee);
    }

    private static final class Lane {
        final Random patternRandom;
        BossId id = BossId.JELLY_KING;
        int hp;
        int max = 1;
        PatternId next;
        PatternId previous;
        PatternId beforePrevious;
        int piecesLeft;
        int windowPieces;
        boolean split;
        int[] splitHp = new int[0];
        long fleeLeft = -1;
        int gauge;
        int ultimateCharges;
        int kills;
        boolean openingUsed;
        int openingBonus;

        Lane(Random patternRandom) {
            this.patternRandom = patternRandom;
        }
    }

    private static final class Fighter {
        final GameSession session;
        final DueTimeClock clock;
        final PendingAttackQueue pending = new PendingAttackQueue();
        final AutoRepeat repeat = new AutoRepeat();
        final EnumSet<GameAction> held = EnumSet.noneOf(GameAction.class);
        final List<EffectState> effects = new ArrayList<>();
        long lastSequence = -1;
        boolean alive = true;
        int[][] lucky = LuckyGrid.empty();
        boolean luckyFalling;
        boolean luckyHold;
        int combo;
        long damageDealt;
        int reviveProgress;
        long lastClearAt = -1;
        long ultimateAt = -1;
        int tSpinLocks;

        Fighter(GameSession session, DueTimeClock clock) {
            this.session = session;
            this.clock = clock;
        }
    }

    private static final class EffectState {
        final PatternId pattern;
        int remaining;
        final int[] area;

        EffectState(PatternId pattern, int remaining, int[] area) {
            this.pattern = pattern;
            this.remaining = remaining;
            this.area = area == null ? new int[0] : area.clone();
        }
    }
}
