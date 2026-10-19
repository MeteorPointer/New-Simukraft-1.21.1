package common.cn.nsimu.simukraft.registry;

import common.cn.nsimu.simukraft.SimuKraft;
import common.cn.nsimu.simukraft.block.entity.ManifestBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * ModBlockEntities: 模组方块实体注册。
 */
public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, SimuKraft.MOD_ID);

    /**
     * MANIFEST: 贴墙/贴地清单方块实体，保存材料列表 NBT。
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ManifestBlockEntity>> MANIFEST =
            BLOCK_ENTITIES.register("manifest", () -> BlockEntityType.Builder.of(
                    ManifestBlockEntity::new, ModBlocks.MANIFEST.get()).build(null));

    private ModBlockEntities() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
