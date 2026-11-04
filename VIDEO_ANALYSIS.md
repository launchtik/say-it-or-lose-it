# VIDEO_ANALYSIS — "Say it in German" mod

Source: a short gameplay video (not included in this repository), a 57.7 s iPhone screen recording (1170×2532, HEVC, 59 fps) of a vertical TikTok/Reels-style edit.
Layout: facecam on top, a **9:16 crop of the Minecraft window** on the bottom, with the social-app sidebar and caption bar laid over it.

Method: frames every 0.5 s; dense 6 fps crops around every unlock; full-res HUD crops; Whisper `small` with word timestamps.
The scene filter at 0.3 found **zero** cuts: the clip is one continuous recording (with a few jump-cut edits inside it, see below).

**Excluded (not part of the mod):** the facecam, the burned-in yellow/white captions, the "GERMAN / WEIZEN = WHEAT" boxes over the facecam, the "ATTEMPTS 29" box over the facecam, and the social-app UI (likes sidebar, account name, post caption, comment box).

The reference stills and transcript used for this analysis are not included in this repository.

---

## 1. Minecraft version and loader

| Clue | Evidence | Implication |
|---|---|---|
| **Wooden Spear** tooltip ("1 Attack Damage, 1.54 Attack Speed") @ 9.0 s | Spears were added in Java 1.21.11 | **≥ 1.21.11** |
| "Leaf Litter" tooltip @ 12.3 s | Added in 1.21.5 | consistent |
| Raw Copper, Stone Axe/Shovel, recipe book, modern inventory | vanilla UI | Java Edition |
| Vanilla Minecraft font in every mod element, no custom font | — | rendered in-game with the vanilla text renderer (not an OBS overlay) |
| Mod UI stays on top of open inventory/crafting screens | @ 10–16 s, 29 s | drawn on a HUD layer above screens (or a screen-render hook) |

There's no F3 screen, no mod-menu button, and no title screen, so **the loader can't be identified.** The plan is to use Fabric on the latest stable release ≥ 1.21.11, and I'll confirm the exact version at the start of Phase 2.

The "heard" text comes back **capitalized and punctuated** ("Weizenkönne!", "Oh, no, no, no. Weissen!"). That points to a Whisper-class or cloud recognizer. Vosk returns lowercase text with no punctuation, so our HUD's "heard" text will look a bit plainer unless we prettify it.

---

## 2. Target language and word pairs

The language is **German**. The HUD says "SAY IT IN GERMAN". Every word matches vanilla `de_de.json` exactly, including capitalization and "ö":

| Time | English item | German word shown | How it was obtained |
|---|---|---|---|
| ≤ 0.0 s | Wheat | **Weizen** | harvested wheat, picked up drops |
| 10.0 s | Bread | **Brot** | **crafted** (crafting/recipe-book UI was open) |
| 17.0 s | Potato | **Kartoffel** | harvested potato crop, picked up drops |
| 37.6 s | Raw Porkchop | **Rohes Schweinefleisch** | killed a pig, picked up drop |
| 48.3 s | Wheat Seeds | **Weizenkörner** | broke grass, picked up drop |

The multi-word name stays whole ("Rohes Schweinefleisch"). The adjective is not stripped, so you have to say the full display name.

---

## 3. Trigger

- **Picking up item entities from the ground** triggers it (wheat, potato, porkchop, seeds).
- **Crafting** triggers it too (bread). The prompt appeared while the crafting screen was open, and the bread was already in hotbar slot 7 at 11.0 s.
- **It only fires the first time you get an item type.** After a word is "UNLOCKED", that item never asks again:
  - 6.4 s: picks up more Wheat right after Weizen unlocked → no prompt.
  - 44.5 s: picks up Potato (Kartoffel already unlocked) → no prompt.
  - The success banner literally says "UNLOCKED", i.e. a per-item-type unlock.
- **Not every item seems to be challenged.** Raw Copper is picked up at 6.0–6.4 s with no prompt, and Stone Axe, Stone Pickaxe, Stone Shovel, Crafting Table, Dirt and Cobblestone sit in the inventory. They might have been unlocked before the clip started, though. (Open question Q2.)
- The game **is not paused**. The player keeps walking, opening inventories and fighting while the prompt runs.

---

## 4. Timing (aligned with the audio)

| Challenge | Prompt shown | Speech (Whisper) | HUD result | Outcome |
|---|---|---|---|---|
| Weizen | (≈ −1 s, 10 s timer) | "Weisen!" 1.4 s, 2.5 s, 3.8 s | 2.5 s: *heard "Oh, no, no, no. Weissen!" 83%* → 3.5 s: *heard "Vai Zen!" 67%* → **4.7 s: UNLOCKED 100%** | kept, about 3 s left |
| Brot | 10.0 s (10 s) | "Brot." 11.1 s | **12.4 s: UNLOCKED 100%** | kept, about 7 s left |
| Kartoffel | 17.0 s (10 s) | 17.9 s, 19.4 s, 20.8 s | 19.0 s: *heard "Gott hoffel." 63%* (stays up through the 2nd attempt) → **22.0 s: UNLOCKED 100%** | kept, about 2–3 s left |
| Rohes Schweinefleisch | 37.6 s (10 s) | "Rohes Schweinflesch!" 39.3–40.0 s | **40.9 s: UNLOCKED 93%** | kept, about 7 s left |
| Weizenkörner | 48.3 s (10 s) | (offscreen "the R" 48.5 s), "Weisen können" 50.2 s, 52.7 s | 50.0 s: *heard "Das war's." 17%* → 52.0 s: *heard "Weizenkönne!" 83%* → **55.1 s: UNLOCKED 100%** | kept with **about 1 s left** |

What this tells us:
- **Time limit is 10 s.** Every prompt starts at "10s" and counts down in whole seconds.
- **Multiple attempts are allowed** within the 10 s. The mic listens continuously, and each finished utterance gets scored.
- **Latency is about 1–1.5 s** from the end of speech to the HUD update. That's consistent with end-of-utterance detection plus recognition.
- **Pass threshold:** 83% failed twice and 93% passed, so it sits somewhere in **84–93%**. Best guess: **85%** (or 90%).
- **The score is text similarity, not ASR confidence.** Shown percentages track normalized character similarity between the heard text and the target (lowercase, punctuation and spaces stripped). Examples: "Vai Zen" vs "weizen" = 67%, "Gott hoffel" vs "kartoffel" ≈ 63%, "Weizenkönne" vs "weizenkörner" = 83%. Levenshtein and difflib both land within about ±5% of every displayed number. With a phrase, the best-matching part counts: "Oh, no, no, no. Weissen!" still scored 83%.

---

## 5. Mod UI elements

All text is the vanilla Minecraft font. Colors below are sampled from the frames and are approximate.

### 5.1 Challenge card (top-center of the screen)


- **Panel:** dark navy, semi-opaque (≈ `#1E2433`, about 90% alpha), no rounded corners. Thin **gold/yellow vertical accent bar** on the left edge (≈ `#F2B926`). About 3/5 of the cropped view wide. Long words overflow past the right edge, so the panel does not auto-widen ("Rohes Schweinefle…" is cut off).
- **Item icon:** the actual item sprite, rendered large (about 2× text height) at the left.
- **Line 1:** `SAY IT IN GERMAN`, small caps, light gray (≈ `#B8BCC8`). **Seconds left** (`9s`, `8s`…) right-aligned on the same line in light gray, turning **red** in the last ~3 s (`3s`, `2s`, `1s`).
- **Line 2:** the **German word**, large (about 2× scale), **gold/yellow** (≈ `#F5C542`) while listening.
- **Timer bar:** a thick horizontal bar under the word, **gold**, that shrinks left→right as time runs out. In the last few seconds the bar is short and the end turns **red**.
- **Line 3, status:**
  - while waiting: `listening…` in gray.
  - after a failed attempt: `heard: "<recognized text>"  NN%` in **red/pink** (≈ `#FF5A6E`), with the percentage right-aligned.
- **Per-syllable feedback after a failed attempt:** the target word is re-colored. Syllables that matched turn **green** (≈ `#5BE37D`). Syllables that did not match turn **red** with a **red underline** under them.
  - "Wei**zen**" → "Wei" green, "zen" red and underlined (heard "Weissen").
  - "Weizen**körner**" → "Weizen" green, "körner" red and underlined (heard "Weizenkönne").
  - After the "Vai Zen" attempt, the whole word "Weizen" is red/underlined.
- The card stays visible while inventory and crafting screens are open.

### 5.2 Success card (replaces the challenge card, same spot)
- Smaller panel, **dark green** (≈ `#1F4A35`), with a **bright green left accent bar** (≈ `#4CE07A`).
- Line 1: German word, large, bright green, centered.
- Line 2: `UNLOCKED  NN%` in white/light gray (`100%`, `93%`).
- Visible about 2.5–3 s, then **fades out** (the 16.0–16.5 s frames show Brot fading).

### 5.3 Center "UNLOCKED" pop-up (near the crosshair)
Two or three sizes appear in the video.
- **Small** (Brot 12.4 s, Porkchop 40.9 s): `[item icon] UNLOCKED` in green with a dark outline, just above the crosshair. Fades in over about 0.3 s, holds about 2 s, then fades out.
- **Medium** (Kartoffel 22.5 s): `[icon] UNLOCKED`, larger, with the German word in small white text underneath.
- **Big "clutch"** (Weizenkörner 55.1 s, and Weizen in the inventory at 7–8 s): `[icon] UNLOCKED!` in very large green blocky text with a thick dark outline. It **pops in oversized and settles** (scale overshoot), with the German word in small text below. It comes with **light-green radial rays** behind it and **green orb particles** (XP-orb-like) floating up through the scene. It lasts about 2–3 s.

### 5.4 Persistent mic indicator
- Text `mic on` in gray, on a dark translucent strip **just left of the hotbar**, visible the whole time (also when no challenge is running). The game crop cuts its left edge off, so it might be longer text (e.g. "🎤 mic on").

### 5.5 Sounds
These **can't be determined**. The audio track is mostly the player's speech, there's no clearly audible mod sound effect, and the video may have background music. The green orbs suggest the XP-orb/level-up sound. (Open question Q6.)

---

## 6. What "lost" looks like

**A failure never happens in this clip.** All five challenges succeed. Here is what we know:
- The caption says the mod "**deletes** any item I can't say in German".
- The player says "I almost just **lost all my wheat**" after nearly failing Weizen.
- **The item is already in the inventory while the challenge runs.** Wheat ×8 in slot 5 at 0.1 s, Bread in slot 7 at 11.0 s, and Raw Porkchop ×2 in slot 2 at 38.0 s are all there during "listening…". So the original mod grants first and **deletes on failure**. It does not hold the stack aside.

Best guess for failure: on timeout or a failed final attempt, **every item of that type is removed from the inventory** ("all my wheat"). It probably comes with a red "FAILED"/"DELETED" card and a vanilla break or fizz sound. The item stays locked, so the next pickup challenges you again.

The prompt asked for a "pending" stack. That changes how it looks: the item wouldn't appear in the hotbar until you pass. See Q1.

---

## 7. Queueing

**Never observed.** Every prompt starts when no other prompt is active. Picking up an already-unlocked item during a challenge was not shown either. (Q4.)

---

## 8. Edit cuts inside the clip (to avoid misreading them)
- 7.0 → 8.5 s and 13.5 → 16.5 s: jump to inventory/crafting screens (bread crafting setup), so the order of events around Brot is partly edited.
- 28.5 → 37.0 s: walking/inventory footage with no mod UI ("wait, I think I've unlocked… I'm becoming German"). Commentary only.

---

## 9. Open questions (with my best guess)

| # | Question | Best guess |
|---|---|---|
| **Q1** | **Grant first and delete on fail (what the video shows), or hold as pending and grant on pass (what the prompt asked for)?** **Decided: grant + revoke, matching the video.** | Use **pending (server holds the stack)** as you specified, because it's safer: nothing gets duplicated if the player drops or uses the item mid-challenge. Optionally show a ghost icon in the card. I could add a config switch `grantMode: PENDING / GRANT_THEN_REVOKE` to match the video's look. |
| **Q2** | Which items are challenged? Raw copper and tools appear without a prompt. | **All items**, first time per item type, persisted per player. The un-prompted items were probably unlocked before the clip. A whitelist/blacklist in config covers the rest. |
| **Q3** | What does failure remove: just the picked-up stack, or every item of that type? | The **picked-up stack** in pending mode. In grant-then-revoke mode, all of that type ("lost all my wheat"). |
| **Q4** | Queueing behavior | Queue FIFO, one prompt at a time. Already-unlocked items pass straight through. More pickups of the *same* type while it's pending get merged into the pending challenge. |
| **Q5** | Pass threshold | **85%** similarity, configurable. |
| **Q6** | Sounds | Tick sound in the last 3 s, `entity.player.levelup` on unlock, `block.fire.extinguish` / item-break on fail. |
| **Q7** | Center pop-up size: what decides small vs big? | Fewer seconds left = bigger and more particles ("clutch"). It fits all five cases: ≥5 s left → small, 2–4 s → medium, ≤2 s → big with rays and orbs. Number of attempts fits almost as well. |
| **Q8** | Card position in the real (uncropped) game window | **Top-center**, below the boss-bar area. The TikTok crop is roughly centered on the hotbar, and the card sits near that center. |
| **Q9** | Is "unlocked" saved across sessions/worlds? | Per-player, per-world, saved with the world. |
| **Q10** | Syllable split for the green/red feedback | A simple German syllabifier (vowel-cluster based) plus compound splitting where the heard text diverges. A character-level alignment fallback keeps it approximately right. |
| **Q11** | "heard" text formatting | Vosk outputs lowercase text. I'll capitalize the first letter and add "!" or "." for display. That's cosmetic. |
| **Q12** | Score metric vs your spec's "Vosk confidence ≥ threshold" | The video's % is **text similarity**. I'll pass on **similarity ≥ threshold** (the displayed %) **and**, optionally, Vosk word confidence ≥ a second threshold (off by default). Your "normalized text equals target" check is then the 100% case. |
