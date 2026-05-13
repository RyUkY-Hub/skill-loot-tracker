package com.ryuky.client.plugins.skillloottracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

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
}