package dev.iiahmed.sep.command;

import dev.iiahmed.sep.StrikeExtraPlaceholders;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DuelCmd implements CommandExecutor, Listener {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private final StrikeExtraPlaceholders plugin;
    private final Map<UUID, String[]> sessions = new HashMap<>();

    // Кэшированные данные — загружаются один раз
    private final Map<Integer, Integer> kitSlotToIndex = new HashMap<>();
    private final List<String> kitIds = new ArrayList<>();
    private final Map<Integer, Integer> roundSlotToIndex = new HashMap<>();
    private final List<Integer> roundValues = new ArrayList<>();

    // Кэшированные шаблоны содержимого инвентарей (создаются один раз)
    private ItemStack[] kitTemplate;
    private ItemStack[] roundTemplate;

    private String kitTitle;
    private int kitSize;
    private String roundTitle;
    private int roundSize;
    private String commandTemplate;

    // Флаг: игрок переходит из кит-меню в раунд-меню (не чистить сессию)
    private final Set<UUID> transitioning = new HashSet<>();

    public DuelCmd(StrikeExtraPlaceholders plugin) {
        this.plugin = plugin;
        loadDuelConfig();
    }

    private void loadDuelConfig() {
        File file = new File(plugin.getDataFolder(), "duel.yml");
        if (!file.exists()) plugin.saveResource("duel.yml", false);
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        kitTitle = hex(cfg.getString("kit_menu.title", "&nвыберите кит:"));
        kitSize = cfg.getInt("kit_menu.size", 54);
        roundTitle = hex(cfg.getString("round_menu.title", "&nкол-во раундов"));
        roundSize = cfg.getInt("round_menu.size", 27);
        commandTemplate = cfg.getString("command_template",
                "strikepractice:1v1 <player> <rounds> <kit>copy");

        // Стекло — один объект
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta gm = glass.getItemMeta();
        if (gm != null) { gm.setDisplayName(" "); glass.setItemMeta(gm); }

        // Строим шаблон кит-меню
        kitTemplate = new ItemStack[kitSize];
        Arrays.fill(kitTemplate, glass);

        ConfigurationSection kits = cfg.getConfigurationSection("kit_menu.kits");
        if (kits != null) {
            for (String id : kits.getKeys(false)) {
                String name = hex(kits.getString(id + ".name", id));
                String mat = kits.getString(id + ".material", "STONE");
                int slot = kits.getInt(id + ".slot", 0);

                int index = kitIds.size();
                kitIds.add(id);
                kitSlotToIndex.put(slot, index);
                if (slot >= 0 && slot < kitSize) {
                    kitTemplate[slot] = buildItem(mat, name);
                }
            }
        }

        // Строим шаблон раунд-меню
        roundTemplate = new ItemStack[roundSize];
        Arrays.fill(roundTemplate, glass);

        ConfigurationSection rounds = cfg.getConfigurationSection("round_menu.rounds");
        if (rounds != null) {
            for (String key : rounds.getKeys(false)) {
                int val = Integer.parseInt(key);
                String name = hex(rounds.getString(key + ".name", "Раунд " + key));
                String mat = rounds.getString(key + ".material", "CLOCK");
                int amount = rounds.getInt(key + ".amount", val);
                int slot = rounds.getInt(key + ".slot", 0);

                int index = roundValues.size();
                roundValues.add(val);
                roundSlotToIndex.put(slot, index);
                if (slot >= 0 && slot < roundSize) {
                    roundTemplate[slot] = buildItem(mat, name, amount);
                }
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;

        if (args.length == 0) {
            player.sendMessage(hex(plugin.getConfig().getString("duel.usage",
                    "&#90FBFE⚔ &#C9E4DEИспользование: &f/duel &#90FBFE<ник>")));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target != null && target.equals(player)) {
            player.sendMessage(hex(plugin.getConfig().getString("duel.self-duel-message",
                    "&cНельзя вызвать себя на дуэль!")));
            return true;
        }

        sessions.put(player.getUniqueId(), new String[]{args[0], null});
        openKitMenu(player);
        return true;
    }

    // Открытие меню — просто копируем кэшированный шаблон в новый инвентарь
    private void openKitMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, kitSize, kitTitle);
        inv.setContents(kitTemplate.clone());
        player.openInventory(inv);
    }

    private void openRoundMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, roundSize, roundTitle);
        inv.setContents(roundTemplate.clone());
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String[] session = sessions.get(player.getUniqueId());
        if (session == null) return;

        String title = event.getView().getTitle();
        int slot = event.getRawSlot();

        if (title.equals(kitTitle)) {
            event.setCancelled(true);
            Integer index = kitSlotToIndex.get(slot);
            if (index != null) {
                session[1] = kitIds.get(index);
                transitioning.add(player.getUniqueId());
                openRoundMenu(player);
            }
        } else if (title.equals(roundTitle)) {
            event.setCancelled(true);
            Integer index = roundSlotToIndex.get(slot);
            if (index != null) {
                String kit = session[1];
                String target = session[0];
                int rounds = roundValues.get(index);
                sessions.remove(player.getUniqueId());
                player.closeInventory();

                player.performCommand(commandTemplate
                        .replace("<player>", target)
                        .replace("<rounds>", String.valueOf(rounds))
                        .replace("<kit>", kit));
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();

        // Если переходим кит→раунды — не чистим
        if (transitioning.remove(uuid)) return;

        String title = event.getView().getTitle();
        if (title.equals(kitTitle) || title.equals(roundTitle)) {
            sessions.remove(uuid);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        sessions.remove(uuid);
        transitioning.remove(uuid);
    }

    private ItemStack buildItem(String material, String name) {
        return buildItem(material, name, 1);
    }

    private ItemStack buildItem(String material, String name, int amount) {
        Material mat;
        if (material.startsWith("basehead-")) {
            mat = Material.PLAYER_HEAD;
        } else {
            try { mat = Material.valueOf(material.toUpperCase()); }
            catch (IllegalArgumentException e) { mat = Material.STONE; }
        }
        ItemStack item = new ItemStack(mat, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.setDisplayName(name); item.setItemMeta(meta); }
        return item;
    }

    private String hex(String msg) {
        if (msg == null) return "";
        Matcher matcher = HEX_PATTERN.matcher(msg);
        StringBuffer buf = new StringBuffer();
        while (matcher.find()) {
            StringBuilder r = new StringBuilder("§x");
            for (char c : matcher.group(1).toCharArray()) r.append("§").append(c);
            matcher.appendReplacement(buf, r.toString());
        }
        matcher.appendTail(buf);
        return ChatColor.translateAlternateColorCodes('&', buf.toString());
    }
}
