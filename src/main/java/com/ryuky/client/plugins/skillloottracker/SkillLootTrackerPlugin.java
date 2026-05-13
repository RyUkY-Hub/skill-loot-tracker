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
package net.runelite.client.plugins.skillloottracker;

import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
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
import java.util.Set;

@Slf4j
@PluginDescriptor(
		name = "Skilling Loot Tracker",
		description = "Tracks loot gained from skilling activities",
		tags = {"skill", "loot", "tracker", "fishing", "mining", "woodcutting", "farming", "hunter"}
)
public class SkillLootTrackerPlugin extends Plugin
{
	private static final String DATA_KEY = "lootData";
	private static final String ACCUMULATED_TIME_KEY = "accumulatedTimeMs";

	private static final Set<Integer> LOG_IDS = ImmutableSet.of(
			1511, 2862, 1521, 1519, 6333, 10810, 1517, 6332, 1515, 1513,
			19669, 21600, 24656, 32910, 32907, 32904, 32902, 25088
	);

	private static final Set<Integer> FISH_IDS = ImmutableSet.of(
			317, 321, 327, 3150, 345, 353, 335, 341, 349, 3379, 331, 359,
			10138, 5001, 377, 363, 371, 2162, 7944, 3142, 389, 395, 383,
			13439, 17797, 11328, 11330, 11332, 21495, 21293, 40166, 31553,
			31561, 40167, 40168, 27680, 2136, 1861, 2878, 7566
	);

	private static final Set<Integer> ORE_IDS = ImmutableSet.of(
			1436, 7936, 434, 436, 438, 3211, 668, 440, 2892, 442, 453, 23491,
			444, 6971, 6973, 6975, 6977, 1625, 21905, 6983, 6979, 6981, 447,
			13356, 449, 451, 21347, 9631, 21552, 31716, 25527
	);

	private static final Set<Integer> FARMING_IDS = ImmutableSet.of(
			1942, 1957, 1965, 1982, 5986, 5504, 5982, 401, 199, 201, 203, 205,
			207, 209, 211, 213, 215, 217, 219, 2485, 267, 269, 249, 251, 253,
			255, 257, 2998, 259, 261, 263, 3000, 265, 2481, 1951, 753, 2120,
			247, 239, 6018, 1955, 1963, 2108, 5970, 2114, 5972, 5974, 22929,
			6006, 5994, 5996, 5931, 5998, 6000, 6002, 21622, 6010, 6012, 6014,
			6016, 24730, 24632, 2460, 225, 6004, 21504, 1975, 2970, 23534,
			23246, 23248, 23250, 23951, 23953, 23955
	);

	private static final Set<Integer> HUNTER_IDS = ImmutableSet.of(
			10033, 10034, 10031, 28801, 9978, 10092, 10132, 10113, 10107, 10109,
			10112, 10114, 10115, 10117, 10119, 10121, 10123, 10125, 10127, 10129,
			10131, 10133, 10137, 10139, 10149, 10148, 10147, 10146, 10020, 10018,
			10016, 10014, 28867, 28868, 11238, 11240, 11242, 11244, 11246, 11248,
			11250, 11252, 11254, 11256, 11258, 11260
	);

	private static final Set<Integer> WOODCUTTING_ANIMATIONS = ImmutableSet.of(
			AnimationID.WOODCUTTING_BRONZE, AnimationID.WOODCUTTING_IRON, AnimationID.WOODCUTTING_STEEL,
			AnimationID.WOODCUTTING_BLACK, AnimationID.WOODCUTTING_MITHRIL, AnimationID.WOODCUTTING_ADAMANT,
			AnimationID.WOODCUTTING_RUNE, AnimationID.WOODCUTTING_DRAGON, 8303, 8324,
			AnimationID.WOODCUTTING_INFERNAL, 4961, 2117, AnimationID.WOODCUTTING_CRYSTAL,
			8707, AnimationID.WOODCUTTING_3A_AXE, AnimationID.WOODCUTTING_TRAILBLAZER,
			2116, 2120, 2121, 2122, 2123, 2124, 7263, 7264
	);

	private static final Set<Integer> MINING_ANIMATIONS = ImmutableSet.of(
			AnimationID.MINING_BRONZE_PICKAXE, AnimationID.MINING_IRON_PICKAXE, AnimationID.MINING_STEEL_PICKAXE,
			AnimationID.MINING_BLACK_PICKAXE, AnimationID.MINING_MITHRIL_PICKAXE, AnimationID.MINING_ADAMANT_PICKAXE,
			AnimationID.MINING_RUNE_PICKAXE, AnimationID.MINING_DRAGON_PICKAXE, AnimationID.MINING_DRAGON_PICKAXE_UPGRADED,
			AnimationID.MINING_DRAGON_PICKAXE_OR, 6758, 8345, AnimationID.MINING_INFERNAL_PICKAXE, 4481, 4483,
			AnimationID.MINING_CRYSTAL_PICKAXE, 8887, AnimationID.MINING_3A_PICKAXE, AnimationID.MINING_TRAILBLAZER_PICKAXE,
			7325, 6757, 6759, 6760, 6761, 6762
	);

	private static final Set<Integer> FISHING_ANIMATIONS = ImmutableSet.of(
			618, 619, 620, 621, 622, 623, 624, 818, 819, 832, 833, 834, 922, 5108,
			5208, 5210, 5211, 5212, 5213, 5214, 5215, 5216, 5217, 5218, 6200, 6201,
			6202, 6236, 6705, 6707, 6709, 7401, 7402, 8288, 8289, 8328, 8329, 8331,
			8332, 10018, 10019, 10223, 12489, 12490, 12491, 12492, 12493
	);

	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Inject private ClientToolbar clientToolbar;
	@Inject private ItemManager itemManager;
	@Inject private SkillLootTrackerConfig config;
	@Inject private OverlayManager overlayManager;
	@Inject private SkillLootTrackerOverlay overlay;
	@Inject private ConfigManager configManager;
	@Inject private Gson gson;

	private SkillLootTrackerPanel panel;
	private NavigationButton navButton;

	private final Map<String, Map<Integer, Integer>> lootPerSkill = new HashMap<>();
	private final Map<Integer, Integer> lastInventory = new HashMap<>();

	private volatile boolean resetInProgress = false;
	private boolean dataLoaded = false;

	private boolean sessionActive = false;
	private Instant sessionStart = Instant.now();
	private Duration accumulatedTime = Duration.ZERO;

	private String lastSkillAction = null;
	private long lastSkillActionTime = 0L;
	private static final long SKILL_WINDOW_MS = 3500;

	@Provides
	SkillLootTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SkillLootTrackerConfig.class);
	}

	@Override
	protected void startUp()
	{
		panel = injector.getInstance(SkillLootTrackerPanel.class);
		panel.init(this::resetAll, this::resetCategory);

		navButton = NavigationButton.builder()
				.tooltip("Loot Tracker Skilling")
				.icon(ImageUtil.loadImageResource(getClass(), "skillingloot-icon.png"))
				.priority(5)
				.panel(panel)
				.build();

		clientToolbar.addNavigation(navButton);
		overlayManager.add(overlay);
	}

	@Override
	protected void shutDown()
	{
		pauseSession();
		saveData();
		clientToolbar.removeNavigation(navButton);
		overlayManager.remove(overlay);
		panel.shutdown();
		lootPerSkill.clear();
		lastInventory.clear();
		dataLoaded = false;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN && !dataLoaded)
		{
			clientThread.invokeLater(this::loadData);
		}

		switch (event.getGameState())
		{
			case LOGGED_IN:
				if (config.enableTimer()) resumeSession();
				break;

			case HOPPING:
			case LOGGING_IN:
			case LOGIN_SCREEN:
			case CONNECTION_LOST:
				pauseSession();
				break;
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals("skillloottracker")) return;

		switch (event.getKey())
		{
			case "enableTimer":
				if (config.enableTimer()) resumeSession();
				else pauseSession();
				break;
			case "pauseTimer":
				if (config.pauseTimer()) pauseSession();
				else if (config.enableTimer()) resumeSession();
				break;
			case "resetTimer":
				if (config.resetTimer())
				{
					accumulatedTime = Duration.ZERO;
					sessionStart = Instant.now();
					configManager.setConfiguration("skillloottracker", "resetTimer", false);
					log.debug("Session timer reset via config");
				}
				break;
		}
	}

	private void resumeSession()
	{
		if (!sessionActive && dataLoaded && config.enableTimer() && !config.pauseTimer())
		{
			sessionActive = true;
			sessionStart = Instant.now();
			log.debug("Session timer resumed");
		}
	}

	private void pauseSession()
	{
		if (sessionActive)
		{
			accumulatedTime = accumulatedTime.plus(Duration.between(sessionStart, Instant.now()));
			sessionActive = false;
			log.debug("Session timer paused");
		}
	}

	private void loadData()
	{
		if (dataLoaded) return;

		try
		{
			String json = configManager.getConfiguration("skillloottracker", DATA_KEY);
			if (json != null && !json.isBlank())
			{
				Type type = new TypeToken<Map<String, Map<Integer, Integer>>>() {}.getType();
				Map<String, Map<Integer, Integer>> loaded = gson.fromJson(json, type);
				if (loaded != null)
				{
					lootPerSkill.putAll(loaded);
				}
			}

			Long savedMs = configManager.getConfiguration("skillloottracker", ACCUMULATED_TIME_KEY, Long.class);
			accumulatedTime = savedMs != null ? Duration.ofMillis(savedMs) : Duration.ZERO;

			lootPerSkill.forEach((category, items) -> {
				items.forEach((itemId, qty) -> {
					int geValue = itemManager.getItemPrice(itemId) * qty;
					panel.updateLoot(itemId, qty, geValue, category);
				});
			});

			dataLoaded = true;
			if (config.enableTimer() && !config.pauseTimer()) resumeSession();
			log.info("Skill Loot Tracker data loaded");
		}
		catch (Exception e)
		{
			log.warn("Failed to load skill loot data", e);
			accumulatedTime = Duration.ZERO;
			dataLoaded = true;
		}
	}

	private void saveData()
	{
		try
		{
			String json = gson.toJson(lootPerSkill);
			configManager.setConfiguration("skillloottracker", DATA_KEY, json);
			configManager.setConfiguration("skillloottracker", ACCUMULATED_TIME_KEY, getCurrentSessionDuration().toMillis());
		}
		catch (Exception e)
		{
			log.warn("Failed to save loot data", e);
		}
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		if (event.getActor() != client.getLocalPlayer()) return;
		updateLastSkillAction();
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		updateLastSkillAction();
	}

	private void updateLastSkillAction()
	{
		int anim = client.getLocalPlayer().getAnimation();
		if (anim == -1) return;

		if (WOODCUTTING_ANIMATIONS.contains(anim))
		{
			lastSkillAction = "Woodcutting";
			lastSkillActionTime = System.currentTimeMillis();
		}
		else if (MINING_ANIMATIONS.contains(anim))
		{
			lastSkillAction = "Mining";
			lastSkillActionTime = System.currentTimeMillis();
		}
		else if (FISHING_ANIMATIONS.contains(anim))
		{
			lastSkillAction = "Fishing";
			lastSkillActionTime = System.currentTimeMillis();
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.INVENTORY.getId()) return;

		ItemContainer inventory = event.getItemContainer();
		Map<Integer, Integer> currentInventory = new HashMap<>();

		for (Item item : inventory.getItems())
		{
			if (item.getId() > 0)
			{
				currentInventory.merge(item.getId(), item.getQuantity(), Integer::sum);
			}
		}

		if (resetInProgress)
		{
			lastInventory.clear();
			lastInventory.putAll(currentInventory);
			return;
		}

		for (Map.Entry<Integer, Integer> entry : currentInventory.entrySet())
		{
			int itemId = entry.getKey();
			int currentQty = entry.getValue();
			int lastQty = lastInventory.getOrDefault(itemId, 0);

			if (currentQty > lastQty)
			{
				int diff = currentQty - lastQty;
				String category = getCategory(itemId);
				if (category != null && shouldTrackItem(category))
				{
					trackLoot(category, itemId, diff);
				}
			}
		}

		lastInventory.clear();
		lastInventory.putAll(currentInventory);
	}

	private boolean shouldTrackItem(String category)
	{
		long now = System.currentTimeMillis();
		boolean recentSkillAction = (now - lastSkillActionTime) < SKILL_WINDOW_MS;

		switch (category)
		{
			case "Mining":      return recentSkillAction && "Mining".equals(lastSkillAction) && config.trackMining();
			case "Fishing":     return recentSkillAction && "Fishing".equals(lastSkillAction) && config.trackFishing();
			case "Woodcutting": return recentSkillAction && "Woodcutting".equals(lastSkillAction) && config.trackWoodcutting();
			case "Farming":     return config.trackFarming();
			case "Hunter":      return config.trackHunter();
			default: return false;
		}
	}

	private void trackLoot(String category, int itemId, int qty)
	{
		Map<Integer, Integer> categoryLoot = lootPerSkill.computeIfAbsent(category, k -> new HashMap<>());
		int newTotal = categoryLoot.merge(itemId, qty, Integer::sum);
		int geValue = itemManager.getItemPrice(itemId) * qty;

		panel.updateLoot(itemId, newTotal, geValue, category);

		if (newTotal % 5 == 0 || newTotal <= 5)
			saveData();
	}

	private String getCategory(int itemId)
	{
		if (LOG_IDS.contains(itemId)) return "Woodcutting";
		if (FISH_IDS.contains(itemId)) return "Fishing";
		if (ORE_IDS.contains(itemId)) return "Mining";
		if (FARMING_IDS.contains(itemId)) return "Farming";
		if (HUNTER_IDS.contains(itemId)) return "Hunter";
		return null;
	}
	private void resetAll()
	{
		resetInProgress = true;
		lootPerSkill.clear();
		lastInventory.clear();
		accumulatedTime = Duration.ZERO;
		sessionStart = Instant.now();
		panel.resetAll();

		clientThread.invoke(() -> {
			try
			{
				ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
				if (inventory != null)
				{
					for (Item item : inventory.getItems())
					{
						if (item.getId() > 0)
						{
							lastInventory.merge(item.getId(), item.getQuantity(), Integer::sum);
						}
					}
				}
			}
			finally
			{
				resetInProgress = false;
			}
		});
	}

	private void resetCategory(String category)
	{
		resetInProgress = true;
		lootPerSkill.remove(category);
		clientThread.invoke(() -> {
			try
			{
				ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
				if (inventory != null)
				{
					Set<Integer> categoryIds;
					switch (category)
					{
						case "Woodcutting": categoryIds = LOG_IDS; break;
						case "Fishing":     categoryIds = FISH_IDS; break;
						case "Mining":      categoryIds = ORE_IDS; break;
						case "Farming":     categoryIds = FARMING_IDS; break;
						case "Hunter":      categoryIds = HUNTER_IDS; break;
						default:            categoryIds = ImmutableSet.of();
					}
					lastInventory.keySet().removeIf(categoryIds::contains);

					for (Item item : inventory.getItems())
					{
						if (item.getId() > 0 && categoryIds.contains(item.getId()))
						{
							lastInventory.merge(item.getId(), item.getQuantity(), Integer::sum);
						}
					}
				}
			}
			finally
			{
				resetInProgress = false;
			}
		});

		if (lootPerSkill.isEmpty())
		{
			accumulatedTime = Duration.ZERO;
			sessionStart = Instant.now();
		}
	}

	private Duration getCurrentSessionDuration()
	{
		if (sessionActive)
		{
			return accumulatedTime.plus(Duration.between(sessionStart, Instant.now()));
		}
		return accumulatedTime;
	}

	public String getSessionTotalValueFormatted()
	{
		long total = lootPerSkill.values().stream()
				.flatMap(m -> m.entrySet().stream())
				.mapToLong(e -> (long) itemManager.getItemPrice(e.getKey()) * e.getValue())
				.sum();
		return QuantityFormatter.quantityToStackSize(total) + " gp";
	}

	public String getSessionHaValueFormatted()
	{
		long total = lootPerSkill.values().stream()
				.flatMap(m -> m.entrySet().stream())
				.mapToLong(e -> {
					ItemComposition comp = itemManager.getItemComposition(e.getKey());
					return (long) comp.getHaPrice() * e.getValue();
				})
				.sum();
		return QuantityFormatter.quantityToStackSize(total) + " gp";
	}

	public long getSessionTotalValueRaw()
	{
		return lootPerSkill.values().stream()
				.flatMap(m -> m.entrySet().stream())
				.mapToLong(e -> (long) itemManager.getItemPrice(e.getKey()) * e.getValue())
				.sum();
	}

	public String getGpPerHourFormatted()
	{
		Duration d = getCurrentSessionDuration();
		if (d.getSeconds() < 6) return "---";

		long total = getSessionTotalValueRaw();
		double hours = d.getSeconds() / 3600.0;
		long gpPerHr = (long) (total / hours);
		return QuantityFormatter.quantityToStackSize(gpPerHr) + " gp/hr";
	}

	public String getSessionTimeFormatted()
	{
		Duration d = getCurrentSessionDuration();
		long seconds = d.getSeconds();
		long hours = seconds / 3600;
		long minutes = (seconds % 3600) / 60;
		long secs = seconds % 60;
		return String.format("%02d:%02d:%02d", hours, minutes, secs);
	}

	public boolean isTimerActive()
	{
		return sessionActive;
	}
}
