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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.QuantityFormatter;
import javax.swing.ImageIcon;

public class SkillLootTrackerPanel extends PluginPanel
{
	private final Map<String, LootBox> boxes = new HashMap<>();
	private final JPanel container = new JPanel();
	private final JLabel totalValueLabel = new JLabel("0 gp");
	private long totalSessionValue = 0;

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

		container.setLayout(new GridLayout(0, 1, 0, 10));
		container.setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(container, BorderLayout.CENTER);
	}

	public void updateLoot(int itemId, int totalAmount, int itemPrice, String category)
	{
		LootBox box = boxes.computeIfAbsent(category, k -> {
			LootBox newBox = new LootBox(k);
			container.add(newBox);
			return newBox;
		});

		box.updateItem(itemId, totalAmount, itemPrice);

		totalSessionValue += itemPrice;
		totalValueLabel.setText("Total: " + QuantityFormatter.quantityToStackSize(totalSessionValue) + " gp");

		repaint();
		revalidate();
	}

	public void resetAll()
	{
		boxes.clear();
		container.removeAll();
		totalSessionValue = 0;
		totalValueLabel.setText("0 gp");
		repaint();
		revalidate();
	}

	private class LootBox extends JPanel
	{
		private final JPanel itemGrid = new JPanel(new GridLayout(0, 5, 2, 2));
		private final JLabel subtotalLabel = new JLabel("0 gp");
		private final Map<Integer, JLabel> itemIcons = new HashMap<>();
		private long boxValue = 0;

		LootBox(String title)
		{
			setLayout(new BorderLayout());
			setBackground(ColorScheme.DARKER_GRAY_COLOR);
			setBorder(new EmptyBorder(5, 5, 5, 5));

			JLabel titleLabel = new JLabel(title);
			titleLabel.setFont(FontManager.getRunescapeSmallFont());
			titleLabel.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);

			subtotalLabel.setFont(FontManager.getRunescapeSmallFont());
			subtotalLabel.setHorizontalAlignment(SwingConstants.RIGHT);

			JPanel topPart = new JPanel(new BorderLayout());
			topPart.setOpaque(false);
			topPart.add(titleLabel, BorderLayout.WEST);
			topPart.add(subtotalLabel, BorderLayout.EAST);

			itemGrid.setOpaque(false);
			add(topPart, BorderLayout.NORTH);
			add(itemGrid, BorderLayout.CENTER);
		}

		void updateItem(int id, int qty, int price)
		{
			boxValue += price;
			subtotalLabel.setText(QuantityFormatter.quantityToStackSize(boxValue) + " gp");

			if (itemIcons.containsKey(id))
			{
				itemIcons.get(id).setText(QuantityFormatter.quantityToStackSize(qty));
			}
			else
			{
				JLabel iconLabel = new JLabel(QuantityFormatter.quantityToStackSize(qty));
				iconLabel.setVerticalTextPosition(SwingConstants.BOTTOM);
				iconLabel.setHorizontalTextPosition(SwingConstants.CENTER);
				iconLabel.setFont(FontManager.getRunescapeSmallFont());
				iconLabel.setForeground(Color.YELLOW);
				iconLabel.setIcon(new ImageIcon(itemManager.getImage(id, qty, qty > 1)));

				itemIcons.put(id, iconLabel);
				itemGrid.add(iconLabel);
			}
		}
	}
}
