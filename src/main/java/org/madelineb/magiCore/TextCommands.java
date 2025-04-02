package org.madelineb.magiCore;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.ChatColor;

public class TextCommands implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Depending on the command label, send back a placeholder message
        switch(label.toLowerCase()) {
            case "placeholderone":
                sender.sendMessage(ChatColor.GREEN + "This is the placeholder text for command one.");
                return true;
            case "placeholdertwo":
                sender.sendMessage(ChatColor.YELLOW + "This is the placeholder text for command two.");
                return true;
            case "placeholderthree":
                sender.sendMessage(ChatColor.AQUA + "This is the placeholder text for command three.");
                return true;
            default:
                return false; // Command not recognized
        }
    }
}
