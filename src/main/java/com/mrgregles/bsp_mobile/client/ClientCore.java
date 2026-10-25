package com.mrgregles.bsp_mobile.client;

import com.mrgregles.bsp_mobile.network.CoreOutlinePacket;
import com.mrgregles.bsp_mobile.network.CoreViewPacket;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

/** The client's side of the core's screen: opens it, passes fresh views to it, and keeps the outline while it is open. */
public final class ClientCore {
    @Nullable
    private static CoreOutlinePacket outline;

    private ClientCore() {}

    public static void view(CoreViewPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof CoreScreen screen) {
            screen.accept(packet);
        } else if (packet.open()) {
            mc.setScreen(new CoreScreen(packet));
        }
    }

    public static void outline(CoreOutlinePacket packet) {
        outline = packet;
    }

    @Nullable
    static CoreOutlinePacket outline() {
        return outline;
    }

    static void clearOutline() {
        outline = null;
    }
}
