package de.minebench.plotsigns;

/*
 * Copyright 2016 Max Lee (https://github.com/Phoenix616/)
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Mozilla Public License as published by
 * the Mozilla Foundation, version 2.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * Mozilla Public License v2.0 for more details.
 * 
 * You should have received a copy of the Mozilla Public License v2.0
 * along with this program. If not, see <http://mozilla.org/MPL/2.0/>.
 */

import com.sk89q.worldedit.BlockVector;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldguard.protection.flags.DefaultFlag;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class SignListener implements Listener {
    private final PlotSigns plugin;

    public SignListener(PlotSigns plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onSignCreate(SignChangeEvent event) {
        if (event.getLine(0).isEmpty() || !event.getLine(0).equalsIgnoreCase(plugin.getSellLine())) {
            return;
        }

        if (!event.getPlayer().hasPermission("plotsigns.sign.create")) {
            event.getPlayer().sendMessage(plugin.getLang("create-sign.no-permission"));
            return;
        }

        ProtectedRegion region;
        if (!event.getLine(1).isEmpty()) {
            region = plugin.getWorldGuard().getRegionManager(event.getBlock().getWorld()).getRegion(event.getLine(1));
            if (region == null) {
                event.getPlayer().sendMessage(plugin.getLang("create-sign.unknown-region", "region", event.getLine(1)));
                return;
            }
        } else {
            List<ProtectedRegion> foundRegions = plugin.getWorldGuard().getRegionManager(event.getBlock().getWorld())
                    .getApplicableRegions(event.getBlock().getLocation()).getRegions()
                    .stream().filter(r -> r.getFlag(DefaultFlag.BUYABLE) != null).collect(Collectors.toList());
            if (foundRegions.size() > 1) {
                foundRegions.sort((r1, r2) -> Integer.compare(r2.getPriority(),r1.getPriority()));
            }
            if (foundRegions.size() > 0) {
                region = foundRegions.get(0);
            } else {
                event.getPlayer().sendMessage(plugin.getLang("create-sign.missing-region"));
                return;
            }
        }

        if (!event.getPlayer().hasPermission("plotsigns.sign.create.others") && !region.getOwners().contains(event.getPlayer().getUniqueId())) {
            event.getPlayer().sendMessage(plugin.getLang("create-sign.doesnt-own-plot"));
            return;
        }

        if (!event.getPlayer().hasPermission("plotsigns.sign.create.outside")) {
            BlockVector loc = new BlockVector(event.getBlock().getX(), event.getBlock().getY(), event.getBlock().getZ());
            if (!region.contains(loc)) {
                event.getPlayer().sendMessage(plugin.getLang("create-sign.sign-outside-region"));
                return;
            }
        }

        if (!event.getPlayer().hasPermission("plotsigns.sign.create.makebuyable") && region.getFlag(DefaultFlag.BUYABLE) == null) {
            event.getPlayer().sendMessage(plugin.getLang("create-sign.region-not-sellable", "region", region.getId()));
            return;
        }

        double price = 0;
        if (event.getLine(2).isEmpty()) {
            if (region.getFlag(DefaultFlag.PRICE) != null) {
                price = region.getFlag(DefaultFlag.PRICE);
            } else {
                event.getPlayer().sendMessage(plugin.getLang("create-sign.missing-price"));
                return;
            }
        } else {
            try {
                price = Double.parseDouble(event.getLine(2));
            } catch (NumberFormatException e) {
                event.getPlayer().sendMessage(plugin.getLang("create-sign.malformed-price", "input", event.getLine(2)));
                return;
            }
        }

        String perm = "";
        if (!event.getLine(3).isEmpty() && !event.getPlayer().hasPermission("plotsigns.sign.create.rights")) {
            if (region.getFlag(PlotSigns.BUY_PERM_FLAG) != null) {
                perm = region.getFlag(PlotSigns.BUY_PERM_FLAG);
            } else {
                event.getPlayer().sendMessage(plugin.getLang("create-sign.cant-use-rights"));
                return;
            }
        }

        region.setFlag(DefaultFlag.BUYABLE, true);
        region.setFlag(DefaultFlag.PRICE, price);
        region.setFlag(PlotSigns.BUY_PERM_FLAG, perm.isEmpty() ? null : perm);
    }
}
