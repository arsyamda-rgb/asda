package com.waystones;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.UUID;

public final class Waystone {
    public final UUID id;
    public final String world;
    public final int x, y, z;
    public String name;
    public boolean active;
    public boolean fromItem;
    public UUID owner;

    public Waystone(UUID id, String world, int x, int y, int z) {
        this.id = id;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Location location() {
        World w = Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x, y, z);
    }

    public Block block() {
        Location l = location();
        return l == null ? null : l.getBlock();
    }
}
