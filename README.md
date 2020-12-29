# PlotSigns
With this simple Bukkit plugin you can sell and buy WorldGuard regions either via a sign or via commands. You also have the ability to limit the amount of regions a player can buy of a certain type! (When not configured differently a player can only buy one region of a certain type in a world!) The type function uses the WorldGuard 6 custom flags api to register a custom `plot-type` flag!

[PlotGenerator](https://github.com/Minebench/PlotGenerator/) is compatible with this plugin! Use the `regionPrice` and `plotType` options to automatically setup regions to be buyable with PlotSigns.

Players can sell their own plots too if they have the permissions to do so (each owner of the region will get a fair share of the price) and you can impose a tax on all plot sales.

## Sign Setup

You can sell a region just by placing down signs with a certain sell tag on the first line. (`[Plot]` in the default config's `sign.sell` key) The second line contains the region's id, the third one the price and the fourth the plot's type. (Leave empty if it shouldn't have one)

![PlotSigns sign explanation](https://lambda.sx/TIW.png) ![PlotSigns sign example](https://lambda.sx/V1j.png)

## Commands

| Command                             | Explanation                                       |
|-------------------------------------|---------------------------------------------------|
| `/plotsigns buy`                    | Buy the region that you are standing in           |
| `/plotsigns buy <regionid>`         | Buy specific region                               |
| `/plotsigns sell <regionid> <price>`| Sell a region                                     |
| `/plotsigns type <regionid> <type>` | Set the type of a region (sets `plot-type` flag)  |
| `/plotsigns sign <regionid>`        | Generate the text for a sell sign                 |
| `/plotsigns reload`                 | Reload the plugin config                          |

## Permissions

| Permission                              | Explanation                                                         |
|-----------------------------------------|---------------------------------------------------------------------|
| `plotsigns.command`                     | Gives permission to the plugin command                              |
| `plotsigns.command.buy`                 | Buy regions via the command                                         |
| `plotsigns.command.buy.byregionid`      | Buy a specific region via the command                               |
| `plotsigns.command.sell`                | Sell regions via the command                                        |
| `plotsigns.command.type`                | Set the type of a region via the command                            |
| `plotsigns.command.sign`                | Write a sell sign via the command                                   |
| `plotsigns.command.reload`              | Reload the plugin via the command                                   |
| `plotsigns.sign.purchase`               | Purchase a plot via right clicking on the sign                      |
| `plotsigns.sign.create`                 | Create plot signs                                                   |
| `plotsigns.sign.create.outside`         | Create plot signs outside of the plot                               |
| `plotsigns.sign.create.others`          | Create plot signs for plots that the player doesn't own             |
| `plotsigns.sign.create.type`            | Set type of plot                                                    |
| `plotsigns.sign.create.makebuyable`     | Make a previously not buyable region buyable (without the command)  |
| `plotsigns.type.<type>.<maxamount>`     | Allows a player to only buy a certain amount of regions of a type   |
| `plotsigns.type.<type>.unlimited`       | Buy onlimited regions of that type                                  |
| `plotsigns.group.<typegroup>`           | Allows a player to buy regions of a certain configured type group   |
| `plotsigns.group.<typegroup>.unlimited` | Buy unlimited regions of the type group                             |

## Config

```yaml
sign:
  sell: [Plot]
  sellformat:
  - ""   # ID line
  - "&8" # Region ID line
  - ""   # Price Line
  - "&8" # Type
  sold:
  - "Sold"
  - "to"
  - "%player%"
  - ""
tax: # Tax to be deducted from the region's price when the region is sold to another user
  fixed: 0.0 # Fixed tax
  share: 0.0 # Share of the price. Use 1.0 to not give the owner any money at all
type-counts:
  max-number: 9 # Maximum number to check the plotsigns.type.<type>.<number> permission for
  groups: # Predefined type groups. Use with plotsigns.group.<groupname>
    single: 1 # Allow players with plotsigns.group.single to only buy one region of that type in a world
    dozen: 12
lang:
  error:
    malformed-price: "&c%input% is not a valid price number!"
    unknown-region: "&cNo region with the name %region% found!"
    world-not-supported: "&cWorldGuard is not enabled in your world!"
  create-sign:
    success: "&aSell sign for region &e%region%&a created! Price: &e%price%&a, Type: &e%type%"
    no-permission: "&cYou don't have the permissions to create sell signs!"
    missing-region: "&cYou need to specify a region name on the second line!"
    missing-price: "&cYou need to specify a price on the third line!"
    sign-outside-region: "&cYour sell sign needs to be inside the region!"
    region-not-sellable: "&cThe region %region% can't be sold!"
    cant-set-type: "&cYou don't have the permissions to set the type of a region!"
    doesnt-own-plot: "&cYou don't have the permissions to create sell signs for plots that you don't own!"
  buy:
    bought-plot: "&aYou bought the plot &e%region%&a for &e%price%&a!"
    your-plot-sold: "&aYour plot &e%region%&a was sold to &e%buyer%&a for &e%price%&a! You've earned &e%earned%&a"
    not-enough-money: "&cYou don't have enough money to buy this plot!"
    maximum-type-count: "&cYou have already bought the maximum amount of plots of the type %type%!"
    not-for-sale: "&cThis plot is not for sale!"
    no-permission: "&cYou don't have the permissions to buy plots with sell signs!"
    price-mismatch: "&cError: The price on the sign (%sign%) does not match the price configured for this region (%region%)"
    right-mismatch: "&cError: The right on the sign (%sign%) does not match the right configured for this region (%region%)"
```

## Downloads

Releases can be downloaded from the [PlotSigns SpigotMC resource page](https://www.spigotmc.org/resources/plotsigns.33847/).

[Dev builds](https://ci.minebench.de/job/PlotSigns/) can be found on the Minebench jenkins server.

## License

```
Copyright (C) 2020 Max Lee aka Phoenix616 (max@themoep.de)

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
```
