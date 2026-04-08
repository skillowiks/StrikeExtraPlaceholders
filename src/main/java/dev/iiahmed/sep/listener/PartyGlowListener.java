package dev.iiahmed.sep.listener;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers.ItemSlot;
import com.comphenix.protocol.wrappers.Pair;
import dev.iiahmed.sep.StrikeExtraPlaceholders;
import ga.strikepractice.events.FightEndEvent;
import ga.strikepractice.events.PartyDisbandEvent;
import ga.strikepractice.events.PartySplitStartEvent;
import ga.strikepractice.events.PartyVsPartyStartEvent;
import ga.strikepractice.fights.party.partyfights.PartySplit;
import ga.strikepractice.party.Party;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PartyGlowListener implements Listener {

    private final StrikeExtraPlaceholders plugin;
    private final ProtocolManager protocolManager;
    private final PacketAdapter equipmentListener;

    private final ItemStack greenHelmet;
    private final ItemStack greenChest;
    private final ItemStack greenLegs;
    private final ItemStack greenBoots;

    // Быстрый лукап: entityId viewer -> Set<entityId> тиммейтов
    // ConcurrentHashMap т.к. packet listener может вызываться из netty thread
    private final Map<Integer, Set<Integer>> teamsByEntityId = new ConcurrentHashMap<>();

    // Для cleanup: имя игрока -> set имён тиммейтов (используется только в main thread)
    private final Map<String, Set<String>> teamsByName = new HashMap<>();

    public PartyGlowListener(StrikeExtraPlaceholders plugin) {
        this.plugin = plugin;
        this.protocolManager = ProtocolLibrary.getProtocolManager();

        greenHelmet = createGreenArmor(Material.LEATHER_HELMET);
        greenChest = createGreenArmor(Material.LEATHER_CHESTPLATE);
        greenLegs = createGreenArmor(Material.LEATHER_LEGGINGS);
        greenBoots = createGreenArmor(Material.LEATHER_BOOTS);

        equipmentListener = new PacketAdapter(
                plugin, ListenerPriority.HIGH, PacketType.Play.Server.ENTITY_EQUIPMENT) {
            @Override
            public void onPacketSending(PacketEvent event) {
                handleEquipmentPacket(event);
            }
        };
        protocolManager.addPacketListener(equipmentListener);
    }

    public void shutdown() {
        protocolManager.removePacketListener(equipmentListener);
        teamsByEntityId.clear();
        teamsByName.clear();
    }

    // ==================== PACKET LISTENER ====================

    private void handleEquipmentPacket(PacketEvent event) {
        // Ранний выход — если нет активных боёв, ничего не делаем.
        // Это O(1) проверка, почти бесплатно.
        if (teamsByEntityId.isEmpty()) return;
        if (event.isCancelled()) return;

        Player viewer = event.getPlayer();
        if (viewer == null) return;

        // O(1) лукап по entityId — без создания строк, без getName()
        Set<Integer> teammates = teamsByEntityId.get(viewer.getEntityId());
        if (teammates == null) return;

        int targetId = event.getPacket().getIntegers().read(0);
        if (!teammates.contains(targetId)) return;

        // Подменяем только слоты брони
        PacketContainer packet = event.getPacket();
        List<Pair<ItemSlot, ItemStack>> pairs = packet.getSlotStackPairLists().read(0);
        if (pairs == null || pairs.isEmpty()) return;

        List<Pair<ItemSlot, ItemStack>> newPairs = null;
        for (int i = 0; i < pairs.size(); i++) {
            Pair<ItemSlot, ItemStack> pair = pairs.get(i);
            ItemStack green = getGreenForSlot(pair.getFirst());
            if (green != null) {
                if (newPairs == null) {
                    newPairs = new ArrayList<>(pairs); // копируем только если нужно
                }
                newPairs.set(i, new Pair<>(pair.getFirst(), green));
            }
        }
        if (newPairs != null) {
            packet.getSlotStackPairLists().write(0, newPairs);
        }
    }

    /** Возвращает зелёную броню для слота, или null если слот не броня */
    private ItemStack getGreenForSlot(ItemSlot slot) {
        return switch (slot) {
            case HEAD -> greenHelmet;
            case CHEST -> greenChest;
            case LEGS -> greenLegs;
            case FEET -> greenBoots;
            default -> null;
        };
    }

    // ==================== СТАРТ БОЁВ ====================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPartyVsParty(PartyVsPartyStartEvent event) {
        addPartyTeam(event.getChallangerParty());
        addPartyTeam(event.getEnemyParty());
        rebuildEntityIdMap();
        sendInitialFakeArmor();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPartySplit(PartySplitStartEvent event) {
        PartySplit fight = event.getFight();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            addTeam(new HashSet<>(fight.getTeam1()));
            addTeam(new HashSet<>(fight.getTeam2()));
            rebuildEntityIdMap();
            sendInitialFakeArmor();
        }, 20L);
    }

    // ==================== КОНЕЦ / ОЧИСТКА ====================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFightEnd(FightEndEvent event) {
        List<Player> players = event.getFight().getPlayersInFight();
        if (players != null && !players.isEmpty()) {
            Set<String> names = new HashSet<>(players.size());
            for (Player p : players) names.add(p.getName());
            cleanup(names);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPartyDisband(PartyDisbandEvent event) {
        Set<String> members = new HashSet<>();
        for (Object name : event.getParty().getMembersNames()) {
            members.add((String) name);
        }
        cleanup(members);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        String name = event.getPlayer().getName();
        if (teamsByName.containsKey(name)) {
            cleanup(Set.of(name));
        }
    }

    // ==================== ВНУТРЕННЯЯ ЛОГИКА ====================

    private void addPartyTeam(Party party) {
        Set<String> names = new HashSet<>();
        for (Object m : party.getMembersNames()) names.add((String) m);
        addTeam(names);
    }

    private void addTeam(Set<String> names) {
        for (String name : names) {
            teamsByName.put(name, names);
        }
    }

    /** Перестраивает entityId карту из teamsByName. Вызывается только из main thread. */
    private void rebuildEntityIdMap() {
        teamsByEntityId.clear();
        Set<Set<String>> processed = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Set<String> team : teamsByName.values()) {
            if (!processed.add(team)) continue;

            // Собираем entityId всех онлайн членов команды
            Map<String, Integer> nameToId = new HashMap<>();
            for (String name : team) {
                Player p = Bukkit.getPlayerExact(name);
                if (p != null && p.isOnline()) {
                    nameToId.put(name, p.getEntityId());
                }
            }

            // Для каждого члена — set entityId тиммейтов (без себя)
            for (var entry : nameToId.entrySet()) {
                Set<Integer> teammateIds = new HashSet<>();
                for (var other : nameToId.entrySet()) {
                    if (!other.getKey().equals(entry.getKey())) {
                        teammateIds.add(other.getValue());
                    }
                }
                teamsByEntityId.put(entry.getValue(), teammateIds);
            }
        }
    }

    private void cleanup(Set<String> playerNames) {
        Set<String> allRelated = new HashSet<>(playerNames);
        for (String name : playerNames) {
            Set<String> team = teamsByName.get(name);
            if (team != null) allRelated.addAll(team);
        }
        for (String name : allRelated) {
            teamsByName.remove(name);
        }

        // Перестраиваем entityId карту
        rebuildEntityIdMap();

        // Отправляем реальную броню (пакеты уже не будут подменяться)
        for (String name : allRelated) {
            Player viewer = Bukkit.getPlayerExact(name);
            if (viewer == null || !viewer.isOnline()) continue;
            for (String other : allRelated) {
                if (other.equals(name)) continue;
                Player target = Bukkit.getPlayerExact(other);
                if (target != null && target.isOnline()) {
                    sendRealEquipment(viewer, target);
                }
            }
        }
    }

    private void sendInitialFakeArmor() {
        Set<Set<String>> processed = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Set<String> team : teamsByName.values()) {
            if (!processed.add(team)) continue;
            for (String viewerName : team) {
                Player viewer = Bukkit.getPlayerExact(viewerName);
                if (viewer == null || !viewer.isOnline()) continue;
                for (String targetName : team) {
                    if (targetName.equals(viewerName)) continue;
                    Player target = Bukkit.getPlayerExact(targetName);
                    if (target == null || !target.isOnline()) continue;
                    sendFakeEquipment(viewer, target);
                }
            }
        }
    }

    // ==================== ПАКЕТЫ ====================

    private void sendFakeEquipment(Player viewer, Player target) {
        try {
            PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.ENTITY_EQUIPMENT);
            packet.getIntegers().write(0, target.getEntityId());
            packet.getSlotStackPairLists().write(0, List.of(
                new Pair<>(ItemSlot.HEAD, greenHelmet),
                new Pair<>(ItemSlot.CHEST, greenChest),
                new Pair<>(ItemSlot.LEGS, greenLegs),
                new Pair<>(ItemSlot.FEET, greenBoots)
            ));
            protocolManager.sendServerPacket(viewer, packet);
        } catch (Exception ignored) {}
    }

    private void sendRealEquipment(Player viewer, Player target) {
        try {
            PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.ENTITY_EQUIPMENT);
            packet.getIntegers().write(0, target.getEntityId());
            packet.getSlotStackPairLists().write(0, List.of(
                new Pair<>(ItemSlot.HEAD, orAir(target.getInventory().getHelmet())),
                new Pair<>(ItemSlot.CHEST, orAir(target.getInventory().getChestplate())),
                new Pair<>(ItemSlot.LEGS, orAir(target.getInventory().getLeggings())),
                new Pair<>(ItemSlot.FEET, orAir(target.getInventory().getBoots()))
            ));
            protocolManager.sendServerPacket(viewer, packet);
        } catch (Exception ignored) {}
    }

    private static ItemStack orAir(ItemStack item) {
        return item != null ? item : new ItemStack(Material.AIR);
    }

    private static ItemStack createGreenArmor(Material material) {
        ItemStack item = new ItemStack(material);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        if (meta != null) {
            meta.setColor(Color.GREEN);
            item.setItemMeta(meta);
        }
        return item;
    }
}
