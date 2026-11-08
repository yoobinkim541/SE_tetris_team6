# Sprint 7 Closeout Implementation Plan

**Goal:** Implement missing S7-A2 and reconcile shared contracts, validation and sprint records.
**Architecture:** Extract attack rows from the pre-deletion visible board and locked block footprint; keep bounded FIFO reception independent of transport. Notify the existing SpawnBoundary only after lock settlement. Adopt A's common API in B and define C's lobby port without implementing S8 gameplay/lobby transport.
**Tech Stack:** Java 21, Swing, JUnit 5, Gradle.
**Spec:** Sprint 7 Notion 40da13e7df0081b5b454e29d60bdcf0e; A2 issue #115; docs/req3/requirements.md.

## Global constraints and review focus
- Preserve existing single-player behavior via SpawnBoundary.IMMEDIATE.
- Attack requires at least two distinct visible deleted rows, including L rows. Preserve existing holes, remove every locked block cell, normalize other cells to 9.
- Queue retains earliest 10 rows; 8 + 3 accepts first 2, full queue ignores additions. Copy every mutable array.
- Capture before deletion; publish once after actual settlement, never at CLEARING start or Pause/Resume.
- Deferred spawn must stop clock and tolerate input/tick until explicit resume. No S8 attack insertion or full MatchEngine implementation.
- Record actual review/demo/KPT authorship. User later explicitly delegated these to AI; mark that delegation in closeout instead of claiming human attendance.
- User authorizes implementation, small convention commits, verification, develop integration and Notion closeout records.

## Deliverables
- [x] Tests first for AttackRowExtractor, PendingAttackQueue and GameSession lock publication/spawn boundary. Observed red, implemented, verified green.
- [x] B consumes feature/versus/api directly; removed provisional duplicate contracts and tested common Fake use.
- [x] C LobbyClient/LobbySnapshot contract with shared MatchConfig and consumer tests; actual lobby adapter remains S8.
- [x] Full check, GUI 9/9, resource 23/23 and native delegated demo/lifecycle 2/2. Read-only review addressed READY revision zero; scoring audit compatibility restored.
- [x] Small convention commits and evidence report prepared. Final CI/develop integration and reconciled Notion/issue links are tracked in the closeout PR and sprint page.

## Execution ledger
- Baseline develop: 5b97b4b. Existing user checkout changes remain untouched.
- Parallel agents have disjoint B UI and C lobby paths; root owns A core, integration, docs and external writes.
- User explicitly delegated review, demonstration and KPT to AI. Record AI delegation instead of inventing human attendees/approvals; this supersedes the initial human-acceptance prerequisite for this closeout.
- An isolated resourceAcceptance rerun passed after one EDT cleanup timeout; no thresholds were weakened. Full evidence and S8 boundaries are in docs/req3/s7-closeout.md.
