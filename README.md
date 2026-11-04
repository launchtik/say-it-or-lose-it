# Say It (or Lose It)

A Fabric mod for **Minecraft Java 1.21.11**. The first time you get a new item, its name appears in a
foreign language. Say it into your microphone before the timer runs out to unlock it forever. Fail, and
every item of that type is **deleted** from your inventory.

- **25 languages**, all offline (Vosk speech recognition), with no account or API key.
- The speech model for your language downloads by itself the first time (~40–100 MB).
- Works in singleplayer and on servers. The server judges every attempt.

It's a recreation of a mod shown in a short gameplay video. `VIDEO_ANALYSIS.md` describes what the video shows.

> Players: see **[dist/INSTALL.md](dist/INSTALL.md)** for the short install guide.

---

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft **1.21.11**.
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) (any `…+1.21.11` version) and
   `sayit-1.0.0+1.21.11.jar` into your `.minecraft/mods` folder.
3. Start the game and join a world. The label next to your hotbar shows `model 0…100%` while the speech
   model downloads, then `mic on`.

**Servers:** put the same two jars in the server's `mods/` folder. Every player also needs the mod.
Players without it can still join; they just never get challenged.

### Microphone permission
- **macOS:** the launcher (Minecraft Launcher, Prism, Modrinth App…) needs microphone access under
  *System Settings → Privacy & Security → Microphone*.
- **Windows:** *Settings → Privacy → Microphone → Let desktop apps access your microphone*.

Without permission the operating system hands the game silence. The mod notices that, cancels the
challenge and **deletes nothing**.

---

## Gameplay

- **Triggers:** the first time you get an item type by
  - **picking it up**,
  - **taking a crafting / smelting / stonecutting result**, or
  - **taking it out of a chest** (or barrel, shulker box, hopper, furnace, villager trade…).
- The item goes into your inventory right away and a card appears at the top of the screen. You keep
  playing, and the card stays visible over open inventories.
- **Card:** "SAY IT IN GERMAN", seconds left (red in the last 3 s), the word, a shrinking timer bar, and
  `listening…`. After each attempt it shows `heard: "…" NN%`: correct syllables go green, wrong ones red
  and underlined.
- Try as often as you like within the time limit (default 10 s).
- **Success:** the word is unlocked forever. You get a green "UNLOCKED NN%" card and a center pop-up,
  which is bigger the closer you cut it.
- **Fail / timeout:** every item of that type is deleted from your inventory and cursor, and the card
  turns red ("LOST · N deleted"). The word stays locked.
- `mic on` with a level meter sits left of the hotbar. `no mic` / `no model` in red means speech can't
  run, and then items are never deleted.

### Strictness (pronunciation accuracy)
Set with `"strictness"` in the config. Default: `NORMAL`.

| Level | How it judges | Native speech passes | English-accented speech passes | Wrong word passes |
|---|---|---|---|---|
| `RELAXED` | Restricted-grammar recognizer only | 80% | 37% | 3% |
| `NORMAL` | …and a second open-vocabulary recognizer must hear roughly the same word | 72% | 8% | 0% |
| `HARD` | Both recognizers must reach `passThreshold` (85%) | 68% | 8% | 0% |

*How these were measured:*
- **Native:** macOS text-to-speech voices saying 10 common item names in 20 languages.
- **Accented:** an English voice reading the foreign words.
- **Wrong word:** another item's audio played against the target.

Real players get more forgiving results than synthetic voices.

*Why the grammar alone is lenient:* a restricted grammar forces the recognizer to pick one phrase from a
short list, so an accented attempt still lands on the closest one, usually the right word. The
open-vocabulary recognizer can output any of the model's ~100k+ words, so the word only comes out right
if it actually sounds right. When it fails, the card shows what it really heard
(e.g. `heard: "weiß sinn" 50%`).

### Languages
`/sayit languages` lists them in-game. Switch with `/sayit language <code>` (op) or `"language"` in the
config.

| Language | Code(s) | | Language | Code(s) |
|---|---|---|---|---|
| German | `de_de` `de_at` `de_ch` | | Arabic | `ar_sa` |
| Spanish | `es_es` `es_mx` `es_ar` `es_cl` `es_ec` `es_uy` `es_ve` | | Persian | `fa_ir` |
| French | `fr_fr` `fr_ca` `fr_ch` | | Hindi | `hi_in` |
| Italian | `it_it` | | Japanese | `ja_jp` |
| Portuguese | `pt_br` `pt_pt` | | Korean | `ko_kr` |
| Dutch | `nl_nl` `nl_be` | | Chinese (Simplified) | `zh_cn` |
| Russian | `ru_ru` | | Vietnamese | `vi_vn` |
| Polish | `pl_pl` | | Georgian | `ka_ge` |
| Czech | `cs_cz` | | Kazakh | `kk_kz` |
| Turkish | `tr_tr` | | Kyrgyz | `ky_kg` |
| Catalan | `ca_es` | | Uzbek | `uz_uz` |
| Esperanto | `eo_uy` | | Swedish | `sv_se` |
| English | `en_us` `en_gb` `en_ca` `en_au` `en_nz` | | | |

Words come from **Minecraft's own translations** (`lang/<code>.json`, vanilla plus every installed mod),
so modded items work too.

How each language was tested:
- For **all 25**, the real model was loaded and Minecraft's ~1,800 item names were checked against its
  vocabulary.
- For **20 of them**, recognition was tested end-to-end with spoken audio. No macOS voice exists for
  Esperanto, Arabic, Persian, Georgian, Kyrgyz or Uzbek, so those 6 are vocabulary-checked only.

Per-language notes:
- **Japanese** (48% of names in the vocabulary) and **Chinese** (63%): the rest fall back to free
  recognition, where kanji vs. kana spelling can differ.
- **Korean:** the open-vocabulary check is replaced by a stricter confidence bar, because its model
  handles single words badly.
- **Vietnamese:** tone marks count ("Táo" ≠ "Tạo").
- **Swedish:** the weakest model in testing (5 of 10 native words passed).

---

## Config: `config/sayit.json`

The gameplay keys (`enabled` through `triggerOnContainer`) are read on the server. The rest are read on
each player's client. `/sayit reload` re-reads the file, and a language change is pushed to every
connected player.

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Master switch |
| `language` | `"de_de"` | Minecraft locale to speak (table above) |
| `strictness` | `"NORMAL"` | `RELAXED` / `NORMAL` / `HARD` (see above) |
| `timeLimitSeconds` | `10` | Time per word |
| `resultGraceSeconds` | `2.0` | Server waits this long after 0 s for a late result |
| `passThreshold` | `0.85` | Minimum similarity, shown as the % on screen |
| `minConfidence` | `0.5` | Minimum recognizer confidence |
| `foldAccents` | `true` | Treat "körner" = "korner" (never applied to Vietnamese) |
| `whitelist` / `blacklist` | `[]` | Item ids or tags: `"minecraft:dirt"`, `"#minecraft:logs"` |
| `challengeInCreative` | `false` | |
| `failRemoves` | `"ALL_OF_TYPE"` | Or `OBTAINED_AMOUNT` (only what triggered it) |
| `triggerOnPickup` / `triggerOnCraft` / `triggerOnContainer` | `true` | |
| `listenMode` | `"AUTOMATIC"` | Or `PUSH_TO_TALK` (hold **V**, rebindable under Controls → Say It) |
| `autoDownloadModels` | `true` | Download a missing model from alphacephei.com on first use |
| `modelsDirectory` | `"config/sayit/models"` | Where models live |
| `modelPaths` | `{}` | Explicit model folder per language |
| `useGrammar` | `true` | Restricted grammar (target + similar words) |
| `distractorCount` / `nearMissDistractors` | `8` / `12` | Similar item names / one-letter-off words in the grammar |
| `microphone` | `""` | Part of the input device name; empty = system default |
| `showMicIndicator` / `sounds` | `true` | |

**Custom words**, in `config/sayit_words.json` on the server:
```json
{ "de_de": { "minecraft:porkchop": "Schweinefleisch" }, "*": { "minecraft:diamond": "Diamant" } }
```

**Commands:**
- Anyone: `/sayit status`, `/sayit language`, `/sayit languages`
- Op only: `/sayit language <code>`, `/sayit challenge` (ask for the held item now),
  `/sayit unlock` (unlock the held item), `/sayit reset [players]`, `/sayit reload`

**Manual model install** (offline PCs): download the zip for your language from
https://alphacephei.com/vosk/models (the names are in `src/main/resources/assets/sayit/languages.json`)
and unzip it into `config/sayit/models/`.

### Edge cases
| Situation | Behavior |
|---|---|
| Several new items at once | Queued, one card at a time. More of an item that is already queued or being asked is merged in. |
| A stack of 64 | One challenge per pickup/click, not per item. |
| Creative / spectator | Never challenged. |
| No mic, no model, mic silent or unplugged, model still downloading | Challenge cancelled, **nothing deleted**, red "item kept" card. |
| Disconnect mid-challenge | Nothing deleted; asked again ~2 s after rejoining. |
| Death mid-challenge | Cancelled; picking the drops up asks again. |

---

## Development

Needs JDK 21+.

```bash
./gradlew build
```
Compiles, runs the 43 unit tests and produces `build/libs/sayit-1.0.0+1.21.11.jar`.

```bash
./gradlew runClient
```
Dev client.

```bash
./gradlew prodClient
```
Runs the **built jar** in a real production Fabric client with Fabric API, the way a player's game would.

```bash
./gradlew downloadVoskModel -Plang=es_es
```
Pre-downloads a model into `run/config/sayit/models`.

```bash
./gradlew listLanguages
```

### Layout
```
src/main/java/dev/sayit/
  core/      WordMatcher, Syllables, GrammarBuilder, Strictness, LanguageNames   (pure Java, unit tested)
  config/    SayItConfig, WordOverrides
  net/       SayItPayloads                     - client/server protocol
  server/    ChallengeManager, PlayerWords, InventoryRevoker
  mixin/     ItemEntityMixin (pickup), ItemStackCraftMixin (crafting), ScreenHandlerMixin (containers)
  command/   /sayit
src/main/resources/assets/sayit/languages.json - locale -> model table (also read by Gradle)
src/client/java/dev/sayit/client/
  speech/    SpeechRecognizer (interface), VoskSpeechRecognizer, VoskNative (JNA), ModelDownloader,
             Microphone, SpeechController
  hud/       ChallengeHud
  ClientWords, SayItClient
```

Notes:
- **Vosk binding:** the bundled `org.vosk` Java binding can't load on macOS, because its `libvosk.dylib`
  lacks `vosk_recognizer_set_grm`. `VoskNative` binds only the functions the mod needs, lazily and in
  UTF-8.
- **Swapping the recognizer:** implement `SpeechRecognizer` (e.g. Azure Pronunciation Assessment). It
  receives 16 kHz mono PCM and returns a transcript, a confidence, and optionally an open transcript.

### Manual test checklist
Run these in a survival world.

Triggers:
- [ ] Pick up a new item → card.
- [ ] Craft bread → card.
- [ ] Take a new item from a chest (click, shift-click, number key) → card.
- [ ] Move your own items around → no card.

The card:
- [ ] Timer turns red in the last 3 s and ticks each second.
- [ ] Wrong word → `heard: "…" NN%`, red syllables, can retry.
- [ ] Correct → green UNLOCKED card and pop-up (huge with < 2 s left).
- [ ] The same item again → no card.

Failure and strictness:
- [ ] Stay silent → every item of that type deleted, red LOST card, still locked.
- [ ] Say it with a strong English accent on `NORMAL` → fails, showing what it heard. On `RELAXED`
      it often passes.

Queueing:
- [ ] Pick up 3 new items at once → cards one after another.

Never delete without working speech:
- [ ] Rename the model folder → `no model`, "item kept".
- [ ] Deny mic permission → "Microphone is silent", nothing deleted.
- [ ] Disconnect mid-card → nothing deleted, asked again on rejoin.
- [ ] Die mid-card → cancelled.

Languages and setup:
- [ ] `/sayit language es_es` → the model downloads (`model NN%`), then cards ask "SAY IT IN SPANISH".
- [ ] `PUSH_TO_TALK` → only audio while V is held counts, also with the inventory open.
- [ ] Fresh `.minecraft` with only Fabric API and this jar → works (`./gradlew prodClient`).

## Known limits
- The video never shows a failure, so the LOST card and the "delete all of that type" rule are best
  guesses (see `VIDEO_ANALYSIS.md`).
- The client reports what it heard, so a modified client could cheat.
- Small offline models are trained on clear adult speech. Noisy rooms lower scores; use `RELAXED` if it
  feels too strict.
