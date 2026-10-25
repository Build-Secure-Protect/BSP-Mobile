package com.mrgregles.bsp_mobile.construct;

import com.mrgregles.bsp_mobile.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The Mobile Core (D13, R2): the one block that makes a base move. The same from every side; as hard to mine as an iron
 * block; pistons cannot move it; it drops itself with the construct's name and tier on the item. Right-clicking opens
 * its screen. While its construct is assembled the core cannot be mined: release the construct first (the damage task
 * decides what a destroyed core does). A locked core (D27 to D31) is mined only by those it admits, and drops its lock
 * with it. The cage and heart are the block model; the ring is drawn by the client's renderer.
 */
public class MobileCoreBlock extends BaseEntityBlock {
    public MobileCoreBlock() {
        super(Properties.of().mapColor(MapColor.COLOR_GRAY).strength(5.0F, 6.0F).sound(SoundType.METAL).noOcclusion()
                .requiresCorrectToolForDrops().pushReaction(PushReaction.BLOCK));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MobileCoreBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof MobileCoreBlockEntity be) {
            if (by instanceof Player p) {
                be.setOwner(p.getUUID(), p.getGameProfile().getName());
            }
            if (be.name().isBlank()) {
                be.setName(ConstructNames.next(server));
            }
            be.setShipId(0);
        }
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, ModBlocks.MOBILE_CORE_ENTITY.get(), (lvl, pos, st, be) -> {
            LockService.tick((ServerLevel) lvl, be);
            PlanService.tick((ServerLevel) lvl, be);
        });
    }

    /** Right-click: a Mobile Core Lock in hand locks the core (D29); otherwise the screen opens. */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer sp && level instanceof ServerLevel server) {
            ItemStack held = player.getItemInHand(hand);
            if (held.is(ModBlocks.MOBILE_CORE_LOCK.get()) && LockService.lock(server, pos, sp, held).ok()) {
                return InteractionResult.CONSUME;
            }
            CoreService.open(sp, server, pos);
        }
        return InteractionResult.CONSUME;
    }

    /** An assembled core cannot be mined (release the construct first); a locked one only by those it admits. */
    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof MobileCoreBlockEntity be) {
            if (be.isAssembled()) {
                return 0.0F;
            }
            if (be.locked()) {
                boolean admitted = player instanceof ServerPlayer sp && level instanceof ServerLevel server
                        ? Access.admitted(server, be, sp)
                        : be.owner() == null || be.owner().equals(player.getUUID()); // the client knows the owner, not the team
                if (!admitted) {
                    return 0.0F;
                }
            }
        }
        return super.getDestroyProgress(state, player, level, pos);
    }

    /** A core destroyed while its construct is assembled breaks the construct up (D45); a release or removal by this mod does not count. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof MobileCoreBlockEntity be) {
            CoreService.coreDestroyed(server, be);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    /** The core is a redstone source while its arrival pulse runs (D66). */
    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, net.minecraft.core.Direction direction) {
        return level.getBlockEntity(pos) instanceof MobileCoreBlockEntity be ? be.signal() : 0;
    }

    /** A mined locked core drops its lock beside the core item. */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MobileCoreBlockEntity be && be.locked()) {
            popResource(level, pos, new ItemStack(ModBlocks.MOBILE_CORE_LOCK.get()));
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        ItemStack stack = super.getCloneItemStack(level, pos, state);
        if (level.getBlockEntity(pos) instanceof MobileCoreBlockEntity be) {
            MobileCoreItem.write(stack, be.name(), be.tier());
        }
        return stack;
    }
}
