package com.mrgregles.bsp_mobile.construct;

import com.mrgregles.bsp_mobile.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Mobile Helm (D36 to D42): a pedestal with a wheel, facing where the driver looks. Right-click to take it
 * (aboard an assembled construct whose core's lock admits you); W and S thrust along its facing, A and D turn,
 * sprint doubles the thrust, sneak steps away. Mined like iron; pistons cannot move it.
 */
public class HelmBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape SHAPE = Shapes.or(Block.box(2, 0, 2, 14, 2, 14), Block.box(4, 2, 4, 12, 12, 12), Block.box(1, 4, 1, 15, 16, 2));

    public HelmBlock() {
        super(Properties.of().mapColor(MapColor.COLOR_GRAY).strength(3.0F, 6.0F).sound(SoundType.METAL).noOcclusion()
                .requiresCorrectToolForDrops().pushReaction(PushReaction.BLOCK));
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        // the driver stands behind the pedestal looking at the wheel: the block faces the way the placer looks
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HelmBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, ModBlocks.MOBILE_HELM_ENTITY.get(), (lvl, pos, st, be) -> HelmService.tick((ServerLevel) lvl, be));
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer sp && level instanceof ServerLevel server) {
            CoreService.Outcome out = HelmService.take(server, pos, sp);
            if (!out.ok()) {
                sp.displayClientMessage(Component.translatable("message.bsp_mobile.helm." + out.reason()).withStyle(ChatFormatting.GOLD), true);
            }
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof HelmBlockEntity be) {
            HelmService.leave(server, be);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    /** The helm as an item, with its tooltip. */
    public static class HelmItem extends BlockItem {
        public HelmItem(Block block, Properties props) {
            super(block, props);
        }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
            super.appendHoverText(stack, level, lines, flag);
            lines.add(Component.translatable("item.bsp_mobile.mobile_helm.tooltip").withStyle(ChatFormatting.GRAY));
        }
    }
}
