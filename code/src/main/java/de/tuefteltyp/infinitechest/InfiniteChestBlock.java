package de.tuefteltyp.infinitechest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** A separate storage block. Ordinary chests are never modified. */
public final class InfiniteChestBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final IntegerProperty LID = IntegerProperty.create("lid", 0, 2);

    public InfiniteChestBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false).setValue(LID, 0));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING, OPEN, LID);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state,
            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof InfiniteChestBlockEntity chest) {
            ChestDirectory.registerNew(serverLevel, chest);
        }
    }

    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int step = state.getValue(LID);
        int goal = state.getValue(OPEN) ? 2 : 0;
        if (step == goal) return;
        int next = step + Integer.signum(goal - step);
        level.setBlock(pos, state.setValue(LID, next), 3);
        if (next != goal) level.scheduleTick(pos, this, 1);
    }

    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new InfiniteChestBlockEntity(pos, state);
    }

    @Override protected InteractionResult useItemOn(ItemStack held, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return open(level, pos, player);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level,
            BlockPos pos, Player player, BlockHitResult hit) {
        return open(level, pos, player);
    }

    private InteractionResult open(Level level, BlockPos pos, Player player) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof InfiniteChestBlockEntity chest) {
            if (level instanceof ServerLevel serverLevel) ChestDirectory.ensure(serverLevel, chest);
            player.openMenu(chest);
        }
        return InteractionResult.SUCCESS;
    }
}
