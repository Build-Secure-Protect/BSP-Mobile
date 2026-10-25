package com.mrgregles.bsp_mobile.network;

import com.mrgregles.bsp_mobile.construct.CoreService;
import com.mrgregles.bsp_mobile.construct.HelmService;
import com.mrgregles.bsp_mobile.construct.LockService;
import com.mrgregles.bsp_mobile.construct.PlanService;
import com.mrgregles.bsp_mobile.construct.MobileCoreBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client to server: what the player did on the core's screen. The server checks reach and acts, then answers with a fresh view. */
public record CoreActionPacket(BlockPos pos, Action action, String text) {
    public enum Action { REFRESH, ASSEMBLE, RELEASE, RENAME, UPGRADE, UNLOCK, STEAL, ANCHOR, PLAN_ADD, PLAN_REMOVE, PLAN_CRUISE, PLAN_GO, PLAN_STOP }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeEnum(action);
        buf.writeUtf(text, MobileCoreBlockEntity.NAME_MAX);
    }

    public static CoreActionPacket decode(FriendlyByteBuf buf) {
        return new CoreActionPacket(buf.readBlockPos(), buf.readEnum(Action.class), buf.readUtf(MobileCoreBlockEntity.NAME_MAX));
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
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
            case ANCHOR -> HelmService.anchor(level, pos, player, "down".equals(text));
            case PLAN_ADD -> PlanService.addHere(level, pos, player);
            case PLAN_REMOVE -> PlanService.removeLast(level, pos, player);
            case PLAN_CRUISE -> PlanService.setCruise(level, pos, player, parseInt(text));
            case PLAN_GO -> PlanService.go(level, pos, player);
            case PLAN_STOP -> PlanService.stop(level, pos, player);
            case REFRESH -> null;
        };
        String note = outcome == null ? "" : outcome.ok() ? action.name().toLowerCase(java.util.Locale.ROOT) + ".ok" : "fail." + outcome.reason();
        if (action == Action.PLAN_CRUISE || action == Action.PLAN_ADD || action == Action.PLAN_REMOVE) {
            note = outcome != null && outcome.ok() ? "" : note; // quiet edits of the plan
        }
        // after assembling or releasing the core sits elsewhere (the shipyard, or the world again): the screen follows it
        BlockPos next = outcome != null && outcome.corePos() != null ? outcome.corePos() : pos;
        CoreService.refresh(player, level, next, note);
    }

}
