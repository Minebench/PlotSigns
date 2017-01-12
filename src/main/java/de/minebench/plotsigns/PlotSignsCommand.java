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

import java.util.ArrayList;
import java.util.List;

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

            } else if ("buy".equalsIgnoreCase(args[0]) || "kaufen".equalsIgnoreCase(args[0]) && sender.hasPermission("plotsigns.command.buy")) {
                // legacy sub command, you can click on signs directly
                if (args.length > 0) {
                    if (!(sender instanceof Player)) {
                        sender.sendMessage(ChatColor.RED + "This command can only be run by a player!");
                        return true;
                    }

                    ProtectedRegion region = null;

                    if (args.length > 1 && sender.hasPermission("plotsigns.command.buy.byregionid")) {
                        region = plugin.getWorldGuard().getRegionManager(((Player) sender).getWorld()).getRegion(args[1]);
                        if (region == null) {
                            sender.sendMessage(plugin.getLang("error.unknown-region", "region", args[1]));
                        }
                    } else {
                        List<ProtectedRegion> regions = new ArrayList<>(plugin.getWorldGuard().getRegionManager(((Player) sender).getWorld()).getApplicableRegions(((Player) sender).getLocation()).getRegions());
                        if (regions.size() > 0) {
                            regions.sort((r1, r2) -> Integer.compare(r2.getPriority(), r1.getPriority()));
                            region = regions.get(0);
                        }
                        if (region == null) {
                            sender.sendMessage(plugin.getLang("error.no-region-at-location"));
                        }
                    }

                    if (region == null) {
                        return true;
                    }

                    if (region.getFlag(DefaultFlag.BUYABLE) == null || !region.getFlag(DefaultFlag.BUYABLE) || region.getFlag(DefaultFlag.PRICE) == null) {
                        sender.sendMessage(plugin.getLang("buy.not-for-sale", "region", region.getId()));
                        return true;
                    }

                    try {
                        plugin.buyRegion((Player) sender, region, region.getFlag(DefaultFlag.PRICE), region.getFlag(PlotSigns.PLOT_TYPE_FLAG));
                        sender.sendMessage(plugin.getLang("buy.bought-plot", "region", region.getId()));
                    } catch (PlotSigns.BuyException e) {
                        sender.sendMessage(ChatColor.RED + "Error while trying to buy the region " + region.getId() + "! " + e.getMessage());
                    }
                } else {
                    sender.sendMessage(ChatColor.RED + "Usage: /" + label + " " + args[0] + " [<region>]");
                }

            } else if ("sell".equalsIgnoreCase(args[0]) || "verkaufen".equalsIgnoreCase(args[0]) && sender.hasPermission("plotsigns.command.sell")) {
                // legacy sub command, you can write the signs directly
                if (args.length > 2) {
                    ProtectedRegion region = getRegion(sender, args[1]);

                    if (region == null) {
                        sender.sendMessage(plugin.getLang("error.unknown-region", "region", args[1]));
                        return true;
                    }

                    if (sender instanceof Player && !region.getOwners().contains(((Player) sender).getUniqueId()) && !sender.hasPermission("plotsigns.command.sell.others")) {
                        sender.sendMessage(plugin.getLang("create-sign.doesnt-own-plot"));
                        return true;
                    }

                    try {
                        double price = Double.parseDouble(args[2]);
                        String perm = region.getFlag(PlotSigns.PLOT_TYPE_FLAG);
                        plugin.makeRegionBuyable(region, price, perm);
                        sender.sendMessage(plugin.getLang("create-sign.success", "region", region.getId(), "price", String.valueOf(price), "perm", perm));
                    } catch (NumberFormatException e) {
                        sender.sendMessage(plugin.getLang("error.malformed-price", "input", args[2]));
                    } catch (IllegalArgumentException e) {
                        sender.sendMessage(ChatColor.RED + "Error while trying to make the region buyable! " + e.getMessage());
                    }
                } else {
                    sender.sendMessage(ChatColor.RED + "Usage: /" + label + " " + args[0] + " <region> <price>");
                }
                return true;

            } else if ("type".equalsIgnoreCase(args[0]) || "permission".equalsIgnoreCase(args[0]) && sender.hasPermission("plotsigns.command.type")) {
                // legacy sub command, you can write the signs directly
                if (args.length > 2) {
                    ProtectedRegion region = getRegion(sender, args[1]);

                    if (region == null) {
                        sender.sendMessage(plugin.getLang("error.unknown-region", "region", args[1]));
                        return true;
                    }

                    if (sender instanceof Player && !region.getOwners().contains(((Player) sender).getUniqueId()) && !sender.hasPermission("plotsigns.command.type.others")) {
                        sender.sendMessage(plugin.getLang("create-sign.doesnt-own-plot"));
                        return true;
                    }

                    try {
                        Double price = region.getFlag(DefaultFlag.PRICE);
                        if (price == null) {
                            sender.sendMessage(plugin.getLang("create-sign.region-not-sellable", "region", region.getId()));
                            return true;
                        }
                        plugin.makeRegionBuyable(region, price, args[2]);
                        sender.sendMessage(plugin.getLang("create-sign.success", "region", region.getId(), "price", String.valueOf(price), "perm", args[2]));
                    } catch (IllegalArgumentException e) {
                        sender.sendMessage(ChatColor.RED + "Error while settings the region's type! " + e.getMessage());
                    }
                } else {
                    sender.sendMessage(ChatColor.RED + "Usage: /" + label + " " + args[0] + " <region> <type>");
                }
                return true;

            } else if ("sign".equalsIgnoreCase(args[0]) && sender.hasPermission("plotsigns.command.sign")) {
                // legacy sub command, you can write the signs directly
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "This command can only be run by a player!");
                    return true;
                }

                if (args.length > 1) {
                    ProtectedRegion region = getRegion(sender, args[1]);

                    if (region == null) {
                        sender.sendMessage(plugin.getLang("error.unknown-region", "region", args[1]));
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

                    try {
                        plugin.registerWriteIntent(((Player) sender).getUniqueId(), plugin.getSignLines(region));
                        sender.sendMessage(ChatColor.YELLOW + "Right click a Sign in the next 10 seconds to write it.");
                    } catch (IllegalArgumentException e) {
                        sender.sendMessage(ChatColor.RED + "Error while trying to make the region buyable! " + e.getMessage());
                    }
                } else {
                    sender.sendMessage(ChatColor.RED + "Usage: /" + label + " " + args[0] + " <region>");
                }
                return true;
            }
        }
        return false;
    }

    private ProtectedRegion getRegion(CommandSender sender, String id) {
        RegionManager regionManager;
        if (sender instanceof Entity) {
            regionManager = plugin.getWorldGuard().getRegionManager(((Entity) sender).getWorld());
        } else if (sender instanceof BlockCommandSender) {
            regionManager = plugin.getWorldGuard().getRegionManager(((BlockCommandSender) sender).getBlock().getWorld());
        } else {
            for (RegionManager rm : plugin.getWorldGuard().getRegionContainer().getLoaded()) {
                ProtectedRegion region = rm.getRegion(id);
                if (region != null) {
                    return region;
                }
            }
            return null;
        }
        if (regionManager == null) {
            sender.sendMessage(plugin.getLang("error.world-not-supported"));
            return null;
        }
        return regionManager.getRegion(id);
    }

}
