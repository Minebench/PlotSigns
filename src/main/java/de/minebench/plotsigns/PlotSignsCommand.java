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

import com.sk89q.worldguard.protection.flags.DefaultFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.ChatColor;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public class PlotSignsCommand implements CommandExecutor {
    private final PlotSigns plugin;

    public PlotSignsCommand(PlotSigns plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length > 0) {

            if ("reload".equalsIgnoreCase(args[0]) && sender.hasPermission("plotsigns.command.reload")) {
                plugin.loadConfig();
                sender.sendMessage(ChatColor.YELLOW + "Config reloaded!");
                return true;

            } else if ("sell".equalsIgnoreCase(args[0]) || "verkaufen".equalsIgnoreCase(args[0]) && sender.hasPermission("plotsigns.command.sell")) {
                // legacy sub command, you can write the signs directly
                if (args.length > 2) {
                    ProtectedRegion region = getRegion(sender, args[1]);

                    if (region == null) {
                        sender.sendMessage(plugin.getLang("create-sign.unknown-region", "region", args[1]));
                        return true;
                    }

                    if (sender instanceof Player && !region.getOwners().contains(((Player) sender).getUniqueId()) && !sender.hasPermission("plotsigns.command.sell.others")) {
                        sender.sendMessage(plugin.getLang("create-sign.doesnt-own-plot"));
                        return true;
                    }

                    try {
                        plugin.makeRegionBuyable(region, Double.parseDouble(args[2]), region.getFlag(PlotSigns.BUY_PERM_FLAG));
                    } catch (NumberFormatException e) {
                        sender.sendMessage(plugin.getLang("create-sign.malformed-price", "input", args[2]));
                    } catch (IllegalArgumentException e) {
                        sender.sendMessage(ChatColor.RED + "Error while trying to make the region buyable! " + e.getMessage());
                    }
                } else {
                    sender.sendMessage(ChatColor.RED + "Usage: /" + label + " " + args[0] + " <region> <price>");
                }
                return true;

            } else if ("permission".equalsIgnoreCase(args[0]) && sender.hasPermission("plotsigns.command.permission")) {
                // legacy sub command, you can write the signs directly
                if (args.length > 2) {
                    ProtectedRegion region = getRegion(sender, args[1]);

                    if (region == null) {
                        sender.sendMessage(plugin.getLang("create-sign.unknown-region", "region", args[1]));
                        return true;
                    }

                    if (sender instanceof Player && !region.getOwners().contains(((Player) sender).getUniqueId()) && !sender.hasPermission("plotsigns.command.permission.others")) {
                        sender.sendMessage(plugin.getLang("create-sign.doesnt-own-plot"));
                        return true;
                    }

                    try {
                        plugin.makeRegionBuyable(region, region.getFlag(DefaultFlag.PRICE), args[2]);
                    } catch (IllegalArgumentException e) {
                        sender.sendMessage(ChatColor.RED + "Error while trying to make the region buyable! " + e.getMessage());
                    }
                } else {
                    sender.sendMessage(ChatColor.RED + "Usage: /" + label + " " + args[0] + " <region> <permission>");
                }
                return true;

            } else if ("sign".equalsIgnoreCase(args[0]) && sender.hasPermission("plotsigns.command.sign")) {
                // legacy sub command, you can write the signs directly
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "This command can only be run by a player!");
                    return true;
                }

                if (args.length > 2) {
                    ProtectedRegion region = getRegion(sender, args[1]);

                    if (region == null) {
                        sender.sendMessage(plugin.getLang("create-sign.unknown-region", "region", args[1]));
                        return true;
                    }

                    if (sender instanceof Player && !region.getOwners().contains(((Player) sender).getUniqueId()) && !sender.hasPermission("plotsigns.command.sign.others")) {
                        sender.sendMessage(plugin.getLang("create-sign.doesnt-own-plot"));
                        return true;
                    }

                    if (region.getFlag(DefaultFlag.BUYABLE) == null || !region.getFlag(DefaultFlag.BUYABLE) || region.getFlag(DefaultFlag.PRICE) == null) {
                        sender.sendMessage(plugin.getLang("create-sign.region-not-sellable", "region", region.getId()));
                        return true;
                    }

                    String[] lines = new String[4];
                    lines[0] = plugin.getSellLine();
                    lines[1] = region.getId();
                    lines[2] = String.valueOf(region.getFlag(DefaultFlag.PRICE));
                    lines[3] = region.getFlag(PlotSigns.BUY_PERM_FLAG) != null ? region.getFlag(PlotSigns.BUY_PERM_FLAG) : "";

                    try {
                        plugin.registerWriteIntent(((Player) sender).getUniqueId(), lines);
                        sender.sendMessage(ChatColor.YELLOW + "Right click a Sign in the next 10 seconds to write it.");
                    } catch (IllegalArgumentException e) {
                        sender.sendMessage(ChatColor.RED + "Error while trying to make the region buyable! " + e.getMessage());
                    }
                } else {
                    sender.sendMessage(ChatColor.RED + "Usage: /" + label + " " + args[0] + " <region> <permission>");
                }
                return true;
            }
        }
        return false;
    }

    private ProtectedRegion getRegion(CommandSender sender, String id) {
        ProtectedRegion region = null;
        if (sender instanceof Entity) {
            region = plugin.getWorldGuard().getRegionManager(((Entity) sender).getWorld()).getRegion(id);
        } else if (sender instanceof BlockCommandSender) {
            region = plugin.getWorldGuard().getRegionManager(((BlockCommandSender) sender).getBlock().getWorld()).getRegion(id);
        } else {
            for (RegionManager rm : plugin.getWorldGuard().getRegionContainer().getLoaded()) {
                region = rm.getRegion(id);
                if (region != null) {
                    break;
                }
            }
        }
        return region;
    }

}
