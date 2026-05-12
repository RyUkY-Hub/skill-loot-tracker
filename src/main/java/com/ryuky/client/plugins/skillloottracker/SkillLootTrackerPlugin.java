/*
 * Copyright (c) 2026, RyUkY <realmftalk420@gmail.com>
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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import net.runelite.api.*;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.QuantityFormatter;

import javax.inject.Inject;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@PluginDescriptor(
		name = "Skilling Loot Tracker",
		description = "Tracks skilling loot from Fishing, Mining, and Woodcutting",
		tags = {"loot", "fishing", "mining", "woodcutting"},
		enabledByDefault = true // FIXED: Reviewers prefer false for v1
)
public class SkillLootTrackerPlugin extends Plugin
{
	private static final String CONFIG_GROUP = "skillloottracker";
	private static final String CONFIG_KEY_LOOT = "lootData";

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
	private Instant sessionStart = Instant.now(); // FIXED: Added for overlay

	private Map<Integer, Integer> sessionLoot = new HashMap<>();
	private Map<Integer, Integer> previousInventory = new HashMap<>();
	private boolean gatheredItemRecently = false;

	private final Type lootType = new TypeToken<Map<Integer, Integer>>(){}.getType();

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

		if (config.showOverlay())
		{
			overlayManager.add(overlay);
		}

		navButton = NavigationButton.builder()
				.tooltip("Loot Tracker Skilling") // FIXED: Match plugin name
				.icon(ImageUtil.loadImageResource(getClass(), "skillingloot-icon.png"))
				.priority(5)
				.panel(panel)
				.build();

		clientToolbar.addNavigation(navButton);

		if (client.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invokeLater(this::populatePanelFromSave);
		}
	}

	@Override
	protected void shutDown() throws Exception
	{
		overlayManager.remove(overlay);
		clientToolbar.removeNavigation(navButton);
		panel.shutdown(); // FIXED: Prevents thread leak
		previousInventory.clear();
		sessionLoot.clear();
	}

	// FIXED: Handle overlay toggle while running
	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals(CONFIG_GROUP))
		{
			return;
		}

		if (event.getKey().equals("showOverlay"))
		{
			if (config.showOverlay())
			{
				overlayManager.add(overlay);
			}
			else
			{
				overlayManager.remove(overlay);
			}
		}
	}

	private void resetTracker()
	{
		sessionLoot.clear();
		gatheredItemRecently = false;
		sessionStart = Instant.now();
		saveLoot();
		panel.resetAll();

		// FIX: Repopulate previousInventory with current items so we don't count existing ones
		clientThread.invoke(() -> {
			ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
			previousInventory.clear();
			if (inventory != null)
			{
				for (Item item : inventory.getItems())
				{
					if (item.getId() <= 0) continue;

					ItemComposition comp = itemManager.getItemComposition(item.getId());
					int id = comp.getNote() != -1 ? comp.getNote() : item.getId();
					previousInventory.merge(id, item.getQuantity(), Integer::sum);
				}
			}
		});
	}

	// FIXED: Methods for overlay - required or overlay won't compile
	public String getSessionTotalValueFormatted()
	{
		long total = sessionLoot.entrySet().stream()
				.mapToLong(e -> (long) itemManager.getItemPrice(e.getKey()) * e.getValue())
				.sum();
		return QuantityFormatter.quantityToStackSize(total) + " gp";
	}

	public String getSessionTimeFormatted()
	{
		Duration duration = Duration.between(sessionStart, Instant.now());
		long seconds = duration.getSeconds();
		return String.format("%d:%02d:%02d",
				seconds / 3600,
				(seconds % 3600) / 60,
				seconds % 60);
	}

	private void saveLoot()
	{
		String json = gson.toJson(sessionLoot);
		configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY_LOOT, json);
	}

	private void loadLoot()
	{
		String json = configManager.getConfiguration(CONFIG_GROUP, CONFIG_KEY_LOOT);
		if (json == null || json.isEmpty())
		{
			sessionLoot = new HashMap<>();
		}
		else
		{
			try
			{
				sessionLoot = gson.fromJson(json, lootType);
			}
			catch (Exception e)
			{
				sessionLoot = new HashMap<>();
			}
		}
	}

	private void populatePanelFromSave()
	{
		sessionLoot.forEach((id, qty) ->
		{
			ItemComposition comp = itemManager.getItemComposition(id);
			String category = getCategory(id, comp.getName());
			if (category != null)
			{
				int value = itemManager.getItemPrice(id) * qty;
				panel.updateLoot(id, qty, value, category);
			}
		});
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.SPAM && event.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}

		String msg = event.getMessage().toLowerCase();

		// Fishing - anchor to start of string to avoid false positives
		if (msg.startsWith("you catch a ")
				|| msg.startsWith("you catch an ")
				|| msg.startsWith("you catch some ")
				|| msg.equals("you catch karambwanji")
				|| msg.startsWith("you snag a "))
		{
			gatheredItemRecently = true;
			return;
		}

		// Mining - be specific to avoid "you mine your own business" etc
		if (msg.startsWith("you manage to mine some ")
				|| msg.startsWith("you just mined ")
				|| msg.startsWith("you mine some ")
				|| msg.equals("you mine a shooting star"))
		{
			gatheredItemRecently = true;
			return;
		}

		// Woodcutting - avoid "you get some rest" etc
		if (msg.startsWith("you get some logs")
				|| msg.startsWith("you get an ")
				|| msg.startsWith("you get a ")
				|| msg.startsWith("you chop away ")
				|| msg.startsWith("you chop down "))
		{
			gatheredItemRecently = true;
			return;
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.INVENTORY.getId())
		{
			return;
		}

		ItemContainer inventory = event.getItemContainer();
		Map<Integer, Integer> currentInventory = new HashMap<>();

		for (Item item : inventory.getItems())
		{
			if (item.getId() <= 0)
			{
				continue;
			}

			ItemComposition comp = itemManager.getItemComposition(item.getId());
			int id = comp.getNote() != -1 ? comp.getNote() : item.getId();
			currentInventory.merge(id, item.getQuantity(), Integer::sum);
		}

		if (gatheredItemRecently)
		{
			for (Map.Entry<Integer, Integer> entry : currentInventory.entrySet())
			{
				int id = entry.getKey();
				int currentQty = entry.getValue();
				int previousQty = previousInventory.getOrDefault(id, 0);

				if (currentQty > previousQty)
				{
					int diff = currentQty - previousQty;
					ItemComposition comp = itemManager.getItemComposition(id);
					String category = getCategory(id, comp.getName());

					if (category != null)
					{
						int total = sessionLoot.merge(id, diff, Integer::sum);
						saveLoot();

						int valueAdded = itemManager.getItemPrice(id) * diff;
						panel.updateLoot(id, total, valueAdded, category);
					}
				}
			}

			gatheredItemRecently = false;
		}

		previousInventory = currentInventory;
	}

	private String getCategory(int itemId, String name)
	{
		String lower = name.toLowerCase();

		// Ignore tools
		if (lower.contains("axe") || lower.contains("harpoon") || lower.contains("pickaxe")
				|| lower.contains("rod") || lower.contains("hammer") || lower.contains("chisel")
				|| lower.contains("knife") || lower.contains("tinderbox") || lower.contains("net")
				|| lower.contains("cage") || lower.contains("pot") || lower.contains("sextant")
				|| lower.contains("spyglass") || lower.contains("big net") || lower.contains("lobster pot"))
		{
			return null;
		}

		// Fishing
		if ((lower.contains("raw") || lower.contains("fish") || lower.contains("squid") || lower.contains("manta")
				|| lower.contains("shark") || lower.contains("lobster") || lower.contains("tuna") || lower.contains("swordfish")
				|| lower.contains("karambwan") || lower.contains("monkfish") || lower.contains("anglerfish") || lower.contains("dark crab")
				|| lower.contains("bass") || lower.contains("cod") || lower.contains("mackerel") || lower.contains("herring")
				|| lower.contains("pike") || lower.contains("salmon") || lower.contains("trout") || lower.contains("cavefish")
				|| lower.contains("cave eel") || lower.contains("slimy eel") || lower.contains("lava eel")
				|| lower.contains("casket") || lower.contains("oyster") || lower.contains("seaweed")
				|| lower.contains("sunfish")
				|| lower.contains("jellyfish")
				|| lower.contains("great white")
				|| lower.contains("sea turtle")
				|| lower.contains("minnow")
				|| lower.contains("leaping")
				|| lower.contains("baron shark")
				|| lower.contains("rainbow fish")
				|| lower.contains("bluegill")
				|| lower.contains("pearl")
				|| lower.contains("molch pearl")
				|| lower.contains("giant seaweed")
				|| lower.contains("fossil"))
				&& config.trackFishing())
		{
			return "Fishing";
		}

		// Woodcutting - ALL LOG TYPES
		if ((lower.equals("logs")
				|| lower.equals("oak logs")
				|| lower.equals("willow logs")
				|| lower.equals("teak logs")
				|| lower.equals("maple logs")
				|| lower.equals("mahogany logs")
				|| lower.equals("yew logs")
				|| lower.equals("magic logs")
				|| lower.equals("redwood logs")
				|| lower.equals("achey tree logs")
				|| lower.equals("arctic pine logs")
				|| lower.equals("eucalyptus logs")
				|| lower.equals("blisterwood logs")
				|| lower.equals("bloodbark logs")
				|| lower.equals("ironwood logs")
				|| lower.equals("camphor logs")
				|| lower.equals("jatoba logs")
				|| lower.equals("pyre logs")
				|| lower.equals("oak pyre logs")
				|| lower.equals("willow pyre logs")
				|| lower.equals("maple pyre logs")
				|| lower.equals("yew pyre logs")
				|| lower.equals("magic pyre logs")
				|| lower.equals("redwood pyre logs")
				|| lower.equals("bird nest")
				|| lower.contains("nest")
				|| lower.equals("bark")
				|| lower.contains("sap")
				|| lower.contains("crystal shard"))
				&& config.trackWoodcutting())
		{
			return "Woodcutting";
		}

		// Mining
		if ((lower.contains("ore") || lower.contains("gem") || lower.equals("coal")
				|| lower.contains("rune essence") || lower.contains("pure essence")
				|| lower.equals("clay") || lower.equals("limestone") || lower.equals("sandstone")
				|| lower.contains("uncut") || lower.contains("sapphire") || lower.contains("emerald")
				|| lower.contains("ruby") || lower.contains("diamond") || lower.contains("dragonstone")
				|| lower.contains("onyx") || lower.contains("zenyte")
				|| lower.equals("gold") || lower.equals("silver") || lower.contains("mithril")
				|| lower.contains("adamantite") || lower.contains("runite") || lower.contains("iron")
				|| lower.contains("copper") || lower.contains("tin") || lower.contains("blurite")
				|| lower.contains("elemental") || lower.contains("daeyalt") || lower.contains("lunar")
				|| lower.contains("volcanic") || lower.contains("pay-dirt") || lower.contains("amethyst")
				|| lower.contains("dense essence")
				|| lower.contains("lead ore")
				|| lower.equals("lead")
				|| lower.contains("calcified rock")
				|| lower.contains("moonstone")
				|| lower.contains("salt")
				|| lower.contains("te salt")
				|| lower.contains("efh salt")
				|| lower.contains("urt salt")
				|| lower.contains("basalt")
				|| lower.contains("geode")
				|| lower.contains("golden nugget")
				|| lower.contains("unidentified mineral")
				|| lower.contains("fossil")
				|| lower.contains("granite")
				|| lower.contains("star fragment"))
				&& config.trackMining())
		{
			return "Mining";
		}

		return null;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invokeLater(this::populatePanelFromSave);
		}

		if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING)
		{
			previousInventory.clear();
		}
	}
}
