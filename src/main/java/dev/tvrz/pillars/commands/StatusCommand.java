package dev.tvrz.pillars.commands;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.NonNull;

public class StatusCommand implements CommandExecutor {

    public StatusCommand() {}
    private static final MiniMessage MM = MiniMessage.miniMessage();

    @Override
    public boolean onCommand(CommandSender sender, @NonNull Command command, @NonNull String label, String @NonNull [] args) {
        if (!sender.hasPermission("pillars.status")) {
            sender.sendMessage(MM.deserialize("У вас нет прав для этой команды!"));
            return true;
        }
        sender.sendMessage(MM.deserialize("[Pillars] Status: started"));
        return true;
    }
}
