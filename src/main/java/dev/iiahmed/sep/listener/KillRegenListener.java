package dev.iiahmed.sep.listener;

import dev.iiahmed.sep.StrikeExtraPlaceholders;
import ga.strikepractice.StrikePractice;
import ga.strikepractice.api.StrikePracticeAPI;
import ga.strikepractice.battlekit.BattleKit;
import ga.strikepractice.playerkits.PlayerKits;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Своя реализация killregen без сброса эффектов.
 * Выключи killregen в StrikePractice и используй это.
 */
public class KillRegenListener implements Listener {

    private final StrikePracticeAPI api;
    private final List<String> enabledKits;
    private final boolean useEditedKits;
    private final boolean refillKit;
    private final boolean healPlayer;

    public KillRegenListener(StrikeExtraPlaceholders plugin) {
        this.api = StrikePractice.getAPI();
        this.enabledKits = plugin.getConfig().getStringList("killregen.kits");
        this.useEditedKits = plugin.getConfig().getBoolean("killregen.use-edited-kits", true);
        this.refillKit = plugin.getConfig().getBoolean("killregen.refill-kit", true);
        this.healPlayer = plugin.getConfig().getBoolean("killregen.heal", true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || killer.equals(event.getEntity())) return;
        if (!api.isInFight(killer)) return;

        BattleKit kit = api.getKit(killer);
        if (kit == null || !isKitEnabled(kit)) return;

        // Сохраняем эффекты
        Collection<PotionEffect> savedEffects = new ArrayList<>(killer.getActivePotionEffects());

        // Хилим
        if (healPlayer) {
            var attr = killer.getAttribute(Attribute.MAX_HEALTH);
            double maxHealth = attr != null ? attr.getValue() : 20.0;
            killer.setHealth(maxHealth);
            killer.setFoodLevel(20);
            killer.setSaturation(20f);
        }

        // Рефилим кит
        if (refillKit) {
            BattleKit kitToGive = kit;
            if (useEditedKits) {
                PlayerKits playerKits = api.getPlayerKits(killer);
                if (playerKits != null) {
                    BattleKit edited = playerKits.getEditedKit(kit, true);
                    if (edited != null) kitToGive = edited;
                }
            }
            refillInventory(killer, kitToGive);
        }

        // Восстанавливаем эффекты
        for (PotionEffect effect : savedEffects) {
            killer.addPotionEffect(effect);
        }
    }

    private boolean isKitEnabled(BattleKit kit) {
        String kitName = kit.getName().toLowerCase();
        for (String k : enabledKits) {
            if (kitName.contains(k.toLowerCase())) return true;
        }
        return false;
    }

    private void refillInventory(Player player, BattleKit kit) {
        PlayerInventory inv = player.getInventory();
        List<ItemStack> kitItems = kit.getInventory();

        // Группируем предметы кита по isSimilar()
        List<ItemStack> uniqueItems = new ArrayList<>();
        List<Integer> amounts = new ArrayList<>();

        for (ItemStack item : kitItems) {
            if (item == null || item.getType() == Material.AIR) continue;
            boolean found = false;
            for (int i = 0; i < uniqueItems.size(); i++) {
                if (uniqueItems.get(i).isSimilar(item)) {
                    amounts.set(i, amounts.get(i) + item.getAmount());
                    found = true;
                    break;
                }
            }
            if (!found) {
                uniqueItems.add(item.clone());
                amounts.add(item.getAmount());
            }
        }

        // Добавляем недостающее
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < uniqueItems.size(); i++) {
            ItemStack kitItem = uniqueItems.get(i);
            int needed = amounts.get(i);
            int has = 0;
            for (ItemStack invItem : contents) {
                if (invItem != null && kitItem.isSimilar(invItem)) {
                    has += invItem.getAmount();
                }
            }
            int toAdd = needed - has;
            if (toAdd > 0) {
                ItemStack toGive = kitItem.clone();
                toGive.setAmount(toAdd);
                inv.addItem(toGive);
            }
        }

        // Броня
        if (kit.getHelmet() != null && inv.getHelmet() == null) inv.setHelmet(kit.getHelmet().clone());
        if (kit.getChestplate() != null && inv.getChestplate() == null) inv.setChestplate(kit.getChestplate().clone());
        if (kit.getLeggings() != null && inv.getLeggings() == null) inv.setLeggings(kit.getLeggings().clone());
        if (kit.getBoots() != null && inv.getBoots() == null) inv.setBoots(kit.getBoots().clone());
    }
}
