# Say It (or Lose It): install guide

Every new item you get shows its name in another language. Say it into your microphone before the timer
runs out, or the item is deleted.

## What you need
- Minecraft **Java Edition 1.21.11**
- A microphone

## Install (5 minutes)
1. **Install Fabric.** Download the installer from https://fabricmc.net/use/installer/, run it, choose
   Minecraft **1.21.11**, and click *Install*.
2. **Download Fabric API** (version ending in `+1.21.11`) from https://modrinth.com/mod/fabric-api.
3. **Open your mods folder:**
   - Windows: press `Win + R`, type `%appdata%\.minecraft\mods`, press Enter
   - macOS: Finder → *Go → Go to Folder…* → `~/Library/Application Support/minecraft/mods`
   - Linux: `~/.minecraft/mods`

   Create the `mods` folder if it doesn't exist.
4. **Copy both jars** into it: `fabric-api-….jar` and `sayit-1.0.0+1.21.11.jar`.
5. **Start Minecraft** with the **fabric-loader-1.21.11** profile in the launcher.
6. **Join a world.** The first time, the label next to your hotbar shows `model 12%…`: the mod is
   downloading the speech model for your language (40–100 MB, only once). When it says `mic on`, you're
   ready. Nothing is ever deleted while it's still loading.

Prism Launcher, the Modrinth App and CurseForge work too: create a Fabric 1.21.11 instance and add both
mods to it.

## Allow the microphone
- **macOS:** *System Settings → Privacy & Security → Microphone* → turn on your launcher.
- **Windows:** *Settings → Privacy & security → Microphone* → turn on *Let desktop apps access your
  microphone*.

## Choose a language
German is the default. To change it, type in chat (you need cheats/op):
```
/sayit language es_es
```
Use `/sayit languages` to see all 25.

## Play on a server
The server needs Fabric, Fabric API and this mod in its `mods` folder, and **every player** needs the mod
too. The server decides pass/fail; each player's own computer does the listening.

## Settings
Edit `.minecraft/config/sayit.json`, then type `/sayit reload`. The most useful settings:
- `"strictness"`: `"RELAXED"`, `"NORMAL"` (default) or `"HARD"`. How good your pronunciation must be.
- `"timeLimitSeconds"`: default `10`.
- `"listenMode"`: `"PUSH_TO_TALK"` to only listen while you hold **V**.
- `"blacklist"`: items that are never asked, e.g. `["minecraft:dirt", "#minecraft:logs"]`.

## Troubleshooting
| You see | Fix |
|---|---|
| Nothing happens when picking up items | Make sure you're in Survival, and check `/sayit status`. |
| `no mic` (red) | Plug in a mic, or allow microphone access for your launcher (see above). |
| `no model` (red) | No internet on first start. Download the model zip (names in the mod's `languages.json`) from https://alphacephei.com/vosk/models and unzip it into `.minecraft/config/sayit/models/`. |
| "Microphone is silent" | The OS is blocking the mic: allow it for your launcher. |
| Always fails | Speak clearly, a moment after the card appears. Or set `"strictness": "RELAXED"`. |
