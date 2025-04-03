package org.madelineb.magiCore;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public class EconomyManager implements CommandExecutor {

    private final JavaPlugin plugin;
    private final NamespacedKey goldCoinKey;
    private final NamespacedKey soulKey;

    public EconomyManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.goldCoinKey = new NamespacedKey(plugin, "gold_coin");
        this.soulKey = new NamespacedKey(plugin, "soul");
    }

    public ItemStack createGoldCoin(int amount) {
        ItemStack coin = new ItemStack(Material.ECHO_SHARD, amount);
        ItemMeta meta = coin.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Gold Coin");
        meta.getPersistentDataContainer().set(goldCoinKey, PersistentDataType.BYTE, (byte) 1);
        meta.setCustomModelData(9999);
        coin.setItemMeta(meta);
        return coin;
    }

    public boolean isValidGoldCoin(ItemStack item) {
        if (item == null || item.getType() != Material.ECHO_SHARD || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        Byte data = meta.getPersistentDataContainer().get(goldCoinKey, PersistentDataType.BYTE);
        return data != null && data == 1 && meta.hasDisplayName() && meta.getDisplayName().equals(ChatColor.GOLD + "Gold Coin");
    }

    public int countGoldCoinsInInventory(Inventory inv) {
        int count = 0;
        for (ItemStack item : inv.getContents()) {
            if (item != null && isValidGoldCoin(item)) count += item.getAmount();
        }
        return count;
    }

    public int getTotalGoldCoins(Player player) {
        return countGoldCoinsInInventory(player.getInventory()) + countGoldCoinsInInventory(player.getEnderChest());
    }

    public ItemStack createSoul(int amount) {
        ItemStack soul = new ItemStack(Material.ENDER_PEARL, amount);
        ItemMeta meta = soul.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_PURPLE + "Soul");
        meta.getPersistentDataContainer().set(soulKey, PersistentDataType.BYTE, (byte) 1);
        meta.setCustomModelData(9998);
        soul.setItemMeta(meta);
        return soul;
    }

    public boolean isValidSoul(ItemStack item) {
        if (item == null || item.getType() != Material.ENDER_PEARL || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        Byte data = meta.getPersistentDataContainer().get(soulKey, PersistentDataType.BYTE);
        return data != null && data == 1 && meta.hasDisplayName() && meta.getDisplayName().equals(ChatColor.DARK_PURPLE + "Soul");
    }

    public int countSoulsInInventory(Inventory inv) {
        int count = 0;
        for (ItemStack item : inv.getContents()) {
            if (item != null && isValidSoul(item)) count += item.getAmount();
        }
        return count;
    }

    public int getTotalSouls(Player player) {
        return countSoulsInInventory(player.getInventory()) + countSoulsInInventory(player.getEnderChest());
    }

    /**
     * Adds souls to a player's inventory.
     */
    public void addSouls(Player player, int amount) {
        ItemStack soulStack = createSoul(amount);
        player.getInventory().addItem(soulStack);
        player.sendMessage(ChatColor.DARK_PURPLE + "You received " + amount + " Souls.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("bag")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
                return true;
            }
            Player player = (Player) sender;
            int totalGold = getTotalGoldCoins(player);
            int totalSouls = getTotalSouls(player);
            player.sendMessage(ChatColor.DARK_GREEN + "Bag:");
            player.sendMessage(ChatColor.GREEN + "You have " + totalGold + " Gold Coins.");
            player.sendMessage(ChatColor.GREEN + "You have " + totalSouls + " Souls.");
            return true;
        }

        if (command.getName().equalsIgnoreCase("givegold")) {
            if (!sender.hasPermission("economy.give")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Usage: /givegold <player> <amount>");
                return true;
            }
            Player target = plugin.getServer().getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found.");
                return true;
            }
            int amount;
            try {
                amount = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Amount must be a number.");
                return true;
            }
            target.getInventory().addItem(createGoldCoin(amount));
            sender.sendMessage(ChatColor.GREEN + "Gave " + amount + " Gold Coins to " + target.getName() + ".");
            target.sendMessage(ChatColor.GREEN + "You received " + amount + " Gold Coins.");
            return true;
        }

        if (command.getName().equalsIgnoreCase("givesouls")) {
            if (!sender.hasPermission("economy.give")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Usage: /givesouls <player> <amount>");
                return true;
            }
            Player target = plugin.getServer().getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found.");
                return true;
            }
            int amount;
            try {
                amount = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Amount must be a number.");
                return true;
            }
            addSouls(target, amount);
            sender.sendMessage(ChatColor.GREEN + "Gave " + amount + " Souls to " + target.getName() + ".");
            return true;
        }
        return false;
    }
}
