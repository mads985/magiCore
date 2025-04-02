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

    // Constructor receives the plugin instance, similar to CombatTagManager.
    public EconomyManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.goldCoinKey = new NamespacedKey(plugin, "gold_coin");
        this.soulKey = new NamespacedKey(plugin, "soul");
    }

    /**
     * Creates a valid Gold Coin item with the required metadata.
     * Using Material.ECHO_SHARD as a placeholder for the gold coin.
     */
    public ItemStack createGoldCoin(int amount) {
        ItemStack coin = new ItemStack(Material.ECHO_SHARD, amount); // Placeholder item (replace with actual material if needed)
        ItemMeta meta = coin.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Gold Coin");
        // Tag the item as a valid coin.
        meta.getPersistentDataContainer().set(goldCoinKey, PersistentDataType.BYTE, (byte) 1);
        // Set custom model data for resource pack texture, if needed.
        meta.setCustomModelData(9999);
        coin.setItemMeta(meta);
        return coin;
    }

    /**
     * Checks if an item is a valid Gold Coin.
     */
    public boolean isValidGoldCoin(ItemStack item) {
        if (item == null) return false;
        if (item.getType() != Material.ECHO_SHARD) return false;
        if (!item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        Byte data = meta.getPersistentDataContainer().get(goldCoinKey, PersistentDataType.BYTE);
        if (data == null || data != 1) return false;
        if (!meta.hasDisplayName() || !meta.getDisplayName().equals(ChatColor.GOLD + "Gold Coin")) return false;
        return true;
    }

    /**
     * Counts valid coins in a given inventory (regular inventory or ender chest).
     */
    public int countGoldCoinsInInventory(Inventory inv) {
        int count = 0;
        for (ItemStack item : inv.getContents()) {
            if (item == null) continue;
            if (isValidGoldCoin(item)) {
                count += item.getAmount();
            }
        }
        return count;
    }

    /**
     * Counts the total number of gold coins a player has across their inventory and ender chest.
     */
    public int getTotalGoldCoins(Player player) {
        int total = countGoldCoinsInInventory(player.getInventory());
        total += countGoldCoinsInInventory(player.getEnderChest());
        return total;
    }

    /**
     * Creates a valid Soul item with the required metadata.
     * Using Material.ENDER_PEARL as a placeholder for the soul.
     */
    public ItemStack createSoul(int amount) {
        ItemStack soul = new ItemStack(Material.ENDER_PEARL, amount); // Placeholder item (replace with actual material if needed)
        ItemMeta meta = soul.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_PURPLE + "Soul");
        // Tag the item as a valid soul.
        meta.getPersistentDataContainer().set(soulKey, PersistentDataType.BYTE, (byte) 1);
        // Set custom model data for resource pack texture, if needed.
        meta.setCustomModelData(9998);
        soul.setItemMeta(meta);
        return soul;
    }

    /**
     * Checks if an item is a valid Soul.
     */
    public boolean isValidSoul(ItemStack item) {
        if (item == null) return false;
        if (item.getType() != Material.ENDER_PEARL) return false;
        if (!item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        Byte data = meta.getPersistentDataContainer().get(soulKey, PersistentDataType.BYTE);
        if (data == null || data != 1) return false;
        if (!meta.hasDisplayName() || !meta.getDisplayName().equals(ChatColor.DARK_PURPLE + "Soul")) return false;
        return true;
    }

    /**
     * Counts valid souls in a given inventory (regular inventory or ender chest).
     */
    public int countSoulsInInventory(Inventory inv) {
        int count = 0;
        for (ItemStack item : inv.getContents()) {
            if (item == null) continue;
            if (isValidSoul(item)) {
                count += item.getAmount();
            }
        }
        return count;
    }

    /**
     * Counts the total number of souls a player has across their inventory and ender chest.
     */
    public int getTotalSouls(Player player) {
        int total = countSoulsInInventory(player.getInventory());
        total += countSoulsInInventory(player.getEnderChest());
        return total;
    }

    /**
     * Handles the /bag and /givegold commands.
     */
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
            ItemStack coinStack = createGoldCoin(amount);
            target.getInventory().addItem(coinStack);
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
            ItemStack soulStack = createSoul(amount);
            target.getInventory().addItem(soulStack);
            sender.sendMessage(ChatColor.GREEN + "Gave " + amount + " Souls to " + target.getName() + ".");
            target.sendMessage(ChatColor.GREEN + "You received " + amount + " Souls.");
            return true;
        }
        return false;
    }
}
