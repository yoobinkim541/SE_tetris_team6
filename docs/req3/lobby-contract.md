# S7 lobby consumer contract draft

`tetris.feature.network.lobby.LobbyClient` is a UI-facing port. S7 declares the
contract and immutable `LobbySnapshot`; it supplies no socket lobby client.
S8 owns transport implementation, integration and two-process acceptance checks.
The `LobbyContractTest` fake is a consumer example, not evidence of working
connections, socket cancellation, callback scheduling or match handoff.

## Lifecycle

States: INITIAL, HOSTING, CONNECTING, LOBBY, STARTING, PLAYING, RESULT.
Host starts INITIAL → HOSTING; join starts INITIAL → CONNECTING. Successful
negotiation enters LOBBY and completes the stage. Failure completes it
exceptionally and returns to INITIAL. `cancel()` cancels pending establishment
and returns to INITIAL; `leave()` releases an established lobby or match and
returns to INITIAL. Both are idempotent. `close()` permanently closes the port,
cancels pending stages and removes listeners; repeated close is harmless.
Commands and subscriptions after close fail with IllegalStateException.

Every operation must return promptly on Swing's EDT. Network I/O and waiting
for peers run elsewhere. Pending host/connect stages are cancelled by cancel,
leave or close; cancellation is observable through
`toCompletableFuture().isCancelled()`. No reachability of arbitrary IP addresses
or ports is promised. Validation and connection failures are implementation
responsibilities, exposed by exceptional stages.

`subscribe` delivers the current immutable snapshot first, then ordered updates.
Implementations serialize delivery and removal; unsubscribe is idempotent and
prevents subsequent callbacks after returning. Implementations use an injected
dispatcher to deliver snapshots and complete asynchronous host/connect stages
on Swing's EDT, including exceptional completion and cancellation. Callbacks
must be brief. Close stops delivery and releases subscriptions. The fake
delivers synchronously for illustration, not as a threading test.

## Settings and READY

Role is NONE before establishment, HOST for the authority, or CLIENT for the
joining participant. Config uses `tetris.feature.versus.api.MatchConfig`.
Config and peerIdentity may be null until negotiation; identity is an opaque
nonblank connection identifier, not an authenticated user identity.
Revision is a nonnegative monotonically increasing host counter per lobby;
a new lobby starts at zero before config selection. READY requires revision
at least one. The long-valued port must remain within the wire int range
(0 through Integer.MAX_VALUE); implementations validate the range and use a
checked conversion before serialization, never truncation. Exhaustion fails
selection rather than wrapping. Each accepted host selection in LOBBY increments
it, including reselecting identical settings, and clears both READY votes.
Clients cannot select settings. Consumers call `ready(revision, value)` using
the revision displayed. Stale votes are rejected; READY requires a connected
peer and current config. Snapshot helpers enforce local rules; S8 must enforce
the policy on wire messages and clear votes on both peers for a new revision.

Both votes at the same revision permit host transition LOBBY → STARTING.
S8 freezes that config and coordinates start before PLAYING; results enter
RESULT. `battleClient()` exposes an Optional of the shared
`tetris.feature.versus.api.BattleClient` after successful match handoff.
Socket start synchronization, seed exchange, timeouts, disconnect recovery and
UI wiring remain S8 work. The fake consumer tests do not validate those paths.
