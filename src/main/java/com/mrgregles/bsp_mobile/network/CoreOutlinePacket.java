package com.mrgregles.bsp_mobile.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server to client: the blocks a core takes and the ones it refuses, as packed positions, for the outline (D17). */
public record CoreOutlinePacket(BlockPos pos, long[] taken, long[] refused) {
    public static final int MAX = 10_000;

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeLongArray(taken.length > MAX ? java.util.Arrays.copyOf(taken, MAX) : taken);
        buf.writeLongArray(refused.length > MAX ? java.util.Arrays.copyOf(refused, MAX) : refused);
    }

    public static CoreOutlinePacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        long[] taken = buf.readLongArray(new long[0], MAX);
        long[] refused = buf.readLongArray(new long[0], MAX);
        return new CoreOutlinePacket(pos, taken, refused);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mrgregles.bsp_mobile.client.ClientCore.outline(this));
        ctx.get().setPacketHandled(true);
    }
}
