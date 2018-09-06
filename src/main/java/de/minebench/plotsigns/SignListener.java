package de.minebench.plotsigns;

/*
 * PlotSigns
 * Copyright (C) 2018 Max Lee aka Phoenix616 (mail@moep.tv)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import com.sk89q.worldedit.BlockVector;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.bukkit.BukkitWorld;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

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

            RegionManager rm = WorldGuard.getInstance().getPlatform().getRegionContainer().get(new BukkitWorld(event.getClickedBlock().getWorld()));
            if (rm == null) {
                event.getPlayer().sendMessage(plugin.getLang("error.world-not-supported", "world", event.getClickedBlock().getWorld().getName()));
                return;
            }

            ProtectedRegion region = rm.getRegion(ChatColor.stripColor(sign.getLine(1)));

            if (region == null) {
                event.getPlayer().sendMessage(plugin.getLang("error.unknown-region", "region", ChatColor.stripColor(sign.getLine(1))));
                return;
            }

            if (region.getFlag(PlotSigns.BUYABLE_FLAG) == null || !region.getFlag(PlotSigns.BUYABLE_FLAG)) {
                event.getPlayer().sendMessage(plugin.getLang("buy.not-for-sale", "region", region.getId()));
                return;
            }

            double price = 0.0;
            try {
                price = Double.parseDouble(ChatColor.stripColor(sign.getLine(2)));
            } catch (NumberFormatException e) {
                event.getPlayer().sendMessage(plugin.getLang("error.malformed-price", "input", sign.getLine(2)));
                return;
            }

            if (region.getFlag(PlotSigns.PRICE_FLAG) != null && price != region.getFlag(PlotSigns.PRICE_FLAG)) {
                plugin.getLogger().log(Level.WARNING, "The prices of the region " + region.getId() + " that " + event.getPlayer().getName()
                        + " tries to buy via the sign at " + event.getClickedBlock().getLocation() + " didn't match!" +
                        " Sign: " + price + ", WorldGuard price flag: " + region.getFlag(PlotSigns.PRICE_FLAG));
                event.getPlayer().sendMessage(plugin.getLang("buy.price-mismatch",
                        "sign", String.valueOf(price),
                        "region", String.valueOf(region.getFlag(PlotSigns.PRICE_FLAG))));
                return;
            }

            String type = ChatColor.stripColor(sign.getLine(3));
            if (region.getFlag(PlotSigns.PLOT_TYPE_FLAG) != null && !region.getFlag(PlotSigns.PLOT_TYPE_FLAG).equals(type)) {
                plugin.getLogger().log(Level.WARNING, "The permissions of the region " + region.getId() + " that " + event.getPlayer().getName() + " tries to buy via the sign at " + event.getClickedBlock().getLocation() + " didn't match! Sign: " + type + ", WorldGuard price flag: " + region.getFlag(PlotSigns.PLOT_TYPE_FLAG));
                event.getPlayer().sendMessage(plugin.getLang("buy.right-mismatch", "sign", type, "region", region.getFlag(PlotSigns.PLOT_TYPE_FLAG)));
                return;
            }

            try {
                plugin.buyRegion(event.getPlayer(), region, price, type);
                event.getPlayer().sendMessage(plugin.getLang("buy.bought-plot", "region", region.getId(), "price", String.valueOf(price)));

                List<String> soldLines = plugin.getConfig().getStringList("sign.sold");
                for (int i = 0; i < soldLines.size(); i++) {
                    sign.setLine(i, ChatColor.translateAlternateColorCodes('&', soldLines.get(i)).replace("%region%", region.getId()).replace("%player%", event.getPlayer().getName()));
                }
                sign.update();

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

        RegionManager rm = WorldGuard.getInstance().getPlatform().getRegionContainer().get(new BukkitWorld(block.getWorld()));
        if (rm == null) {
            player.sendMessage(plugin.getLang("error.world-not-supported", "world", block.getWorld().getName()));
            return false;
        }

        ProtectedRegion region;
        if (!lines[1].isEmpty()) {
            region = rm.getRegion(lines[1]);
            if (region == null) {
                player.sendMessage(plugin.getLang("error.unknown-region", "region", lines[1]));
                return false;
            }
        } else {
            Location l = block.getLocation();
            List<ProtectedRegion> foundRegions = new ArrayList<>(rm.getApplicableRegions(new Vector(l.getX(), l.getY(), l.getZ())).getRegions());
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

        if (!player.hasPermission("plotsigns.sign.create.makebuyable") && region.getFlag(PlotSigns.BUYABLE_FLAG) == null) {
            player.sendMessage(plugin.getLang("create-sign.region-not-sellable", "region", region.getId()));
            return false;
        }

        double price = 0;
        if (lines[2].isEmpty()) {
            if (region.getFlag(PlotSigns.PRICE_FLAG) != null) {
                price = region.getFlag(PlotSigns.PRICE_FLAG);
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

        String type = "";
        if (region.getFlag(PlotSigns.PLOT_TYPE_FLAG) != null) {
            type = region.getFlag(PlotSigns.PLOT_TYPE_FLAG);
        }
        if (!lines[3].isEmpty()) {
            if (player.hasPermission("plotsigns.sign.create.type")) {
                type = lines[3];
            } else {
                player.sendMessage(plugin.getLang("create-sign.cant-set-type"));
            }
        }

        try {
            plugin.makeRegionBuyable(region, price, type);
        } catch (IllegalArgumentException e) {
            player.sendMessage(ChatColor.RED + e.getMessage());
            return false;
        }
    
        System.arraycopy(plugin.getSignLines(region), 0, lines, 0, 4);

        player.sendMessage(plugin.getLang("create-sign.success", "region", region.getId(), "price", String.valueOf(price), "type", type));
        return true;
    }
}
