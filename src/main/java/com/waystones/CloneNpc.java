package com.waystones;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/** Clone visual player: NPC packet bawaan (FakePlayer), fallback ArmorStand. */
public interface CloneNpc {
    void move(Location loc);

    void remove();

    static CloneNpc spawn(Player p, Location loc) {
        try {
            return FakePlayer.spawn(p, loc);
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[Waystones] FakePlayer gagal, pakai ArmorStand: " + t);
        }
        return armorStand(p, loc);
    }

    private static CloneNpc armorStand(Player p, Location loc) {
        ArmorStand as = loc.getWorld().spawn(loc, ArmorStand.class, a -> {
            a.setArms(true);
            a.setBasePlate(false);
            a.setGravity(false);
            a.setInvulnerable(true);
            a.setPersistent(false);
            a.setSilent(true);
            EntityEquipment eq = a.getEquipment();
            PlayerInv.copy(p, eq);
            if (eq.getHelmet() == null || eq.getHelmet().getType() == Material.AIR) {
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta sm = (SkullMeta) head.getItemMeta();
                sm.setPlayerProfile(p.getPlayerProfile());
                head.setItemMeta(sm);
                eq.setHelmet(head);
            }
        });
        return new CloneNpc() {
            @Override
            public void move(Location l) {
                as.teleport(l);
            }

            @Override
            public void remove() {
                as.remove();
            }
        };
    }

    final class PlayerInv {
        static void copy(Player p, EntityEquipment eq) {
            var inv = p.getInventory();
            eq.setHelmet(inv.getHelmet());
            eq.setChestplate(inv.getChestplate());
            eq.setLeggings(inv.getLeggings());
            eq.setBoots(inv.getBoots());
            eq.setItemInMainHand(inv.getItemInMainHand());
            eq.setItemInOffHand(inv.getItemInOffHand());
        }
    }
}
