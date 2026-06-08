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

import net.runelite.api.SpriteID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.QuantityFormatter;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class SkillLootTrackerPanel extends PluginPanel
{
	private final Map<String, LootBox> boxes = new HashMap<>();
	private final Map<String, Long> lastUpdateTimes = new HashMap<>();
	private final JPanel container = new JPanel();
	private final JLabel gpPerHrLabel = new JLabel("Total per hr: 0/hr");
	private final JLabel timerLabel = new JLabel("Time: 00:00:00");
	private final JLabel geValueLabel = new JLabel("GE: 0 gp");
	private final JLabel haValueLabel = new JLabel("HA: 0 gp");
	private final ExecutorService iconLoader = Executors.newSingleThreadExecutor();
	private Consumer<String> onCategoryReset;

	@Inject
	private ItemManager itemManager;

	@Inject
	private SpriteManager spriteManager;

	@Inject
	private SkillLootTrackerPlugin plugin;


	public void init(Runnable onResetAll, Consumer<String> onCategoryReset, Runnable onResetTimer)
	{
		this.onCategoryReset = onCategoryReset;
		removeAll();
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel header = new JPanel(new GridBagLayout());
		header.setBorder(new EmptyBorder(8, 10, 8, 10));
		header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		GridBagConstraints c = new GridBagConstraints();

		JPanel leftPanel = new JPanel();
		leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
		leftPanel.setOpaque(false);
		leftPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

		gpPerHrLabel.setFont(FontManager.getRunescapeSmallFont());
		gpPerHrLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		gpPerHrLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

		JButton resetBtn = new JButton("Reset Loot");
		resetBtn.setFocusable(false);
		resetBtn.setFont(FontManager.getRunescapeSmallFont());
		resetBtn.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
		resetBtn.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		resetBtn.setBorder(new EmptyBorder(4, 12, 4, 12));
		resetBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		resetBtn.addActionListener(e -> {
			lastUpdateTimes.clear();
			onResetAll.run();
		});
		resetBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
		resetBtn.addMouseListener(new java.awt.event.MouseAdapter() {
			public void mouseEntered(java.awt.event.MouseEvent e) {
				resetBtn.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
			}
			public void mouseExited(java.awt.event.MouseEvent e) {
				resetBtn.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
			}
		});

		JButton resetTimerBtn = new JButton("Reset Timer");
		resetTimerBtn.setFocusable(false);
		resetTimerBtn.setFont(FontManager.getRunescapeSmallFont());
		resetTimerBtn.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
		resetTimerBtn.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		resetTimerBtn.setBorder(new EmptyBorder(4, 12, 4, 12));
		resetTimerBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		resetTimerBtn.addActionListener(e -> onResetTimer.run());
		resetTimerBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
		resetTimerBtn.addMouseListener(new java.awt.event.MouseAdapter() {
			public void mouseEntered(java.awt.event.MouseEvent e) {
				resetTimerBtn.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
			}
			public void mouseExited(java.awt.event.MouseEvent e) {
				resetTimerBtn.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
			}
		});

		leftPanel.add(gpPerHrLabel);
		leftPanel.add(Box.createRigidArea(new Dimension(0, 4)));
		leftPanel.add(resetBtn);
		leftPanel.add(Box.createRigidArea(new Dimension(0, 4)));
		leftPanel.add(resetTimerBtn);

		c.gridx = 0;
		c.gridy = 0;
		c.weightx = 1.0;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.anchor = GridBagConstraints.WEST;
		header.add(leftPanel, c);

		JPanel rightPanel = new JPanel();
		rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
		rightPanel.setOpaque(false);

		timerLabel.setFont(FontManager.getRunescapeSmallFont());
		timerLabel.setForeground(ColorScheme.BRAND_ORANGE);
		timerLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
		timerLabel.setHorizontalAlignment(SwingConstants.RIGHT);

		geValueLabel.setFont(FontManager.getRunescapeSmallFont());
		geValueLabel.setForeground(new Color(126, 255, 126));
		geValueLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
		geValueLabel.setHorizontalAlignment(SwingConstants.RIGHT);

		haValueLabel.setFont(FontManager.getRunescapeSmallFont());
		haValueLabel.setForeground(new Color(255, 255, 126));
		haValueLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
		haValueLabel.setHorizontalAlignment(SwingConstants.RIGHT);

		rightPanel.add(timerLabel);
		rightPanel.add(geValueLabel);
		rightPanel.add(haValueLabel);

		c.gridx = 1;
		c.weightx = 0;
		c.fill = GridBagConstraints.NONE;
		c.anchor = GridBagConstraints.SOUTHEAST;
		header.add(rightPanel, c);

		add(header, BorderLayout.NORTH);

		container.setLayout(new GridBagLayout());
		container.setBackground(ColorScheme.DARK_GRAY_COLOR);
		container.setBorder(new EmptyBorder(8, 8, 8, 8)); // Fixed: was missing 4th param

		JScrollPane scrollPane = new JScrollPane(container);
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setBorder(null);
		scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
		scrollPane.getVerticalScrollBar().setUnitIncrement(16);

		scrollPane.getVerticalScrollBar().setUI(new BasicScrollBarUI() {
			@Override
			protected void configureScrollBarColors() {
				this.thumbColor = ColorScheme.MEDIUM_GRAY_COLOR;
				this.trackColor = ColorScheme.DARK_GRAY_COLOR;
			}

			@Override
			protected JButton createDecreaseButton(int orientation) {
				return createZeroButton();
			}

			@Override
			protected JButton createIncreaseButton(int orientation) {
				return createZeroButton();
			}

			private JButton createZeroButton() {
				JButton button = new JButton();
				button.setPreferredSize(new Dimension(0, 0));
				button.setMinimumSize(new Dimension(0, 0));
				button.setMaximumSize(new Dimension(0, 0));
				return button;
			}
		});

		add(scrollPane, BorderLayout.CENTER);
	}

	public void setTimerText(String time)
	{
		SwingUtilities.invokeLater(() -> timerLabel.setText("Time: " + time));
	}

	public void setTotalsText(String gpHr, String ge, String ha)
	{
		SwingUtilities.invokeLater(() -> {
			gpPerHrLabel.setText(gpHr);
			geValueLabel.setText(ge);
			haValueLabel.setText(ha);
		});
	}

	public void updateLoot(int itemId, int totalAmount, String geValue, String haValue, String category,
	                       String itemName, long gePrice, long haPrice)
	{
		LootBox box = boxes.computeIfAbsent(category, k -> {
			LootBox newBox = new LootBox(k, plugin);
			newBox.setAlignmentX(Component.LEFT_ALIGNMENT);
			return newBox;
		});

		lastUpdateTimes.put(category, System.currentTimeMillis());
		box.updateItem(itemId, totalAmount, geValue, haValue, itemName, gePrice, haPrice);
		SwingUtilities.invokeLater(() ->
		{
			container.removeAll();

			GridBagConstraints constraints = new GridBagConstraints();
			constraints.fill = GridBagConstraints.HORIZONTAL;
			constraints.weightx = 1.0;
			constraints.gridx = 0;
			constraints.gridy = 0;

			List<String> sortedCategories = new ArrayList<>(boxes.keySet());
			sortedCategories.sort((c1, c2) -> {
				long t1 = lastUpdateTimes.getOrDefault(c1, 0L);
				long t2 = lastUpdateTimes.getOrDefault(c2, 0L);
				return Long.compare(t2, t1);
			});

			for (String catKey : sortedCategories)
			{
				LootBox lootBox = boxes.get(catKey);
				if (lootBox == null) continue;

				lootBox.invalidate();
				lootBox.setPreferredSize(null);
				lootBox.setMinimumSize(null);

				container.add(lootBox, constraints);
				constraints.gridy++;

				container.add(Box.createRigidArea(new Dimension(0, 8)), constraints);
				constraints.gridy++;
			}

			GridBagConstraints pusherConstraints = new GridBagConstraints();
			pusherConstraints.fill = GridBagConstraints.BOTH;
			pusherConstraints.weightx = 1.0;
			pusherConstraints.weighty = 1.0;
			pusherConstraints.gridx = 0;
			pusherConstraints.gridy = constraints.gridy;
			container.add(Box.createVerticalGlue(), pusherConstraints);

			container.revalidate();
			container.repaint();
		});
	}

	public void resetAll()
	{
		boxes.clear();
		container.removeAll();
		container.add(Box.createVerticalGlue());
		gpPerHrLabel.setText("Total per hr: 0/hr");
		timerLabel.setText("Time: 00:00:00");
		geValueLabel.setText("GE: 0 gp");
		haValueLabel.setText("HA: 0 gp");
		repaint();
		revalidate();
	}

	public void resetCategory(String category)
	{
		LootBox box = boxes.remove(category);
		if (box!= null) {
			SwingUtilities.invokeLater(() -> {
				Component[] components = container.getComponents();
				for (int i = 0; i < components.length; i++) {
					if (components[i] == box) {
						container.remove(i);
						if (i + 1 < components.length && components[i + 1] instanceof Box.Filler) {
							container.remove(i);
						}
						break;
					}
				}
				container.revalidate();
				container.repaint();
			});

			if (onCategoryReset!= null) {
				onCategoryReset.accept(category);
			}
		}
	}

	public void shutdown()
	{
		iconLoader.shutdown();
	}

	private class LootBox extends JPanel
	{
		private final SkillLootTrackerPlugin plugin; // add this
		private final JPanel itemGrid = new JPanel(new GridLayout(0, 4, 6, 6));
		private final JPanel subtotalPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		private final Map<Integer, JPanel> itemBoxes = new HashMap<>();
		private final Map<Integer, Integer> itemQtys = new HashMap<>();
		private final Map<Integer, String> itemGeValues = new HashMap<>();
		private final Map<Integer, String> itemHaValues = new HashMap<>();
		private final String category;

		LootBox(String title, SkillLootTrackerPlugin plugin) // add plugin param
		{
			this.plugin = plugin; // store it
			this.category = title;
			setLayout(new BorderLayout(0, 6));
			setBackground(ColorScheme.DARKER_GRAY_COLOR);
			setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createMatteBorder(0, 3, 0, 0, getCategoryColor(title)),
					new EmptyBorder(8, 10, 8, 10)
			));
			setAlignmentX(Component.LEFT_ALIGNMENT);

			JPanel topPart = new JPanel(new GridBagLayout());
			topPart.setOpaque(false);
			GridBagConstraints c = new GridBagConstraints();

			JLabel titleLabel = new JLabel(title);
			titleLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(10f));
			titleLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

			JPanel titleWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
			titleWrap.setOpaque(false);

			JLabel icon = getSkillSprite(title);
			if (icon!= null) {
				titleWrap.add(icon);
			}
			titleWrap.add(titleLabel);

			c.gridx = 0;
			c.gridy = 0;
			c.weightx = 1.0;
			c.fill = GridBagConstraints.HORIZONTAL;
			c.anchor = GridBagConstraints.WEST;
			topPart.add(titleWrap, c);

			subtotalPanel.setOpaque(false);

			c.gridx = 1;
			c.weightx = 0;
			c.fill = GridBagConstraints.NONE;
			c.anchor = GridBagConstraints.EAST;
			topPart.add(subtotalPanel, c);

			itemGrid.setOpaque(false);
			itemGrid.setBorder(new EmptyBorder(6, 0, 0, 0));
			itemGrid.setAlignmentX(Component.LEFT_ALIGNMENT);

			add(topPart, BorderLayout.NORTH);
			add(itemGrid, BorderLayout.CENTER);

			setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
			setAlignmentX(Component.LEFT_ALIGNMENT);
		}

		private Color getCategoryColor(String category) {
			switch (category) {
				case "Fishing": return new Color(52, 152, 219);
				case "Mining": return new Color(149, 165, 166);
				case "Woodcutting": return new Color(39, 174, 96);
				case "Farming": return new Color(241, 196, 15);
				case "Hunter": return new Color(230, 126, 34);
				default: return ColorScheme.BRAND_ORANGE;
			}
		}

		private JLabel getSkillSprite(String category) {
			int spriteId;
			switch (category) {
				case "Fishing": spriteId = SpriteID.SKILL_FISHING; break;
				case "Mining": spriteId = SpriteID.SKILL_MINING; break;
				case "Woodcutting": spriteId = SpriteID.SKILL_WOODCUTTING; break;
				case "Farming": spriteId = SpriteID.SKILL_FARMING; break;
				case "Hunter": spriteId = SpriteID.SKILL_HUNTER; break;
				default: return null;
			}

			BufferedImage sprite = spriteManager.getSprite(spriteId, 0);
			if (sprite == null) return null;

			Image scaled = sprite.getScaledInstance(16, 16, Image.SCALE_SMOOTH);
			return new JLabel(new ImageIcon(scaled));
		}

		void updateItem(int id, int qty, String geValue, String haValue, String itemName, long gePrice, long haPrice)
		{
			itemQtys.put(id, qty);
			itemGeValues.put(id, geValue);
			itemHaValues.put(id, haValue);

			if (qty <= 0) {
				JPanel old = itemBoxes.remove(id);
				if (old != null) {
					itemGrid.remove(old);
					itemQtys.remove(id);
					itemGeValues.remove(id);
					itemHaValues.remove(id);
					itemGrid.revalidate();
					itemGrid.repaint();
				}
				return; // early exit
			}

			JPanel itemBox = itemBoxes.computeIfAbsent(id, k -> {
				JPanel newBox = new JPanel(new BorderLayout());
				newBox.setOpaque(true);
				newBox.setBackground(new Color(35, 35, 35));
				newBox.setBorder(BorderFactory.createLineBorder(new Color(50, 50, 50), 1));

				Dimension boxSize = new Dimension(36, 48);
				newBox.setPreferredSize(boxSize);
				newBox.setMaximumSize(boxSize);
				newBox.setMinimumSize(boxSize);

				JPanel content = new JPanel();
				content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
				content.setOpaque(false);
				content.setPreferredSize(boxSize);
				content.setMaximumSize(boxSize);
				content.setMinimumSize(boxSize);

				JLabel iconLabel = new JLabel();
				iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
				Dimension iconSize = new Dimension(32, 32);
				iconLabel.setPreferredSize(iconSize);
				iconLabel.setMaximumSize(iconSize);
				iconLabel.setMinimumSize(iconSize);

				JLabel qtyLabel = new JLabel();
				qtyLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
				qtyLabel.setHorizontalAlignment(SwingConstants.CENTER);
				qtyLabel.setFont(FontManager.getRunescapeSmallFont());
				qtyLabel.setForeground(Color.WHITE);
				Dimension qtySize = new Dimension(36, 14);
				qtyLabel.setPreferredSize(qtySize);
				qtyLabel.setMaximumSize(qtySize);
				qtyLabel.setMinimumSize(qtySize);

				AsyncBufferedImage img = itemManager.getImage(k);
				img.addTo(iconLabel);

				content.add(iconLabel);
				content.add(qtyLabel);
				newBox.add(content, BorderLayout.CENTER);

				// KEY FIX: Update tooltip on hover
				MouseAdapter tooltipUpdater = new MouseAdapter() {
					@Override
					public void mouseEntered(MouseEvent e) {
						String liveTooltip = buildTooltip(id, itemQtys.get(id), itemName, gePrice, haPrice);
						newBox.setToolTipText(liveTooltip);
						content.setToolTipText(liveTooltip);
						iconLabel.setToolTipText(liveTooltip);
						qtyLabel.setToolTipText(liveTooltip);
					}
				};

				newBox.addMouseListener(tooltipUpdater);
				content.addMouseListener(tooltipUpdater);
				iconLabel.addMouseListener(tooltipUpdater);
				qtyLabel.addMouseListener(tooltipUpdater);

				itemGrid.add(newBox);
				return newBox;
			});

			// Update qty label
			JPanel content = (JPanel) itemBox.getComponent(0);
			JLabel qtyLabel = (JLabel) content.getComponent(1);
			qtyLabel.setText(QuantityFormatter.quantityToStackSize(qty));

			// Set initial tooltip
			String tooltip = buildTooltip(id, qty, itemName, gePrice, haPrice);
			itemBox.setToolTipText(tooltip);
			content.setToolTipText(tooltip);
			((JComponent) content.getComponent(0)).setToolTipText(tooltip); // icon
			qtyLabel.setToolTipText(tooltip);

			updateSubtotal();

			SwingUtilities.invokeLater(() -> {
				setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
				revalidate();
			});

			itemGrid.revalidate();
			itemGrid.repaint();
		}

		private void updateSubtotal()
		{
			subtotalPanel.removeAll();

			long totalGe = 0L;
			long totalHa = 0L;
			for (Map.Entry<Integer, Integer> entry : itemQtys.entrySet()) {
				int id = entry.getKey();
				int qty = entry.getValue();
				try {
					String geStr = itemGeValues.get(id).replaceAll("[^0-9]", "");
					String haStr = itemHaValues.get(id).replaceAll("[^0-9]", "");
					totalGe += geStr.isEmpty()? 0 : Long.parseLong(geStr) * qty;
					totalHa += haStr.isEmpty()? 0 : Long.parseLong(haStr) * qty;
				} catch (Exception ignored) {}
			}

			JLabel gePill = new JLabel(QuantityFormatter.quantityToStackSize(totalGe) + " gp");
			gePill.setFont(FontManager.getRunescapeSmallFont());
			gePill.setForeground(new Color(126, 255, 126));
			gePill.setBackground(new Color(30, 58, 30));
			gePill.setOpaque(true);
			gePill.setBorder(new EmptyBorder(2, 6, 2, 6));
			subtotalPanel.add(gePill);

			if (totalHa > 0) {
				JLabel haPill = new JLabel("HA: " + QuantityFormatter.quantityToStackSize(totalHa));
				haPill.setFont(FontManager.getRunescapeSmallFont());
				haPill.setForeground(new Color(255, 255, 126));
				haPill.setBackground(new Color(58, 58, 30));
				haPill.setOpaque(true);
				haPill.setBorder(new EmptyBorder(2, 6, 2, 6));
				subtotalPanel.add(haPill);
			}

			JButton resetBtn = new JButton("X");
			resetBtn.setFont(FontManager.getRunescapeSmallFont().deriveFont(10f));
			resetBtn.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			resetBtn.setBackground(new Color(80, 30, 30));
			resetBtn.setOpaque(true);
			resetBtn.setBorder(new EmptyBorder(2, 5, 2, 5));
			resetBtn.setFocusable(false);
			resetBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			resetBtn.setToolTipText("Reset " + category);
			resetBtn.addActionListener(e -> resetCategory(category));
			resetBtn.addMouseListener(new java.awt.event.MouseAdapter() {
				public void mouseEntered(java.awt.event.MouseEvent e) {
					resetBtn.setBackground(new Color(120, 40, 40));
				}
				public void mouseExited(java.awt.event.MouseEvent e) {
					resetBtn.setBackground(new Color(80, 30, 30));
				}
			});
			subtotalPanel.add(resetBtn);

			subtotalPanel.revalidate();
			subtotalPanel.repaint();
		}

		private String buildTooltip(int itemId, int qty, String itemName, long gePrice, long haPrice)
		{
			long geTotal = gePrice * qty;
			long haTotal = haPrice * qty;

			// Get live duration instead of using stale value
			long elapsedMs = plugin.getCurrentSessionDuration().toMillis();

			StringBuilder sb = new StringBuilder("<html>");
			sb.append(itemName).append(" x ").append(QuantityFormatter.formatNumber(qty)).append("<br>");
			sb.append("GE: ").append(QuantityFormatter.quantityToStackSize(gePrice)).append(" gp<br>");
			sb.append("GE Total: ").append(QuantityFormatter.quantityToStackSize(geTotal)).append(" gp");

			if (haPrice > 0)
			{
				sb.append("<br>HA: ").append(QuantityFormatter.quantityToStackSize(haPrice)).append(" gp");
				sb.append("<br>HA Total: ").append(QuantityFormatter.quantityToStackSize(haTotal)).append(" gp");
			}

			if (elapsedMs > 6000) {
				double hours = elapsedMs / 3600000.0;
				long gpPerHr = (long) (geTotal / hours);
				long haPerHr = (long) (haTotal / hours);
				sb.append("<br>---<br>");
				sb.append("GE: ").append(QuantityFormatter.quantityToStackSize(gpPerHr)).append(" gp/hr");
				if (haPrice > 0) {
					sb.append("<br>HA: ").append(QuantityFormatter.quantityToStackSize(haPerHr)).append(" gp/hr");
				}
			} else if (elapsedMs > 0) {
				sb.append("<br>---<br>GP/hr: ---");
			}

			sb.append("</html>");
			return sb.toString();
		}
	}
}