package client.cn.kafei.simukraft.client.city.map;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.GrassBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simukraft 自有方块颜色映射系统。
 * 优先用顶面纹理均值乘生物群系着色，接近 Xaero World Map 的地表质感。
 */
@OnlyIn(Dist.CLIENT)
public class SimuBlockColors {
    private static final SimuBlockColors INSTANCE = new SimuBlockColors();
    private final Map<Block, Integer> colorOverrides = new ConcurrentHashMap<>();
    private boolean initialized = false;

    private SimuBlockColors() {
    }

    public static SimuBlockColors getInstance() {
        return INSTANCE;
    }

    /** 初始化颜色覆盖表。 */
    public void init() {
        if (initialized) return;
        initialized = true;

        colorOverrides.put(Blocks.WATER, 0xFF3F76E4);
        colorOverrides.put(Blocks.LAVA, 0xFFD4610A);
        colorOverrides.put(Blocks.ICE, 0xFF91B4FC);
        colorOverrides.put(Blocks.PACKED_ICE, 0xFF8DB4FA);
        colorOverrides.put(Blocks.BLUE_ICE, 0xFF74AEF9);
        colorOverrides.put(Blocks.SNOW, 0xFFFAFAFA);
        colorOverrides.put(Blocks.SNOW_BLOCK, 0xFFF0F0F0);
        colorOverrides.put(Blocks.SAND, 0xFFDBD3A0);
        colorOverrides.put(Blocks.RED_SAND, 0xFFA85320);
        colorOverrides.put(Blocks.GRAVEL, 0xFF837E7A);
        colorOverrides.put(Blocks.CLAY, 0xFF9EA4B0);
        colorOverrides.put(Blocks.BEDROCK, 0xFF545454);
        colorOverrides.put(Blocks.NETHERRACK, 0xFF6B3535);
        colorOverrides.put(Blocks.END_STONE, 0xFFDBDE8E);
        colorOverrides.put(Blocks.OBSIDIAN, 0xFF14121D);
        colorOverrides.put(Blocks.DIAMOND_BLOCK, 0xFF6EECD2);
        colorOverrides.put(Blocks.GOLD_BLOCK, 0xFFF9EC4E);
        colorOverrides.put(Blocks.IRON_BLOCK, 0xFFD8D8D8);
        colorOverrides.put(Blocks.EMERALD_BLOCK, 0xFF41C950);
        colorOverrides.put(Blocks.COAL_BLOCK, 0xFF161616);
        colorOverrides.put(Blocks.REDSTONE_BLOCK, 0xFFA81303);
        colorOverrides.put(Blocks.LAPIS_BLOCK, 0xFF1D47A5);
        colorOverrides.put(Blocks.MYCELIUM, 0xFF6F6265);
        colorOverrides.put(Blocks.SOUL_SAND, 0xFF544033);
        colorOverrides.put(Blocks.GLOWSTONE, 0xFFAB8654);
        colorOverrides.put(Blocks.MELON, 0xFF669E1F);
        colorOverrides.put(Blocks.PUMPKIN, 0xFFC07615);
        colorOverrides.put(Blocks.TNT, 0xFFDB4A2B);
        colorOverrides.put(Blocks.BOOKSHELF, 0xFF6B5339);
        colorOverrides.put(Blocks.COBBLESTONE, 0xFF7F7F7F);
        colorOverrides.put(Blocks.STONE, 0xFF7D7D7D);
        colorOverrides.put(Blocks.DEEPSLATE, 0xFF505050);
        colorOverrides.put(Blocks.MOSS_BLOCK, 0xFF596D28);
        colorOverrides.put(Blocks.CHERRY_LEAVES, 0xFFEEB3C7);
        colorOverrides.put(Blocks.CHERRY_LOG, 0xFF3A1F23);
    }

    /**
     * 获取方块在给定位置的地图颜色。
     *
     * @param state 方块状态
     * @param level 世界实例
     * @param pos 方块位置
     * @return ARGB 颜色值
     */
    public int getBlockColor(BlockState state, Level level, BlockPos pos) {
        if (state.isAir()) {
            return 0x00000000;
        }

        int textureColor = colorFromTexture(state, level, pos);
        if (textureColor != 0) {
            return textureColor;
        }

        Integer override = colorOverrides.get(state.getBlock());
        if (override != null) {
            return tintedFallback(state, level, pos, override);
        }

        try {
            MapColor mapColor = state.getMapColor(Objects.requireNonNull(level), Objects.requireNonNull(pos));
            if (mapColor != MapColor.NONE) {
                return tintedFallback(state, level, pos, 0xFF000000 | mapColor.col);
            }
        } catch (RuntimeException ignored) {
        }

        return 0xFF7F7F7F;
    }

    /**
     * colorFromTexture: 用顶面贴图均值乘生物群系着色，失败返回 0。
     */
    private int colorFromTexture(BlockState state, Level level, BlockPos pos) {
        SimuBlockTextureColors.SampledTexture sampled = SimuBlockTextureColors.sample(state);
        if (sampled == null) {
            return 0;
        }
        int color = sampled.argb();
        if (!sampled.tinted()) {
            return 0xFF000000 | color;
        }
        int tint = readBlockTint(state, level, pos, sampled.tintIndex());
        if (tint == -1) {
            tint = inferBiomeTint(state, level, pos);
        }
        if (tint == -1) {
            return 0xFF000000 | color;
        }
        return multiplyTint(color, tint);
    }

    /**
     * tintedFallback: 无贴图时用中灰乘群系色，避免草地直接变成原版亮绿。
     */
    private int tintedFallback(BlockState state, Level level, BlockPos pos, int baseColor) {
        int tint = inferBiomeTint(state, level, pos);
        if (tint == -1) {
            return baseColor;
        }
        return multiplyTint(0xFF9A9A9A, tint);
    }

    private int readBlockTint(BlockState state, Level level, BlockPos pos, int tintIndex) {
        try {
            BlockColors blockColors = Minecraft.getInstance().getBlockColors();
            int tintColor = blockColors.getColor(state, level, pos, tintIndex);
            if (tintColor != -1 && tintColor != 0) {
                return tintColor;
            }
        } catch (RuntimeException ignored) {
        }
        return -1;
    }

    private int inferBiomeTint(BlockState state, Level level, BlockPos pos) {
        Block block = state.getBlock();
        if (block instanceof GrassBlock) {
            return getBiomeGrassColor(level, pos);
        }
        if (state.is(Objects.requireNonNull(BlockTags.LEAVES))) {
            return getBiomeFoliageColor(level, pos);
        }
        if (block instanceof LiquidBlock && state.getFluidState().is(Fluids.WATER)) {
            return getBiomeWaterColor(level, pos);
        }
        return -1;
    }

    /**
     * multiplyTint: 将纹理底色与生物群系着色相乘，得到 Xaero 风格的地表色。
     */
    public static int multiplyTint(int argb, int tintRgb) {
        if (tintRgb == -1 || (tintRgb & 0x00FFFFFF) == 0x00FFFFFF) {
            return argb;
        }
        int tint = tintRgb;
        if ((tint >>> 24) == 0) {
            tint = 0xFF000000 | (tint & 0x00FFFFFF);
        }
        return FastColor.ARGB32.multiply(argb | 0xFF000000, tint);
    }

    /**
     * slopeBrightness: 按西北高差计算明暗，正值表示迎光脊线。
     */
    public static float slopeBrightness(int height, int north, int west, boolean water) {
        float scale = water ? 0.045f : 0.09f;
        return Mth.clamp(((height - north) + (height - west)) * scale, -0.42f, 0.38f);
    }

    /** 获取生物群系草地颜色。 */
    private int getBiomeGrassColor(Level level, BlockPos pos) {
        try {
            Biome biome = level.getBiome(Objects.requireNonNull(pos)).value();
            int color = biome.getGrassColor(pos.getX(), pos.getZ());
            return 0xFF000000 | color;
        } catch (Exception e) {
            return 0xFF7CBB4A; // 默认草地颜色
        }
    }

    /** 获取生物群系树叶颜色。 */
    private int getBiomeFoliageColor(Level level, BlockPos pos) {
        try {
            Biome biome = level.getBiome(Objects.requireNonNull(pos)).value();
            int color = biome.getFoliageColor();
            return 0xFF000000 | color;
        } catch (Exception e) {
            return 0xFF59AE30; // 默认树叶颜色
        }
    }

    /** 获取生物群系水体颜色。 */
    private int getBiomeWaterColor(Level level, BlockPos pos) {
        try {
            Biome biome = level.getBiome(Objects.requireNonNull(pos)).value();
            int color = biome.getWaterColor();
            return 0xFF000000 | color;
        } catch (Exception e) {
            return 0xFF3F76E4; // 默认水体颜色
        }
    }

    /** 未采样到地表时的 3D/2D 回退色。 */
    public static final int FALLBACK_TERRAIN_COLOR = 0xFF6E8B5A;

    /** 城市核心所在格叠一层半透明蓝，保留地表色。 */
    public static final int CORE_HIGHLIGHT_OVERLAY = 0x994080FF;

    /**
     * opaqueTerrainColor: 透明采样当成未扫描，回退到默认地表色。
     */
    public static int opaqueTerrainColor(int argb) {
        return (argb >>> 24) == 0 ? FALLBACK_TERRAIN_COLOR : argb | 0xFF000000;
    }

    /**
     * composeMapColumnColor: 3D 柱体色 = 地表 + 坡度 + 领地填充 + 可选核心高亮。
     * 领地填充必须用它自己的 alpha。再 OR 0x66 会把 0x55 变成 0x77，草地会被盖成亮绿。
     */
    public static int composeMapColumnColor(int terrainArgb, int territoryFillArgb, boolean highlightCore,
                                            int height, int northHeight, int westHeight, boolean water) {
        return composeThreeDColumnColor(
                terrainArgb, territoryFillArgb, highlightCore,
                height, northHeight, westHeight, Integer.MIN_VALUE, Integer.MIN_VALUE,
                water, false);
    }

    /**
     * composeThreeDColumnColor: 立体地图柱体色。
     * 已烘过坡度的贴图像素不再乘一次西北坡；高差遮挡单独压暗，让建筑脚下落影。
     */
    public static int composeThreeDColumnColor(int terrainArgb, int overlayArgb, boolean highlightCore,
                                               int height, int northHeight, int westHeight, int southHeight, int eastHeight,
                                               boolean water, boolean alreadyShaded) {
        int color = opaqueTerrainColor(terrainArgb);
        int north = northHeight == Integer.MIN_VALUE ? height : northHeight;
        int west = westHeight == Integer.MIN_VALUE ? height : westHeight;
        float shade = alreadyShaded ? 0.0F : slopeBrightness(height, north, west, water);
        shade += heightOcclusion(height, northHeight, westHeight, southHeight, eastHeight);
        if (shade != 0.0F) {
            color = adjustBrightness(color, Mth.clamp(shade, -0.5F, 0.38F));
        }
        if ((overlayArgb >>> 24) != 0) {
            color = blendColors(color, overlayArgb);
        }
        if (highlightCore) {
            color = blendColors(color, CORE_HIGHLIGHT_OVERLAY);
        }
        return color;
    }

    /**
     * heightOcclusion: 四周更高的柱把脚下压暗，平坦草地几乎不变。
     */
    public static float heightOcclusion(int height, int north, int west, int south, int east) {
        return occlusionFrom(height, north, 0.08F)
                + occlusionFrom(height, west, 0.08F)
                + occlusionFrom(height, south, 0.05F)
                + occlusionFrom(height, east, 0.05F);
    }

    private static float occlusionFrom(int height, int neighbor, float weight) {
        if (neighbor == Integer.MIN_VALUE || neighbor <= height) {
            return 0.0F;
        }
        return -Math.min(weight * 2.0F, (neighbor - height) * weight * 0.5F);
    }

    /**
     * lambertShade: 高度场法线点西北光。朝西北的坡更亮，朝东南的坡更暗。
     */
    public static float lambertShade(int west, int east, int north, int south, float cellSize) {
        float span = Math.max(1.0F, cellSize);
        float dx = (east - west) / (2.0F * span);
        float dz = (south - north) / (2.0F * span);
        float nx = -dx;
        float ny = 1.0F;
        float nz = -dz;
        float inv = 1.0F / (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        nx *= inv;
        ny *= inv;
        nz *= inv;
        float lit = nx * -0.52F + ny * 0.78F + nz * -0.34F;
        return Mth.clamp(0.42F + lit * 0.70F, 0.30F, 1.18F);
    }

    /**
     * scaleColor: 按系数缩放 RGB，用于沙盘法线打光。
     */
    public static int scaleColor(int argb, float factor) {
        int a = (argb >>> 24) & 0xFF;
        int r = Mth.clamp((int) (((argb >> 16) & 0xFF) * factor + 0.5F), 0, 255);
        int g = Mth.clamp((int) (((argb >> 8) & 0xFF) * factor + 0.5F), 0, 255);
        int b = Mth.clamp((int) ((argb & 0xFF) * factor + 0.5F), 0, 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * 混合两个 ARGB 颜色。
     * 
     * @param base 基色
     * @param overlay 叠加色，alpha 控制混合强度
     * @return 混合后的颜色
     */
    public static int blendColors(int base, int overlay) {
        int oa = (overlay >> 24) & 0xFF;
        if (oa == 0) return base;
        if (oa == 255) return overlay;

        int ba = (base >> 24) & 0xFF;
        int br = (base >> 16) & 0xFF;
        int bg = (base >> 8) & 0xFF;
        int bb = base & 0xFF;

        int or = (overlay >> 16) & 0xFF;
        int og = (overlay >> 8) & 0xFF;
        int ob = overlay & 0xFF;

        float alpha = oa / 255f;
        int r = (int) (br * (1 - alpha) + or * alpha);
        int g = (int) (bg * (1 - alpha) + og * alpha);
        int b = (int) (bb * (1 - alpha) + ob * alpha);
        int a = Math.max(ba, oa);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * 调整 ARGB 颜色亮度。
     * 
     * @param color ARGB 颜色
     * @param brightness 亮度调节值，范围 [-1.0, 1.0]
     * @return 调整后的颜色
     */
    public static int adjustBrightness(int color, float brightness) {
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        if (brightness > 0) {
            r = (int) (r + (255 - r) * brightness);
            g = (int) (g + (255 - g) * brightness);
            b = (int) (b + (255 - b) * brightness);
        } else {
            float factor = 1.0f + brightness;
            r = (int) (r * factor);
            g = (int) (g * factor);
            b = (int) (b * factor);
        }

        r = Math.min(Math.max(r, 0), 255);
        g = Math.min(Math.max(g, 0), 255);
        b = Math.min(Math.max(b, 0), 255);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** 将 ARGB 转换为 NativeImage 使用的 ABGR 格式。 */
    public static int toNativeColor(int argb) {
        int a = (argb >> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    /** fromNativeColor: NativeImage ABGR 转回 ARGB。 */
    public static int fromNativeColor(int abgr) {
        int a = (abgr >> 24) & 0xFF;
        int b = (abgr >> 16) & 0xFF;
        int g = (abgr >> 8) & 0xFF;
        int r = abgr & 0xFF;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
