package com.mrgregles.bsp_mobile.construct;

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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The Mobile Core (D13, R2): the one block that makes a base move. The same from every side; as hard to mine as an iron
 * block; pistons cannot move it; it drops itself with the construct's name and tier on the item. Right-clicking opens
 * its screen. While its construct is assembled the core cannot be mined: release the construct first (the damage task
 * decides what a destroyed core does). The cage and heart are the block model; the ring is drawn by the client's
 * renderer.
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

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer sp && level instanceof ServerLevel server) {
            CoreService.open(sp, server, pos);
        }
        return InteractionResult.CONSUME;
    }

    /** An assembled core cannot be mined: the construct must be released first. */
    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof MobileCoreBlockEntity be && be.isAssembled()) {
            return 0.0F;
        }
        return super.getDestroyProgress(state, player, level, pos);
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
