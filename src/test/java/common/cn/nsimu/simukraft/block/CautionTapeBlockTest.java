package common.cn.nsimu.simukraft.block;

import common.cn.nsimu.simukraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CautionTapeBlockTest {
    @Test
    void isolatedBlockKeepsTheRing() {
        BlockState tape = ModBlocks.CAUTION_TAPE.get().defaultBlockState();
        BlockState result = CautionTapeBlock.applyConnections(tape, getter(Map.of(BlockPos.ZERO, tape)), BlockPos.ZERO);

        assertFalse(result.getValue(CautionTapeBlock.NORTH));
        assertFalse(result.getValue(CautionTapeBlock.EAST));
        assertFalse(result.getValue(CautionTapeBlock.SOUTH));
        assertFalse(result.getValue(CautionTapeBlock.WEST));
    }

    @Test
    void oneNorthNeighborBecomesANorthSouthLine() {
        BlockState tape = ModBlocks.CAUTION_TAPE.get().defaultBlockState();
        BlockGetter level = getter(Map.of(
                BlockPos.ZERO, tape,
                BlockPos.ZERO.north(), tape));

        BlockState result = CautionTapeBlock.applyConnections(tape, level, BlockPos.ZERO);

        assertTrue(result.getValue(CautionTapeBlock.NORTH));
        assertTrue(result.getValue(CautionTapeBlock.SOUTH));
        assertFalse(result.getValue(CautionTapeBlock.EAST));
        assertFalse(result.getValue(CautionTapeBlock.WEST));
    }

    @Test
    void northAndEastNeighborsBecomeACorner() {
        BlockState tape = ModBlocks.CAUTION_TAPE.get().defaultBlockState();
        BlockGetter level = getter(Map.of(
                BlockPos.ZERO, tape,
                BlockPos.ZERO.north(), tape,
                BlockPos.ZERO.east(), tape));

        BlockState result = CautionTapeBlock.applyConnections(tape, level, BlockPos.ZERO);

        assertTrue(result.getValue(CautionTapeBlock.NORTH));
        assertTrue(result.getValue(CautionTapeBlock.EAST));
        assertFalse(result.getValue(CautionTapeBlock.SOUTH));
        assertFalse(result.getValue(CautionTapeBlock.WEST));
    }

    @Test
    void aThreeBlockSouthLineConnectsEveryCellIncludingTheEnds() {
        BlockState tape = ModBlocks.CAUTION_TAPE.get().defaultBlockState();
        BlockPos start = BlockPos.ZERO;
        BlockPos middle = start.south();
        BlockPos end = middle.south();
        BlockGetter level = getter(Map.of(
                start, tape,
                middle, tape,
                end, tape));

        assertLine(CautionTapeBlock.applyConnections(tape, level, start));
        assertLine(CautionTapeBlock.applyConnections(tape, level, middle));
        assertLine(CautionTapeBlock.applyConnections(tape, level, end));
    }

    @Test
    void applyConnectionsDoesNotWriteTheWorld() {
        BlockState tape = ModBlocks.CAUTION_TAPE.get().defaultBlockState();
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        blocks.put(BlockPos.ZERO, tape);
        blocks.put(BlockPos.ZERO.south(), tape);
        BlockGetter level = getter(blocks);

        CautionTapeBlock.applyConnections(tape, level, BlockPos.ZERO);

        assertSame(tape, blocks.get(BlockPos.ZERO));
        assertSame(tape, blocks.get(BlockPos.ZERO.south()));
    }

    private static void assertLine(BlockState state) {
        assertTrue(state.getValue(CautionTapeBlock.NORTH));
        assertTrue(state.getValue(CautionTapeBlock.SOUTH));
        assertFalse(state.getValue(CautionTapeBlock.EAST));
        assertFalse(state.getValue(CautionTapeBlock.WEST));
    }

    private static BlockGetter getter(Map<BlockPos, BlockState> blocks) {
        return new BlockGetter() {
            @Override
            public BlockState getBlockState(BlockPos pos) {
                return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
            }

            @Override
            public FluidState getFluidState(BlockPos pos) {
                return Fluids.EMPTY.defaultFluidState();
            }

            @Nullable
            @Override
            public BlockEntity getBlockEntity(BlockPos pos) {
                return null;
            }

            @Override
            public int getHeight() {
                return 384;
            }

            @Override
            public int getMinBuildHeight() {
                return -64;
            }
        };
    }
}
