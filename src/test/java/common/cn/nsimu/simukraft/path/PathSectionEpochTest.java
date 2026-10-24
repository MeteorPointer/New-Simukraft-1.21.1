package common.cn.nsimu.simukraft.path;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PathSectionEpochTest {
    private static final ResourceLocation DIMENSION = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");

    @Test
    void blockChangeInvalidatesOnlyStampsThatCoverTheSection() {
        PathSectionEpochs epochs = new PathSectionEpochs();
        long[] near = PathSectionEpochs.sectionsIn(0, 15, 64, 80, 0, 15);
        long[] far = PathSectionEpochs.sectionsIn(400, 415, 64, 80, 400, 415);
        PathSectionStamp nearStamp = epochs.capture(near);
        PathSectionStamp farStamp = epochs.capture(far);

        epochs.bumpAround(new BlockPos(3, 70, 4));

        assertFalse(epochs.matches(nearStamp));
        assertTrue(epochs.matches(farStamp));
    }

    @Test
    void pathCacheDropsAResultWhoseSectionChanged() {
        PathSectionEpochs epochs = new PathSectionEpochs();
        PathSectionStamp stamp = epochs.capture(PathSectionEpochs.sectionsIn(0, 15, 64, 64, 0, 15));
        PathResultCache cache = new PathResultCache();
        PathRequest request = new PathRequest(UUID.randomUUID(), DIMENSION, new BlockPos(1, 64, 1),
                new Vec3(4.5D, 64.0D, 1.5D), MovementIntent.WALK, 0L);
        PathCacheKey key = new PathCacheKey(DIMENSION, request.startPos(), request.targetBlockPos(), request.intent());
        cache.put(key, PathResult.success(request, List.of(
                        new PathWaypoint(new BlockPos(1, 64, 1), new Vec3(1.5D, 64.0D, 1.5D), MovementMode.WALK))),
                0L, 100, stamp);

        assertNotNull(cache.get(key, 1L, epochs));
        epochs.bumpAround(new BlockPos(2, 64, 2));
        assertNull(cache.get(key, 1L, epochs));
    }
}
