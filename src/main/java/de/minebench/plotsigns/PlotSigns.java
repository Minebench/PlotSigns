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

import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.DefaultFlag;
import com.sk89q.worldguard.protection.flags.StringFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class PlotSigns extends JavaPlugin {

    private Economy economy;
    private WorldGuardPlugin worldGuard;
    private String signSellLine;

    public static final StringFlag BUY_PERM_FLAG = new StringFlag("buy-permission");

    @Override
    public void onEnable() {
        loadConfig();
        if (!setupEconomy()) {
            getLogger().log(Level.SEVERE, "Failed to hook into Vault! The plugin will not run without it!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (getServer().getPluginManager().isPluginEnabled("WorldGuard")) {
            worldGuard = WorldGuardPlugin.inst();
        } else {
            getLogger().log(Level.SEVERE, "You don't seem to have WorldGuard installed? The plugin will not run without it!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        try {
            worldGuard.getFlagRegistry().register(BUY_PERM_FLAG);
        } catch (FlagConflictException e) {
            getLogger().log(Level.WARNING, "Error while registering the buy flag: " + e.getMessage());
        }
        getServer().getPluginManager().registerEvents(new SignListener(this), this);
        getCommand("plotsigns").setExecutor(new PlotSignsCommand(this));
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().isPluginEnabled("Vault")) {
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
     * @param perm The right for the max region count, use null or empty string if it shouldn't be limited
     * @throws IllegalArgumentException If the region's id or the permission string is longer than 15 chars
     */
    public void makeRegionBuyable(ProtectedRegion region, double price, String perm) throws IllegalArgumentException {
        if (perm != null && perm.length() > 15)
            throw new IllegalArgumentException("Permission string can't be longer than 15 chars! (It might not fit on a sign)");
        if (region.getId().length() > 15)
            throw new IllegalArgumentException("The region's ID can't be longer than 15 chars! (It might not fit on a sign)");
        region.setFlag(DefaultFlag.BUYABLE, true);
        region.setFlag(DefaultFlag.PRICE, price);
        region.setFlag(PlotSigns.BUY_PERM_FLAG, perm == null || perm.isEmpty() ? null : perm);
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
}
