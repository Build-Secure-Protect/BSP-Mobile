package com.mrgregles.bsp_mobile.network;

import com.mrgregles.bsp_mobile.BSPMobile;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/** The mod's channel: {@code bsp_mobile:main}. The server decides, the client displays. Packet ids are positional: append only. */
public final class MobileNetwork {
    private static final String PROTOCOL = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(BSPMobile.MODID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private MobileNetwork() {}

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(CoreViewPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CoreViewPacket::encode).decoder(CoreViewPacket::decode).consumerMainThread(CoreViewPacket::handle).add();
        CHANNEL.messageBuilder(CoreOutlinePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CoreOutlinePacket::encode).decoder(CoreOutlinePacket::decode).consumerMainThread(CoreOutlinePacket::handle).add();
        CHANNEL.messageBuilder(CoreActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CoreActionPacket::encode).decoder(CoreActionPacket::decode).consumerMainThread(CoreActionPacket::handle).add();
    }
}
