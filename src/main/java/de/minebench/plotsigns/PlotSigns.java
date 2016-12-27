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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.DefaultFlag;
import com.sk89q.worldguard.protection.flags.StringFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public final class PlotSigns extends JavaPlugin {

    private Economy economy;
    private WorldGuardPlugin worldGuard;
    private String signSellLine;

    private Cache<UUID, String[]> writeIntents = CacheBuilder.newBuilder().expireAfterWrite(10, TimeUnit.SECONDS).build();
    private Cache<UUID, List<String>> messageIntents = CacheBuilder.newBuilder().maximumSize(1000).build();

    public static final StringFlag PLOT_TYPE_FLAG = new StringFlag("plot-type");

    @Override
    public void onLoad() {
        worldGuard = (WorldGuardPlugin) getServer().getPluginManager().getPlugin("WorldGuard");
        if (worldGuard == null) {
            getLogger().log(Level.SEVERE, "You don't seem to have WorldGuard installed? The plugin will not run without it!");
            return;
        }
        try {
            worldGuard.getFlagRegistry().register(PLOT_TYPE_FLAG);
        } catch (FlagConflictException e) {
            getLogger().log(Level.WARNING, "Error while registering the plot type flag: " + e.getMessage());
        }
    }

    @Override
    public void onEnable() {
        if (worldGuard == null) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        loadConfig();
        if (!setupEconomy()) {
            getLogger().log(Level.SEVERE, "Failed to hook into Vault! The plugin will not run without it!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getServer().getPluginManager().registerEvents(new SignListener(this), this);
        getServer().getPluginManager().registerEvents(new JoinListener(this), this);
        getCommand("plotsigns").setExecutor(new PlotSignsCommand(this));
    }

    private boolean setupEconomy() {
        if (!getServer().getPluginManager().isPluginEnabled("Vault")) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    public void loadConfig() {
        saveDefaultConfig();
        reloadConfig();
        signSellLine = getConfig().getString("sign.sell");
    }

    /**
     * Make a WorldGuard region buyable
     * @param region The region to make buyable
     * @param price The price the region should cost
     * @param type The right for the max region count, use null or empty string if it shouldn't be limited
     * @throws IllegalArgumentException If the region's id or the permission string is longer than 15 chars
     */
    public void makeRegionBuyable(ProtectedRegion region, double price, String type) throws IllegalArgumentException {
        if (type != null && type.length() > 15)
            throw new IllegalArgumentException("Type string can't be longer than 15 chars! (It might not fit on a sign)");
        if (region.getId().length() > 15)
            throw new IllegalArgumentException("The region's ID can't be longer than 15 chars! (It might not fit on a sign)");
        region.setFlag(DefaultFlag.BUYABLE, true);
        region.setFlag(DefaultFlag.PRICE, price);
        region.setFlag(PlotSigns.PLOT_TYPE_FLAG, type == null || type.isEmpty() ? null : type);
    }

    /**
     * Buy a region for a player
     * @param player The player that should buy the region
     * @param region The region to buy
     * @param price The price of the region
     * @param type The region's type for the count
     * @throws BuyException if the player can't buy the region for whatever reason
     */
    public void buyRegion(Player player, ProtectedRegion region, double price, String type) throws BuyException {
        if (region.getFlag(DefaultFlag.BUYABLE) == null || !region.getFlag(DefaultFlag.BUYABLE)) {
            throw new BuyException(getLang("buy.not-for-sale", "region", region.getId()));
        }

        if (!getEconomy().has(player, price)) {
            throw new BuyException(getLang("buy.not-enough-money", "region", region.getId(), "price", String.valueOf(price)));
        }

        if (!checkTypeCount(player, player.getWorld(), type)) {
            throw new BuyException(getLang("buy.maximum-type-count", "region", region.getId(), "type", type));
        }

        double earnedPerOwner = price - getConfig().getDouble("tax.fixed", 0) - price * getConfig().getDouble("tax.share", 0);
        if (region.getOwners().size() > 1) {
            earnedPerOwner = earnedPerOwner / region.getOwners().size();
        }
        earnedPerOwner = Math.floor(earnedPerOwner * 100) / 100; // Make sure to round down to the second decimal point

        EconomyResponse withdraw = getEconomy().withdrawPlayer(player, price);
        if (!withdraw.transactionSuccess()) {
            throw new BuyException(withdraw.errorMessage);
        }

        if (region.getOwners().size() > 0) {
            for (UUID ownerId : region.getOwners().getUniqueIds()) {
                OfflinePlayer owner = getServer().getOfflinePlayer(ownerId);
                EconomyResponse deposit = getEconomy().depositPlayer(owner, earnedPerOwner);
                if (!deposit.transactionSuccess()) {
                    getLogger().log(Level.WARNING, "Error while depositing " + deposit.amount + " to " + ownerId + " from region " + region.getId() + ". " + deposit.errorMessage);
                }

                String message = getLang("buy.your-plot-sold",
                            "region", region.getId(),
                            "buyer", player.getName(),
                            "earned", String.valueOf(earnedPerOwner),
                            "price", String.valueOf(price)
                );
                if (owner.getPlayer() != null) {
                    owner.getPlayer().sendMessage(message);
                } else {
                    registerMessageIntent(ownerId, message);
                }
            }
        }

        region.setFlag(DefaultFlag.BUYABLE, false);
        if (region.getFlag(DefaultFlag.PRICE) == null) {
            region.setFlag(DefaultFlag.PRICE, price);
        }
        if (region.getFlag(PLOT_TYPE_FLAG) == null && type != null && !type.isEmpty()) {
            region.setFlag(PLOT_TYPE_FLAG, type);
        }
        region.getOwners().clear();
        region.getOwners().addPlayer(player.getUniqueId());
    }

    public boolean checkTypeCount(Player player, World world, String type) {
        if (player.hasPermission("plotsigns.type." + type + ".unlimited") || player.hasPermission("plotsigns.group." + type + ".unlimited")) {
            return true;
        }

        int maxAmount = 0;
        if (getConfig().contains("rights.groups." + type) && player.hasPermission("plotsigns.group." + type)) {
            maxAmount = getConfig().getInt("rights.groups." + type);
        } else {
            for (int i = getConfig().getInt("rights.maxNumber"); i > 0; i--) {
                if (player.hasPermission("plotsigns.type." + type + "." + i)) {
                    maxAmount = i;
                    break;
                }
            }
        }
        if (maxAmount == 0 && player.hasPermission("plotsigns.type." + type)) {
            maxAmount = 1;
        }

        if (maxAmount == 0) {
            return false;
        }

        int count = 0;
        for (ProtectedRegion region : getWorldGuard().getRegionManager(world).getRegions().values()) {
            if (region.getOwners().contains(player.getUniqueId()) && region.getFlag(PLOT_TYPE_FLAG) != null && type.equals(region.getFlag(PLOT_TYPE_FLAG))) {
                count++;
                if (count >= maxAmount) {
                    return false;
                }
            }
        }

        return count < maxAmount;
    }

    public void registerMessageIntent(UUID playerId, String message) {
        List<String> messages = getMessageIntents(playerId);
        if (messages == null) {
            messages = new ArrayList<>();
        }
        messages.add(message);
        messageIntents.put(playerId, messages);
    }

    public boolean hasMessageIntents(UUID playerId) {
        return getMessageIntents(playerId) != null;
    }

    public List<String> getMessageIntents(UUID playerId) {
        return messageIntents.getIfPresent(playerId);
    }

    public void removeMessageIntents(UUID playerId) {
        messageIntents.invalidate(playerId);
    }

    public void registerWriteIntent(UUID playerId, String[] lines) {
        writeIntents.put(playerId, lines);
    }

    public boolean hasWriteIntent(UUID playerId) {
        return writeIntents.getIfPresent(playerId) != null;
    }

    public String[] getWriteIntent(UUID playerId) {
        return writeIntents.getIfPresent(playerId);
    }

    public void removeWriteIntent(UUID playerId) {
        writeIntents.invalidate(playerId);
    }

    /**
     * Get the lines that should go onto a sign for a specific region
     * @param region The region to sell
     * @return An array with the length 4 with the lines
     * @throws IllegalArgumentException when the region doesn't have a price set
     */
    public String[] getSignLines(ProtectedRegion region) throws IllegalArgumentException{
        if (region.getFlag(DefaultFlag.PRICE) == null) {
            throw new IllegalArgumentException("The region " + region.getId() + " does not have the price flag set?");
        }
        String[] lines = new String[4];
        lines[0] = getSellLine();
        lines[1] = region.getId();
        lines[2] = String.valueOf(region.getFlag(DefaultFlag.PRICE));
        lines[3] = region.getFlag(PlotSigns.PLOT_TYPE_FLAG) != null ? region.getFlag(PlotSigns.PLOT_TYPE_FLAG) : "";

        return lines;
    }

    public String getLang(String key, String... args) {
        String message = getConfig().getString("lang." + key, getName() + ": &cUnknown language key &6" + key + "&c!");
        for (int i = 0; i + 1 < args.length; i += 2) {
            message = message.replace("%" + args[i] + "%", args[i+1]);
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public WorldGuardPlugin getWorldGuard() {
        return worldGuard;
    }

    public Economy getEconomy() {
        return economy;
    }

    public String getSellLine() {
        return signSellLine;
    }

    public class BuyException extends Exception {
        public BuyException(String message) {
            super(message);
        }
    }
}
