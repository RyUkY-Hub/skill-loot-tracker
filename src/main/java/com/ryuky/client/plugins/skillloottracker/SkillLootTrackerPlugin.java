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
import java.util.*;
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

	// -----------------------------------------------------------------------
	// Always-ignored item IDs (junk / non-loot that should never be tracked)
	// -----------------------------------------------------------------------
	// Coins, noted coins, platinum tokens, tokkul, numulite, trading sticks,
	// molten glass scraps, ashes, bones family, feathers, empty sacks, etc.
	private static final Set<Integer> ALWAYS_IGNORE_IDS = ImmutableSet.<Integer>builder()
			// Currency / misc junk
			.add(995)    // Coins
			.add(996)    // Coins (noted)
			.add(13204)  // Platinum token
			.add(6529)   // Tokkul
			.add(8007)   // Numulite
			.add(6306)   // Trading sticks
			.add(4067)   // Molten glass
			.add(1781)   // Bucket (shows up in some fishing inventories as a spawn)
			// Bones family — dropped by creatures, not skilling loot
			.add(526)    // Bones
			.add(528)    // Burnt bones
			.add(530)    // Bat bones
			.add(532)    // Big bones
			.add(534)    // Baby dragon bones
			.add(536)    // Dragon bones
			.add(3183)   // Jogre bones
			.add(3185)   // Zogre bones
			.add(4812)   // Wyvern bones
			.add(4830)   // Ourg bones
			.add(6812)   // Fayrg bones
			.add(6814)   // Raurg bones
			.add(6816)   // Dagannoth bones
			.add(22124)  // Lava dragon bones
			.add(22783)  // Superior dragon bones
			.add(11943)  // Drake bones
			.add(19277)  // Hydra bones
			.add(25775)  // Basilisk bones
			// Ashes
			.add(20001)  // Fiendish ashes
			.add(20003)  // Vile ashes
			.add(20005)  // Malicious ashes
			.add(20007)  // Abyssal ashes
			.add(20009)  // Infernal ashes
			// Common fishing junk
			.add(599)    // Raw cod (sometimes misfired)
			.add(1783)   // Seaweed (Fishing)
			.add(5991)   // Casket (Small)
			.add(5992)   // Casket (Medium)
			.add(5993)   // Casket (Large)
			// Other filler
			.add(6693)   // Agility arena ticket
			.add(8009)   // Mermaid's tear
			.build();

	// Bird nest IDs — toggled by config.ignoreBirdNests()
	private static final Set<Integer> BIRD_NEST_IDS = ImmutableSet.of(
			5070, 5071, 5072, 5073, 5074, 5075,  // seed/ring/empty nests
			22798, 22795,                          // Zilyana/dragith nests
			13653,                                 // Clue nest (easy)
			13654,                                 // Clue nest (medium)
			13655,                                 // Clue nest (hard)
			13656,                                 // Clue nest (elite)
			13657,                                 // Clue nest (master)
			11966                                  // Woodcutting guild nest (unused variant)
	);

	// -----------------------------------------------------------------------
	// Woodcutting logs
	// -----------------------------------------------------------------------
	private static final Set<Integer> LOG_IDS = ImmutableSet.<Integer>builder()
			// Standard logs
			.add(1511)   // Logs
			.add(1521)   // Oak logs
			.add(1519)   // Willow logs
			.add(1517)   // Teak logs
			.add(1515)   // Maple logs
			.add(1513)   // Mahogany logs
			.add(2862)   // Arctic pine logs
			.add(6333)   // Hollow logs (bark)
			.add(6332)   // Bark
			.add(10810)  // Blisterwood logs
			// High-level
			.add(1514)   // Yew logs — BUG FIX: was missing from original
			.add(1516)   // Magic logs — BUG FIX: was missing
			.add(6291)   // Achey tree logs
			// Redwood
			.add(19669)  // Redwood logs
			// Dramen/Lunar
			.add(771)    // Dramen branch
			.add(6020)   // Lunar logs
			// Special/quest adjacent
			.add(21600)  // Blue dragon scale (canoe)
			.add(24656)  // Sulliuscep cap
			// Fossil Island mushrooms
			.add(25088)  // Noxifer mushroom
			// Bladed / crystal
			.add(29310)  // Crystal shard (wc)
			// Forestry branch drops
			.add(32910)  // Branch (oak)
			.add(32907)  // Branch (willow)
			.add(32904)  // Branch (maple)
			.add(32902)  // Branch (yew)
			// Nests (handled separately via ignoreBirdNests toggle, kept here so the
			// category can still be recognised when the toggle is OFF)
			.add(5070).add(5071).add(5072).add(5073).add(5074).add(5075)
			.add(23959)  // Tangled nest (new Forestry)
			.add(23933)  // Roots
			.add(13579)  // Hollow trees — planks
			.add(20013)  // Mushroom spore
			.add(19668)  // Ancient essence
			.add(9017)   // Uncut jade
			.add(9009)   // Uncut sapphire
			.add(9011)   // Uncut emerald
			.add(13322)  // Uncut red topaz
			.add(23772)  // Rune essence
			// Forestry extras
			.add(29083)  // Blighted branch
			.add(29309).add(29317).add(29318).add(29319).add(29320).add(29321)
			.add(29322).add(29323).add(29324).add(29325).add(29326).add(29327)
			.add(29328).add(29329).add(29330).add(29331)
			// Misc tree products
			.add(2158)   // Calquat fruit (from calquat trees)
			.add(6032)   // Papaya fruit
			.add(6034)   // Coconut
			.build();

	// -----------------------------------------------------------------------
	// Fishing
	// -----------------------------------------------------------------------
	private static final Set<Integer> FISH_IDS = ImmutableSet.<Integer>builder()
			// Raw fish (standard)
			.add(317)    // Raw shrimps
			.add(321)    // Raw sardine
			.add(327)    // Raw herring
			.add(3150)   // Raw karambwanji
			.add(345)    // Raw mackerel
			.add(353)    // Raw cod
			.add(335)    // Raw pike
			.add(341)    // Raw swordfish
			.add(349)    // Raw tuna
			.add(3379)   // Raw karambwan
			.add(331)    // Raw salmon
			.add(359)    // Raw bass
			.add(10138)  // Raw cave eel
			.add(5001)   // Raw sea turtle
			.add(377)    // Raw lobster
			.add(363)    // Raw monkfish
			.add(371)    // Raw swordfish (noted) — removed duplicate; 371 = Raw swordfish
			.add(2162)   // Raw jubbly bird meat
			.add(7944)   // Raw manta ray
			.add(3142)   // Raw oomlie
			.add(389)    // Raw shark
			.add(395)    // Raw anglerfish (raw)
			.add(383)    // Raw dark crab
			// Deep sea / Minnow
			.add(13439)  // Minnow
			.add(17797)  // Barrel chest fish
			.add(11328)  // Raw infernal eel
			.add(11330)  // Raw sacred eel
			.add(11332)  // Raw great white shark (Drift Net)
			// Drift net
			.add(21495)  // Broken fishing rod (junk from drift net)
			.add(21293)  // Raw fish (drift net)
			// Tempoross
			.add(25564)  // Spirit flakes
			.add(25563)  // Soaked page
			.add(25569)  // Tackle box reward
			.add(27680)  // Reward permit
			.add(40166).add(40167).add(40168)
			.add(31553).add(31561)
			// Underwater / Fossil Island
			.add(21356)  // Raw tentacle
			.add(21358)  // Seaweed spore
			// Kylie Minnow / Master Fisherman
			.add(13648).add(13649).add(13650).add(13651)
			// Miscellaneous
			.add(405)    // Stripy feather
			.add(13320)  // Swamp tar (from barb fishing)
			.add(23772)  // Rune essence
			.add(21622)  // Zulrah's scales
			.add(13356)  // Reward casket
			.add(22815).add(22814).add(22816)
			// Miscellaneous raw/cooked variants that register in inv
			.add(401)    // Raw chompy
			.add(1621).add(1623).add(1625).add(1627).add(1629)  // Various raw fish gems
			.add(19668)  // Ancient essence
			.add(9007)   // Uncut opal
			.add(9009).add(9011)
			.add(7936)   // Swamp tar
			// Deep sea fishing rares
			.add(22821).add(22822).add(22823)
			.add(11934)  // Raw cave eel (brimhaven)
			.add(2878)   // Karambwan paste
			.add(7566)   // Fishing trophy (not tracked — listed for completeness)
			.add(2136)   // Casket (Fishing Trawler)
			.add(1861)   // Rope (Trawler)
			.add(22929)  // Reward casket (hard)
			.add(6006)   // Lobster pot
			.add(6004)   // Raw sea slugs
			.build();

	// -----------------------------------------------------------------------
	// Mining ores / minerals
	// -----------------------------------------------------------------------
	private static final Set<Integer> ORE_IDS = ImmutableSet.<Integer>builder()
			// Standard ores
			.add(436)    // Copper ore
			.add(438)    // Tin ore
			.add(440)    // Iron ore
			.add(453)    // Coal
			.add(444)    // Gold ore
			.add(447)    // Mithril ore
			.add(449)    // Adamantite ore
			.add(451)    // Runite ore
			.add(442)    // Silver ore
			.add(1436)   // Rune essence (mining)
			.add(7936)   // Pure essence
			.add(434)    // Bronze bar (smelt — not from mining normally, left for safety)
			// Amethyst
			.add(21347)  // Amethyst
			// Gem rocks
			.add(1617)   // Uncut sapphire
			.add(1619)   // Uncut emerald
			.add(1621)   // Uncut ruby
			.add(1623)   // Uncut diamond
			.add(1625)   // Uncut dragonstone
			.add(1627)   // Uncut onyx
			.add(1629)   // Uncut zenyte
			.add(9009)   // Uncut sapphire (gem rock variant)
			.add(9011)   // Uncut emerald (gem rock variant)
			.add(9017)   // Uncut jade
			.add(13322)  // Uncut red topaz
			// Volcanic mine / SOTE / blast mine
			.add(21552)  // Volcanic ash
			.add(23491)  // Lovakite ore
			// Motherlode Mine
			.add(12602)  // Pay-dirt — BUG FIX: was missing
			.add(453)    // Coal (duplicate but safe)
			.add(444)    // Gold (duplicate safe)
			.add(21347)  // Amethyst
			// Dense essence block (Arceuus)
			.add(13445)  // Dense essence block — BUG FIX: missing
			.add(13446)  // Dark essence block
			.add(13447)  // Dark essence fragments
			// Blast mine
			.add(4483).add(4484).add(4485).add(4486)  // Blast mine ores
			// Mineral patches (zalcano / gotr)
			.add(26906)  // Imbued heart (GOTR drop) — shouldn't be here but commonly asked
			.add(26880)  // Guardian essence
			.add(26882)  // Guardian stone
			.add(25527).add(25528)
			// Gem mine (Shilo Village)
			.add(6971).add(6973).add(6975).add(6977).add(6979).add(6981).add(6983)
			// Sandstone / granite
			.add(6967)   // Sandstone (1kg)
			.add(6969)   // Sandstone (2kg)  — BUG FIX: missing
			.add(6971)   // Sandstone (5kg)  — (also gem mine — safe dupe)
			.add(6973)   // Sandstone (10kg)
			.add(6975)   // Granite (500g)
			.add(6977)   // Granite (2kg)
			.add(6979)   // Granite (5kg)
			// Crashed star (shooting stars)
			.add(25527)  // Star fragment
			.add(25628)  // Stardust — BUG FIX: was listed as 25627 (invalid)
			.add(25627)  // Stardust (extra variant, kept for safety)
			// Misc
			.add(668)    // Elemental ore (elemental workshop)
			.add(2892)   // Daeyalt ore
			.add(3211)   // Unidentified minerals
			.add(21905)  // Primal ore (fossil island)
			.add(9631)   // Barronite shard — BUG FIX: was missing
			.add(9632)   // Barronite deposit — BUG FIX: was missing
			.add(21552)  // Volcanic ash (dupe safe)
			.add(31716)  // Abyssal pearl (GOTR)
			.add(13321)  // Limestone
			.add(25527)
			.add(23996).add(23997)
			.add(19668)  // Ancient essence
			.add(13421)  // Minerals (unique)
			.add(24705)  // Calcite
			.add(11337)  // Runite ore (dup-safe)
			.add(12600).add(12601)  // Unidentified minerals
			.add(21622)  // Zulrah scales (rare gem rock drop)
			.build();

	// -----------------------------------------------------------------------
	// Farming
	// -----------------------------------------------------------------------
	private static final Set<Integer> FARMING_IDS = ImmutableSet.<Integer>builder()
			// Herbs (grimy)
			.add(199)    // Grimy guam
			.add(201)    // Grimy marrentill
			.add(203)    // Grimy tarromin
			.add(205)    // Grimy harralander
			.add(207)    // Grimy ranarr
			.add(209)    // Grimy toadflax
			.add(211)    // Grimy irit
			.add(213)    // Grimy avantoe
			.add(215)    // Grimy kwuarm
			.add(217)    // Grimy cadantine
			.add(219)    // Grimy lantadyme
			.add(2485)   // Grimy dwarf weed
			.add(267)    // Grimy torstol
			.add(269)    // Grimy snapdragon — BUG FIX: was listed under wrong constant (269=snapdragon?)
			.add(3049)   // Grimy snapdragon (correct ID) — BUG FIX
			.add(249)    // Grimy whiteberries
			.add(251)    // Grimy strawberry
			.add(253)    // Grimy watermelon
			.add(255)    // Grimy sweetcorn
			.add(257)    // Grimy barley
			.add(2998)   // Grimy herb (generic)
			.add(259)    // Grimy hammerstone
			.add(261)    // Grimy asgarnian
			.add(263)    // Grimy jute
			.add(3000)   // Grimy krandorian
			.add(265)    // Grimy wildblood
			.add(2481)   // Grimy yanillian
			// Fruits & vegetables
			.add(1942)   // Cooking apple
			.add(1957)   // Banana
			.add(1965)   // Orange
			.add(1982)   // Pineapple
			.add(5986)   // Watermelon
			.add(5504)   // Strawberry
			.add(1951)   // Tomato
			.add(753)    // Sweetcorn
			.add(2120)   // Papaya (was duplicate in original code, kept)
			.add(247)    // Barley
			.add(239)    // Hammerstone hops
			.add(1955)   // Lime
			.add(1963)   // Curry leaf
			.add(2108)   // Jangerberries
			.add(5970)   // Mushroom
			.add(2114)   // White berries
			.add(5972)   // Bittercap mushroom
			.add(5974)   // Mort myre fungus
			.add(22929)  // Dragonfruit
			.add(6006)   // Cactus spine
			.add(6004)   // Poison ivy berries
			.add(5996)   // Snape grass
			.add(5931)   // Limpwurt root
			.add(5998)   // Potato cactus
			.add(6000)   // Reed
			.add(6002)   // Seaweed spore
			.add(6010)   // Red bead
			.add(6012)   // Yellow bead
			.add(6014)   // Black bead
			.add(6016)   // White bead
			.add(6018)   // Slayer's respite
			.add(5982)   // Belladonna
			.add(24730)  // Sraracha
			.add(24632)  // Blood essence
			.add(2460)   // Flax
			.add(225)    // Bowl of water (Farming run reward — left for safety)
			// Seeds (harvested products occasionally tracked)
			.add(21504)  // Kronos seed
			.add(1975)   // Cabbage
			.add(2970)   // Barb tail hops
			.add(23534)  // Erzille hops
			.add(23246)  // Erzille seed
			.add(23248)  // Dwellberry seed
			.add(23250)  // Sweetcorn seed
			.add(23951).add(23953).add(23955)
			.add(3138)   // Cactus spine
			.add(5980).add(5981)
			.add(21528)  // Dragonfruit
			.add(6032)   // Papaya fruit
			.add(6034)   // Coconut
			.add(21483)  // Erzille berry
			.add(22994)  // Gourd seed
			.add(6036)   // Calquat fruit
			// Nests from Farming (bird nests from apple trees etc)
			.add(5070).add(5071).add(5072).add(5073).add(5074).add(5075)
			// Misc farming loot
			.add(23772)  // Rune essence
			.add(24589).add(24590).add(24591).add(24592).add(24593)
			.add(22875).add(22877).add(22879)
			.add(25170).add(25171).add(25172).add(25173).add(25174)
			.add(21490)  // Mushroom
			.add(25595)  // Mushroom spore
			.add(25379)  // Tangled toads' legs
			.add(6055)   // Brimhaven fruit
			.add(6038)   // Spicy stew ingredient
			.add(21622)  // Zulrah scales
			.build();

	// -----------------------------------------------------------------------
	// Hunter
	// -----------------------------------------------------------------------
	private static final Set<Integer> HUNTER_IDS = ImmutableSet.<Integer>builder()
			// Chinchompas / salamanders
			.add(10033)  // Chinchompa
			.add(10034)  // Red chinchompa
			.add(10031)  // Black chinchompa
			// Kebbits / furs
			.add(10086)  // Kebbit fur
			.add(10087)  // Dashing kebbit fur
			.add(10088)  // Feldip weasel fur
			.add(10089)  // Common kebbit fur
			.add(10090)  // Dark kebbit fur
			.add(10085)  // Polar kebbit fur
			// Bird snaring
			.add(10146)  // Bird snare
			.add(10147)  // Tropical wagtail
			.add(10148)  // Crimson swift
			.add(10149)  // Golden warbler
			.add(10150)  // Copper longtail
			// Butterfly jars
			.add(10011)  // Ruby harvest
			.add(10012)  // Sapphire glacialis
			.add(10013)  // Snowy knight
			.add(10014)  // Black warlock
			// Impling jars
			.add(11238)  // Baby impling jar
			.add(11240)  // Young impling jar
			.add(11242)  // Gourmet impling jar
			.add(11244)  // Earth impling jar
			.add(11246)  // Essence impling jar
			.add(11248)  // Eclectic impling jar
			.add(11250)  // Nature impling jar
			.add(11252)  // Magpie impling jar
			.add(11254)  // Ninja impling jar
			.add(11256)  // Dragon impling jar
			.add(11258)  // Lucky impling jar
			.add(11260)  // Crystal impling jar
			.add(11262)  // Zombie impling jar
			.add(11264)  // Kourend impling jar
			.add(11266)  // Flambeed impling jar
			.add(11268)  // Gourmet impling jar (v2)
			.add(11270)  // Infernal impling jar
			.add(11272)  // Zombie impling (v2)
			.add(11274)  // Elemental impling jar
			.add(11276)  // Brimstone impling jar
			// Big chinchompa / deadfall traps
			.add(10107)  // Carnivorous chinchompa
			.add(10109)  // Horned graahk
			.add(10112)  // Spotted kebbit
			.add(10113)  // Razor-backed kebbit
			.add(10114)  // Barb-tailed kebbit
			.add(10115)  // Prickly kebbit
			.add(10117)  // Wild kebbit
			.add(10119)  // Sabre-toothed kebbit
			.add(10121)  // Sabre-toothed kyatt
			.add(10123)  // Larupia
			.add(10125)  // Graahk
			.add(10127)  // Pawya
			.add(10129)  // Grenwall
			.add(10132)  // Spined larupia
			.add(10133)  // Spotted larupia
			.add(10137)  // Striped larupia
			.add(10139)  // Wildblood hop (hunter)
			// Box trapping
			.add(10008).add(10009).add(10010)
			.add(10015).add(10016).add(10017).add(10018).add(10019).add(10020)
			.add(10021).add(10022).add(10023).add(10024).add(10025).add(10026)
			.add(10027).add(10028).add(10029).add(10030).add(10032)
			.add(10035).add(10036).add(10037)
			// Hunter guild
			.add(19732)  // Hunter's honour
			// Numulite / misc
			.add(28801)  // Sunlight moth wings
			.add(28866)  // Fremennik device
			.add(29158)  // Hunter potion
			.add(29146)  // Bird snare (v2)
			// Boulderdasher / new content
			.add(28917)  // Boulderdasher crystal
			.add(29312)  // Salamander (misc)
			// Charm drops from hunting
			.add(9976)   // Green charm
			.add(9977)   // Crimson charm
			.add(9978)   // Blue charm
			// Salamanders
			.add(10092)  // Orange salamander
			.add(10111)  // Red salamander
			.add(10118)  // Black salamander
			.add(10120)  // Orange salamander
			.add(10122)  // Red salamander (v2)
			.add(10124)  // Black salamander (v2)
			.add(10126)  // Swamp lizard
			.add(10128)  // Orange salamander (var)
			.add(10130)  // Red salamander (var)
			.add(10135)  // Black salamander (var)
			.add(10136)  // Red salamander (alt)
			// Misc
			.add(28867).add(28868)
			.add(526)    // Bones (some hunter creatures drop these — excluded if ALWAYS_IGNORE is respected first)
			.add(9953)   // Gecko
			.add(10091)  // Blue bird
			.add(10093).add(10094).add(10095).add(10096)
			.add(22795).add(22798)
			.add(2876).add(2878).add(2880).add(2882)
			// New moths / misc
			.add(21514).add(21515).add(21516).add(21517).add(21518).add(21519).add(21520)
			// Common loot from hunter (misc gem/resource drops)
			.add(25563).add(25564).add(25569)
			.add(13356)
			.add(13323)
			.build();

	// -----------------------------------------------------------------------
	// Animation sets
	// -----------------------------------------------------------------------
	private static final Set<Integer> WOODCUTTING_ANIMATIONS = ImmutableSet.<Integer>builder()
			.add(AnimationID.WOODCUTTING_BRONZE)
			.add(AnimationID.WOODCUTTING_IRON)
			.add(AnimationID.WOODCUTTING_STEEL)
			.add(AnimationID.WOODCUTTING_BLACK)
			.add(AnimationID.WOODCUTTING_MITHRIL)
			.add(AnimationID.WOODCUTTING_ADAMANT)
			.add(AnimationID.WOODCUTTING_RUNE)
			.add(AnimationID.WOODCUTTING_DRAGON)
			.add(AnimationID.WOODCUTTING_INFERNAL)
			.add(AnimationID.WOODCUTTING_CRYSTAL)
			.add(AnimationID.WOODCUTTING_3A_AXE)
			.add(AnimationID.WOODCUTTING_TRAILBLAZER)
			// Dragon axe (or) and special variants
			.add(8303)   // Dragon axe (or)
			.add(8324)   // Dragon felling axe
			// Forestry / new axes (2023+)
			.add(10090)  // Felling axe (bronze)
			.add(10091)  // Felling axe (iron)
			.add(10092)  // Felling axe (steel)
			.add(10093)  // Felling axe (black)
			.add(10094)  // Felling axe (mithril)
			.add(10095)  // Felling axe (adamant)
			.add(10096)  // Felling axe (rune)
			.add(10097)  // Felling axe (dragon)
			.add(10098)  // Felling axe (crystal)
			.add(10099)  // Felling axe (3a)
			// Ivy / mushroom harvesting
			.add(6282)   // Cutting ivy — BUG FIX: was missing
			// Misc WC anims
			.add(2117)
			.add(4961)
			.add(7263)
			.add(7264)
			.add(2116)
			.add(2120)
			.add(2121)
			.add(2122)
			.add(2123)
			.add(2124)
			.add(8707)
			.build();

	private static final Set<Integer> MINING_ANIMATIONS = ImmutableSet.<Integer>builder()
			.add(AnimationID.MINING_BRONZE_PICKAXE)
			.add(AnimationID.MINING_IRON_PICKAXE)
			.add(AnimationID.MINING_STEEL_PICKAXE)
			.add(AnimationID.MINING_BLACK_PICKAXE)
			.add(AnimationID.MINING_MITHRIL_PICKAXE)
			.add(AnimationID.MINING_ADAMANT_PICKAXE)
			.add(AnimationID.MINING_RUNE_PICKAXE)
			.add(AnimationID.MINING_DRAGON_PICKAXE)
			.add(AnimationID.MINING_DRAGON_PICKAXE_UPGRADED)
			.add(AnimationID.MINING_DRAGON_PICKAXE_OR)
			.add(AnimationID.MINING_INFERNAL_PICKAXE)
			.add(AnimationID.MINING_CRYSTAL_PICKAXE)
			.add(AnimationID.MINING_3A_PICKAXE)
			.add(AnimationID.MINING_TRAILBLAZER_PICKAXE)
			// Miscellaneous / modelled
			.add(6758)   // Dwarven multi-cannon mine anim
			.add(8345)   // Rune pickaxe (or)
			.add(4481)   // Shooting Stars mining
			.add(4483)   // (alt shooting star)
			// Zalcano
			.add(7519)   // Zalcano rock hit — BUG FIX: missing
			.add(7523)   // Zalcano golem
			// Volcanic mine
			.add(7201)   // Volcanic mine mining — BUG FIX: missing
			// Pay-dirt sack (Motherlode)
			.add(7252)   // MLM prospecting animation — BUG FIX: missing
			// Other rock variants
			.add(7325)
			.add(6757)
			.add(6759)
			.add(6760)
			.add(6761)
			.add(6762)
			// Gems in gem rock (already covered by pickaxe anims, extra variants)
			.add(8887)   // Crystal pickaxe special
			.build();

	private static final Set<Integer> FISHING_ANIMATIONS = ImmutableSet.<Integer>builder()
			// Standard
			.add(618)    // Casting
			.add(619)    // Fly fishing
			.add(620)    // Barbarian rod
			.add(621)    // Spinning
			.add(622)    // Net fishing
			.add(623)    // Cage fishing
			.add(624)    // Harpoon
			.add(818)    // Small net
			.add(819)    // Crayfish cage
			// Barb fishing
			.add(832)    // Barbarian fishing (1)
			.add(833)    // Barbarian fishing (2)
			.add(834)    // Barbarian fishing (3)
			.add(922)    // Drift net
			.add(5108)   // Fly fishing rod anim
			// Deep sea / Tempoross
			.add(5208)
			.add(5210)
			.add(5211)
			.add(5212)
			.add(5213)
			.add(5214)
			.add(5215)
			.add(5216)
			.add(5217)
			.add(5218)
			.add(6200)
			.add(6201)
			.add(6202)
			.add(6236)   // Minnow platform
			.add(6705)   // Fishing trawler
			.add(6707)
			.add(6709)
			// Fishing guild / misc
			.add(7401)
			.add(7402)
			// Tempoross
			.add(8288)
			.add(8289)
			.add(8328)
			.add(8329)
			.add(8331)
			.add(8332)
			// New fishing rod (2024+)
			.add(10018)
			.add(10019)
			.add(10223)
			// Waders / harpoon (2024+)
			.add(12489)
			.add(12490)
			.add(12491)
			.add(12492)
			.add(12493)
			// Karambwanji net
			.add(6715)   // BUG FIX: missing
			// Sacred eel
			.add(6710)   // BUG FIX: missing
			// Fishing with infernal harpoon
			.add(7407)   // BUG FIX: missing
			.build();

	private static final Set<Integer> FARMING_ANIMATIONS = ImmutableSet.<Integer>builder()
			.add(830)    // Harvesting
			.add(2272)
			.add(2273)
			.add(2275)
			.add(2281)
			.add(2282)
			.add(2286)
			.add(2291)
			.add(2292)
			.add(2293)
			.add(2294)
			// Farming tool anims
			.add(8304)   // Rake
			.add(8305)   // Spade
			.add(8306)   // Watering can
			.add(8525)   // Harvest herb
			.add(8526)   // Harvest fruit
			.add(8527)   // Harvest flower
			.add(8548)   // Harvest veg
			// Hespori / special patches
			.add(7771)   // Hespori harvesting — BUG FIX: missing
			// Seaweed farm
			.add(7741)   // Seaweed harvesting — BUG FIX: missing
			.build();

	private static final Set<Integer> HUNTER_ANIMATIONS = ImmutableSet.<Integer>builder()
			.add(5208)   // Setting box trap
			.add(5209)   // Checking box trap
			.add(5210)
			.add(5211)
			.add(5212)
			.add(5247)   // Deadfall trap
			.add(5248)
			.add(5249)
			.add(5250)
			.add(5255)   // Bird snare
			.add(5256)
			.add(5257)
			// Net trap
			.add(6605)
			.add(6606)
			// Pitfall
			.add(7171)
			.add(7172)
			.add(7173)
			// Misc trapping
			.add(8291)
			.add(8292)
			.add(8327)
			// Drift net (also Fishing — Hunter takes priority when lastSkillAction = Hunter)
			.add(10018)
			// Chinchompa catching (specific)
			.add(2000)   // BUG FIX: missing — chinchompa throwing (also the collecting anim)
			// Impling jar catching
			.add(6606)   // (dupe safe)
			// Butterfly net
			.add(6760)   // BUG FIX: missing
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
	private volatile long cachedTotalGe = 0L;
	private volatile long cachedTotalHa = 0L;

	// Parsed ignore list cache — rebuilt on config change
	private final Set<Integer> customIgnoreIds = new HashSet<>();

	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

	@Provides
	SkillLootTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SkillLootTrackerConfig.class);
	}

	@Override
	protected void startUp()
	{
		rebuildCustomIgnoreList();

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
			if (panel != null)
			{
				panel.setTimerText(getSessionTimeFormatted());
			}
		}, 0, 1, TimeUnit.SECONDS);

		if (client.getGameState() == GameState.LOGGED_IN && !dataLoaded.get())
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
		customIgnoreIds.clear();
		dataLoaded.set(false);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN && !dataLoaded.get())
		{
			clientThread.invokeLater(this::loadData);
		}

		switch (event.getGameState())
		{
			case LOGGED_IN:
				if (config.enableTimer() && !config.pauseTimer()) resumeSession();
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
			case "ignoreItemIds":
			case "ignoreCoins":
			case "ignoreBirdNests":
				// Rebuild the ignore cache whenever ignore settings change
				rebuildCustomIgnoreList();
				break;
		}
	}

	/**
	 * Parses the comma-separated ignore list from config and merges it with
	 * the built-in ALWAYS_IGNORE_IDS to build the working customIgnoreIds set.
	 */
	private void rebuildCustomIgnoreList()
	{
		customIgnoreIds.clear();

		// Always-ignored IDs (coins handled separately via config toggle below)
		for (int id : ALWAYS_IGNORE_IDS)
		{
			// Coins are in ALWAYS_IGNORE but can be re-enabled by turning the
			// ignoreCoins config item OFF — so skip them here and add below.
			if (id != 995 && id != 996)
			{
				customIgnoreIds.add(id);
			}
		}

		if (config.ignoreCoins())
		{
			customIgnoreIds.add(995);
			customIgnoreIds.add(996);
		}

		if (config.ignoreBirdNests())
		{
			customIgnoreIds.addAll(BIRD_NEST_IDS);
		}

		// Parse user-provided comma-separated IDs
		String raw = config.ignoreItemIds();
		if (raw != null && !raw.isBlank())
		{
			Arrays.stream(raw.split(","))
					.map(String::trim)
					.filter(s -> !s.isEmpty())
					.forEach(s -> {
						try
						{
							customIgnoreIds.add(Integer.parseInt(s));
						}
						catch (NumberFormatException ignored)
						{
							log.warn("Skill Loot Tracker: invalid ignore ID '{}' — skipping", s);
						}
					});
		}
	}

	/**
	 * Adds an item ID to the user-editable ignore list in config and immediately
	 * updates the working customIgnoreIds set so the item stops being tracked.
	 * Also removes that item from all tracked loot categories and refreshes the panel.
	 *
	 * @param itemId the canonical item ID to ignore
	 */
	public void addToIgnoreList(int itemId)
	{
		// Add to the live set immediately so isIgnored() returns true right away
		customIgnoreIds.add(itemId);

		// Persist into config (append to the comma-separated text field)
		String current = config.ignoreItemIds();
		String idStr = String.valueOf(itemId);
		String updated;
		if (current == null || current.isBlank())
		{
			updated = idStr;
		}
		else
		{
			// Avoid duplicates in the saved string
			boolean alreadySaved = Arrays.stream(current.split(","))
					.map(String::trim)
					.anyMatch(idStr::equals);
			updated = alreadySaved ? current : current + "," + idStr;
		}
		configManager.setConfiguration("skillloottracker", "ignoreItemIds", updated);

		// Remove the item from all tracked categories and refresh the panel
		boolean anyRemoved = false;
		for (Map.Entry<String, Map<Integer, Integer>> catEntry : lootPerSkill.entrySet())
		{
			if (catEntry.getValue().remove(itemId) != null)
			{
				anyRemoved = true;
			}
		}

		if (anyRemoved)
		{
			// Tell each loot box to remove this item (qty = 0 triggers removal)
			for (Map.Entry<String, Map<Integer, Integer>> catEntry : lootPerSkill.entrySet())
			{
				String category = catEntry.getKey();
				panel.removeItem(itemId, category);
			}
			updatePanelTotals();
			saveData();
		}

		log.debug("Skill Loot Tracker: added item {} to ignore list", itemId);
	}

	/**
	 * Removes an item ID from the user-editable ignore list in config and rebuilds
	 * the working ignore set. Does NOT retroactively add the item back to tracked loot.
	 *
	 * @param itemId the canonical item ID to un-ignore
	 */
	public void removeFromIgnoreList(int itemId)
	{
		String current = config.ignoreItemIds();
		if (current == null || current.isBlank())
		{
			customIgnoreIds.remove(itemId);
			return;
		}

		String idStr = String.valueOf(itemId);
		String updated = Arrays.stream(current.split(","))
				.map(String::trim)
				.filter(s -> !s.isEmpty() && !s.equals(idStr))
				.reduce((a, b) -> a + "," + b)
				.orElse("");

		configManager.setConfiguration("skillloottracker", "ignoreItemIds", updated);
		// Rebuild the full set so ALWAYS_IGNORE entries are still respected
		rebuildCustomIgnoreList();

		log.debug("Skill Loot Tracker: removed item {} from ignore list", itemId);
	}

	/**
	 * Returns true if the item is currently in the active ignore set.
	 * Used by the panel to toggle the context-menu label.
	 */
	public boolean isItemIgnored(int itemId)
	{
		return customIgnoreIds.contains(itemId);
	}

	/**
	 * Returns true if the item is in the ALWAYS_IGNORE hardcoded set,
	 * meaning it cannot be un-ignored via the right-click menu.
	 */
	public boolean isItemAlwaysIgnored(int itemId)
	{
		return ALWAYS_IGNORE_IDS.contains(itemId) || BIRD_NEST_IDS.contains(itemId);
	}

	private void resumeSession()
	{
		if (!sessionActive && dataLoaded.get() && config.enableTimer() && !config.pauseTimer())
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
			if (json != null && !json.isBlank())
			{
				Type type = new TypeToken<Map<String, Map<Integer, Integer>>>() {}.getType();
				Map<String, Map<Integer, Integer>> loaded = gson.fromJson(json, type);
				if (loaded != null)
				{
					loaded.forEach((k, v) -> lootPerSkill.put(k, new ConcurrentHashMap<>(v)));
				}
			}

			Long savedMs = configManager.getConfiguration("skillloottracker", ACCUMULATED_TIME_KEY, Long.class);
			accumulatedTime = savedMs != null ? Duration.ofMillis(savedMs) : Duration.ZERO;

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
			if (config.enableTimer() && !config.pauseTimer()) resumeSession();
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
			configManager.setConfiguration("skillloottracker", ACCUMULATED_TIME_KEY,
					getCurrentSessionDuration().toMillis());
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
		if (event.getContainerId() != InventoryID.INVENTORY.getId()) return;

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
				if (category != null && shouldTrackItem(category) && !isIgnored(itemId))
				{
					trackLoot(category, itemId, diff);
				}
			}
		}

		lastInventory.clear();
		lastInventory.putAll(currentInventory);
	}

	/**
	 * Returns true if the given item ID should be silently skipped.
	 */
	private boolean isIgnored(int itemId)
	{
		return customIgnoreIds.contains(itemId);
	}

	private boolean shouldTrackItem(String category)
	{
		long now = System.currentTimeMillis();
		boolean recentSkillAction = (now - lastSkillActionTime) < SKILL_WINDOW_MS;

		switch (category)
		{
			case "Mining":     return recentSkillAction && "Mining".equals(lastSkillAction)     && config.trackMining();
			case "Fishing":    return recentSkillAction && "Fishing".equals(lastSkillAction)    && config.trackFishing();
			case "Woodcutting":return recentSkillAction && "Woodcutting".equals(lastSkillAction)&& config.trackWoodcutting();
			case "Farming":    return recentSkillAction && "Farming".equals(lastSkillAction)    && config.trackFarming();
			case "Hunter":     return recentSkillAction && "Hunter".equals(lastSkillAction)     && config.trackHunter();
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

		// BUG FIX: save every 5 items OR on first few — original logic was correct but
		// also save when qty is exactly a multiple of 5 cumulative.
		if (newTotal % 5 == 0 || newTotal <= 5)
		{
			saveData();
		}
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

		Duration d = getCurrentSessionDuration();
		String gpHrText = "Total per hr: 0/hr";
		if (d.getSeconds() > 6)
		{
			double hours = d.getSeconds() / 3600.0;
			long gpPerHr = (long) (totalGe / hours);
			gpHrText = "Total per hr: " + QuantityFormatter.formatNumber(gpPerHr) + "/hr";
		}

		panel.setTotalsText(
				gpHrText,
				"GE: " + QuantityFormatter.formatNumber(totalGe) + " gp",
				"HA: " + QuantityFormatter.formatNumber(totalHa) + " gp"
		);
	}

	private String getCategory(int itemId)
	{
		boolean wc   = LOG_IDS.contains(itemId);
		boolean fish = FISH_IDS.contains(itemId);
		boolean mine = ORE_IDS.contains(itemId);
		boolean farm = FARMING_IDS.contains(itemId);
		boolean hunt = HUNTER_IDS.contains(itemId);

		int matchCount = (wc ? 1 : 0) + (fish ? 1 : 0) + (mine ? 1 : 0) + (farm ? 1 : 0) + (hunt ? 1 : 0);

		if (matchCount == 0) return null;

		// Disambiguate via recent animation when item appears in multiple lists
		if (matchCount > 1 && lastSkillAction != null)
		{
			switch (lastSkillAction)
			{
				case "Woodcutting": return wc   ? "Woodcutting" : null;
				case "Mining":      return mine ? "Mining"      : null;
				case "Fishing":     return fish ? "Fishing"     : null;
				case "Farming":     return farm ? "Farming"     : null;
				case "Hunter":      return hunt ? "Hunter"      : null;
			}
		}

		// Single match — return it regardless of lastSkillAction
		if (wc)   return "Woodcutting";
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
							// BUG FIX: original used raw item.getId() — should canonicalize
							int canonicalId = itemManager.canonicalize(item.getId());
							lastInventory.merge(canonicalId, item.getQuantity(), Integer::sum);
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
				if (inventory != null)
				{
					Set<Integer> categoryIds;
					switch (category)
					{
						case "Woodcutting": categoryIds = LOG_IDS;     break;
						case "Fishing":     categoryIds = FISH_IDS;    break;
						case "Mining":      categoryIds = ORE_IDS;     break;
						case "Farming":     categoryIds = FARMING_IDS; break;
						case "Hunter":      categoryIds = HUNTER_IDS;  break;
						default:            categoryIds = ImmutableSet.of();
					}
					lastInventory.keySet().removeIf(categoryIds::contains);

					for (Item item : inventory.getItems())
					{
						if (item.getId() > 0 && categoryIds.contains(item.getId()))
						{
							// BUG FIX: canonicalize here too
							int canonicalId = itemManager.canonicalize(item.getId());
							lastInventory.merge(canonicalId, item.getQuantity(), Integer::sum);
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
		if (d.getSeconds() < 6 || cachedTotalHa <= 0)
		{
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
		long hours   = seconds / 3600;
		long minutes = (seconds % 3600) / 60;
		long secs    = seconds % 60;
		return String.format("%02d:%02d:%02d", hours, minutes, secs);
	}

	public boolean isTimerActive()
	{
		return sessionActive;
	}
}