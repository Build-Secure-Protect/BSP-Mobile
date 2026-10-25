package com.mrgregles.bsp_mobile.network;

import com.mrgregles.bsp_mobile.construct.CoreService;
import com.mrgregles.bsp_mobile.construct.LockService;
import com.mrgregles.bsp_mobile.construct.MobileCoreBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client to server: what the player did on the core's screen. The server checks reach and acts, then answers with a fresh view. */
public record CoreActionPacket(BlockPos pos, Action action, String text) {
    public enum Action { REFRESH, ASSEMBLE, RELEASE, RENAME, UPGRADE, UNLOCK, STEAL }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeEnum(action);
        buf.writeUtf(text, MobileCoreBlockEntity.NAME_MAX);
    }

    public static CoreActionPacket decode(FriendlyByteBuf buf) {
        return new CoreActionPacket(buf.readBlockPos(), buf.readEnum(Action.class), buf.readUtf(MobileCoreBlockEntity.NAME_MAX));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        ctx.get().setPacketHandled(true);
        if (player == null || !(player.level() instanceof ServerLevel level) || !CoreService.canReach(player, level, pos)) {
            return;
        }
        CoreService.Outcome outcome = switch (action) {
            case ASSEMBLE -> CoreService.assemble(level, pos, player);
            case RELEASE -> CoreService.release(level, pos, player);
            case RENAME -> CoreService.rename(level, pos, text, player);
            case UPGRADE -> CoreService.upgrade(level, pos, player);
            case UNLOCK -> LockService.unlock(level, pos, player);
            case STEAL -> LockService.startSteal(level, pos, player);
            case REFRESH -> null;
        };
        String note = outcome == null ? "" : outcome.ok() ? action.name().toLowerCase(java.util.Locale.ROOT) + ".ok" : "fail." + outcome.reason();
        // after assembling or releasing the core sits elsewhere (the shipyard, or the world again): the screen follows it
        BlockPos next = outcome != null && outcome.corePos() != null ? outcome.corePos() : pos;
        CoreService.refresh(player, level, next, note);
    }

}
