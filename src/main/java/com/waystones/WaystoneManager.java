package com.waystones;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class WaystoneManager {
    private final WaystonePlugin plugin;
    private final Map<UUID, Waystone> byId = new LinkedHashMap<>();
    private final Map<String, Waystone> byLoc = new HashMap<>();
    private final File file;
    public final NamespacedKey itemKey;

    public WaystoneManager(WaystonePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "waystones.yml");
        this.itemKey = new NamespacedKey(plugin, "waystone_item");
    }

    private static String key(String world, int x, int y, int z) {
        return world + ":" + x + ":" + y + ":" + z;
    }

    private static String key(Block b) {
        return key(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
    }

    public void load() {
        byId.clear();
        byLoc.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = y.getConfigurationSection("waystones");
        if (sec == null) return;
        for (String k : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(k);
            if (s == null) continue;
            Waystone w = new Waystone(UUID.fromString(k), s.getString("world"),
                    s.getInt("x"), s.getInt("y"), s.getInt("z"));
            w.name = s.getString("name");
            w.active = s.getBoolean("active");
            w.fromItem = s.getBoolean("from-item");
            String o = s.getString("owner");
            if (o != null) w.owner = UUID.fromString(o);
            index(w);
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Waystone w : byId.values()) {
            String p = "waystones." + w.id;
            y.set(p + ".name", w.name);
            y.set(p + ".world", w.world);
            y.set(p + ".x", w.x);
            y.set(p + ".y", w.y);
            y.set(p + ".z", w.z);
            y.set(p + ".active", w.active);
            y.set(p + ".from-item", w.fromItem);
            if (w.owner != null) y.set(p + ".owner", w.owner.toString());
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Gagal simpan waystones.yml: " + e.getMessage());
        }
    }

    private void index(Waystone w) {
        byId.put(w.id, w);
        byLoc.put(key(w.world, w.x, w.y, w.z), w);
    }

    public Waystone get(Block b) {
        return byLoc.get(key(b));
    }

    public Waystone add(Block b, UUID owner, boolean fromItem) {
        Waystone w = new Waystone(UUID.randomUUID(), b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
        w.owner = owner;
        w.fromItem = fromItem;
        index(w);
        save();
        return w;
    }

    public void remove(Waystone w) {
        byId.remove(w.id);
        byLoc.remove(key(w.world, w.x, w.y, w.z));
        save();
    }

    public Collection<Waystone> all() {
        return byId.values();
    }

    public List<Waystone> active() {
        List<Waystone> l = new ArrayList<>();
        for (Waystone w : byId.values()) if (w.active && w.name != null) l.add(w);
        return l;
    }

    public boolean nameTaken(String name) {
        for (Waystone w : byId.values())
            if (w.active && w.name != null && w.name.equalsIgnoreCase(name)) return true;
        return false;
    }

    public ItemStack createItem() {
        Material m = Material.matchMaterial(plugin.getConfig().getString("item.material", "LODESTONE"));
        if (m == null || !m.isBlock()) m = Material.LODESTONE;
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        im.displayName(WaystonePlugin.MM.deserialize(plugin.getConfig().getString("item.name", "Waystone"))
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String s : plugin.getConfig().getStringList("item.lore"))
            lore.add(WaystonePlugin.MM.deserialize(s).decoration(TextDecoration.ITALIC, false));
        im.lore(lore);
        int cmd = plugin.getConfig().getInt("item.custom-model-data", 0);
        if (cmd > 0) im.setCustomModelData(cmd);
        im.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(im);
        return it;
    }

    public boolean isWaystoneItem(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return false;
        return it.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE);
    }

    /** Titik muncul di sebelah waystone: sisi bebas, menghadap keluar. */
    public Location arrivalSpot(Waystone w) {
        Location base = w.location();
        if (base == null) return null;
        Block b = base.getBlock();
        BlockFace[] faces = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
        for (BlockFace f : faces) {
            Block s = b.getRelative(f);
            if (s.isPassable() && !s.isLiquid()
                    && s.getRelative(BlockFace.UP).isPassable()
                    && s.getRelative(BlockFace.DOWN).getType().isSolid()) {
                Location l = s.getLocation().add(0.5, 0, 0.5);
                l.setDirection(new Vector(f.getModX(), 0, f.getModZ()));
                return l;
            }
        }
        return b.getLocation().add(0.5, 1, 0.5);
    }
}
