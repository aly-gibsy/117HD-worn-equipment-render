<p align="center"><img width=60% src="https://user-images.githubusercontent.com/831317/223843536-9aa497c7-37ed-4d5f-98fd-1ba8aaa99d40.png"></p>

<p align="center">
  <img src="https://img.shields.io/discord/886733267284398130.svg?label=Discord&logo=Discord&colorB=7289da&style=for-the-badge">
</p>


# 117 HD

GPU renderer with a suite of graphical enhancements.

# About this fork: HD equipment preview

This fork renders the character preview in the **Equip Your Character** interface with 117 HD. The character is lit by the sun, ambient light, sky and nearby point lights where you're standing. The game normally draws that preview with its software renderer, which GPU plugins can't reach, so this has to live inside 117 HD itself rather than in a separate plugin.

The fork is named **117hd-equip-test**, with its own package (`rs117.hdequiptest`), config group and cache folder. It can be installed next to 117 HD without touching 117 HD's settings, but only one of the two can be enabled at a time.

- **Build:** `./gradlew jar` produces `build/libs/hd-<version>.jar`. To load it, put the jar in RuneLite's `sideloaded-plugins` folder and start RuneLite with `--developer-mode`.
- **Settings** (under Equipment preview):

  | Setting | What it does |
  |---|---|
  | HD equipment preview | Turns the HD preview on or off |
  | Pose | What the character does: "As in game" (default), stand, walk, run, or one of 24 emotes. Also set from the dropdown above the preview |
  | Rotate side to side | On by default; turns the character from side to side like the game's preview, pauses while you drag it and resumes 5 seconds after you let go |
  | Scene shadows | Off by default, because the shadow map comes from your in-world pose |
  | Zoom | Sets how large the character is drawn |
  | Show preview area | Debug tint of the box the preview is drawn into |

- **Rotating the preview:** drag it left or right.
- **Picking a pose:** click the dropdown at the top of the preview. Stand, walk and run use your weapon's own stance. The pose is swapped in only for the preview, by briefly changing your character's pose animation while the preview model is built, so nothing changes in the world. An action animation you're doing in game, such as skilling, still takes priority, as it does in game. If RuneLite's Animation Smoothing plugin is on, the preview blends between animation frames too.
- **Where the code lives:** `renderer/zone/EquipmentPreviewRenderer.java`. It works only with the default zone renderer, not the legacy one.

# How to install it

- Download RuneLite from their website: https://runelite.net
- Launch RuneLite
- Click the Wrench icon on the top right of the RuneLite window
- Click the Plugin-Hub button on the right side near the top
- Search for "117 HD"
- Click Install

![Installation intruction gif](https://user-images.githubusercontent.com/72366279/223843005-6175c825-7f35-4bcb-84eb-882321a017f7.gif)

# Links
![discord](https://user-images.githubusercontent.com/5789682/173276137-8ea82e88-4ec1-444f-baf0-4b0dc171901f.png)
[Discord](https://discord.gg/U4p6ChjgSE) - Our Discord server.

🔌 [117HD](https://github.com/117HD/RLHD) - The plugin's source code.

🤖 [Discord-Bot](https://github.com/117HD/discord-bot) - Our Discord bot's source code.

# Links to the original author
![twitter](https://user-images.githubusercontent.com/5789682/173276125-347af1a5-e866-4770-97b2-7ebd3ab1aaa6.png)
[117's Twitter](https://twitter.com/117scape) - The original author's Twitter handle.

![patreon](https://user-images.githubusercontent.com/5789682/173276176-22dd9dcc-40c8-472b-9da2-455eebec296a.png)
[117's Patreon](https://www.patreon.com/RS_117) - If you wish to support the original author of the plugin.
