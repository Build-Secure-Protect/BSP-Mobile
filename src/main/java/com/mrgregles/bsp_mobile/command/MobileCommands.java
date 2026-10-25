package com.mrgregles.bsp_mobile.command;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mrgregles.bsp_mobile.BSPMobile;
import com.mrgregles.bsp_mobile.MobileConfig;
import com.mrgregles.bsp_mobile.compat.BspCore;
import com.mrgregles.bsp_mobile.compat.ValkyrienSkiesBridge;
import com.mrgregles.bsp_mobile.construct.Blueprints;
import com.mrgregles.bsp_mobile.construct.Constructs;
import com.mrgregles.bsp_mobile.construct.MobileCoreItem;
import com.mrgregles.bsp_mobile.construct.Tiers;
import com.mrgregles.bsp_mobile.construct.Where;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * {@code /mobile} for operators and BSP-Core admins (D59): {@code list}, {@code remove <id> [delete]}, {@code tp <id>},
 * {@code limits}, {@code save <id> <name>}, {@code paste <name>} at the block you look at, {@code files}.
 */
public final class MobileCommands {
    private MobileCommands() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("mobile")
                .requires(MobileCommands::admin)
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("limits").executes(ctx -> limits(ctx.getSource())))
                .then(Commands.literal("files").executes(ctx -> files(ctx.getSource())))
                .then(Commands.literal("remove").then(Commands.argument("id", LongArgumentType.longArg(1))
                        .executes(ctx -> remove(ctx, false))
                        .then(Commands.literal("delete").executes(ctx -> remove(ctx, true)))))
                .then(Commands.literal("tp").then(Commands.argument("id", LongArgumentType.longArg(1)).executes(MobileCommands::tp)))
                .then(Commands.literal("save").then(Commands.argument("id", LongArgumentType.longArg(1))
                        .then(Commands.argument("name", StringArgumentType.word()).executes(MobileCommands::save))))
                .then(Commands.literal("paste").then(Commands.argument("name", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Blueprints.names(), b)).executes(MobileCommands::paste))));
    }

    private static boolean admin(CommandSourceStack src) {
        return src.hasPermission(2) || (src.getEntity() instanceof ServerPlayer p && BspCore.isAdmin(p));
    }

    private static Component t(String key, Object... args) {
        return Component.translatable("command.bsp_mobile." + key, args);
    }

    private static int list(CommandSourceStack src) {
        List<Constructs.Entry> all = Constructs.all(src.getServer());
        if (all.isEmpty()) {
            src.sendSuccess(() -> t("list.none"), false);
            return 0;
        }
        src.sendSuccess(() -> t("list.header", all.size()), false);
        for (Constructs.Entry e : all) {
            BlockPos at = Constructs.whereInWorld(e);
            String state = e.construct().parked() ? "parked" : e.construct().speed() > MobileConfig.movingSpeed() ? "moving" : "free";
            src.sendSuccess(() -> t("list.row", e.id(), e.core().name(), e.core().ownerName().isEmpty() ? "?" : e.core().ownerName(), e.blocks(),
                    MobileCoreItem.roman(e.core().tier()), at.getX(), at.getY(), at.getZ(), e.level().dimension().location(), t("state." + state)), false);
        }
        return all.size();
    }

    private static int limits(CommandSourceStack src) {
        int n = Constructs.all(src.getServer()).size();
        List<String> ladder = new ArrayList<>();
        for (int tier = 1; tier <= Tiers.count(); tier++) {
            ladder.add(Integer.toString(Tiers.allowance(tier)));
        }
        src.sendSuccess(() -> t("limits", n, MobileConfig.limitPerServer(), MobileConfig.limitPerPlayer(), String.join("/", ladder),
                String.format(Locale.ROOT, "%.0f", MobileConfig.damageImpactSpeed()), t(Where.serverClosed() ? "limits.closed" : "limits.open")), false);
        return n;
    }

    private static int files(CommandSourceStack src) {
        List<String> names = Blueprints.names();
        src.sendSuccess(() -> names.isEmpty() ? t("files.none") : t("files", String.join(", ", names)), false);
        return names.size();
    }

    private static int remove(CommandContext<CommandSourceStack> ctx, boolean delete) {
        CommandSourceStack src = ctx.getSource();
        long id = LongArgumentType.getLong(ctx, "id");
        Constructs.Entry e = Constructs.byId(src.getServer(), id);
        if (e == null) {
            src.sendFailure(t("no_such", id));
            return 0;
        }
        String name = e.core().name();
        if (delete) {
            ValkyrienSkiesBridge.remove(e.level(), id, true);
            log(src, "deleted construct #" + id + " (" + name + ")");
            src.sendSuccess(() -> t("deleted", id, name), true);
            return 1;
        }
        ValkyrienSkiesBridge.Release done = ValkyrienSkiesBridge.release(e.level(), id);
        if (!done.ok()) {
            src.sendFailure(t("remove_failed", id, done.reason()));
            return 0;
        }
        BlockPos corePos = done.moved().get(e.core().getBlockPos());
        if (corePos != null && e.level().getBlockEntity(corePos) instanceof com.mrgregles.bsp_mobile.construct.MobileCoreBlockEntity moved) {
            moved.setShipId(0);
        }
        log(src, "set construct #" + id + " (" + name + ") down");
        src.sendSuccess(() -> t("removed", id, name), true);
        return 1;
    }

    private static int tp(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        long id = LongArgumentType.getLong(ctx, "id");
        Constructs.Entry e = Constructs.byId(src.getServer(), id);
        if (e == null || !(src.getEntity() instanceof ServerPlayer p)) {
            src.sendFailure(t("no_such", id));
            return 0;
        }
        Vec3 at = ValkyrienSkiesBridge.toWorld(e.level(), Vec3.atCenterOf(e.core().getBlockPos().above()));
        p.teleportTo(e.level(), at.x, at.y, at.z, p.getYRot(), p.getXRot());
        src.sendSuccess(() -> t("tp", id, e.core().name()), false);
        return 1;
    }

    private static int save(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        long id = LongArgumentType.getLong(ctx, "id");
        String name = StringArgumentType.getString(ctx, "name");
        Constructs.Entry e = Constructs.byId(src.getServer(), id);
        if (e == null) {
            src.sendFailure(t("no_such", id));
            return 0;
        }
        Optional<Blueprints.Saved> saved = Blueprints.save(e.level(), id, name);
        if (saved.isEmpty()) {
            src.sendFailure(t("save_failed", name));
            return 0;
        }
        log(src, "saved construct #" + id + " as " + name);
        src.sendSuccess(() -> t("saved", id, saved.get().name(), saved.get().blocks()), true);
        return 1;
    }

    private static int paste(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "name");
        if (!(src.getEntity() instanceof ServerPlayer p) || !(p.level() instanceof ServerLevel level)) {
            src.sendFailure(t("paste_failed", name));
            return 0;
        }
        if (!Blueprints.names().contains(Blueprints.file(name).getFileName().toString().replaceAll("\\.nbt$", ""))) {
            src.sendFailure(t("no_file", name));
            return 0;
        }
        Vec3 look = p.getEyePosition().add(p.getLookAngle().scale(4));
        BlockPos at = BlockPos.containing(look).above();
        Optional<Blueprints.Pasted> pasted = Blueprints.paste(level, name, at, p);
        if (pasted.isEmpty() || pasted.get().shipId() == 0) {
            src.sendFailure(t("paste_failed", name));
            return 0;
        }
        log(src, "pasted " + name + " as construct #" + pasted.get().shipId());
        src.sendSuccess(() -> t("pasted", name, pasted.get().shipId(), pasted.get().blocks()), true);
        return 1;
    }

    /** The admin's action in the server log (D62: Server Utilities' action log when it offers a call). */
    private static void log(CommandSourceStack src, String what) {
        BSPMobile.LOGGER.info("BSP-Mobile: {} {}", src.getTextName(), what);
    }
}
