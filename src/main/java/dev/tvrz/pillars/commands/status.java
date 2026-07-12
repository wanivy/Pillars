package dev.tvrz.pillars.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class status implements CommandExecutor {

    public status(JavaPlugin plugin) {
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("pillars.status")) {
            sender.sendMessage("У вас нет прав для этой команды!");
            return true;
        }
        sender.sendMessage("[Pillars] Status: started");
        return true;
    }
}
