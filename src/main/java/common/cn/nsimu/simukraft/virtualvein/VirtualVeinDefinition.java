package common.cn.nsimu.simukraft.virtualvein;

import common.cn.nsimu.simukraft.building.BuildingLocale;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Climate;

import java.util.Objects;

public record VirtualVeinDefinition(String id,
                                    String displayName,
                                    String displayNameEn,
                                    int priority,
                                    VirtualVeinRange continentalness,
                                    VirtualVeinRange erosion,
                                    VirtualVeinRange depth,
                                    VirtualVeinRange temperature,
                                    VirtualVeinRange humidity,
                                    VirtualVeinRange weirdness,
                                    int minY,
                                    int maxY,
                                    ResourceLocation productId,
                                    int minAmount,
                                    int maxAmount,
                                    int periodTicks) {
    private static final int OVERWORLD_MIN_Y = -64;
    private static final int OVERWORLD_MAX_Y = 320;

    public VirtualVeinDefinition(String id,
                                 String displayName,
                                 int priority,
                                 VirtualVeinRange continentalness,
                                 VirtualVeinRange erosion,
                                 VirtualVeinRange depth,
                                 VirtualVeinRange temperature,
                                 VirtualVeinRange humidity,
                                 VirtualVeinRange weirdness,
                                 int minY,
                                 int maxY,
                                 ResourceLocation productId,
                                 int minAmount,
                                 int maxAmount,
                                 int periodTicks) {
        this(id, displayName, "", priority, continentalness, erosion, depth, temperature, humidity, weirdness,
                minY, maxY, productId, minAmount, maxAmount, periodTicks);
    }

    public VirtualVeinDefinition {
        id = requireText(id, "id");
        displayName = requireText(displayName, "displayName");
        displayNameEn = displayNameEn == null ? "" : displayNameEn.trim();
        continentalness = Objects.requireNonNull(continentalness, "continentalness");
        erosion = Objects.requireNonNull(erosion, "erosion");
        depth = Objects.requireNonNull(depth, "depth");
        temperature = Objects.requireNonNull(temperature, "temperature");
        humidity = Objects.requireNonNull(humidity, "humidity");
        weirdness = Objects.requireNonNull(weirdness, "weirdness");
        if (minY > maxY || minY < OVERWORLD_MIN_Y || maxY > OVERWORLD_MAX_Y) {
            throw new IllegalArgumentException("Virtual vein Y range must stay within the overworld bounds");
        }
        productId = Objects.requireNonNull(productId, "productId");
        if (minAmount <= 0 || maxAmount < minAmount || periodTicks <= 0) {
            throw new IllegalArgumentException("Invalid virtual vein production");
        }
    }

    /** localizedName: 英文玩家显示矿脉英文名。 */
    public String localizedName() {
        if (BuildingLocale.preferEnglish() && !displayNameEn.isBlank()) {
            return displayNameEn;
        }
        return displayName;
    }

    /** matches: 按多重噪声参数点的六项范围判断矿脉资格。 */
    public boolean matches(Climate.ParameterPoint point) {
        return matches(continentalness, point.continentalness())
                && matches(erosion, point.erosion())
                && matches(depth, point.depth())
                && matches(temperature, point.temperature())
                && matches(humidity, point.humidity())
                && matches(weirdness, point.weirdness());
    }

    private static boolean matches(VirtualVeinRange definitionRange, Climate.Parameter parameterRange) {
        return definitionRange.overlaps(
                Climate.unquantizeCoord(parameterRange.min()),
                Climate.unquantizeCoord(parameterRange.max())
        );
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
