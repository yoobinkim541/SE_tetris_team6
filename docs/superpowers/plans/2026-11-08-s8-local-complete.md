# Sprint 8 local completion implementation plan

**Goal:** Complete S8-A1/A2 and connect the existing B screens to a real local classic match.
**Architecture:** Keep single-player GameSession unchanged by default. Apply incoming rows only at deferred Spawn boundaries. LocalMatchEngine owns two sessions, due clocks, command queues and a single final result; LocalBattleClient owns the EDT pulse and subscriptions.
**Tech stack:** Java 21, Swing, Gradle 8.10.2, JUnit 5.
**Spec:** Notion S8-A1 / S8-A2 and docs/req3/decisions.md (D-02, D-07, D-08, D-11–14).

## Global constraints
- 20x10 visible board; attack value 9; FIFO cap 10; no cancellation.
- Two losses in one pulse are a draw. Due ticks only, no per-pulse descent.
- No score persistence for versus. Preserve single-player behavior.
- Physical keyboard rollover and independent team approval remain human acceptance.

## Review focus
- Hidden rows and item marks must shift safely without changing walls.
- Input after lock must wait until the next pulse and release must always clear held state.
- Simultaneous loss cannot depend on player/callback order.
- Pause/focus/quit must not leak clocks or commands into the next match.
- Default production screen must use the engine, not a preview fixture.

## Task 1: Incoming attacks
Files: Board.java, GameSession.java; test GarbageInsertionTest.java.
- [x] Add failing tests for row/hole order, item shifts, hidden/visible overflow, no mutation before settlement and single consumption.
- [x] Add Board.insertAttackRows(AttackRows): boolean (true means overflow), preserving walls and items.
- [x] Add GameSession.applyPendingAttack(AttackRows), legal only at pending Spawn; overflow calls gameOver.
- [x] Run focused tests and existing lock/session regressions.

## Task 2: Local engine
Files: feature/versus/LocalMatchEngine.java; test LocalMatchEngineTest.java.
- [x] Write failing tests for independent movement, due clocks, deferred inputs, attacks at Spawn, draws, pause, duplicate input, quit, restart and stopped clocks.
- [x] Implement MatchEngine with injectable MatchClock and generator factory, deferred boundary hooks and aggregate loss determination.
- [x] Reject unsupported S9 modes explicitly; only CLASSIC in this sprint.
- [x] Verify focused and full check.

## Task 3: Production client and acceptance
Files: LocalBattleClient.java, MainFrame.java, BattleScreenController.java as necessary; integration tests and docs/sprint8-local.md.
- [x] Test actual movement/attack/result through BattleClient and production MainFrame.
- [x] Replace default PreviewBattleClient with LocalBattleClient and retain injected fixtures for isolated UI tests.
- [x] Validate actual GUI where runtime permits, full check, releaseJar and existing TCP tests.
- [x] Obtain fresh AI code review, fix important findings and record limits accurately.

## Execution record

All three tasks implemented and locally verified. Final `check guiAcceptance releaseJar -PnativeInput=true` passed. Evidence and remaining human acceptance: docs/sprint8-local.md.

Ruling: execute in the existing clean checkout on a new feature branch, preserving develop; no separate worktree was necessary for this dedicated workspace.
Ruling: classic local completion only; ITEM/TIMED/P2P match handoff stay in S9 as specified, preventing an untested expansion.
Ruling: the review's duplicate-input issue was treated as required engine correctness and fixed, since direct MatchEngine clients must honor D-13 too.
Independent AI review: modal-pause and duplicate-input findings reproduced RED→GREEN; no deferred code findings. Physical keyboard and human approval remain open; CI is reported separately.
