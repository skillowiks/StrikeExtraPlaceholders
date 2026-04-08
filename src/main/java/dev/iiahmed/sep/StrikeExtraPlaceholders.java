package dev.iiahmed.sep;

import dev.iiahmed.sep.command.QuickRanked;
import dev.iiahmed.sep.command.QueueNotify;
import dev.iiahmed.sep.command.SEP;
import dev.iiahmed.sep.listener.KillRegenListener;
import dev.iiahmed.sep.listener.PartyGlowListener;
import dev.iiahmed.sep.listener.QueueListener;
import dev.iiahmed.sep.util.Expantion;
import ga.strikepractice.StrikePractice;
import ga.strikepractice.api.StrikePracticeAPI;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.logging.Level;

@Getter
public final class StrikeExtraPlaceholders extends JavaPlugin {

    @Getter
    private static StrikeExtraPlaceholders instance;
    int taskID;
    private HashMap<String, Integer> queueAmounts;
    private HashMap<String, Integer> fightAmounts;
    private boolean debug;
    private QueueListener queueListener;
    private PartyGlowListener partyGlowListener;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        this.debug = getConfig().getBoolean("settings.debug");
        runTask();
        new Expantion().register();

        var sepCmd = getCommand("SEP");
        if (sepCmd != null) sepCmd.setExecutor(new SEP());
        var qrCmd = getCommand("quickranked");
        if (qrCmd != null) qrCmd.setExecutor(new QuickRanked());
        var qnCmd = getCommand("queuenotify");
        if (qnCmd != null) qnCmd.setExecutor(new QueueNotify());

        queueListener = new QueueListener(this);
        queueListener.start();
        
        // Регистрируем свой killregen если включён
        if (getConfig().getBoolean("killregen.enabled", true)) {
            Bukkit.getPluginManager().registerEvents(new KillRegenListener(this), this);
            getLogger().info("KillRegen enabled");
        }
        
        // Регистрируем glow для пати боёв если включён и ProtocolLib есть
        if (getConfig().getBoolean("party-glow.enabled", true)) {
            if (Bukkit.getPluginManager().getPlugin("ProtocolLib") != null) {
                partyGlowListener = new PartyGlowListener(this);
                Bukkit.getPluginManager().registerEvents(partyGlowListener, this);
                getLogger().info("PartyGlow enabled (ProtocolLib)");
            } else {
                getLogger().warning("PartyGlow: ProtocolLib не найден, функция отключена");
            }
        }
    }

    public void reloadSystem() {
        cancelTask();
        runTask();
    }

    public void debug(String message) {
        if (!debug) return;
        getLogger().log(Level.INFO, message);
    }

    private void runTask() {
        taskID = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, this::update, 0, 10 * 20);
        if (taskID == -1) runTask();
    }

    public void update() {
        StrikePracticeAPI api = StrikePractice.getAPI();
        HashMap<String, Integer> newQueueAmounts = new HashMap<>();
        HashMap<String, Integer> newFightAmounts = new HashMap<>();
        api.getKits().forEach(kit -> {
            newQueueAmounts.put(kit.getName(), 0);
            newFightAmounts.put(kit.getName(), 0);
        });
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (api.isInFight(player)) {
                var fight = api.getFight(player);
                if (fight == null || fight.getKit() == null) continue;
                String kit = fight.getKit().getName();
                if (!newFightAmounts.containsKey(kit)) continue;
                newFightAmounts.merge(kit, 1, Integer::sum);
            } else if (api.isInQueue(player)) {
                var qKit = api.getQueuedKit(player);
                if (qKit == null) continue;
                String kit = qKit.getName();
                if (!newQueueAmounts.containsKey(kit)) continue;
                newQueueAmounts.merge(kit, 1, Integer::sum);
            }
        }
        this.queueAmounts = newQueueAmounts;
        this.fightAmounts = newFightAmounts;
    }

    private void cancelTask() {
        Bukkit.getScheduler().cancelTask(taskID);
    }

    @Override
    public void onDisable() {
        cancelTask();
        if (queueListener != null) {
            queueListener.stop();
        }
        if (partyGlowListener != null) {
            partyGlowListener.shutdown();
        }
    }
}
