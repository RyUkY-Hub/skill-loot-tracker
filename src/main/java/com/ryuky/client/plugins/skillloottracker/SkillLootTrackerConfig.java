/*
 * Copyright (c) 2026, RyUkY-Hub <realmftalk420@gmail.com>
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.ryuky.client.plugins.skillloottracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("skillloottracker")
public interface SkillLootTrackerConfig extends Config
{
	@ConfigItem(
			keyName = "showOverlay",
			name = "Show overlay",
			description = "Display GP/hr overlay in-game",
			position = 0
	)
	default boolean showOverlay() { return false; }

	@ConfigItem(
			keyName = "trackFishing",
			name = "Track Fishing",
			description = "Track items gained from Fishing",
			position = 1
	)
	default boolean trackFishing() { return true; }

	@ConfigItem(
			keyName = "trackMining",
			name = "Track Mining",
			description = "Track items gained from Mining",
			position = 2
	)
	default boolean trackMining() { return true; }

	@ConfigItem(
			keyName = "trackWoodcutting",
			name = "Track Woodcutting",
			description = "Track items gained from Woodcutting",
			position = 3
	)
	default boolean trackWoodcutting() { return true; }

	@ConfigItem(
			keyName = "trackFarming",
			name = "Track Farming",
			description = "Track items gained from Farming",
			position = 4
	)
	default boolean trackFarming() { return true; }

	@ConfigItem(
			keyName = "trackHunter",
			name = "Track Hunter",
			description = "Track items gained from Hunter",
			position = 5
	)
	default boolean trackHunter() { return true; }

	// -----------------------------------------------------------------------
	// Ignore List
	// -----------------------------------------------------------------------
	@ConfigSection(
			name = "Ignore List",
			description = "Items to never track (comma-separated item IDs)",
			position = 6,
			closedByDefault = true
	)
	String ignoreSection = "ignoreSection";

	@ConfigItem(
			keyName = "ignoreItemIds",
			name = "Ignored Item IDs",
			description = "Comma-separated list of item IDs to never track (e.g. 526,995,1521)",
			section = ignoreSection,
			position = 0
	)
	default String ignoreItemIds() { return ""; }

	@ConfigItem(
			keyName = "ignoreCoins",
			name = "Ignore Coins",
			description = "Never track coins (item ID 995)",
			section = ignoreSection,
			position = 1
	)
	default boolean ignoreCoins() { return true; }

	@ConfigItem(
			keyName = "ignoreBirdNests",
			name = "Ignore Bird Nests",
			description = "Ignore bird nests (opened/random drops) — they are not from skilling directly",
			section = ignoreSection,
			position = 2
	)
	default boolean ignoreBirdNests() { return false; }

	// -----------------------------------------------------------------------
	// Session Timer
	// -----------------------------------------------------------------------
	@ConfigSection(
			name = "Session Timer",
			description = "Control the session timer",
			position = 7
	)
	String timerSection = "timerSection";

	@ConfigItem(
			keyName = "enableTimer",
			name = "Enable Timer",
			description = "Turn the session timer on/off. Timer auto-pauses on logout/hop",
			section = timerSection,
			position = 0
	)
	default boolean enableTimer() { return true; }

	@ConfigItem(
			keyName = "pauseTimer",
			name = "Pause Timer",
			description = "Manually pause the session timer",
			section = timerSection,
			position = 1
	)
	default boolean pauseTimer() { return false; }

	@ConfigItem(
			keyName = "resetTimer",
			name = "Reset Timer",
			description = "Reset the session timer to 00:00:00",
			section = timerSection,
			position = 2
	)
	default boolean resetTimer() { return false; }
}