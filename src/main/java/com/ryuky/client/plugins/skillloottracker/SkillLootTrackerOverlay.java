/*
 * Copyright (c) 2017, Steve <steve.rs.dev@gmail.com>
 * Copyright (c) 2026, RyUkY <realmftalk420@gmail.com>
 *  * All rights reserved.
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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.text.DecimalFormat;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import static net.runelite.api.MenuAction.RUNELITE_OVERLAY_CONFIG;
import net.runelite.api.Point;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.plugins.xptracker.XpTrackerService;
import net.runelite.client.ui.overlay.Overlay;
import static net.runelite.client.ui.overlay.OverlayManager.OPTION_CONFIGURE;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

public class SkillLootTrackerOverlay extends Overlay
{
    private static final int MINIMUM_STEP = 10;
    private static final Color DARK_OVERLAY_COLOR = new Color(0, 0, 0, 180);
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.0");

    private final Client client;
    private final SkillLootTrackerPlugin plugin;
    private final SkillLootTrackerConfig config;
    private final XpTrackerService xpTrackerService;
    private final TooltipManager tooltipManager;
    private final SkillIconManager iconManager;
    @Inject
    private SkillLootTrackerOverlay(
            Client client,
            SkillLootTrackerPlugin plugin,
            SkillLootTrackerConfig config,
            XpTrackerService xpTrackerService,
            SkillIconManager iconManager,
            TooltipManager tooltipManager)
    {
        super(plugin);
        this.iconManager = iconManager;
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        this.xpTrackerService = xpTrackerService;
        this.tooltipManager = tooltipManager;
        setPosition(OverlayPosition.TOP_CENTER);
        addMenuEntry(RUNELITE_OVERLAY_CONFIG, OPTION_CONFIGURE, "XP Globes overlay");
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        final List<SkillLootTracker> xpGlobes = plugin.getXpGlobes();
        if (xpGlobes.isEmpty())
        {
            return null;
        }

        int curDrawPosition = 10;
        for (final SkillLootTracker globe : xpGlobes)
        {
            renderProgressCircle(graphics, globe, curDrawPosition, 10, getBounds());
            curDrawPosition += MINIMUM_STEP + 32;
        }

        return null;
    }

    private void renderProgressCircle(Graphics2D graphics, SkillLootTracker globe, int x, int y, Rectangle bounds)
    {
        Ellipse2D backgroundCircle = new Ellipse2D.Double(x, y, 32, 32);
        graphics.setColor(DARK_OVERLAY_COLOR);
        graphics.fill(backgroundCircle);

        // FIX: Using .getSkillImage() is the standard for most versions
        BufferedImage icon = iconManager.getSkillImage(globe.getSkill());

        if (icon != null)
        {
            graphics.drawImage(icon, x + 8, y + 8, null);
        }

        Point mouse = client.getMouseCanvasPosition();
        if (backgroundCircle.contains(mouse.getX() - bounds.x, mouse.getY() - bounds.y))
        {
            drawTooltip(globe);
        }
    }




    private void drawTooltip(SkillLootTracker globe)
    {
        tooltipManager.add(new Tooltip(
                globe.getSkill().getName()
                        + " - Level " + globe.getCurrentLevel()
        ));
    }
}
