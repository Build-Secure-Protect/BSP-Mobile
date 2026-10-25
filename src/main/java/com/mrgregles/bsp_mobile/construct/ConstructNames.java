package com.mrgregles.bsp_mobile.construct;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** The counter behind new cores' names (D18): "Construct 1", "Construct 2", ... per server. */
public final class ConstructNames extends SavedData {
    private static final String KEY = "bsp_mobile_names";
    private int next = 1;

    public static String next(ServerLevel level) {
        ConstructNames data = level.getServer().overworld().getDataStorage().computeIfAbsent(ConstructNames::load, ConstructNames::new, KEY);
        String name = "Construct " + data.next;
        data.next++;
        data.setDirty();
        return name;
    }

    private static ConstructNames load(CompoundTag tag) {
        ConstructNames d = new ConstructNames();
        d.next = Math.max(1, tag.getInt("Next"));
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("Next", next);
        return tag;
    }
}
