# Whatscraft

A WhatsApp‑style messenger for a small circle of friends. Android app +
self‑hosted relay server. Chats are stored in the cloud: open a conversation
on any signed‑in device and the newest messages load from the relay, newest at
the bottom. **No message history is saved on the phone** — the app keeps only
contacts on disk, and the relay keeps the text so every device sees the same
conversation.

That is a deliberate trade‑off: simple, Messenger‑like chats instead of
end‑to‑end encryption. The relay can read what it stores, so run your own or
pick one you trust. Passwords are hashed with argon2id; the app never writes
message rows to its database.

- **Client** – Kotlin, Jetpack Compose (Material 3), Room (contacts only),
  OkHttp/WebSocket, kotlinx.serialization. Ten colour stories: **Monochrome**
  (default: ink and paper, a soft gray conversation canvas, optional OLED
  black), **WhatsApp** (green accents, teal chat header, beige conversation),
  plus **Ocean**, **Berry**, **Sunset**, **Forest**, **Rose**, **Lavender**,
  **Sand** and **Midnight**. `minSdk 24`.
- **Server** – Bun + Postgres, REST for auth/state and a WebSocket relay for
  live messages. Runs anywhere; this project uses the free tiers of
  [Render](https://render.com) (host, no card required) and
  [Neon](https://neon.tech) (Postgres).
- **Features** – accounts (username + password), friend requests, 1:1
  conversations, typing indicators, delivery/read receipts, emoji reactions
  (double‑tap a message for ❤️, long‑press for the picker), inline
  notification replies, offline sending with automatic retry, per‑chat and
  global clear‑messages, password recovery via one‑time recovery code (no
  email needed), relay address configurable in‑app. Removing a connection is
  mutual: the relay deletes the direct chat for both people, and only a new
  accepted invitation opens a fresh one. The relay is already group‑aware;
  the app UI is 1:1 for now.

---

## Repository layout

This repository is the **Android app**. The relay (Bun + Postgres) lives in
its own repository:
[brahimlakhal3/whatscraft-server](https://github.com/brahimlakhal3/whatscraft-server).

```
app/                       Android application
  src/main/java/com/brahim/whatscraft/
    crypto/                Keystore-backed vault for session tokens
    data/                  API client, WebSocket client, repository, Room
    notify/                foreground relay service + notifications
    di/Container.kt        tiny hand‑rolled dependency graph
    ui/                    Compose screens (auth, chats, contacts, settings)
  src/test/                JVM unit tests
gradle/                    wrapper + version catalogue
```

The release signing keystore, its passwords and the relay's `.env` are
deliberately **not** committed.

---

## How messages are stored

1. The app sends `{ id, kind, body: { text: "..." } }` over the WebSocket, or
   over REST when the socket is down.
2. The relay stores the row in Postgres and fans it out to every member's
   other devices. The sending device gets an `ack` with the real sequence
   number.
3. Opening a conversation fetches the **newest 30** messages with
   `GET /v1/conversations/:id/messages?limit=30`; **Load earlier messages**
   walks back 30 at a time (30 → 60 → 90 …) using the `before` cursor.
4. Reactions are ordinary rows of kind `reaction` (`react|<messageId>|<emoji>`)
   and are folded onto the target bubble; they never replace the chat preview
   or add unread.

There is no local message cache. That keeps the phone clean and makes every
device identical, at the cost of needing a connection to read old chats:
offline, a conversation opens empty and messages typed then wait in memory
with a clock icon until the relay is reachable. The cloud has no presence
signals at all — no online dot, no last seen, no profile photos to leak.

**Clear messages** (`⋮` in a chat, or *Settings → Account*) is per account: the
relay remembers how far you cleared, so your devices see an empty chat while
the other person keeps their copy, and new messages still arrive. **Delete
account** removes the account and every message from the relay.

---

## Running the server locally

```sh
git clone https://github.com/brahimlakhal3/whatscraft-server
cd whatscraft-server
cp .env.example .env            # optional; JWT_SECRET has a dev default
bun install
bun run src/index.ts            # http://localhost:8080
```

Without `DATABASE_URL` the server uses an embedded **PGlite** database in
`.data` — zero setup, good enough for development. Point `DATABASE_URL` at
Postgres (e.g. Neon) for production.

Run the end‑to‑end suite against a running server (59 assertions over
registration, recovery, friends, conversations, plaintext messages, history,
read receipts, reactions, clearing, unfriending and re‑inviting):

```sh
bun run smoke
```

### Deploying to Render + Neon (free, no credit card)

The relay repository ships a `render.yaml`, so Render builds it directly.

1. Create a [Neon](https://neon.tech) project and copy its connection string
   (`...?sslmode=require`). The server applies `schema.sql` on boot.
2. Push the relay repo to GitHub, then in the Render dashboard:
   **New +** → **Blueprint** → pick the repo → **Apply**.
3. When prompted, paste the Neon string as `DATABASE_URL`. Render generates
   `JWT_SECRET` automatically. Set `REGISTER_CODE` if you want the relay to be
   invite‑only (recommended for anything public).
4. After the first deploy, the relay answers at
   `https://<service-name>.onrender.com` – check `/v1/health`.

This repo's live instance: `https://whatscraft-relay.onrender.com`.

Free services **sleep after 15 minutes without traffic** and take ~1 minute
to wake. An *incoming WebSocket message* counts as traffic (Render changelog,
Feb 2026), so the relay stays awake while any phone is connected. For
always‑on behaviour, point a free uptime monitor (e.g. UptimeRobot, 5‑minute
checks) at `/v1/health`; one always‑warm instance uses ~744 of the 750 free
instance hours a month.

The same flow works on any Docker host – the `Dockerfile` is self‑contained.

### Optional: background push (FCM)

Without push credentials the app still receives messages while it is open or
running its foreground notification service. For true background delivery,
create a Firebase project, add an Android app with the package
`com.brahim.whatscraft` (no `google-services.json` in the APK needed – the
server sends HTTP v1 messages directly), and set these variables on the
service:

```sh
FCM_PROJECT_ID=...
FCM_CLIENT_EMAIL=...
FCM_PRIVATE_KEY="-----BEGIN PRIVATE KEY-----\n..."
```

---

## Building the Android app

```sh
# debug build (points at http://10.0.2.2:8080, i.e. your machine from the emulator)
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# release build for friends - signed with whatscraft-release.jks
./gradlew :app:assembleRelease
# -> app/build/outputs/apk/release/app-release.apk
```

The release APK is deliberately **not** minified or obfuscated, so crash
reports stay readable. `gradle.properties` tunes Gradle for an 8 GB machine;
raise `org.gradle.jvmargs` if your build box has more RAM.

> **Signing is configured outside the repo.** `assembleRelease` reads four
> properties from `~/.gradle/gradle.properties` (never committed):
>
> ```properties
> whatscraft.storeFile=/absolute/path/to/whatscraft-release.jks
> whatscraft.storePassword=...
> whatscraft.keyAlias=...
> whatscraft.keyPassword=...
> ```
>
> **Keep the keystore safe.** Android only installs updates signed with the
> same key, so losing it means your friends must uninstall before installing a
> new build. Change the password with `keytool -storepasswd` if you ever share
> the key. Debug builds need none of this.

### Pointing the app at your server

Release builds default to `https://whatscraft-relay.onrender.com` (see
`DEFAULT_SERVER_URL` in `app/build.gradle.kts`); debug builds default to
`http://10.0.2.2:8080` for emulator use. On any install, you can change the
address before signing in: on the sign‑in screen tap **Server** (or later,
*Settings → Relay*). HTTPS works out of the box; cleartext HTTP is allowed
only in debug builds.

To try it on a phone before you have a public host: install the **debug**
APK (cleartext is permitted there), run the server on your computer, and set
the relay to `http://<your-computer-LAN-IP>:8080` — phone and computer on the
same Wi‑Fi.

### Tests

```sh
./gradlew :app:testDebugUnitTest
```

---

## Using the app

1. Install the APK, open it, pick your relay if the default is not yours
   (**Server**), then choose a username (3–24 chars: `a-z 0-9 _ .`), a
   password (8+ chars) and the **friends code** if the relay is invite‑only.
   You get a **recovery code** – write it down; it is the only way to reset
   the password.
2. Add a friend by username; they accept the request under **People**.
3. Start chatting. New messages pop in with a short entrance animation; the
   clock sits at the end of the last line like WhatsApp. **Double‑tap** a
   message to send ❤️ (double‑tap again to take it back) or **long‑press** for
   the emoji picker and copy. The header **⋮** menu has mute/unmute,
   clear‑messages, and remove‑connection (mutual on both phones). Every
   conversation opens with a short loading state, then the newest **30**
   messages are fetched from the cloud with the view at the bottom – the same
   every time, no matter how long the history is. **Load earlier messages** at
   the top adds 30 more per tap (30 → 60 → 90 …) without the list jumping
   around. With no connection, a message is written normally and waits with a
   clock icon (and a small banner); it is sent by itself the moment the relay
   is reachable again.
4. **Settings** is a small stack of pages, not one long list: a profile card at
   the top, then **Appearance**, **Chats**, **Notifications**, **Privacy**,
   **Relay**, **Account** and **About**. Appearance holds the
   theme (system/light/dark), colours, text size (Small → Largest), bubble
   shape (Rounded/Soft/Square), a live chat preview, **True black** and
   **Reduce motion**. Monochrome dark is graphite gray by default; turn on
   **True black** for an OLED canvas. WhatsApp is the familiar green‑and‑beige
   look with blue read ticks; the accent palettes recolour the bubbles, header,
   conversation wallpaper and the background of every screen. Chats holds
   Enter‑to‑send, double‑tap‑to‑react and haptics; Privacy holds read receipts
   and typing indicators, and explains that there is **no online status and no
   last seen** anywhere in the app. Account holds password changes, the
   delete‑all‑messages switch, sign‑out and account deletion.
