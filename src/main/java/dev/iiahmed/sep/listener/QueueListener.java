package dev.iiahmed.sep.listener;

import dev.iiahmed.sep.StrikeExtraPlaceholders;
import ga.strikepractice.StrikePractice;
import ga.strikepractice.api.StrikePracticeAPI;
import ga.strikepractice.battlekit.BattleKit;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QueueListener {

    private final StrikeExtraPlaceholders plugin;
    private final Set<UUID> playersInQueue = new HashSet<>();
    private static final Set<UUID> disabledNotifications = new HashSet<>();
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private int taskId;

    // Кэшированный шаблон сообщения — пересоздаётся только при reload
    private String cachedMessageTemplate;

    public QueueListener(StrikeExtraPlaceholders plugin) {
        this.plugin = plugin;
    }

    public static boolean toggleNotifications(UUID uuid) {
        if (disabledNotifications.contains(uuid)) {
            disabledNotifications.remove(uuid);
            return false;
        } else {
            disabledNotifications.add(uuid);
            return true;
        }
    }

    public static boolean hasNotificationsDisabled(UUID uuid) {
        return disabledNotifications.contains(uuid);
    }

    public void start() {
        StrikePracticeAPI api = StrikePractice.getAPI();
        cachedMessageTemplate = getMessage("queue-join-broadcast");

        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();
                boolean inQueue = api.isInQueue(player);

                if (inQueue && !playersInQueue.contains(uuid)) {
                    playersInQueue.add(uuid);

                    BattleKit kit = api.getQueuedKit(player);
                    if (kit == null) continue;
                    String kitName = kit.getName();
                    if (kitName.toLowerCase().contains("ffa")) continue;

                    String kitDisplay = kit.getFancyName();
                    if (kitDisplay == null || kitDisplay.isEmpty()) {
                        if (kit.getIcon() != null && kit.getIcon().hasItemMeta()
                                && kit.getIcon().getItemMeta() != null
                                && kit.getIcon().getItemMeta().hasDisplayName()) {
                            kitDisplay = kit.getIcon().getItemMeta().getDisplayName();
                        } else {
                            kitDisplay = kitName;
                        }
                    }

                    String message = cachedMessageTemplate
                            .replace("<player>", player.getName())
                            .replace("<kit>", kitDisplay);

                    for (Player online : Bukkit.getOnlinePlayers()) {
                        if (!disabledNotifications.contains(online.getUniqueId())) {
                            online.sendMessage(message);
                        }
                    }
                } else if (!inQueue && playersInQueue.contains(uuid)) {
                    playersInQueue.remove(uuid);
                }
            }
        }, 0L, 40L);
    }

    public void stop() {
        Bukkit.getScheduler().cancelTask(taskId);
        playersInQueue.clear();
    }

    private String getMessage(String path) {
        String msg = plugin.getConfig().getString("quickranked." + path);
        if (msg == null) {
            if (path.equals("queue-join-broadcast")) {
                return translateHex("&r樂 &#C9E4DEИгрок &b<player> &#C9E4DEначал поиск с китом &b<kit>");
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
