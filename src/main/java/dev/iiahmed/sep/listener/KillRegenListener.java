package dev.iiahmed.sep.listener;

import dev.iiahmed.sep.StrikeExtraPlaceholders;
import ga.strikepractice.StrikePractice;
import ga.strikepractice.api.StrikePracticeAPI;
import ga.strikepractice.battlekit.BattleKit;
import ga.strikepractice.playerkits.PlayerKits;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.List;

/**
 * Своя реализация killregen без сброса эффектов.
 * Выключи killregen в StrikePractice и используй это.
 */
public class KillRegenListener implements Listener {

    private final StrikeExtraPlaceholders plugin;
    private final StrikePracticeAPI api;
    private final List<String> enabledKits;
    private final boolean useEditedKits;
    private final boolean refillKit;
    private final boolean healPlayer;

    public KillRegenListener(StrikeExtraPlaceholders plugin) {
        this.plugin = plugin;
        this.api = StrikePractice.getAPI();
        this.enabledKits = plugin.getConfig().getStringList("killregen.kits");
        this.useEditedKits = plugin.getConfig().getBoolean("killregen.use-edited-kits", true);
        this.refillKit = plugin.getConfig().getBoolean("killregen.refill-kit", true);
        this.healPlayer = plugin.getConfig().getBoolean("killregen.heal", true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer == null || killer.equals(victim)) return;

        if (!api.isInFight(killer)) return;

        BattleKit kit = api.getKit(killer);
        if (kit == null) return;

        if (!isKitEnabled(kit)) {
            plugin.debug("KillRegen: кит " + kit.getName() + " не в списке");
            return;
        }

        plugin.debug("KillRegen: обрабатываем убийство для " + killer.getName());
        
        // Сохраняем текущие эффекты
        java.util.Collection<org.bukkit.potion.PotionEffect> savedEffects = 
                new java.util.ArrayList<>(killer.getActivePotionEffects());
        
        plugin.debug("KillRegen: сохранено " + savedEffects.size() + " эффектов");

        // Хилим игрока
        if (healPlayer) {
            killer.setHealth(killer.getMaxHealth());
            killer.setFoodLevel(20);
            killer.setSaturation(20f);
            plugin.debug("KillRegen: " + killer.getName() + " исцелён");
        }

        // Выдаём кит (отредактированный если есть)
        if (refillKit) {
            BattleKit kitToGive = kit;
            
            if (useEditedKits) {
                PlayerKits playerKits = api.getPlayerKits(killer);
                if (playerKits != null) {
                    BattleKit editedKit = playerKits.getEditedKit(kit, true);
                    if (editedKit != null) {
                        kitToGive = editedKit;
                        plugin.debug("KillRegen: используем отредактированный кит для " + killer.getName());
                    }
                }
            }
            
            // Рефилим инвентарь — добавляем недостающие предметы, не трогая расположение
            refillInventory(killer, kitToGive);
            plugin.debug("KillRegen: кит рефилнут для " + killer.getName());
        }
        
        // Восстанавливаем эффекты после выдачи кита
        for (org.bukkit.potion.PotionEffect effect : savedEffects) {
            killer.addPotionEffect(effect, true);
        }
        plugin.debug("KillRegen: восстановлено " + savedEffects.size() + " эффектов");
    }

    private boolean isKitEnabled(BattleKit kit) {
        String kitName = kit.getName().toLowerCase();
        for (String k : enabledKits) {
            if (kitName.contains(k.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Рефилит инвентарь — добавляет недостающие предметы до нужного количества.
     * Простой подход: для каждого типа предмета в ките считаем сколько нужно и сколько есть.
     */
    private void refillInventory(Player player, BattleKit kit) {
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        java.util.List<org.bukkit.inventory.ItemStack> kitItems = kit.getInventory();
        
        // Группируем предметы кита по isSimilar()
        java.util.List<org.bukkit.inventory.ItemStack> uniqueKitItems = new java.util.ArrayList<>();
        java.util.List<Integer> kitAmounts = new java.util.ArrayList<>();
        
        for (org.bukkit.inventory.ItemStack item : kitItems) {
            if (item == null || item.getType() == org.bukkit.Material.AIR) continue;
            
            // Ищем похожий предмет в списке
            boolean found = false;
            for (int i = 0; i < uniqueKitItems.size(); i++) {
                if (uniqueKitItems.get(i).isSimilar(item)) {
                    kitAmounts.set(i, kitAmounts.get(i) + item.getAmount());
                    found = true;
                    break;
                }
            }
            if (!found) {
                uniqueKitItems.add(item.clone());
                kitAmounts.add(item.getAmount());
            }
        }
        
        // Для каждого уникального предмета из кита проверяем сколько есть у игрока
        for (int i = 0; i < uniqueKitItems.size(); i++) {
            org.bukkit.inventory.ItemStack kitItem = uniqueKitItems.get(i);
            int needed = kitAmounts.get(i);
            
            // Считаем сколько такого предмета есть у игрока
            int has = 0;
            for (org.bukkit.inventory.ItemStack invItem : inv.getContents()) {
                if (invItem != null && kitItem.isSimilar(invItem)) {
                    has += invItem.getAmount();
                }
            }
            
            int toAdd = needed - has;
            plugin.debug("KillRegen: " + kitItem.getType().name() + " нужно=" + needed + " есть=" + has + " добавить=" + toAdd);
            
            if (toAdd > 0) {
                org.bukkit.inventory.ItemStack toGive = kitItem.clone();
                toGive.setAmount(toAdd);
                inv.addItem(toGive);
                plugin.debug("KillRegen: добавлено " + toAdd + "x " + kitItem.getType().name());
            }
        }
        
        // Рефилим броню
        if (kit.getHelmet() != null && inv.getHelmet() == null) {
            inv.setHelmet(kit.getHelmet().clone());
        }
        if (kit.getChestplate() != null && inv.getChestplate() == null) {
            inv.setChestplate(kit.getChestplate().clone());
        }
        if (kit.getLeggings() != null && inv.getLeggings() == null) {
            inv.setLeggings(kit.getLeggings().clone());
        }
        if (kit.getBoots() != null && inv.getBoots() == null) {
            inv.setBoots(kit.getBoots().clone());
        }
    }
}
