package dev.iiahmed.sep.command;

import dev.iiahmed.sep.StrikeExtraPlaceholders;
import ga.strikepractice.StrikePractice;
import ga.strikepractice.api.StrikePracticeAPI;
import ga.strikepractice.battlekit.BattleKit;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class QuickRanked implements CommandExecutor {

    private final StrikeExtraPlaceholders plugin = StrikeExtraPlaceholders.getInstance();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Только для игроков!");
            return true;
        }

        Player player = (Player) sender;
        StrikePracticeAPI api = StrikePractice.getAPI();

        if (api.isInFight(player)) {
            player.sendMessage(getMessage("already-in-fight"));
            return true;
        }

        if (api.isInQueue(player)) {
            player.sendMessage(getMessage("already-in-queue"));
            return true;
        }

        // Ищем игрока в ranked очереди
        String foundKit = null;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(player)) continue;
            if (api.isInQueue(online) && api.isRanked(online)) {
                BattleKit kit = api.getQueuedKit(online);
                if (kit != null) {
                    foundKit = kit.getName();
                    break;
                }
            }
        }

        if (foundKit == null) {
            player.sendMessage(getMessage("no-queue-found"));
            return true;
        }

        // Выполняем команду ranked от имени игрока
        player.performCommand("ranked " + foundKit);

        return true;
    }

    private String getMessage(String path) {
        String msg = plugin.getConfig().getString("quickranked." + path);
        if (msg == null) {
            switch (path) {
                case "already-in-fight": return ChatColor.RED + "Ты уже в бою!";
                case "already-in-queue": return ChatColor.RED + "Ты уже в очереди!";
                case "no-queue-found": return ChatColor.RED + "Нет доступных очередей с игроками!";
                default: return ChatColor.RED + "Неизвестная ошибка";
            }
        }
        return ChatColor.translateAlternateColorCodes('&', msg);
    }
}
