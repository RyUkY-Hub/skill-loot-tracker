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

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class SkillLootTrackerOverlay extends OverlayPanel
{
    private final SkillLootTrackerPlugin plugin;
    private final SkillLootTrackerConfig config;

    @Inject
    public SkillLootTrackerOverlay(
            SkillLootTrackerPlugin plugin,
            SkillLootTrackerConfig config)
    {
        super(plugin);
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.showOverlay())
        {
            return null;
        }

        panelComponent.getChildren().clear();

        panelComponent.setPreferredSize(new Dimension(220, 0));
        panelComponent.setGap(new Point(0, 2));

        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Skilling Loot Tracker")
                .color(new Color(191, 64, 191)) // Bright electric purple
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Time:")
                .leftColor(Color.LIGHT_GRAY)
                .right(plugin.getSessionTimeFormatted())
                .rightColor(new Color(225, 103, 0)) // Orange
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("GE/hr:")
                .leftColor(Color.LIGHT_GRAY)
                .right(plugin.getGpPerHourFormatted())
                .rightColor(new Color(126, 255, 126)) // Green
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("HA/hr:")
                .leftColor(Color.LIGHT_GRAY)
                .right(plugin.getSessionHaPerHourFormatted())
                .rightColor(new Color(255, 255, 126)) // Yellow
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("GE Total:")
                .leftColor(Color.LIGHT_GRAY)
                .right(plugin.getSessionGeValueFormatted())
                .rightColor(new Color(126, 255, 126))
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("HA Total:")
                .leftColor(Color.LIGHT_GRAY)
                .right(plugin.getSessionHaValueFormatted())
                .rightColor(new Color(255, 255, 126))
                .build());

        return super.render(graphics);
    }
}