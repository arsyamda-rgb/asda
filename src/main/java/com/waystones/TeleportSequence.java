package com.waystones;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;

public final class TeleportSequence extends BukkitRunnable {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int CHARGE = 45;      // tick mulai laser
    private static final int BURST = 60;       // tick clone hancur + teleport

    private final WaystonePlugin plugin;
    private final Player p;
    private final Waystone target;
    private final Location origin;
    private final Location dest;
    private final boolean wasInvulnerable;
    private final Particle.DustOptions cyan = new Particle.DustOptions(Color.fromRGB(0, 245, 255), 1.6f);
    private final Particle.DustOptions violet = new Particle.DustOptions(Color.fromRGB(168, 85, 247), 1.6f);
    private final Particle.DustOptions white = new Particle.DustOptions(Color.WHITE, 2.2f);

    private CloneNpc clone;
    private int t = 0;
    private int phase = 0;   // 0 charge+laser, 1 nunggu teleport, 2 arrival
    private int arrive = 0;
    private boolean started = false;
    private boolean done = false;

    private TeleportSequence(WaystonePlugin plugin, Player p, Waystone target, Location dest) {
        this.plugin = plugin;
        this.p = p;
        this.target = target;
        this.origin = p.getLocation().clone();
        this.dest = dest;
        this.wasInvulnerable = p.isInvulnerable();
    }

    public static void begin(WaystonePlugin plugin, Player p, Waystone to) {
        if (plugin.sequences().containsKey(p.getUniqueId())) return;
        Location d = plugin.manager().arrivalSpot(to);
        if (d == null) {
            p.sendMessage(MM.deserialize("<red>Dunia tujuan tidak ditemukan."));
            return;
        }
        TeleportSequence s = new TeleportSequence(plugin, p, to, d);
        s.start();
    }

    private void start() {
        plugin.sequences().put(p.getUniqueId(), this);
        p.setInvulnerable(true);
        for (Player o : Bukkit.getOnlinePlayers())
            if (!o.equals(p)) o.hidePlayer(plugin, p);
        clone = CloneNpc.spawn(p, origin);
        started = true;
        runTaskTimer(plugin, 0L, 1L);
    }

    public boolean frozen() {
        return phase != 2;
    }

    @Override
    public void run() {
        if (!p.isOnline()) {
            finish();
            return;
        }
        try {
            if (phase == 0) charge();
            else if (phase == 2) arrival();
        } catch (Throwable ex) {
            plugin.getLogger().warning("Sequence error: " + ex);
            finish();
        }
    }

    private void charge() {
        World w = origin.getWorld();
        if (t == 0) {
            w.playSound(origin, Sound.BLOCK_BEACON_POWER_SELECT, 1.2f, 0.8f);
            w.playSound(origin, Sound.BLOCK_END_PORTAL_SPAWN, 0.4f, 1.6f);
        }
        if (t == 25) w.playSound(origin, Sound.BLOCK_BEACON_AMBIENT, 1.5f, 1.8f);
        if (t == CHARGE) w.playSound(origin, Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.5f, 1.2f);

        double lift = Math.min(t, 50) * 0.03;
        Location c = origin.clone().add(0, lift, 0);
        c.setYaw(origin.getYaw() + t * 8f);
        clone.move(c);

        for (int k = 0; k < 3; k++) {
            double a = t * 0.45 + k * (2 * Math.PI / 3);
            double r = 1.3 - Math.min(t, CHARGE) * 0.012;
            double y = (t % 30) / 30.0 * 2.2;
            Location pt = origin.clone().add(Math.cos(a) * r, y, Math.sin(a) * r);
            w.spawnParticle(Particle.DUST, pt, 1, 0, 0, 0, 0, k == 0 ? cyan : violet);
            w.spawnParticle(Particle.END_ROD, pt, 1, 0, 0, 0, 0);
        }
        if (t % 8 == 0) ring(w, origin.clone().add(0, 0.1, 0), 0.8 + (t / 8) * 0.25, (t / 8) % 2 == 0 ? cyan : violet);

        if (t >= CHARGE && t < BURST) {
            beam(w, origin.clone().add(0, lift + 1, 0), 30);
            w.spawnParticle(Particle.ELECTRIC_SPARK, origin.clone().add(0, lift + 1, 0), 6, 0.4, 0.6, 0.4, 0.2);
            if (t == CHARGE + 1) w.playSound(origin, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.2f, 1.4f);
        }

        if (t == BURST) {
            Location mid = origin.clone().add(0, lift + 1, 0);
            w.spawnParticle(Particle.FLASH, mid, 1);
            w.spawnParticle(Particle.FIREWORK, mid, 80, 0.3, 0.6, 0.3, 0.25);
            w.spawnParticle(Particle.END_ROD, mid, 60, 0.3, 0.8, 0.3, 0.4);
            w.spawnParticle(Particle.REVERSE_PORTAL, mid, 80, 0.4, 0.8, 0.4, 0.5);
            w.playSound(mid, Sound.ENTITY_ENDERMAN_TELEPORT, 1.5f, 0.6f);
            w.playSound(mid, Sound.BLOCK_BEACON_DEACTIVATE, 1.2f, 1.5f);
            clone.remove();
            clone = null;
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, false, false, false));
            phase = 1;
            p.teleportAsync(dest).thenAccept(ok -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (done) return;
                if (ok) {
                    p.setFallDistance(0);
                    phase = 2;
                } else {
                    p.sendMessage(MM.deserialize("<red>Teleport gagal."));
                    finish();
                }
            }));
        }
        t++;
    }

    private void arrival() {
        World w = dest.getWorld();
        Location base = dest.clone();
        if (arrive == 0) {
            w.playSound(base, Sound.BLOCK_BEACON_ACTIVATE, 1.4f, 1.3f);
            w.playSound(base, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
        }
        if (arrive < 10) {
            beam(w, base.clone().add(0, 1, 0), 30);
        }
        if (arrive == 10) {
            Location mid = base.clone().add(0, 1, 0);
            w.spawnParticle(Particle.FLASH, mid, 1);
            w.spawnParticle(Particle.FIREWORK, mid, 70, 0.3, 0.6, 0.3, 0.25);
            w.spawnParticle(Particle.TOTEM_OF_UNDYING, mid, 50, 0.4, 0.8, 0.4, 0.3);
            ring(w, base.clone().add(0, 0.1, 0), 1.2, cyan);
            ring(w, base.clone().add(0, 0.1, 0), 2.0, violet);
            w.playSound(mid, Sound.ITEM_TRIDENT_THUNDER, 0.8f, 1.4f);
            w.playSound(mid, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            for (Player o : Bukkit.getOnlinePlayers())
                if (!o.equals(p)) o.showPlayer(plugin, p);
            p.removePotionEffect(PotionEffectType.BLINDNESS);
            p.showTitle(Title.title(
                    MM.deserialize("<gradient:#00f5ff:#a855f7><bold>BERHASIL TELEPORT KE</bold></gradient>"),
                    MM.deserialize("<gold><bold>" + target.name + "</bold>"),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2500), Duration.ofMillis(700))));
        }
        if (arrive > 10 && arrive < 35) {
            double a = arrive * 0.5;
            for (int k = 0; k < 2; k++) {
                double aa = a + k * Math.PI;
                w.spawnParticle(Particle.END_ROD,
                        base.clone().add(Math.cos(aa) * 1.1, (arrive - 10) * 0.08, Math.sin(aa) * 1.1),
                        1, 0, 0, 0, 0);
            }
        }
        if (++arrive >= 40) finish();
    }

    private void ring(World w, Location c, double r, Particle.DustOptions opt) {
        for (int i = 0; i < 28; i++) {
            double a = i * (2 * Math.PI / 28);
            w.spawnParticle(Particle.DUST, c.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r), 1, 0, 0, 0, 0, opt);
        }
    }

    private void beam(World w, Location bottom, double height) {
        for (double y = 0; y <= height; y += 0.4) {
            Location pt = bottom.clone().add(0, y, 0);
            w.spawnParticle(Particle.DUST, pt, 1, 0.05, 0, 0.05, 0, y % 0.8 < 0.4 ? white : cyan);
            w.spawnParticle(Particle.END_ROD, pt, 1, 0.02, 0, 0.02, 0);
        }
    }

    public void finish() {
        if (done) return;
        done = true;
        if (started) {
            try {
                cancel();
            } catch (IllegalStateException ignored) {
            }
        }
        if (clone != null) {
            clone.remove();
            clone = null;
        }
        plugin.sequences().remove(p.getUniqueId());
        for (Player o : Bukkit.getOnlinePlayers())
            if (!o.equals(p)) o.showPlayer(plugin, p);
        p.removePotionEffect(PotionEffectType.BLINDNESS);
        p.setInvulnerable(wasInvulnerable);
    }
}
