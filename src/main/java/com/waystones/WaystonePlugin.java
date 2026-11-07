package com.waystones;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WaystonePlugin extends JavaPlugin implements TabExecutor {
    public static final MiniMessage MM = MiniMessage.miniMessage();
    private WaystoneManager manager;
    private final Map<UUID, TeleportSequence> sequences = new ConcurrentHashMap<>();

    public WaystoneManager manager() {
        return manager;
    }

    public Map<UUID, TeleportSequence> sequences() {
        return sequences;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        manager = new WaystoneManager(this);
        manager.load();
        getServer().getPluginManager().registerEvents(new WaystoneListener(this), this);
        getCommand("waystone").setExecutor(this);
        getCommand("waystone").setTabCompleter(this);

        // partikel ambient di waystone aktif (hanya kalau ada pemain dekat)
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Waystone w : manager.active()) {
                Location l = w.location();
                if (l == null || !l.isChunkLoaded()) continue;
                World wd = l.getWorld();
                if (wd.getNearbyPlayers(l, 24).isEmpty()) continue;
                Location c = l.clone().add(0.5, 1.2, 0.5);
                wd.spawnParticle(Particle.END_ROD, c, 1, 0.3, 0.4, 0.3, 0.01);
                wd.spawnParticle(Particle.ENCHANT, c.clone().add(0, 0.5, 0), 6, 0.4, 0.5, 0.4, 0.5);
            }
        }, 20L, 10L);

        try {
            FakePlayer.check();
            getLogger().info("NPC packet bawaan aktif (skin + armor asli).");
        } catch (Throwable t) {
            getLogger().warning("NPC packet tidak kompatibel dengan versi server ini, pakai ArmorStand: " + t);
        }
    }

    @Override
    public void onDisable() {
        new ArrayList<>(sequences.values()).forEach(TeleportSequence::finish);
        if (manager != null) manager.save();
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (a.length == 0) {
            s.sendMessage(MM.deserialize("<gray>/waystone <white>give [player] | register | remove | list | reload"));
            return true;
        }
        switch (a[0].toLowerCase()) {
            case "give" -> {
                Player t = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : (s instanceof Player pl ? pl : null);
                if (t == null) {
                    s.sendMessage(MM.deserialize("<red>Pemain tidak ditemukan."));
                    return true;
                }
                t.getInventory().addItem(manager.createItem());
                s.sendMessage(MM.deserialize("<green>Waystone diberikan ke " + t.getName() + "."));
            }
            case "register" -> {
                if (!(s instanceof Player pl)) return true;
                Block b = pl.getTargetBlockExact(6);
                if (b == null || b.getType().isAir()) {
                    s.sendMessage(MM.deserialize("<red>Lihat ke block yang mau dijadikan waystone."));
                } else if (manager.get(b) != null) {
                    s.sendMessage(MM.deserialize("<red>Block ini sudah waystone."));
                } else {
                    manager.add(b, pl.getUniqueId(), false);
                    s.sendMessage(MM.deserialize("<green>Block didaftarkan. Klik kanan untuk aktivasi."));
                }
            }
            case "remove" -> {
                if (!(s instanceof Player pl)) return true;
                Block b = pl.getTargetBlockExact(6);
                Waystone w = b == null ? null : manager.get(b);
                if (w == null) {
                    s.sendMessage(MM.deserialize("<red>Bukan waystone."));
                } else {
                    manager.remove(w);
                    s.sendMessage(MM.deserialize("<green>Registrasi waystone dihapus (block tetap)."));
                }
            }
            case "list" -> {
                s.sendMessage(MM.deserialize("<gray>Waystone aktif: <white>" + manager.active().size()));
                for (Waystone w : manager.active())
                    s.sendMessage(MM.deserialize("<dark_gray>- <white>" + w.name + " <gray>(" + w.world + " "
                            + w.x + " " + w.y + " " + w.z + ")"));
            }
            case "reload" -> {
                reloadConfig();
                manager.load();
                s.sendMessage(MM.deserialize("<green>Reload selesai."));
            }
            default -> s.sendMessage(MM.deserialize("<red>Subcommand tidak dikenal."));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        if (a.length == 1) return List.of("give", "register", "remove", "list", "reload");
        return List.of();
    }
}
