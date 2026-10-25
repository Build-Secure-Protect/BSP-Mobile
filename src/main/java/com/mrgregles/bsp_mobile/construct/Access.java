package com.mrgregles.bsp_mobile.construct;

import com.mrgregles.bsp_mobile.compat.BspCore;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Who a Mobile Core admits (D30, D33): everyone while it is not locked; once locked, its owner, their teammates and
 * BSP-Core admins. A null actor is the server itself (commands, tests) and is always admitted. Totem friends are
 * admitted when BSP-Core offers a friends-list call (request M-R2).
 */
public final class Access {
    private Access() {}

    public static boolean admitted(ServerLevel level, MobileCoreBlockEntity be, @Nullable ServerPlayer actor) {
        if (!be.locked() || actor == null) {
            return true;
        }
        if (be.owner() == null || be.owner().equals(actor.getUUID())) {
            return true;
        }
        if (BspCore.isAdmin(actor)) {
            return true;
        }
        return BspCore.sameTeam(level.getServer(), be.owner(), actor.getUUID());
    }
}
