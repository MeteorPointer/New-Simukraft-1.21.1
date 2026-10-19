package common.cn.nsimu.simukraft.block;

import common.cn.nsimu.simukraft.block.entity.ManifestBlockEntity;
import common.cn.nsimu.simukraft.item.ManifestItem;
import common.cn.nsimu.simukraft.registry.ModBlocks;
import common.cn.nsimu.simukraft.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class ManifestBlockTest {
    /**
     * wallShapeIsSixteenByFourteenByOne: 墙面清单为 16 高、14 宽、1 厚。
     */
    @Test
    void wallShapeIsSixteenByFourteenByOne() {
        BlockState state = ModBlocks.MANIFEST.get().defaultBlockState()
                .setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.WALL)
                .setValue(FaceAttachedHorizontalDirectionalBlock.FACING, Direction.SOUTH);
        VoxelShape shape = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        assertEquals(1.0 / 16.0, shape.min(Direction.Axis.X), 1.0e-6);
        assertEquals(15.0 / 16.0, shape.max(Direction.Axis.X), 1.0e-6);
        assertEquals(0.0, shape.min(Direction.Axis.Y), 1.0e-6);
        assertEquals(1.0, shape.max(Direction.Axis.Y), 1.0e-6);
        assertEquals(0.0, shape.min(Direction.Axis.Z), 1.0e-6);
        assertEquals(1.0 / 16.0, shape.max(Direction.Axis.Z), 1.0e-6);
    }

    @Test
    void defaultStateLiesOnTheFloor() {
        assertEquals(AttachFace.FLOOR, ModBlocks.MANIFEST.get().defaultBlockState()
                .getValue(FaceAttachedHorizontalDirectionalBlock.FACE));
    }

    @Test
    void manifestItemPlacesTheManifestBlock() {
        assertInstanceOf(BlockItem.class, ModItems.MANIFEST.get());
        assertEquals(ModBlocks.MANIFEST.get(), ((BlockItem) ModItems.MANIFEST.get()).getBlock());
    }

    @Test
    void blockEntityRoundtripsManifestCustomData() {
        ManifestBlockEntity entity = new ManifestBlockEntity(BlockPos.ZERO, ModBlocks.MANIFEST.get().defaultBlockState());
        ItemStack stack = new ItemStack(ModItems.MANIFEST.get());
        CompoundTag tag = new CompoundTag();
        tag.putString("BuildingName", "warehouse");
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

        entity.loadFromItem(stack);

        ItemStack restored = entity.toItem();
        assertEquals("warehouse", ManifestItem.getBuildingName(restored));
    }

    @Test
    void blockResourcesExist() throws Exception {
        assertResource("/assets/simukraft/blockstates/manifest.json");
        assertResource("/assets/simukraft/models/block/manifest_floor.json");
        assertResource("/assets/simukraft/models/block/manifest_wall.json");
        assertResource("/assets/simukraft/textures/block/manifest_front.png");
        assertResource("/assets/simukraft/textures/block/manifest_back.png");
        assertResource("/data/simukraft/loot_table/blocks/manifest.json");
    }

    private static void assertResource(String path) throws Exception {
        try (InputStream in = ManifestBlockTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path);
        }
    }
}
