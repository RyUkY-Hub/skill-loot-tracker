/*
 * Copyright (c) 2017, Steve <steve.rs.dev@gmail.com>
 * All rights reserved.
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
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
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
    private final Tooltip xpTooltip = new Tooltip(new PanelComponent());

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
        this.xpTooltip.getComponent().setPreferredSize(new Dimension(150, 0));
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
        PanelComponent panelComponent = (PanelComponent) xpTooltip.getComponent();
        panelComponent.getChildren().clear();
        panelComponent.getChildren().add(LineComponent.builder()
                .left(globe.getSkill().getName())
                .right("Level " + globe.getCurrentLevel())
                .build());
        tooltipManager.add(xpTooltip);
    }
}
