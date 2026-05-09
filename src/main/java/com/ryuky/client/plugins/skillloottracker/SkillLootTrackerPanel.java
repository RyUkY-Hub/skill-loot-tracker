/*
 * Copyright (c) 2017, Steve <steve.rs.dev@gmail.com>
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

import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.QuantityFormatter;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SkillLootTrackerPanel extends PluginPanel
{
	private final Map<String, LootBox> boxes = new HashMap<>();
	private final JPanel container = new JPanel();
	private final JLabel totalValueLabel = new JLabel("Total: 0 gp");
	private final ExecutorService iconLoader = Executors.newSingleThreadExecutor();

	@Inject
	private ItemManager itemManager;

	public void init(Runnable onReset)
	{
		removeAll();
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel header = new JPanel(new BorderLayout());
		header.setBorder(new EmptyBorder(5, 10, 5, 10));
		header.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		totalValueLabel.setFont(FontManager.getRunescapeSmallFont());
		totalValueLabel.setForeground(Color.WHITE);

		JButton resetBtn = new JButton("Reset");
		resetBtn.setFocusable(false);
		resetBtn.setFont(FontManager.getRunescapeSmallFont());
		resetBtn.addActionListener(e -> onReset.run());

		header.add(totalValueLabel, BorderLayout.WEST);
		header.add(resetBtn, BorderLayout.EAST);
		add(header, BorderLayout.NORTH);

		container.setLayout(new GridLayout(0, 1, 0, 5));
		container.setBackground(ColorScheme.DARK_GRAY_COLOR);
		container.setBorder(new EmptyBorder(5, 5, 5, 5));
		add(container, BorderLayout.CENTER);
	}

	public void updateLoot(int itemId, int totalAmount, int valueAdded, String category)
	{
		LootBox box = boxes.computeIfAbsent(category, k ->
		{
			LootBox newBox = new LootBox(k);
			container.add(newBox);
			return newBox;
		});

		box.updateItem(itemId, totalAmount);

		recalculateTotals();
		repaint();
		revalidate();
	}

	private void recalculateTotals()
	{
		long totalSessionValue = boxes.values().stream()
				.mapToLong(LootBox::getBoxValue)
				.sum();

		totalValueLabel.setText("Total: " + QuantityFormatter.quantityToStackSize(totalSessionValue) + " gp");
	}

	public void resetAll()
	{
		boxes.clear();
		container.removeAll();
		totalValueLabel.setText("Total: 0 gp");
		repaint();
		revalidate();
	}

	private class LootBox extends JPanel
	{
		private final JPanel itemGrid = new JPanel(new GridLayout(0, 5, 2, 2));
		private final JLabel subtotalLabel = new JLabel("0 gp");
		private final Map<Integer, JLabel> itemIcons = new HashMap<>();
		private final Map<Integer, Integer> itemQtys = new HashMap<>();

		LootBox(String title)
		{
			setLayout(new BorderLayout());
			setBackground(ColorScheme.DARKER_GRAY_COLOR);
			setBorder(new EmptyBorder(5, 5, 5, 5));

			JLabel titleLabel = new JLabel(title);
			titleLabel.setFont(FontManager.getRunescapeBoldFont());
			titleLabel.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);

			subtotalLabel.setFont(FontManager.getRunescapeSmallFont());
			subtotalLabel.setHorizontalAlignment(SwingConstants.RIGHT);
			subtotalLabel.setForeground(Color.WHITE);

			JPanel topPart = new JPanel(new BorderLayout());
			topPart.setOpaque(false);
			topPart.add(titleLabel, BorderLayout.WEST);
			topPart.add(subtotalLabel, BorderLayout.EAST);

			itemGrid.setOpaque(false);
			add(topPart, BorderLayout.NORTH);
			add(itemGrid, BorderLayout.CENTER);
		}

		void updateItem(int id, int qty)
		{
			itemQtys.put(id, qty);

			long boxValue = getBoxValue();
			subtotalLabel.setText(QuantityFormatter.quantityToStackSize(boxValue) + " gp");

			JLabel iconLabel = itemIcons.get(id);
			if (iconLabel != null)
			{
				iconLabel.setText(QuantityFormatter.quantityToStackSize(qty));
				updateTooltip(iconLabel, id, qty);
			}
			else
			{
				iconLabel = new JLabel(QuantityFormatter.quantityToStackSize(qty));
				iconLabel.setVerticalTextPosition(SwingConstants.BOTTOM);
				iconLabel.setHorizontalTextPosition(SwingConstants.CENTER);
				iconLabel.setFont(FontManager.getRunescapeSmallFont());
				iconLabel.setForeground(Color.YELLOW);

				updateTooltip(iconLabel, id, qty);

				final JLabel finalIconLabel = iconLabel;
				iconLoader.submit(() ->
				{
					BufferedImage img = itemManager.getImage(id, qty, qty > 1);
					SwingUtilities.invokeLater(() -> finalIconLabel.setIcon(new ImageIcon(img)));
				});

				itemIcons.put(id, iconLabel);
				itemGrid.add(iconLabel);
			}
		}

		private void updateTooltip(JLabel label, int itemId, int qty)
		{
			String itemName = itemManager.getItemComposition(itemId).getName();
			long itemPrice = itemManager.getItemPrice(itemId);
			long totalPrice = itemPrice * qty;

			String tooltip = String.format("<html>%s<br>GE Value: %s gp<br>Total: %s gp</html>",
					itemName,
					QuantityFormatter.quantityToStackSize(itemPrice),
					QuantityFormatter.quantityToStackSize(totalPrice));

			label.setToolTipText(tooltip);
		}

		long getBoxValue()
		{
			return itemQtys.entrySet().stream()
					.mapToLong(e -> (long) itemManager.getItemPrice(e.getKey()) * e.getValue())
					.sum();
		}
	}
}