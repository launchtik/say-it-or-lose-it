# Store listing (Modrinth / CurseForge)

**Name:** Say It (or Lose It)
**Summary (one line):** Say every new item's name in a foreign language into your mic, or lose it.
**Loader:** Fabric · **Game version:** 1.21.11 · **Environment:** client *and* server (required on both)
**Dependencies:** Fabric API (required)
**License:** MIT · **Categories:** Game Mechanics, Utility, Challenge
**Icon:** `src/main/resources/assets/sayit/icon.png`

---

## Description

Every time you get an item you've never had before, its name pops up in a foreign language, and you have
**10 seconds to say it out loud**. Get the pronunciation right and the word is unlocked forever. Get it
wrong and **every item of that type is deleted**.

- 🎙️ **Real speech recognition, fully offline.** No account, no API key; the speech model downloads by
  itself the first time.
- 🌍 **25 languages:** German, Spanish, French, Italian, Portuguese, Dutch, Russian, Polish, Czech,
  Turkish, Catalan, Esperanto, Arabic, Persian, Hindi, Japanese, Korean, Chinese, Vietnamese, Georgian,
  Kazakh, Kyrgyz, Uzbek, Swedish and English.
- 🎯 **Accents don't cut it.** Two recognizers listen at once, so a fake accent fails while real
  pronunciation passes. Three strictness levels.
- 📦 Triggers on picking up, crafting, smelting and taking items from chests.
- 🟩 Live feedback: see exactly which syllables you got wrong.
- 🧩 Uses Minecraft's own translations, so items from other mods work too.
- 🛡️ Never deletes anything if your mic or the speech model isn't working.

**Commands:** `/sayit status`, `/sayit language <code>`, `/sayit languages`
**Config:** `config/sayit.json` (strictness, time limit, push-to-talk, item blacklist, …)

Speech recognition by [Vosk](https://alphacephei.com/vosk/) (Apache 2.0).
