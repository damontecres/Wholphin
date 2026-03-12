# SyncPlay Design And Integration Plan

## Scope

This document captures the current SyncPlay contract and the planned integration design for Wholphin.

It is intentionally design-only. It does not imply any user-visible behavior change, playback behavior change, or persistence change.

## Dependency Anchor

- Repo-pinned Jellyfin SDK version: `1.7.1`
- Source: `gradle/libs.versions.toml`

## SDK Support Matrix

| Action | Status | Notes |
| --- | --- | --- |
| Create group | available directly in SDK | `SyncPlayApi.syncPlayCreateGroup(data: NewGroupRequestDto)` |
| Join group | available directly in SDK | `SyncPlayApi.syncPlayJoinGroup(data: JoinGroupRequestDto)` |
| Leave group | available directly in SDK | `SyncPlayApi.syncPlayLeaveGroup()` |
| Get group | available directly in SDK | `SyncPlayApi.syncPlayGetGroup(id: UUID)` |
| List groups | available directly in SDK | `SyncPlayApi.syncPlayGetGroups()` |
| Pause | available directly in SDK | `SyncPlayApi.syncPlayPause()` and inbound `SendCommandType.PAUSE` |
| Unpause | available directly in SDK | `SyncPlayApi.syncPlayUnpause()` and inbound `SendCommandType.UNPAUSE` |
| Seek | available directly in SDK | `SyncPlayApi.syncPlaySeek(data: SeekRequestDto)` and inbound `SendCommandType.SEEK` |
| Stop | available directly in SDK | `SyncPlayApi.syncPlayStop()` and inbound `SendCommandType.STOP` |
| Ready | available directly in SDK | `SyncPlayApi.syncPlayReady(data: ReadyRequestDto)`; no inbound `SendCommandType.READY` |
| Queue update messages | available indirectly but needs wrapping | Exposed through generic `SyncPlayGroupUpdateMessage` with concrete `SyncPlayPlayQueueUpdate` |
| State update messages | available indirectly but needs wrapping | Exposed through generic `SyncPlayGroupUpdateMessage` with concrete `SyncPlayStateUpdate` |

## API Contract Table

| Action | SDK support status | Planned wrapper name | Source of truth | Expected downstream consumer |
| --- | --- | --- | --- | --- |
| Create group | available directly in SDK | `SyncPlayApiAdapter.createGroup(...)` | `SyncPlayApi.syncPlayCreateGroup(...)` mapped into `data.syncplay.SyncPlayState` | `ui.main.syncplay` create-group flow |
| Join group | available directly in SDK | `SyncPlayApiAdapter.joinGroup(...)` | `SyncPlayApi.syncPlayJoinGroup(...)` plus follow-up state mapped into `data.syncplay.SyncPlayState` | `ui.main.syncplay` join flow, later playback handoff |
| Leave group | available directly in SDK | `SyncPlayApiAdapter.leaveGroup()` | `SyncPlayApi.syncPlayLeaveGroup()` plus membership or group updates mapped into `data.syncplay.SyncPlayState` | `ui.main.syncplay` group status UI and `ui.playback.syncplay` leave action |
| Get group | available directly in SDK | `SyncPlayApiAdapter.getGroup(...)` | `SyncPlayApi.syncPlayGetGroup(...)` mapped into `data.syncplay.SyncPlayState` | `services.syncplay` reconciliation and `ui.main.syncplay` status UI |
| List groups | available directly in SDK | `SyncPlayApiAdapter.listGroups()` | `SyncPlayApi.syncPlayGetGroups()` response | `ui.main.syncplay` group browser or picker |
| Pause | available directly in SDK | `SyncPlayApiAdapter.pause()` and `SyncPlayCommandDispatcher.applyPause()` | Outgoing API call plus inbound authoritative command or update folded into `data.syncplay.SyncPlayState` | `ui.playback.syncplay` and shared playback command handling |
| Unpause | available directly in SDK | `SyncPlayApiAdapter.unpause()` and `SyncPlayCommandDispatcher.applyUnpause()` | Outgoing API call plus inbound authoritative command or update folded into `data.syncplay.SyncPlayState` | `ui.playback.syncplay` and shared playback command handling |
| Seek | available directly in SDK | `SyncPlayApiAdapter.seek(...)` and `SyncPlayCommandDispatcher.applySeek(...)` | Outgoing API call plus inbound authoritative command or update folded into `data.syncplay.SyncPlayState` | `ui.playback.syncplay` and shared playback command handling |
| Stop | available directly in SDK | `SyncPlayApiAdapter.stop()` and `SyncPlayCommandDispatcher.applyStop()` | Outgoing API call plus inbound authoritative command or update folded into `data.syncplay.SyncPlayState` | `ui.playback.syncplay` and shared playback command handling |
| Ready | available directly in SDK | `SyncPlayApiAdapter.ready(...)` | `SyncPlayApi.syncPlayReady(...)` plus subsequent state updates folded into `data.syncplay.SyncPlayState` | `ui.playback.syncplay` ready or wait UX |
| Queue update messages | available indirectly but needs wrapping | `SyncPlayWebSocketHandler.observeQueueUpdates()` | `SyncPlayGroupUpdateMessage` filtered to `SyncPlayPlayQueueUpdate` as authoritative queue state in `data.syncplay.SyncPlayState` | `ui.playback.syncplay` queue reconciliation and in-player queue UI |
| State update messages | available indirectly but needs wrapping | `SyncPlayWebSocketHandler.observeStateUpdates()` | `SyncPlayGroupUpdateMessage` filtered to `SyncPlayStateUpdate` as authoritative group playback state in `data.syncplay.SyncPlayState` | `ui.playback.syncplay` ready or wait or playback-state UX and shared playback command handling |

## Current Remote Playback Command Path

Current remote playback command handling lives in `PlaybackViewModel.subscribe()` and is started from playback setup through `configurePlayer()`.

The current dispatch table is:

- `STOP -> release() + navigationManager.goBack()`
- `PAUSE -> player.pause()`
- `UNPAUSE -> player.play()`
- `NEXT_TRACK -> playNextUp()`
- `PREVIOUS_TRACK -> playPrevious()`
- `SEEK -> player.seekTo(...)`
- `REWIND -> player.seekBack(...)`
- `FAST_FORWARD -> player.seekForward(...)`
- `PLAY_PAUSE -> toggle player.play()` or `player.pause()`

Remote-control capability is advertised separately in `ServerEventListener.init()` through `api.sessionApi.postCapabilities(..., supportsMediaControl = true, ...)`.

### What SyncPlay Should Reuse

- Reuse the local playback effect methods already used by the current dispatcher: `release()`, `navigationManager.goBack()`, `player.pause()`, `player.play()`, `player.seekTo(...)`, `playNextUp()`, `playPrevious()`, `player.seekBack(...)`, and `player.seekForward(...)` where SyncPlay semantics intentionally match.
- Reuse the main-thread execution rule for commands that mutate player state or navigation.
- Reuse the existing player or session state reporting path rather than replacing the local playback primitives.

### What Must Be Extracted Later

- Extract the command-to-action mapping out of the `subscribe()` `when` block into a reusable dispatcher callable from both classic remote control and SyncPlay.
- Extract a shared command model or adapter layer so `PlaystateCommand`, `SendCommandType`, `SyncPlayStateUpdate`, and `SyncPlayPlayQueueUpdate` can drive the same local playback actions where semantics overlap.
- Extract shared subscription plumbing for socket collection, message filtering, main-thread handoff, and error handling.
- Keep SyncPlay-specific lifecycle, membership, ready or buffering or wait state, queue reconciliation, and group update interpretation outside the generic remote-command dispatcher.

## Concrete Command Flow Notes

### Remote Pause

1. A websocket `PlaystateMessage` arrives in `PlaybackViewModel.subscribe()`.
2. `PlaystateCommand.PAUSE` matches in the dispatch block.
3. The handler switches to `Dispatchers.Main` and calls `player.pause()`.
4. `player.pause()` triggers the normal player listener path in `TrackActivityPlaybackListener.onIsPlayingChanged(isPlaying = false)`.
5. Because the playback activity listener is already initialized, it calls `saveActivity(-1)` and sends `playStateApi.reportPlaybackProgress(...)` with `isPaused = true`.

Loop suppression insertion point for a later PR:

- Mark the inbound remote or SyncPlay pause in the planned shared remote-command dispatcher before calling `player.pause()`.
- Have `TrackActivityPlaybackListener.onIsPlayingChanged()` or `saveActivity()` consult that suppression state so a remotely-applied pause is not echoed back as a fresh outbound control or progress signal.

### Remote Seek

1. A websocket `PlaystateMessage` arrives in `PlaybackViewModel.subscribe()`.
2. `PlaystateCommand.SEEK` matches and `seekPositionTicks` is converted to milliseconds.
3. The handler switches to `Dispatchers.Main` and calls `player.seekTo(...)`.
4. The player position changes immediately for local playback behavior.
5. Outbound reporting does not happen in the subscribe block itself. The existing `TrackActivityPlaybackListener` timer later calls `saveActivity(-1)` and sends `playStateApi.reportPlaybackProgress(...)` with the new position.

Loop suppression insertion point for a later PR:

- Mark the inbound remote or SyncPlay seek in the planned shared remote-command dispatcher before calling `player.seekTo(...)`.
- Have `TrackActivityPlaybackListener.saveActivity()` suppress or annotate the next position report originating from that remote-applied seek so it is not treated as a user-originated local seek echo.

### Loop Suppression Recommendation

- Insert suppression at the shared playback command-dispatch layer, not inside the raw websocket subscriber.
- Consume that suppression state inside outbound playback reporting in `TrackActivityPlaybackListener` because that is the current point where remote-applied player changes are turned back into server-facing playstate or progress calls.

## Queue Flow: SyncPlay Versus Current Wholphin Flow

- Current Wholphin queue state is local and playback-session scoped. `data.model.Playlist` is a simple in-memory list plus mutable local index with no server-authoritative queue identity, queue revision, or multi-user reconciliation.
- Navigation decides the playback entry mode in `ui.nav.Destination.kt`: `Destination.Playback` is single-item or resume-oriented, while `Destination.PlaybackList` is the entry point for generated local playlists.
- The playback entry path in `ui.nav.DestinationContent.kt` sends both `Destination.Playback` and `Destination.PlaybackList` into `PlaybackPage`, so the queue decision is made before entering the playback screen rather than being driven by a live shared queue.
- Inside `PlaybackViewModel.init()`, `Destination.PlaybackList` triggers `playlistCreator.createFrom(...)`, stores the result in `playlist`, and starts with the first playable item. The queue is materialized once on entry and then owned locally by the ViewModel.
- Subsequent queue movement in current Wholphin is local-index driven: `playNextUp()` uses `playlist.getAndAdvance()`, `playPrevious()` uses `playlist.getPreviousAndReverse()`, and `playItemInPlaylist()` uses `playlist.advanceTo(item.id)`.
- SyncPlay queue semantics are different. The authoritative queue must come from shared group state, not from a one-time local playlist snapshot or local index mutation.
- For SyncPlay, `SyncPlayPlayQueueUpdate` should become the queue source of truth in `data.syncplay.SyncPlayState`, and playback integration should react to that state rather than mutating `data.model.Playlist` as the primary authority.
- Compatibility plan: keep `Playlist` and `Destination.PlaybackList` for non-SyncPlay playback, but introduce a separate SyncPlay queue representation instead of trying to stretch `Playlist` into a shared-group model.
- Playback handoff plan: SyncPlay playback should still enter through the existing playback surface, `DestinationContent -> PlaybackPage`, but the active queue inside playback should be hydrated from SyncPlay state and websocket queue updates rather than from `playlistCreator.createFrom(...)`.
- Later extraction point: local next or previous or item-selection actions in `PlaybackViewModel` should route through a shared playback command layer so non-SyncPlay mode can keep mutating local `Playlist`, while SyncPlay mode translates the same intent into SyncPlay API requests and waits for authoritative queue updates before considering the queue changed.

## WebSocket Lifecycle Pattern

`ServerEventListener` is `@ActivityScoped` and registered as a `DefaultLifecycleObserver` on the activity.

- It observes `serverRepository.current`; any user or server change cancels the existing websocket job and re-runs `init(...)` for the new authenticated context.
- `init(...)` posts session capabilities first, then calls `setupListeners()`.
- `setupListeners()` cancels any prior listener job and starts `api.webSocket.subscribe<GeneralCommandMessage>()`, launched in `activity.lifecycleScope`.
- This listener is intentionally activity-lifetime UI infrastructure. It handles toast-like general commands, `DISPLAY_MESSAGE` and `SEND_STRING`, that matter while the activity is foregrounded.
- The websocket job is explicitly cancelled in both `onPause()` and `onStop()`, so it does not remain alive in background or across inactive activity states.
- Separately, playback-specific socket listening already exists in `PlaybackViewModel.subscribe()` for `PlaystateMessage`, and that subscription is tied to playback or player setup and teardown rather than the activity observer.

### Recommended SyncPlay Listening Scope

Use split responsibility.

- Keep general, non-playback, activity-visible server messaging activity-scoped alongside `ServerEventListener`.
- Keep active SyncPlay playback control, queue updates, and state updates playback-scoped when a player or session is running, alongside the existing `PlaybackViewModel` command subscription.
- Add only the minimum activity-scoped SyncPlay listener needed for pre-playback group UX if required later, such as invites or group status surfaces shown outside the player.
- Do not make all SyncPlay listening purely activity-scoped. That would mix player orchestration into general UI infrastructure and make command ownership ambiguous.
- Do not make all SyncPlay listening purely playback-scoped either. Lightweight non-playback SyncPlay awareness may still belong above the player if the product later exposes group entry or status before playback begins.

## Planned Package Layout

- `com.github.damontecres.wholphin.data.syncplay`: authoritative shared SyncPlay state and domain-level snapshots that may outlive a single composable or websocket callback. Intended home for `SyncPlayState`, group or session snapshots, membership or role info, ready state, and queue or state reconciliation models.
- `com.github.damontecres.wholphin.services.syncplay`: Jellyfin-facing integration boundary. Intended home for the SyncPlay API adapter and websocket handler.
- `com.github.damontecres.wholphin.ui.playback.syncplay`: playback-scoped SyncPlay integration and playback-visible SyncPlay UI surfaces.
- `com.github.damontecres.wholphin.ui.main.syncplay`: non-playback UI entry points and feature-facing presentation, such as join or create group screens, group status surfaces, invite prompts, and related ViewModels.
- `com.github.damontecres.wholphin.ui.nav`: keep navigation registration and destination wiring here, but only as thin routing glue. Do not place SyncPlay feature logic here.

### Explicit Ownership By Concern

- SyncPlay state: `data.syncplay`
- API adapter: `services.syncplay`
- Websocket handler: `services.syncplay`
- Playback integration: `ui.playback.syncplay`
- UI entry points: `ui.main.syncplay` for pre-playback flows and `ui.playback.syncplay` for in-player surfaces, with route registration remaining in `ui.nav`

## Commit Boundaries

### First Hard Boundary For Commit 2

- Scope is limited to immutable SyncPlay state models plus a repository interface or repository skeleton only.
- Allowed additions: app-owned immutable models in `data.syncplay` for group or session state, queue or state snapshots, membership or ready metadata, and a `SyncPlayRepository` interface or stub implementation boundary that does not yet wire full behavior.
- Explicitly out of scope: any UI, any playback-screen widgets, any dialogs or pages, any nav drawer exposure, any destination wiring in `ui.nav`, any settings or preferences surface, and any Room schema or entity or DAO or migration work.
- Explicitly out of scope: websocket handling implementation, concrete API adapter behavior, playback command dispatch, and player integration.
- Acceptance test for the boundary: after Commit 2, the repo should only have new immutable SyncPlay state or model types and a repository contract or skeleton, with no user-visible behavior changes and no persistence schema changes.

### Suggested PR Rollout

- PR 1: design and discovery documentation only
- PR 2: `services.syncplay` plus minimal `data.syncplay` models or state contract
- PR 3: `ui.playback.syncplay` integration against the shared remote-command dispatcher
- PR 4: `ui.main.syncplay` entry points and thin navigation wiring in `ui.nav`

## Contributor Notes

- This design assumes SyncPlay state becomes app-owned and authoritative only after SDK responses and websocket updates are mapped into Wholphin models.
- Existing playback behavior must remain unchanged until a later implementation PR explicitly moves a behavior boundary.
- Any later PR should keep non-SyncPlay playback working with the current local `Playlist` flow unless that PR is specifically scoped to unify those flows.
