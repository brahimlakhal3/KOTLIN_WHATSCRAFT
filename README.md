# Whatscraft

A WhatsApp‑style messenger for a small circle of friends. Android app +
self‑hosted relay server, with real end‑to‑end encryption: **the server only
ever stores and forwards ciphertext.** It cannot read messages, and neither
can anyone who gets hold of the database.

- **Client** – Kotlin, Jetpack Compose (Material 3), Room, BouncyCastle crypto.
  Five colour stories: **Monochrome** (default: ink and paper, a soft gray
  conversation canvas, optional OLED black), **WhatsApp** (green accents,
  teal chat header, beige conversation), and **Ocean**, **Berry** and
  **Sunset** (bubbles, header and the app background all take the chosen
  tint). `minSdk 24`.
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
    crypto/                X25519/Ed25519/AES‑GCM primitives, keystore vault
    data/                  API client, WebSocket client, repository, Room
    notify/                foreground relay service + notifications
    di/Container.kt        tiny hand‑rolled dependency graph
    ui/                    Compose screens (auth, chats, contacts, settings)
  src/test/                JVM unit tests, incl. cross‑implementation crypto
gradle/                    wrapper + version catalogue
```

The release signing keystore, its passwords and the relay's `.env` are
deliberately **not** committed.

---

## How the encryption works (protocol v1)

Each device generates two key pairs the first time it signs in:

| Key | Algorithm | Stored as |
| --- | --- | --- |
| Identity | X25519 | seeds sealed by the Android Keystore (`SecretVault`) |
| Signing | Ed25519 | same |

Public keys are uploaded to the server and are visible to friends; private
keys never leave the phone and are encrypted at rest with a Keystore‑backed
AES‑GCM key.

To send a message, the client:

1. Generates a random **AES‑256‑GCM message key** and encrypts the text with
   it (AAD `whatscraft/v1/msg|<conversation>|<msgId>`).
2. For every recipient device, derives a **KEK** from X25519 ECDH via
   HKDF‑SHA256 (`info = "whatscraft/v1/kek"`, salt =
   `SHA256(senderPub‖recipientPub)`, standard base64, canonical padding), and
   uses it to AES‑GCM‑wrap the message key (AAD
   `whatscraft/v1/wrap|<conversation>|<msgId>|<deviceId>`).
3. Signs the ciphertext with the Ed25519 key
   (`whatscraft/v1/msg|<conversation>|<msgId>|<iv>|<ct>|<signingPub>`).

The server stores per‑device wrapped keys plus one ciphertext blob. A device
sends its own wrapped key when posting; the server fans out the other
recipients' copies over WebSocket or push.

**Safety number** – comparing the 60‑digit number shown on the contact's
profile (SHA‑512 over both identity keys, sorted, in 12 groups of 5) out of
band detects a server that swaps keys.

Cross‑implementation checks make sure the Android (BouncyCastle) and server
(WebCrypto) stacks agree bit‑for‑bit — see *Conformance checks* below. The
server code never imports the private keys and cannot decrypt anything.

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

Run the end‑to‑end suite against a running server (51 assertions over
registration, recovery, friends, conversations, messages, read receipts,
reactions, unfriending, re‑inviting, WebSocket delivery, tamper rejection):

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
*Settings → Server*). HTTPS works out of the box; cleartext HTTP is allowed
only in debug builds.

To try it on a phone before you have a public host: install the **debug**
APK (cleartext is permitted there), run the server on your computer, and set
the relay to `http://<your-computer-LAN-IP>:8080` — phone and computer on the
same Wi‑Fi.

### Conformance checks

Whenever you touch crypto or the wire format, run the three checks:

```sh
# in the relay repository:
bun run smoke            # protocol/API end to end
bun run vectors          # seal a WebCrypto message -> JSON
bun run verify-sealed    # open a Kotlin-sealed message in Bun

# in this repository:
./gradlew :app:testDebugUnitTest      # 9 JVM tests incl. opening that message
```

The vector step writes `app/src/test/resources/message-vectors.json` and must
run **before** the unit tests when the protocol changes.

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
   the emoji picker and copy. The header **⋮** menu has the safety number,
   mute/unmute, clear‑messages, and remove‑connection (mutual on both phones).
   Every conversation opens instantly on its **newest message only** – with a
   short loading state the first time – and older messages wait behind
   **Load earlier messages** at the top, which reveals 30 more per tap. Entry
   cost never depends on how long the history is. Decryption stays off the
   main thread with an LRU plaintext cache so typing never fights the crypto,
   and anything the relay collected while the phone was away is pulled in
   quietly after the screen is already up. With no connection, a
   message is written normally and waits with a clock icon (and a small
   banner); it is encrypted and sent by itself the moment the relay is
   reachable again.
4. In **Settings** you can change the display name, theme (system/light/dark),
   **Colors** (Monochrome, WhatsApp, Ocean, Berry, Sunset), notifications,
   relay address, delete all messages, sign out, or delete the account.
   Monochrome dark is graphite gray by default; turn on **True black** for an
   OLED canvas. WhatsApp is the familiar green‑and‑beige look with blue read
   ticks; the three accent palettes recolour the bubbles, header, conversation
   wallpaper and the background of every screen. The sign‑in and account pages
   are single screens – nothing scrolls unless the keyboard is up.
