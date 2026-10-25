package com.mrgregles.bsp_mobile.network;

import com.mrgregles.bsp_mobile.construct.Prices;
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
 * {@code coins} says whether BSP-Core's coins can be used; {@code prices} is one entry per step up the ladder (Tier I
 * to II first); {@code worth} and {@code carried} are the viewer's coins, in copper units and as the fewest coins.
 */
public record CoreViewPacket(BlockPos pos, boolean open, String name, int tier, int allowance, int count, boolean tooMany,
                             int state, double speed, List<Refused> refused, List<Kind> kinds, int totems, int chests, int machines,
                             String ownerName, boolean owner, int nextAllowance, int tiers, boolean coins, List<Prices.Coins> prices,
                             int worth, List<Prices.Coins> carried, String note) {
    public static final int MAX_KINDS = 512;
    private static final int MAX_COINS = 16;

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
        buf.writeBoolean(coins);
        writeCoins(buf, prices);
        buf.writeVarInt(worth);
        writeCoins(buf, carried);
        buf.writeUtf(note, 256);
    }

    private static void writeCoins(FriendlyByteBuf buf, List<Prices.Coins> list) {
        int n = Math.min(list.size(), MAX_COINS);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            buf.writeUtf(list.get(i).tier(), 32);
            buf.writeVarInt(list.get(i).count());
            buf.writeVarInt(list.get(i).value());
        }
    }

    private static List<Prices.Coins> readCoins(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), MAX_COINS);
        List<Prices.Coins> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(new Prices.Coins(buf.readUtf(32), buf.readVarInt(), buf.readVarInt()));
        }
        return out;
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
        boolean coins = buf.readBoolean();
        List<Prices.Coins> prices = readCoins(buf);
        int worth = buf.readVarInt();
        List<Prices.Coins> carried = readCoins(buf);
        String note = buf.readUtf(256);
        return new CoreViewPacket(pos, open, name, tier, allowance, count, tooMany, state, speed, refused, kinds, totems, chests, machines,
                ownerName, owner, nextAllowance, tiers, coins, prices, worth, carried, note);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mrgregles.bsp_mobile.client.ClientCore.view(this));
        ctx.get().setPacketHandled(true);
    }
}
