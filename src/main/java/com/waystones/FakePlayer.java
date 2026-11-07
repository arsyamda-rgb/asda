package com.waystones;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * NPC player palsu, murni packet, tanpa Citizens / ProtocolLib / NMS compile-time.
 * Semua akses NMS lewat reflection (nama Mojang mappings). Target: Paper 1.21.1.
 * NPC tidak masuk world server, cuma dikirim ke viewer.
 */
public final class FakePlayer implements CloneNpc {
    private static final String PKG = "net.minecraft.network.protocol.game.";

    private final Object npc;
    private final Object team;
    private final int id;
    private final UUID uuid;
    private final List<Player> viewers = new ArrayList<>();
    private boolean removed;

    /** Lempar exception kalau server ini tidak kompatibel. */
    public static void check() throws ReflectiveOperationException {
        Nms.cls("net.minecraft.server.level.ServerPlayer");
        Nms.cls("net.minecraft.server.level.ClientInformation");
        Nms.cls(PKG + "ClientboundAddEntityPacket");
        Nms.cls(PKG + "ClientboundPlayerInfoUpdatePacket");
    }

    public static FakePlayer spawn(Player owner, Location loc) throws ReflectiveOperationException {
        return new FakePlayer(owner, loc);
    }

    private FakePlayer(Player owner, Location loc) throws ReflectiveOperationException {
        Object server = Nms.invoke(Bukkit.getServer(), "getServer");
        Object level = Nms.invoke(loc.getWorld(), "getHandle");

        // profile + skin asli player
        String name = "wsn" + Integer.toHexString(ThreadLocalRandom.current().nextInt(0x1000000));
        PlayerProfile pp = Bukkit.createProfile(UUID.randomUUID(), name);
        for (ProfileProperty pr : owner.getPlayerProfile().getProperties())
            if (pr.getName().equals("textures")) pp.setProperty(pr);
        Object gameProfile = Nms.invoke(pp, "buildGameProfile");

        Object clientInfo = Nms.invokeStatic(Nms.cls("net.minecraft.server.level.ClientInformation"), "createDefault");
        npc = Nms.construct(Nms.cls("net.minecraft.server.level.ServerPlayer"),
                server, level, gameProfile, clientInfo);

        Nms.invoke(npc, "setPos", loc.getX(), loc.getY(), loc.getZ());
        Nms.invoke(npc, "setYRot", loc.getYaw());
        Nms.invoke(npc, "setXRot", loc.getPitch());
        Nms.invoke(npc, "setYHeadRot", loc.getYaw());
        id = (int) Nms.invoke(npc, "getId");
        uuid = (UUID) Nms.invoke(npc, "getUUID");

        // semua layer skin tampil
        Object entityData = Nms.invoke(npc, "getEntityData");
        Nms.invoke(entityData, "set", skinLayersAccessor(), (byte) 0x7F);

        // team: sembunyikan nametag
        Object scoreboard = Nms.construct(Nms.cls("net.minecraft.world.scores.Scoreboard"));
        team = Nms.construct(Nms.cls("net.minecraft.world.scores.PlayerTeam"), scoreboard, name);
        Nms.invoke(team, "setNameTagVisibility", Nms.enumConst("net.minecraft.world.scores.Team$Visibility", "NEVER"));
        @SuppressWarnings("unchecked")
        java.util.Collection<String> members = (java.util.Collection<String>) Nms.invoke(team, "getPlayers");
        members.add(name);

        for (Player p : loc.getWorld().getPlayers())
            if (p.getLocation().distanceSquared(loc) <= 96 * 96) viewers.add(p);

        // 1) team  2) tab-info (tidak listed)  3) entity  4) metadata  5) equipment  6) head
        send(Nms.invokeStatic(Nms.cls(PKG + "ClientboundSetPlayerTeamPacket"), "createAddOrModifyPacket", team, true));

        Class<?> entryCls = Nms.cls(PKG + "ClientboundPlayerInfoUpdatePacket$Entry");
        Object entry = Nms.construct(entryCls, uuid, gameProfile, false, 0,
                Nms.enumConst("net.minecraft.world.level.GameType", "SURVIVAL"), null, null);
        Class<?> infoCls = Nms.cls(PKG + "ClientboundPlayerInfoUpdatePacket");
        Object info = Nms.constructExact(infoCls, new Class<?>[]{EnumSet.class, List.class},
                enumSetOf(Nms.enumConst(PKG + "ClientboundPlayerInfoUpdatePacket$Action", "ADD_PLAYER")),
                List.of(entry));
        send(info);

        Object playerType = Nms.staticField(Nms.cls("net.minecraft.world.entity.EntityType"), "PLAYER");
        Object zero = Nms.staticField(Nms.cls("net.minecraft.world.phys.Vec3"), "ZERO");
        send(Nms.construct(Nms.cls(PKG + "ClientboundAddEntityPacket"),
                id, uuid, loc.getX(), loc.getY(), loc.getZ(), loc.getPitch(), loc.getYaw(),
                playerType, 0, zero, (double) loc.getYaw()));

        Object data = Nms.invoke(entityData, "getNonDefaultValues");
        if (data != null) send(Nms.construct(Nms.cls(PKG + "ClientboundSetEntityDataPacket"), id, data));

        send(equipmentPacket(owner.getInventory()));
        send(Nms.construct(Nms.cls(PKG + "ClientboundRotateHeadPacket"), npc, angle(loc.getYaw())));
    }

    private static Object skinLayersAccessor() throws ReflectiveOperationException {
        try {
            return Nms.staticField(Nms.cls("net.minecraft.world.entity.player.Player"), "DATA_PLAYER_MODE_CUSTOMISATION");
        } catch (ReflectiveOperationException ex) {
            Object byteSer = Nms.staticField(Nms.cls("net.minecraft.network.syncher.EntityDataSerializers"), "BYTE");
            return Nms.construct(Nms.cls("net.minecraft.network.syncher.EntityDataAccessor"), 17, byteSer);
        }
    }

    private Object equipmentPacket(PlayerInventory inv) throws ReflectiveOperationException {
        String slotCls = "net.minecraft.world.entity.EquipmentSlot";
        Class<?> pair = Nms.cls("com.mojang.datafixers.util.Pair");
        List<Object> list = new ArrayList<>();
        list.add(Nms.invokeStatic(pair, "of", Nms.enumConst(slotCls, "HEAD"), nms(inv.getHelmet())));
        list.add(Nms.invokeStatic(pair, "of", Nms.enumConst(slotCls, "CHEST"), nms(inv.getChestplate())));
        list.add(Nms.invokeStatic(pair, "of", Nms.enumConst(slotCls, "LEGS"), nms(inv.getLeggings())));
        list.add(Nms.invokeStatic(pair, "of", Nms.enumConst(slotCls, "FEET"), nms(inv.getBoots())));
        list.add(Nms.invokeStatic(pair, "of", Nms.enumConst(slotCls, "MAINHAND"), nms(inv.getItemInMainHand())));
        list.add(Nms.invokeStatic(pair, "of", Nms.enumConst(slotCls, "OFFHAND"), nms(inv.getItemInOffHand())));
        return Nms.construct(Nms.cls(PKG + "ClientboundSetEquipmentPacket"), id, list);
    }

    private static Object nms(ItemStack it) throws ReflectiveOperationException {
        if (it == null)
            return Nms.staticField(Nms.cls("net.minecraft.world.item.ItemStack"), "EMPTY");
        return Nms.invokeStatic(Nms.cls("org.bukkit.craftbukkit.inventory.CraftItemStack"), "asNMSCopy", it);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static EnumSet enumSetOf(Object constant) {
        return EnumSet.of((Enum) constant);
    }

    private static byte angle(float deg) {
        return (byte) (int) Math.floor(deg * 256.0f / 360.0f);
    }

    private void send(Object packet) {
        for (Player v : viewers) {
            try {
                Object handle = Nms.invoke(v, "getHandle");
                Object conn = Nms.field(handle, "connection");
                Nms.invoke(conn, "send", packet);
            } catch (Throwable ignored) {
                // viewer keluar / koneksi putus
            }
        }
    }

    @Override
    public void move(Location l) {
        if (removed) return;
        try {
            Nms.invoke(npc, "setPos", l.getX(), l.getY(), l.getZ());
            Nms.invoke(npc, "setYRot", l.getYaw());
            Nms.invoke(npc, "setXRot", l.getPitch());
            Nms.invoke(npc, "setYHeadRot", l.getYaw());
            send(Nms.construct(Nms.cls(PKG + "ClientboundTeleportEntityPacket"), npc));
            send(Nms.construct(Nms.cls(PKG + "ClientboundRotateHeadPacket"), npc, angle(l.getYaw())));
        } catch (ReflectiveOperationException e) {
            Bukkit.getLogger().warning("[Waystones] move NPC gagal: " + e);
        }
    }

    @Override
    public void remove() {
        if (removed) return;
        removed = true;
        try {
            send(Nms.construct(Nms.cls(PKG + "ClientboundRemoveEntitiesPacket"), (Object) new int[]{id}));
            send(Nms.construct(Nms.cls(PKG + "ClientboundPlayerInfoRemovePacket"), List.of(uuid)));
            send(Nms.invokeStatic(Nms.cls(PKG + "ClientboundSetPlayerTeamPacket"), "createRemovePacket", team));
        } catch (ReflectiveOperationException e) {
            Bukkit.getLogger().warning("[Waystones] remove NPC gagal: " + e);
        }
    }
}
