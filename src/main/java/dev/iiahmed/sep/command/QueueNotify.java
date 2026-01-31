package dev.iiahmed.sep.command;

import dev.iiahmed.sep.StrikeExtraPlaceholders;
import dev.iiahmed.sep.listener.QueueListener;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QueueNotify implements CommandExecutor {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Только для игроков!");
            return true;
        }

        Player player = (Player) sender;
        boolean nowDisabled = QueueListener.toggleNotifications(player.getUniqueId());

        if (nowDisabled) {
            player.sendMessage(getMessage("notifications-disabled"));
        } else {
            player.sendMessage(getMessage("notifications-enabled"));
        }

        return true;
    }

    private String getMessage(String path) {
        String msg = StrikeExtraPlaceholders.getInstance().getConfig().getString("quickranked." + path);
        if (msg == null) {
            if (path.equals("notifications-disabled")) {
                return translateHex("&#FF6B6B✖ &#C9E4DEУведомления о поиске матчей &#FF6B6Bотключены");
            } else if (path.equals("notifications-enabled")) {
                return translateHex("&#90FBFE✔ &#C9E4DEУведомления о поиске матчей &#90FBFEвключены");
            }
            return ChatColor.RED + "Неизвестная ошибка";
        }
        return translateHex(msg);
    }

    private String translateHex(String message) {
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                replacement.append("§").append(c);
            }
            matcher.appendReplacement(buffer, replacement.toString());
        }
        matcher.appendTail(buffer);

        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }
}
