<p align="center"> <img src="https://github.com/user-attachments/assets/4423fd75-35e9-403d-b6b6-80d20e5ca956" alt="Skilling Loot Tracker Panel" width="270" /> </p> <h1 align="center">Skilling Loot Tracker v2.0.0</h1> <p align="center"> <b>A RuneLite plugin for tracking resources and profit from gathering skills</b><br> <em>Stop guessing your GP/hr. Know exactly what your grind is worth.</em> </p> <p align="center"> <img src="https://img.shields.io/badge/RuneLite-Plugin%20Hub-17191c?style=for-the-badge" /> <img src="https://img.shields.io/badge/License-BSD%202--Clause-blue?style=for-the-badge" /> <img src="https://img.shields.io/badge/Version-2.0.0-success?style=for-the-badge" /> </p>
What It Does
Skilling Loot Tracker runs silently while you skill and automatically logs every item you obtain from Fishing, Mining, Woodcutting, Farming, and Hunter.

No setup. No spreadsheets. Just real loot data, GP/hr, and session stats built straight into RuneLite with full persistence between logins.

Core Features
Feature

Description

Automatic Loot Detection

Tracks all resources from Fishing, Mining, Woodcutting, Farming, and Hunter. Compatible with barehanded, crystal tools, 3-tick, and tick manipulation.

Session Management

Start, reset, and view stats from the side panel. Tracks active skilling time only. Session data saved locally via ConfigManager + GSON.

Value & Profit Tracking

Live Grand Exchange prices from ItemManager for total haul value. Calculates real GP/hr based on session start time.

Per-Skill Breakdown

See item counts, value subtotals, and GP/hr per skill. Know exactly how much coal, runite ore, magic logs, anglerfish, herbs, or chinchompas you’ve earned.

Multi-Skill Support

Individual toggles for all 5 skills. Disable any you don’t want tracked.

Data Persistence

lootData and sessionStartTime persist between client restarts. No progress lost on logout.

UI & Quality of Life
Dedicated Side Panel: Lives with your other RuneLite plugins. Expandable sections for each skill with item sprites, counts, values, and quick-reset buttons. Built with PluginPanel, ColorScheme, FontManager.
In-Game Overlay: Optional minimal OverlayPanel showing session time, total value, and GP/hr. Toggle on/off in config. Default: off.
Item Icons: Real in-game sprites via SpriteManager + AsyncBufferedImage for every tracked item.
Smart Formatting: QuantityFormatter displays 1.2K / 3.4M style numbers. Clean custom scrollbars.
Fully Configurable: Enable/disable overlay + individual skills: Fishing, Mining, Woodcutting, Farming, Hunter.
Lightweight: Uses GameStateChanged, AnimationChanged, ItemContainerChanged, GameTick. Performance friendly.
Technical Details
Category

Details

Built For

RuneLite Plugin Hub

License

BSD 2-Clause — Copyright 2026 RyUkY realmftalk420@gmail.com

Privacy

All data stored locally via RuneLite's ConfigManager. Nothing sent externally.

Dependencies

Guava ImmutableSet, Gson, Lombok @Slf4j, ClientThread for thread safety

Tracked Items

18 log types, 19 fish types, plus ores, herbs, hunter catches via hardcoded ID sets

Open Source

Contributions welcome on GitHub

Ideal For
Players grinding 99s, Ironmen tracking supplies, efficiency scapers who want accurate profit data, and anyone doing Farming runs or Hunter for profit without running 3rd party tools.

What’s New in 2.0.0
Farming + Hunter Added: Now tracks herb runs, birdhouse runs, chinchompas, salamanders, and more
Full Side Panel UI: Complete item breakdown with sprites, not just overlay text
Session Persistence: Data now saves between logins using ConfigManager
GameStateChanged Handler: Properly pauses/resumes sessions on logout/hop
Expanded Item Database: More LOG_IDS and FISH_IDS coverage
