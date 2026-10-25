package com.mrgregles.bsp_mobile.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server to client: everything the core's screen shows. {@code state} is {@code ConstructState.ordinal()}; a refusal's
 * {@code reason} is {@code Gather.Reason.ordinal()}; block ids are registry names the client turns into names.
 */
public record CoreViewPacket(BlockPos pos, boolean open, String name, int tier, int allowance, int count, boolean tooMany,
                             int state, double speed, List<Refused> refused, List<Kind> kinds, int totems, int chests, int machines,
                             String ownerName, boolean owner, int nextAllowance, int tiers, String note) {
    public static final int MAX_KINDS = 512;

    public record Refused(String block, int reason, int count) {}

    public record Kind(String block, int count) {}

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeBoolean(open);
        buf.writeUtf(name, 64);
        buf.writeVarInt(tier);
        buf.writeVarInt(allowance);
        buf.writeVarInt(count);
        buf.writeBoolean(tooMany);
        buf.writeVarInt(state);
        buf.writeDouble(speed);
        buf.writeVarInt(Math.min(refused.size(), MAX_KINDS));
        for (int i = 0; i < Math.min(refused.size(), MAX_KINDS); i++) {
            Refused r = refused.get(i);
            buf.writeUtf(r.block(), 256);
            buf.writeVarInt(r.reason());
            buf.writeVarInt(r.count());
        }
        buf.writeVarInt(Math.min(kinds.size(), MAX_KINDS));
        for (int i = 0; i < Math.min(kinds.size(), MAX_KINDS); i++) {
            buf.writeUtf(kinds.get(i).block(), 256);
            buf.writeVarInt(kinds.get(i).count());
        }
        buf.writeVarInt(totems);
        buf.writeVarInt(chests);
        buf.writeVarInt(machines);
        buf.writeUtf(ownerName, 64);
        buf.writeBoolean(owner);
        buf.writeVarInt(nextAllowance);
        buf.writeVarInt(tiers);
        buf.writeUtf(note, 256);
    }

    public static CoreViewPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        boolean open = buf.readBoolean();
        String name = buf.readUtf(64);
        int tier = buf.readVarInt(), allowance = buf.readVarInt(), count = buf.readVarInt();
        boolean tooMany = buf.readBoolean();
        int state = buf.readVarInt();
        double speed = buf.readDouble();
        int n = Math.min(buf.readVarInt(), MAX_KINDS);
        List<Refused> refused = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            refused.add(new Refused(buf.readUtf(256), buf.readVarInt(), buf.readVarInt()));
        }
        int k = Math.min(buf.readVarInt(), MAX_KINDS);
        List<Kind> kinds = new ArrayList<>(k);
        for (int i = 0; i < k; i++) {
            kinds.add(new Kind(buf.readUtf(256), buf.readVarInt()));
        }
        int totems = buf.readVarInt(), chests = buf.readVarInt(), machines = buf.readVarInt();
        String ownerName = buf.readUtf(64);
        boolean owner = buf.readBoolean();
        int nextAllowance = buf.readVarInt(), tiers = buf.readVarInt();
        String note = buf.readUtf(256);
        return new CoreViewPacket(pos, open, name, tier, allowance, count, tooMany, state, speed, refused, kinds, totems, chests, machines,
                ownerName, owner, nextAllowance, tiers, note);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mrgregles.bsp_mobile.client.ClientCore.view(this));
        ctx.get().setPacketHandled(true);
    }
}
