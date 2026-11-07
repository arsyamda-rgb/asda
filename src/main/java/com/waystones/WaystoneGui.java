package com.waystones;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public final class WaystoneGui implements InventoryHolder {
    private static final int PER_PAGE = 45;
    private final WaystonePlugin plugin;
    private final Waystone origin;
    private final Inventory inv;
    private final Map<Integer, Waystone> slots = new HashMap<>();
    private int page = 0;

    public WaystoneGui(WaystonePlugin plugin, Waystone origin) {
        this.plugin = plugin;
        this.origin = origin;
        Component title = WaystonePlugin.MM.deserialize(plugin.getConfig().getString("gui.title", "Waystone"));
        this.inv = Bukkit.createInventory(this, 54, title);
        build();
    }

    public Waystone origin() {
        return origin;
    }

    public Waystone at(int slot) {
        return slots.get(slot);
    }

    public int page() {
        return page;
    }

    public void setPage(int p) {
        this.page = Math.max(0, p);
        build();
    }

    public boolean hasNext() {
        return (page + 1) * PER_PAGE < list().size();
    }

    private List<Waystone> list() {
        List<Waystone> l = new ArrayList<>(plugin.manager().active());
        l.sort(Comparator.comparing(w -> w.name.toLowerCase()));
        return l;
    }

    private void build() {
        inv.clear();
        slots.clear();
        List<Waystone> l = list();
        int start = page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < l.size(); i++) {
            Waystone w = l.get(start + i);
            inv.setItem(i, icon(w));
            slots.put(i, w);
        }
        ItemStack pane = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 45; i < 54; i++) inv.setItem(i, pane);
        if (page > 0) inv.setItem(45, named(Material.ARROW, "<yellow>Halaman sebelumnya"));
        if (hasNext()) inv.setItem(53, named(Material.ARROW, "<yellow>Halaman berikutnya"));
        inv.setItem(49, named(Material.NETHER_STAR, "<gradient:#00f5ff:#a855f7>Lokasi sekarang: " + origin.name));
    }

    private ItemStack icon(Waystone w) {
        boolean here = w.id.equals(origin.id);
        ItemStack it = new ItemStack(here ? Material.BEACON : Material.ENDER_EYE);
        ItemMeta im = it.getItemMeta();
        im.displayName(WaystonePlugin.MM.deserialize("<gradient:#00f5ff:#a855f7><bold>" + w.name)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        if (here) {
            lore.add(line("<green>Kamu berdiri di sini"));
            im.addEnchant(Enchantment.UNBREAKING, 1, true);
            im.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        } else {
            var a = origin.location();
            var b = w.location();
            if (a != null && b != null && a.getWorld().equals(b.getWorld()))
                lore.add(line("<gray>Jarak: <white>" + (int) a.distance(b) + " blok"));
            else
                lore.add(line("<gray>Dimensi lain"));
            lore.add(line("<gray>Dunia: <white>" + w.world));
            lore.add(Component.empty());
            lore.add(line("<yellow>Klik untuk teleport"));
        }
        im.lore(lore);
        it.setItemMeta(im);
        return it;
    }

    private static Component line(String s) {
        return WaystonePlugin.MM.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack named(Material m, String name) {
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        im.displayName(line(name));
        it.setItemMeta(im);
        return it;
    }

    public void open(Player p) {
        p.openInventory(inv);
        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_ENDER_CHEST_OPEN, 1f, 1.4f);
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }
}
