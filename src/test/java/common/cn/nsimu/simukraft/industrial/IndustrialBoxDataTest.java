package common.cn.nsimu.simukraft.industrial;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IndustrialBoxDataTest {
    @Test
    void workStateRoundTripsThroughNbt() {
        IndustrialBoxData data = new IndustrialBoxData(new BlockPos(1, 64, 2));
        data.setWorkState("{\"items\":[{\"nbt\":\"{}\"}]}");

        IndustrialBoxData loaded = IndustrialBoxData.fromTag(data.toTag());

        assertEquals(data.workState(), loaded.workState());
    }
}
