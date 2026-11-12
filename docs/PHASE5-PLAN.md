# Phase 5 plan: shared sessions over a tailnet (ON HOLD)

_Drafted 2026-11-11. The owner liked this plan but chose not to build it for now; Phase 6
was done first. Nothing here has been built. Revisit the open decisions before starting._

**Goal:** two or more phones add bearings to one shared session, and each phone sees all the
sightlines and the combined fix.

## Server (new `server/` folder)
- One small Python file: **FastAPI + SQLite** (free, open source), as in `PLAN.md`.
- Listens on `localhost` only. **`tailscale serve`** exposes it to the tailnet over HTTPS at
  `https://<machine>.<tailnet>.ts.net` (free Tailscale certificate, so no Android cleartext
  exception). No Funnel, so nothing is reachable from the public internet.
- Endpoints: create a shared session (returns a short join code like `K7-QX4M`), get session
  info, add a bearing, list bearings changed since a time, delete my bearing.
- Access control = the tailnet itself (only your devices can reach it) + the join code. No accounts.
- `docs/SERVER.md`: step-by-step setup for non-technical readers.

## App
- **Settings:** server address (blank = sharing off) and a device name ("Aldo's phone").
- **Session screen:** "Share this session" (shows the join code) and "Join shared session" (type the code).
- **Sent per bearing:** position, true bearing, error band, time, device name, car-mode flag.
  **No photos**; they stay on the phone that took them.
- **Sync only while a shared session's screen or map is open:** new bearings upload at once,
  others' bearings are checked every ~15 s, plus a "Sync now" button. Nothing runs in the
  background; with no server address set, no traffic at all.
- **Offline:** bearings saved without signal are queued and uploaded next time the session is opened.
- **Others' bearings** are stored locally (work offline, on the map, in exports), labelled
  with the device name, no photo. Untick them locally; only the phone that took a bearing
  can delete it from the shared session.
- Networking with plain `HttpURLConnection` + `org.json` (no new libraries).
- **Database:** Room auto-migration to the next version: sessions gain share code + server;
  bearings gain device name, "from another phone" and "uploaded yet". Existing bearings kept.

## Tests and docs
- Unit tests for the JSON exchange format and the "what still needs uploading" logic.
- Update README, INSTALL, USAGE, STATUS; new SERVER.md.

## Open decisions (ask the owner before building)
1. Which always-on machine runs the server, and its OS (Windows/Linux/Mac/Pi)? Python isn't
   installed on the owner's Windows PC.
2. No photos shared in this phase (small thumbnails could come later)?
3. HTTPS via `tailscale serve` (needs "HTTPS certificates" enabled once in the Tailscale admin
   console, free), or plain HTTP with a cleartext exception for one host name?
4. Sync only while the screen is open, every ~15 s?
