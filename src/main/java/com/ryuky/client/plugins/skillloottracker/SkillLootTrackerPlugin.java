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

import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Injector;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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

	private static final Set<Integer> LOG_IDS = ImmutableSet.<Integer>builder()
			.add(1511, 2862, 1521, 1519, 6333, 10810, 1517, 6332, 1515, 1513, 19669, 21600, 24656, 32910, 32907, 32904, 32902, 25088, 29310)
			.add(5070, 5071, 5072, 5073, 5074, 5075, 23959, 23933, 13579, 20013, 19668, 9017, 9009, 9011, 13322, 23772)
			.add(29309, 29083, 29317, 29318, 29319, 29320, 29321, 29322, 29323, 29324, 29325, 29326, 29327, 29328, 29329, 29330, 29331)
			.add(2158, 6032, 6034)
			.build();

	private static final Set<Integer> FISH_IDS = ImmutableSet.<Integer>builder()
			.add(317, 321, 327, 3150, 345, 353, 335, 341, 349, 3379, 331, 359, 10138, 5001, 377, 363, 371, 2162, 7944, 3142, 389, 395, 383)
			.add(13439, 17797, 11328, 11330, 11332, 21495, 21293, 40166, 31553, 31561, 40167, 40168, 27680, 2136, 1861, 2878, 7566, 21356, 21358)
			.add(11934, 22815, 22814, 22816, 13648, 13649, 13650, 13651, 405, 13320, 23772, 21622, 25563, 25564, 25569, 13356, 22821, 22822, 22823)
			.add(401, 1781, 1625, 1627, 1629, 1623, 1621, 1619, 1617, 19668, 9007, 9009, 9011, 7936)
			.build();

	private static final Set<Integer> ORE_IDS = ImmutableSet.<Integer>builder()
			.add(1436, 7936, 434, 436, 438, 3211, 668, 440, 2892, 442, 453, 23491, 444, 6971, 6973, 6975, 6977, 21905, 6983, 6979, 6981, 447)
			.add(13356, 449, 451, 21347, 9631, 21552, 31716, 1625, 1627, 1629, 1623, 1621, 1619, 1617, 4483, 4484, 4485, 4486, 13321)
			.add(25527, 25528, 21622, 23996, 23997, 19668, 13421, 9017, 9009, 9011, 25627, 25628, 24705, 11337, 12600, 12601)
			.build();

	private static final Set<Integer> FARMING_IDS = ImmutableSet.<Integer>builder()
			.add(199, 201, 203, 205, 207, 209, 211, 213, 215, 217, 219, 2485, 267, 269, 249, 251, 253, 255, 257, 2998, 259, 261, 263, 3000, 265, 2481)
			.add(1942, 1957, 1965, 1982, 5986, 5504, 1951, 753, 2120, 247, 239, 1955, 1963, 2108, 5970, 2114, 5972, 5974, 22929, 6006)
			.add(6004, 5996, 5931, 5998, 6000, 6002, 6010, 6012, 6014, 6016, 6018, 5982, 24730, 24632, 2460, 225)
			.add(21504, 1975, 2970, 23534, 23246, 23248, 23250, 23951, 23953, 23955, 3138, 5980, 5981, 21528)
			.add(6032, 6034, 21483, 22994, 6036, 5070, 5071, 5072, 5073, 5074, 5075, 23772, 24589, 24590, 24591, 24592, 24593)
			.add(22875, 22877, 22879, 25170, 25171, 25172, 25173, 25174, 21490, 25595, 25379, 6055, 6038, 21622)
			.build();

	private static final Set<Integer> HUNTER_IDS = ImmutableSet.<Integer>builder()
			.add(10033, 10034, 10031, 28801, 28866, 29158, 29146, 10146, 10147, 10148, 10149, 10150)
			.add(11238, 11240, 11242, 11244, 11246, 11248, 11250, 11252, 11254, 11256, 11258, 11260, 11262, 11264, 11266, 11268, 11270, 11272, 11274, 11276)
			.add(19732, 28917, 29312, 9976, 9977, 9978, 10092, 10111, 10118, 10120, 10122, 10124, 10126, 10128, 10130, 10135, 10136, 28867, 28868)
			.add(526, 9953, 10091, 10093, 10094, 10095, 10096, 22795, 22798, 2876, 2878, 2880, 2882)
			.add(21514, 21515, 21516, 21517, 21518, 21519, 21520)
			.add(10008, 10009, 10010, 10011, 10012, 10013, 10014, 10015, 10016, 10017, 10018, 10019, 10020, 10021, 10022, 10023, 10024, 10025, 10026, 10027)
			.add(10028, 10029, 10030, 10032, 10035, 10036, 10037, 11267, 10085, 10086, 10087, 10088, 10089, 10090)
			.add(25563, 25564, 25569, 13356, 13323, 10107, 10109, 10112, 10113, 10114, 10115, 10117, 10119, 10121, 10123, 10125, 10127, 10129, 10132, 10133, 10137, 10139)
			.build();

	private static final Set<Integer> WOODCUTTING_ANIMATIONS = ImmutableSet.<Integer>builder()
			.add(AnimationID.WOODCUTTING_BRONZE, AnimationID.WOODCUTTING_IRON, AnimationID.WOODCUTTING_STEEL)
			.add(AnimationID.WOODCUTTING_BLACK, AnimationID.WOODCUTTING_MITHRIL, AnimationID.WOODCUTTING_ADAMANT)
			.add(AnimationID.WOODCUTTING_RUNE, AnimationID.WOODCUTTING_DRAGON, 8303, 8324)
			.add(AnimationID.WOODCUTTING_INFERNAL, 4961, 2117, AnimationID.WOODCUTTING_CRYSTAL)
			.add(8707, AnimationID.WOODCUTTING_3A_AXE, AnimationID.WOODCUTTING_TRAILBLAZER)
			.add(2116, 2120, 2121, 2122, 2123, 2124, 7263, 7264)
			.build();

	private static final Set<Integer> MINING_ANIMATIONS = ImmutableSet.<Integer>builder()
			.add(AnimationID.MINING_BRONZE_PICKAXE, AnimationID.MINING_IRON_PICKAXE, AnimationID.MINING_STEEL_PICKAXE)
			.add(AnimationID.MINING_BLACK_PICKAXE, AnimationID.MINING_MITHRIL_PICKAXE, AnimationID.MINING_ADAMANT_PICKAXE)
			.add(AnimationID.MINING_RUNE_PICKAXE, AnimationID.MINING_DRAGON_PICKAXE, AnimationID.MINING_DRAGON_PICKAXE_UPGRADED)
			.add(AnimationID.MINING_DRAGON_PICKAXE_OR, 6758, 8345, AnimationID.MINING_INFERNAL_PICKAXE, 4481, 4483)
			.add(AnimationID.MINING_CRYSTAL_PICKAXE, 8887, AnimationID.MINING_3A_PICKAXE, AnimationID.MINING_TRAILBLAZER_PICKAXE)
			.add(7325, 6757, 6759, 6760, 6761, 6762)
			.build();

	private static final Set<Integer> FISHING_ANIMATIONS = ImmutableSet.<Integer>builder()
			.add(618, 619, 620, 621, 622, 623, 624, 818, 819, 832, 833, 834, 922, 5108)
			.add(5208, 5210, 5211, 5212, 5213, 5214, 5215, 5216, 5217, 5218, 6200, 6201)
			.add(6202, 6236, 6705, 6707, 6709, 7401, 7402, 8288, 8289, 8328, 8329, 8331)
			.add(8332, 10018, 10019, 10223, 12489, 12490, 12491, 12492, 12493)
			.build();

	private static final Set<Integer> FARMING_ANIMATIONS = ImmutableSet.<Integer>builder()
			.add(830, 2272, 2273, 2275, 2281, 2282, 2286, 2291, 2292, 2293, 2294)
			.add(8304, 8305, 8306, 8525, 8526, 8527, 8548)
			.build();

	private static final Set<Integer> HUNTER_ANIMATIONS = ImmutableSet.<Integer>builder()
			.add(5208, 5209, 5210, 5211, 5212, 5247, 5248, 5249, 5250, 5255, 5256)
			.add(5257, 6605, 6606, 7171, 7172, 7173, 8291, 8292, 8327)
			.build();

	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Inject private ClientToolbar clientToolbar;
	@Inject private ItemManager itemManager;
	@Inject private SkillLootTrackerConfig config;
	@Inject private OverlayManager overlayManager;
	@Inject private SkillLootTrackerOverlay overlay;
	@Inject private ConfigManager configManager;
	@Inject private Gson gson;
	@Inject private Injector injector;

	private SkillLootTrackerPanel panel;
	private NavigationButton navButton;

	private final Map<String, Map<Integer, Integer>> lootPerSkill = new ConcurrentHashMap<>();
	private final Map<Integer, Integer> lastInventory = new HashMap<>();

	private final AtomicBoolean resetInProgress = new AtomicBoolean(false);
	private final AtomicBoolean dataLoaded = new AtomicBoolean(false);

	private volatile boolean sessionActive = false;
	private volatile Instant sessionStart = Instant.now();
	private volatile Duration accumulatedTime = Duration.ZERO;

	private volatile String lastSkillAction = null;
	private volatile long lastSkillActionTime = 0L;
	private static final long SKILL_WINDOW_MS = 5000;
	private volatile long sessionHaValue = 0L;
	private volatile long cachedTotalGe = 0L;
	private volatile long cachedTotalHa = 0L;

	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

	@Provides
	SkillLootTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SkillLootTrackerConfig.class);
	}

	@Override
	protected void startUp()
	{
		panel = injector.getInstance(SkillLootTrackerPanel.class);
		panel.init(this::resetAll, this::resetCategory, this::resetTimer);

		navButton = NavigationButton.builder()
				.tooltip("Loot Tracker Skilling")
				.icon(ImageUtil.loadImageResource(getClass(), "skillingloot-icon.png"))
				.priority(5)
				.panel(panel)
				.build();

		clientToolbar.addNavigation(navButton);
		overlayManager.add(overlay);

		scheduler.scheduleAtFixedRate(() -> {
			if (panel!= null)
			{
				panel.setTimerText(getSessionTimeFormatted());
			}
		}, 0, 1, TimeUnit.SECONDS);

		if (client.getGameState() == GameState.LOGGED_IN &&!dataLoaded.get())
		{
			clientThread.invokeLater(this::loadData);
		}
	}

	@Override
	protected void shutDown()
	{
		pauseSession();
		saveData();
		clientToolbar.removeNavigation(navButton);
		overlayManager.remove(overlay);
		if (panel != null) panel.shutdown();
		scheduler.shutdownNow();
		lootPerSkill.clear();
		lastInventory.clear();
		dataLoaded.set(false);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN &&!dataLoaded.get())
		{
			clientThread.invokeLater(this::loadData);
		}

		switch (event.getGameState())
		{
			case LOGGED_IN:
				if (config.enableTimer() &&!config.pauseTimer()) resumeSession();
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
					resetTimer();
					configManager.setConfiguration("skillloottracker", "resetTimer", false);
				}
				break;
		}
	}

	private void resumeSession()
	{
		if (!sessionActive && dataLoaded.get() && config.enableTimer() &&!config.pauseTimer())
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

	private synchronized void loadData()
	{
		if (dataLoaded.get()) return;

		try
		{
			String json = configManager.getConfiguration("skillloottracker", DATA_KEY);
			if (json!= null &&!json.isBlank())
			{
				Type type = new TypeToken<Map<String, Map<Integer, Integer>>>() {}.getType();
				Map<String, Map<Integer, Integer>> loaded = gson.fromJson(json, type);
				if (loaded!= null)
				{
					loaded.forEach((k, v) -> lootPerSkill.put(k, new ConcurrentHashMap<>(v)));
				}
			}

			Long savedMs = configManager.getConfiguration("skillloottracker", ACCUMULATED_TIME_KEY, Long.class);
			accumulatedTime = savedMs!= null? Duration.ofMillis(savedMs) : Duration.ZERO;

			for (Map.Entry<String, Map<Integer, Integer>> catEntry : lootPerSkill.entrySet())
			{
				String category = catEntry.getKey();
				for (Map.Entry<Integer, Integer> itemEntry : catEntry.getValue().entrySet())
				{
					int itemId = itemEntry.getKey();
					int qty = itemEntry.getValue();
					ItemComposition comp = itemManager.getItemComposition(itemId);
					String itemName = comp.getName();
					long gePrice = itemManager.getItemPrice(itemId);
					long haPrice = comp.getHaPrice();
					panel.updateLoot(
							itemId,
							qty,
							QuantityFormatter.quantityToStackSize(gePrice),
							QuantityFormatter.quantityToStackSize(haPrice),
							category,
							itemName,
							gePrice,
							haPrice
					);
				}
			}
			updatePanelTotals();

			dataLoaded.set(true);
			if (config.enableTimer() &&!config.pauseTimer()) resumeSession();
			log.info("Skill Loot Tracker data loaded");
		}
		catch (Exception e)
		{
			log.warn("Failed to load skill loot data", e);
			accumulatedTime = Duration.ZERO;
			dataLoaded.set(true);
		}
	}

	private synchronized void saveData()
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
		if (event.getActor()!= client.getLocalPlayer()) return;
		updateLastSkillAction();
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		updateLastSkillAction();
	}

	private void updateLastSkillAction()
	{
		Player p = client.getLocalPlayer();
		if (p == null) return;
		int anim = p.getAnimation();
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
		else if (FARMING_ANIMATIONS.contains(anim))
		{
			lastSkillAction = "Farming";
			lastSkillActionTime = System.currentTimeMillis();
		}
		else if (HUNTER_ANIMATIONS.contains(anim))
		{
			lastSkillAction = "Hunter";
			lastSkillActionTime = System.currentTimeMillis();
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId()!= InventoryID.INVENTORY.getId()) return;

		ItemContainer inventory = event.getItemContainer();
		Map<Integer, Integer> currentInventory = new HashMap<>();

		for (Item item : inventory.getItems())
		{
			if (item.getId() > 0)
			{
				int canonicalId = itemManager.canonicalize(item.getId());
				currentInventory.merge(canonicalId, item.getQuantity(), Integer::sum);
			}
		}

		if (resetInProgress.get())
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
				if (category!= null && shouldTrackItem(category))
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
			case "Mining": return recentSkillAction && "Mining".equals(lastSkillAction) && config.trackMining();
			case "Fishing": return recentSkillAction && "Fishing".equals(lastSkillAction) && config.trackFishing();
			case "Woodcutting": return recentSkillAction && "Woodcutting".equals(lastSkillAction) && config.trackWoodcutting();
			case "Farming": return recentSkillAction && "Farming".equals(lastSkillAction) && config.trackFarming();
			case "Hunter": return recentSkillAction && "Hunter".equals(lastSkillAction) && config.trackHunter();
			default: return false;
		}
	}

	private void trackLoot(String category, int itemId, int qty)
	{
		Map<Integer, Integer> categoryLoot = lootPerSkill.computeIfAbsent(category, k -> new ConcurrentHashMap<>());
		int newTotal = categoryLoot.merge(itemId, qty, Integer::sum);

		clientThread.invoke(() -> {
			ItemComposition comp = itemManager.getItemComposition(itemId);
			String itemName = comp.getName();
			long gePrice = itemManager.getItemPrice(itemId);
			long haPrice = comp.getHaPrice();

			panel.updateLoot(
					itemId,
					newTotal,
					QuantityFormatter.quantityToStackSize(gePrice),
					QuantityFormatter.quantityToStackSize(haPrice),
					category,
					itemName,
					gePrice,
					haPrice
			);

			updatePanelTotals();
		});

		if (newTotal % 5 == 0 || newTotal <= 5)
			saveData();
	}

	private void updatePanelTotals()
	{
		long totalGe = 0L;
		long totalHa = 0L;
		for (Map<Integer, Integer> items : lootPerSkill.values())
		{
			for (Map.Entry<Integer, Integer> entry : items.entrySet())
			{
				int id = entry.getKey();
				int qty = entry.getValue();
				totalGe += (long) itemManager.getItemPrice(id) * qty;
				totalHa += (long) itemManager.getItemComposition(id).getHaPrice() * qty;
			}
		}
		cachedTotalGe = totalGe;
		cachedTotalHa = totalHa;
		sessionHaValue = totalHa;
		Duration d = getCurrentSessionDuration();
		String gpHrText = "Total per hr: 0/hr";
		if (d.getSeconds() > 6)
		{
			double hours = d.getSeconds() / 3600.0;
			long gpPerHr = (long) (totalGe / hours);
			gpHrText = "Total per hr: " + QuantityFormatter.formatNumber(gpPerHr) + "/hr";
		}

		panel.setTotalsText(gpHrText, "GE: " + QuantityFormatter.formatNumber(totalGe) + " gp", "HA: " + QuantityFormatter.formatNumber(totalHa) + " gp");
	}

	private String getCategory(int itemId)
	{
		boolean wc = LOG_IDS.contains(itemId);
		boolean fish = FISH_IDS.contains(itemId);
		boolean mine = ORE_IDS.contains(itemId);
		boolean farm = FARMING_IDS.contains(itemId);
		boolean hunt = HUNTER_IDS.contains(itemId);

		int matchCount = (wc ? 1 : 0) + (fish ? 1 : 0) + (mine ? 1 : 0) + (farm ? 1 : 0) + (hunt ? 1 : 0);

		if (matchCount == 0) return null;

		if (matchCount > 1 && lastSkillAction != null)
		{
			switch (lastSkillAction)
			{
				case "Woodcutting": return wc ? "Woodcutting" : null;
				case "Mining": return mine ? "Mining" : null;
				case "Fishing": return fish ? "Fishing" : null;
				case "Farming": return farm ? "Farming" : null;
				case "Hunter": return hunt ? "Hunter" : null;
			}
		}

		if (wc) return "Woodcutting";
		if (fish) return "Fishing";
		if (mine) return "Mining";
		if (farm) return "Farming";
		if (hunt) return "Hunter";
		return null;
	}

	private void resetAll()
	{
		resetInProgress.set(true);
		lootPerSkill.clear();
		lastInventory.clear();
		accumulatedTime = Duration.ZERO;
		sessionStart = Instant.now();
		cachedTotalGe = 0L;
		cachedTotalHa = 0L;
		sessionHaValue = 0L;
		panel.resetAll();

		clientThread.invoke(() -> {
			try
			{
				ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
				if (inventory!= null)
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
				resetInProgress.set(false);
			}
		});
	}

	private void resetCategory(String category)
	{
		resetInProgress.set(true);
		lootPerSkill.remove(category);
		clientThread.invoke(() -> {
			try
			{
				ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
				if (inventory!= null)
				{
					Set<Integer> categoryIds;
					switch (category)
					{
						case "Woodcutting": categoryIds = LOG_IDS; break;
						case "Fishing": categoryIds = FISH_IDS; break;
						case "Mining": categoryIds = ORE_IDS; break;
						case "Farming": categoryIds = FARMING_IDS; break;
						case "Hunter": categoryIds = HUNTER_IDS; break;
						default: categoryIds = ImmutableSet.of();
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
				updatePanelTotals();
			}
			finally
			{
				resetInProgress.set(false);
			}
		});
	}

	private void resetTimer()
	{
		accumulatedTime = Duration.ZERO;
		sessionStart = Instant.now();
		updatePanelTotals();
		log.debug("Session timer reset via panel button");
	}

	public Duration getCurrentSessionDuration()
	{
		if (sessionActive)
		{
			return accumulatedTime.plus(Duration.between(sessionStart, Instant.now()));
		}
		return accumulatedTime;
	}

	public String getSessionGeValueFormatted()
	{
		return QuantityFormatter.quantityToStackSize(cachedTotalGe) + " gp";
	}

	public String getSessionHaValueFormatted()
	{
		return QuantityFormatter.quantityToStackSize(cachedTotalHa) + " gp";
	}

	public String getGpPerHourFormatted()
	{
		Duration d = getCurrentSessionDuration();
		if (d.getSeconds() < 6) return "---";

		double hours = d.getSeconds() / 3600.0;
		long gpPerHr = (long) (cachedTotalGe / hours);
		return QuantityFormatter.quantityToStackSize(gpPerHr) + " gp/hr";
	}

	public String getSessionHaPerHourFormatted()
	{
		Duration d = getCurrentSessionDuration();
		if (d.getSeconds() < 6 || cachedTotalHa <= 0) {
			return "--- gp/hr";
		}

		double hours = d.getSeconds() / 3600.0;
		long haPerHr = (long) (cachedTotalHa / hours);
		return QuantityFormatter.quantityToStackSize(haPerHr) + " gp/hr";
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

	private long getSessionTotalValueRaw()
	{
		return cachedTotalGe;
	}

	public boolean isTimerActive()
	{
		return sessionActive;
	}
}