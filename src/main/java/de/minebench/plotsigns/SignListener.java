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
import com.sk89q.worldguard.protection.flags.DefaultFlag;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class SignListener implements Listener {
    private final PlotSigns plugin;

    public SignListener(PlotSigns plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onSignInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        if (!(event.getClickedBlock().getState() instanceof Sign)) {
            return;
        }

        Sign sign = (Sign) event.getClickedBlock().getState();

        if (plugin.hasWriteIntent(event.getPlayer().getUniqueId())) {
            // Write sign
            String[] lines = plugin.getWriteIntent(event.getPlayer().getUniqueId());
            SignChangeEvent sce = new SignChangeEvent(event.getClickedBlock(), event.getPlayer(), lines);
            plugin.getServer().getPluginManager().callEvent(sce);
            if (!sce.isCancelled()) {
                for (int i = 0; i < sce.getLines().length; i++) {
                    sign.setLine(i, sce.getLine(i));
                }
                sign.update();
                plugin.removeWriteIntent(event.getPlayer().getUniqueId());
            }
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.GREEN + "Sign successfully written!");

        } else if (sign.getLines().length > 2 && sign.getLine(0).equalsIgnoreCase(plugin.getSellLine())) {
            // Buy plot
            event.setCancelled(true);

            if (!event.getPlayer().hasPermission("plotsigns.sign.purchase")) {
                event.getPlayer().sendMessage("buy.no-permission");
                return;
            }

            ProtectedRegion region = plugin.getWorldGuard().getRegionManager(event.getClickedBlock().getWorld()).getRegion(sign.getLine(1));

            if (region == null) {
                event.getPlayer().sendMessage(plugin.getLang("error.unknown-region", "region", sign.getLine(1)));
                return;
            }

            if (region.getFlag(DefaultFlag.BUYABLE) == null || !region.getFlag(DefaultFlag.BUYABLE)) {
                event.getPlayer().sendMessage(plugin.getLang("buy.not-for-sale", "region", region.getId()));
                return;
            }

            double price = 0.0;
            try {
                price = Double.parseDouble(sign.getLine(2));
            } catch (NumberFormatException e) {
                event.getPlayer().sendMessage(plugin.getLang("error.malformed-price", "input", sign.getLine(2)));
                return;
            }

            if (region.getFlag(DefaultFlag.PRICE) != null && price != region.getFlag(DefaultFlag.PRICE)) {
                plugin.getLogger().log(Level.WARNING, "The prices of the region " + region.getId() + " that " + event.getPlayer().getName() + " tries to buy via the sign at " + event.getClickedBlock().getLocation() + " didn't match! Sign: " + price + ", WorldGuard price flag: " + region.getFlag(DefaultFlag.PRICE));
                event.getPlayer().sendMessage(plugin.getLang("buy.price-mismatch", "sign", String.valueOf(price), "region", String.valueOf(region.getFlag(DefaultFlag.PRICE))));
                return;
            }

            String perm = sign.getLine(3);
            if (region.getFlag(PlotSigns.BUY_PERM_FLAG) != null && !region.getFlag(PlotSigns.BUY_PERM_FLAG).equals(perm)) {
                plugin.getLogger().log(Level.WARNING, "The permissions of the region " + region.getId() + " that " + event.getPlayer().getName() + " tries to buy via the sign at " + event.getClickedBlock().getLocation() + " didn't match! Sign: " + perm + ", WorldGuard price flag: " + region.getFlag(PlotSigns.BUY_PERM_FLAG));
                event.getPlayer().sendMessage(plugin.getLang("buy.right-mismatch", "sign", perm, "region", region.getFlag(PlotSigns.BUY_PERM_FLAG)));
                return;
            }

            try {
                plugin.buyRegion(event.getPlayer(), region, price, perm);
                event.getPlayer().sendMessage(plugin.getLang("buy.bought-plot", "region", region.getId()));
            } catch (PlotSigns.BuyException e) {
                event.getPlayer().sendMessage(e.getMessage());
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSignCreate(SignChangeEvent event) {
        if (event.getLine(0).isEmpty() || !event.getLine(0).equalsIgnoreCase(plugin.getSellLine())) {
            return;
        }

        if (!handleSignCreation(event.getPlayer(), event.getBlock(), event.getLines())) {
            event.setCancelled(true);
        }
    }
    
    private boolean handleSignCreation(Player player, Block block, String[] lines) {
        if (!player.hasPermission("plotsigns.sign.create")) {
            player.sendMessage(plugin.getLang("create-sign.no-permission"));
            return false;
        }

        ProtectedRegion region;
        if (!lines[1].isEmpty()) {
            region = plugin.getWorldGuard().getRegionManager(block.getWorld()).getRegion(lines[1]);
            if (region == null) {
                player.sendMessage(plugin.getLang("error.unknown-region", "region", lines[1]));
                return false;
            }
        } else {
            List<ProtectedRegion> foundRegions = plugin.getWorldGuard().getRegionManager(block.getWorld())
                    .getApplicableRegions(block.getLocation()).getRegions()
                    .stream().filter(r -> r.getFlag(DefaultFlag.BUYABLE) != null).collect(Collectors.toList());
            if (foundRegions.size() > 1) {
                foundRegions.sort((r1, r2) -> Integer.compare(r2.getPriority(),r1.getPriority()));
            }
            if (foundRegions.size() > 0) {
                region = foundRegions.get(0);
            } else {
                player.sendMessage(plugin.getLang("create-sign.missing-region"));
                return false;
            }
        }

        if (!player.hasPermission("plotsigns.sign.create.others") && !region.getOwners().contains(player.getUniqueId())) {
            player.sendMessage(plugin.getLang("create-sign.doesnt-own-plot"));
            return false;
        }

        if (!player.hasPermission("plotsigns.sign.create.outside")) {
            BlockVector loc = new BlockVector(block.getX(), block.getY(), block.getZ());
            if (!region.contains(loc)) {
                player.sendMessage(plugin.getLang("create-sign.sign-outside-region"));
                return false;
            }
        }

        if (!player.hasPermission("plotsigns.sign.create.makebuyable") && region.getFlag(DefaultFlag.BUYABLE) == null) {
            player.sendMessage(plugin.getLang("create-sign.region-not-sellable", "region", region.getId()));
            return false;
        }

        double price = 0;
        if (lines[2].isEmpty()) {
            if (region.getFlag(DefaultFlag.PRICE) != null) {
                price = region.getFlag(DefaultFlag.PRICE);
            } else {
                player.sendMessage(plugin.getLang("create-sign.missing-price"));
                return false;
            }
        } else {
            try {
                price = Double.parseDouble(lines[2]);
            } catch (NumberFormatException e) {
                player.sendMessage(plugin.getLang("error.malformed-price", "input", lines[2]));
                return false;
            }
        }

        String perm = "";
        if (region.getFlag(PlotSigns.BUY_PERM_FLAG) != null) {
            perm = region.getFlag(PlotSigns.BUY_PERM_FLAG);
        }
        if (!lines[3].isEmpty()) {
            if (player.hasPermission("plotsigns.sign.create.rights")) {
                perm = lines[3];
            } else {
                player.sendMessage(plugin.getLang("create-sign.cant-use-rights"));
            }
        }

        try {
            plugin.makeRegionBuyable(region, price, perm);
        } catch (IllegalArgumentException e) {
            player.sendMessage(ChatColor.RED + e.getMessage());
            return false;
        }

        lines[1] = region.getId();
        lines[2] = String.valueOf(price);
        lines[3] = perm;

        player.sendMessage(plugin.getLang("create-sign.success", "region", region.getId(), "price", String.valueOf(price), "perm", perm));
        return true;
    }
}
