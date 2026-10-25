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
 * {@code locked} and {@code admitted} are the lock's state for the viewer (D30); while a steal runs {@code thiefName}
 * is set and {@code stealLeft} of {@code stealTotal} ticks remain, {@code stealOutside} while the thief is away.
 * The helm (MB-4): {@code driverName} (empty when nobody drives), {@code helms} aboard, the compass {@code heading}
 * (-1 without a construct), the {@code topSpeed} with the totem's cap, whether the construct is {@code anchored}, and
 * whether its core stands in a {@code closedRegion} (D50: it may only back out). The parts aboard (D52) by count,
 * the lift they carry against the construct's {@code mass}, its {@code height} and its {@code ceiling} (-1 when uncapped).
 * The flight plan (D63): the waypoints as packed positions, the cruise height, the next waypoint, whether it runs, and
 * the distance to the next waypoint (-1 without one).
 */
public record CoreViewPacket(BlockPos pos, boolean open, String name, int tier, int allowance, int count, boolean tooMany,
                             int state, double speed, List<Refused> refused, List<Kind> kinds, int totems, int chests, int machines,
                             String ownerName, boolean owner, int nextAllowance, int tiers, boolean coins, List<Prices.Coins> prices,
                             int worth, List<Prices.Coins> carried, boolean locked, boolean admitted, String thiefName, int stealLeft,
                             int stealTotal, boolean stealOutside, String driverName, int helms, int heading, double topSpeed, boolean anchored,
                             boolean closedRegion, int engines, int floaters, int balloons, int wings, int jets, double liftCapacity,
                             double mass, int height, int ceiling, long[] plan, int cruise, int planNext, boolean planRunning, int planDistance,
                             String note) {
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
        buf.writeBoolean(locked);
        buf.writeBoolean(admitted);
        buf.writeUtf(thiefName, 64);
        buf.writeVarInt(stealLeft);
        buf.writeVarInt(stealTotal);
        buf.writeBoolean(stealOutside);
        buf.writeUtf(driverName, 64);
        buf.writeVarInt(helms);
        buf.writeVarInt(heading + 1);
        buf.writeDouble(topSpeed);
        buf.writeBoolean(anchored);
        buf.writeBoolean(closedRegion);
        buf.writeVarInt(engines);
        buf.writeVarInt(floaters);
        buf.writeVarInt(balloons);
        buf.writeVarInt(wings);
        buf.writeVarInt(jets);
        buf.writeDouble(liftCapacity);
        buf.writeDouble(mass);
        buf.writeVarInt(height);
        buf.writeVarInt(ceiling + 1);
        buf.writeLongArray(plan);
        buf.writeVarInt(cruise);
        buf.writeVarInt(planNext);
        buf.writeBoolean(planRunning);
        buf.writeVarInt(planDistance + 1);
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
        boolean locked = buf.readBoolean(), admitted = buf.readBoolean();
        String thiefName = buf.readUtf(64);
        int stealLeft = buf.readVarInt(), stealTotal = buf.readVarInt();
        boolean stealOutside = buf.readBoolean();
        String driverName = buf.readUtf(64);
        int helms = buf.readVarInt(), heading = buf.readVarInt() - 1;
        double topSpeed = buf.readDouble();
        boolean anchored = buf.readBoolean();
        boolean closedRegion = buf.readBoolean();
        int engines = buf.readVarInt(), floaters = buf.readVarInt(), balloons = buf.readVarInt(), wings = buf.readVarInt(), jets = buf.readVarInt();
        double liftCapacity = buf.readDouble(), mass = buf.readDouble();
        int height = buf.readVarInt(), ceiling = buf.readVarInt() - 1;
        long[] plan = buf.readLongArray();
        int cruise = buf.readVarInt(), planNext = buf.readVarInt();
        boolean planRunning = buf.readBoolean();
        int planDistance = buf.readVarInt() - 1;
        String note = buf.readUtf(256);
        return new CoreViewPacket(pos, open, name, tier, allowance, count, tooMany, state, speed, refused, kinds, totems, chests, machines,
                ownerName, owner, nextAllowance, tiers, coins, prices, worth, carried, locked, admitted, thiefName, stealLeft, stealTotal, stealOutside,
                driverName, helms, heading, topSpeed, anchored, closedRegion, engines, floaters, balloons, wings, jets, liftCapacity, mass, height, ceiling,
                plan, cruise, planNext, planRunning, planDistance, note);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mrgregles.bsp_mobile.client.ClientCore.view(this));
        ctx.get().setPacketHandled(true);
    }
}
