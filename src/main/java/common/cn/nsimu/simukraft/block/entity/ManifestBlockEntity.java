package common.cn.nsimu.simukraft.block.entity;

import common.cn.nsimu.simukraft.item.ManifestItem;
import common.cn.nsimu.simukraft.registry.ModBlockEntities;
import common.cn.nsimu.simukraft.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * ManifestBlockEntity: 保存贴在方块上的清单 NBT，与手持清单使用同一套 CUSTOM_DATA。
 */
public final class ManifestBlockEntity extends BlockEntity {
    private static final String TAG_MANIFEST = "Manifest";

    private CompoundTag manifestTag = new CompoundTag();

    public ManifestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MANIFEST.get(), pos, state);
    }

    /**
     * loadFromItem: 把物品上的清单数据写入方块实体。
     */
    public void loadFromItem(@Nonnull ItemStack stack) {
        manifestTag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /**
     * toItem: 生成带当前清单数据的物品，供打开界面和掉落使用。
     */
    @Nonnull
    public ItemStack toItem() {
        ItemStack stack = new ItemStack(ModItems.MANIFEST.get());
        if (!manifestTag.isEmpty()) {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(manifestTag.copy()));
        }
        return stack;
    }

    /**
     * setChecked: 勾选状态写回方块实体。
     */
    public void setChecked(int index, boolean checked) {
        ItemStack stack = toItem();
        ManifestItem.setChecked(stack, index, checked);
        loadFromItem(stack);
    }

    @Override
    protected void saveAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!manifestTag.isEmpty()) {
            tag.put(TAG_MANIFEST, manifestTag.copy());
        }
    }

    @Override
    protected void loadAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(TAG_MANIFEST, CompoundTag.TAG_COMPOUND)) {
            manifestTag = tag.getCompound(TAG_MANIFEST).copy();
        } else {
            manifestTag = new CompoundTag();
        }
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    @Nonnull
    public CompoundTag getUpdateTag(@Nonnull HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }
}
