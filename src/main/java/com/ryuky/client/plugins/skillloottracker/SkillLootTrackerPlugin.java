/*
 * Copyright (c) 2017, Steve <steve.rs.dev@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.Experience;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.xptracker.XpTrackerPlugin;
import net.runelite.client.task.Schedule;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

@PluginDescriptor(
		name = "Skilling Loot Tracker",
		description = "Tracks skilling loot and shows XP globes",
		tags = {"experience", "levels", "loot", "fishing", "mining", "woodcutting"},
		enabledByDefault = true
)
@PluginDependency(XpTrackerPlugin.class)
public class SkillLootTrackerPlugin extends Plugin
{
	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Inject private ClientToolbar clientToolbar;
	@Inject private SkillLootTrackerPanel panel;
	@Inject private ItemManager itemManager;
	@Inject private ConfigManager configManager;
	@Inject private SkillLootTrackerConfig config;
	@Inject private OverlayManager overlayManager;
	@Inject private SkillLootTrackerOverlay overlay;
	@Inject private Gson gson;

	private NavigationButton navButton;
	private Map<Integer, Integer> sessionLoot = new HashMap<>();
	private boolean gatheredItemRecently = false;
	private SkillLootTracker[] globeCache = new SkillLootTracker[Skill.values().length];

	@Getter
	private final List<SkillLootTracker> xpGlobes = new ArrayList<>();

	@Provides
	SkillLootTrackerConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SkillLootTrackerConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		panel.init(this::resetTracker);
		loadLoot();
		overlayManager.add(overlay);

		navButton = NavigationButton.builder()
				.tooltip("Skilling Loot Tracker")
				.icon(ImageUtil.loadImageResource(getClass(), "skillingloot-icon.png"))
				.priority(5)
				.panel(panel)
				.build();

		clientToolbar.addNavigation(navButton);

		clientThread.invokeLater(() ->
				sessionLoot.forEach((id, qty) ->
						panel.updateLoot(id, qty, itemManager.getItemPrice(id), getCategory(client.getItemDefinition(id).getName()))
				)
		);
	}

	@Override
	protected void shutDown() throws Exception
	{
		overlayManager.remove(overlay);
		clientToolbar.removeNavigation(navButton);
		xpGlobes.clear();
	}

	private void resetTracker()
	{
		sessionLoot.clear();
		saveLoot();
		panel.resetAll();
	}

	private void saveLoot()
	{
		configManager.setConfiguration("skillloottracker", "lootData", gson.toJson(sessionLoot));
	}

	private void loadLoot()
	{
		String json = configManager.getConfiguration("skillloottracker", "lootData");
		if (json != null && !json.isEmpty())
		{
			sessionLoot = gson.fromJson(json, new TypeToken<Map<Integer, Integer>>(){}.getType());
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		String msg = event.getMessage().toLowerCase();
		// Broaden the check to catch "You get some... logs" or "You catch a... fish"
		if (msg.contains("you catch") || msg.contains("you get some") || msg.contains("you mine") || msg.contains("you find"))
		{
			gatheredItemRecently = true;
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.INVENTORY.getId() || !gatheredItemRecently) return;

		ItemContainer inventory = event.getItemContainer();
		for (Item item : inventory.getItems())
		{
			int id = item.getId();
			if (id <= 0) continue;

			String name = client.getItemDefinition(id).getName().toLowerCase();
			String category = getCategory(name);

			if (category == null) continue;

			// FIX: Ignore tools explicitly
			if (name.contains("axe") || name.contains("harpoon") || name.contains("pickaxe")) continue;

			// FIX: Ensure Woodcutting only tracks logs and Fishing only tracks raw fish
			if (category.equals("Woodcutting") && !name.contains("logs")) continue;
			if (category.equals("Fishing") && !name.contains("raw")) continue;

			int total = sessionLoot.getOrDefault(id, 0) + 1;
			sessionLoot.put(id, total);
			saveLoot();
			panel.updateLoot(id, total, itemManager.getItemPrice(id), category);

			gatheredItemRecently = false; // Reset flag so we don't count everything in the bag
			break;
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged statChanged)
	{
		Skill skill = statChanged.getSkill();
		int currentXp = statChanged.getXp();
		int currentLevel = statChanged.getLevel();
		int skillIdx = skill.ordinal();
		SkillLootTracker cachedGlobe = globeCache[skillIdx];

		if (cachedGlobe != null && (cachedGlobe.getCurrentXp() >= currentXp)) return;

		if (currentLevel >= Experience.MAX_REAL_LEVEL) currentLevel = Experience.getLevelForXp(currentXp);

		if (cachedGlobe != null)
		{
			cachedGlobe.setCurrentXp(currentXp);
			cachedGlobe.setCurrentLevel(currentLevel);
			cachedGlobe.setTime(Instant.now());
			if (!xpGlobes.contains(cachedGlobe)) xpGlobes.add(cachedGlobe);
		}
		else
		{
			globeCache[skillIdx] = new SkillLootTracker(skill, currentXp, currentLevel, Instant.now());
		}
	}

	@Schedule(period = 1, unit = ChronoUnit.SECONDS)
	public void removeExpiredXpGlobes()
	{
		Instant expireTime = Instant.now().minusSeconds(config.xpOrbDuration());
		xpGlobes.removeIf(globe -> globe.getTime().isBefore(expireTime));
	}

	private String getCategory(String name)
	{
		name = name.toLowerCase();
		if ((name.contains("raw") || name.contains("fish")) && config.trackFishing()) return "Fishing";
		if (name.contains("logs") && config.trackWoodcutting()) return "Woodcutting";
		if ((name.contains("ore") || name.contains("gem")) && config.trackMining()) return "Mining";
		return null;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState().equals(net.runelite.api.GameState.LOGGING_IN))
		{
			xpGlobes.clear();
			globeCache = new SkillLootTracker[Skill.values().length];
		}
	}
}
