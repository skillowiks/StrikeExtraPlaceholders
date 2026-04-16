package dev.iiahmed.sep.command;

import dev.iiahmed.sep.StrikeExtraPlaceholders;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * Перехватывает /kiteditor БЕЗ аргументов → открывает DeluxeMenus меню.
 * /kiteditor leave, /kiteditor funtime1 и т.д. → пропускает в StrikePractice.
 */
public class KitEditorCmd implements Listener {

    private final String menuName;

    public KitEditorCmd() {
        this.menuName = StrikeExtraPlaceholders.getInstance()
                .getConfig().getString("kiteditor.menu", "kiteditor");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage().trim();

        // Проверяем: /kiteditor без аргументов
        if (msg.equalsIgnoreCase("/kiteditor")) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            Bukkit.dispatchCommand(player, "dm open " + menuName);
        }
        // /kiteditor leave, /kiteditor funtime1 и т.д. — не трогаем, идёт в StrikePractice
    }
}
