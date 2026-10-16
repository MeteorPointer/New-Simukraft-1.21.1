package client.cn.nsimu.simukraft.client.guidebook;

import com.mojang.blaze3d.platform.NativeImage;
import common.cn.nsimu.simukraft.SimuKraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 指南书插图缓存。本地图走资源包，远程图异步下载后再注册动态纹理。
 */
@OnlyIn(Dist.CLIENT)
public final class GuideBookImageCache {
    private static final Map<String, ImageEntry> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, CompletableFuture<ImageEntry>> LOADING = new ConcurrentHashMap<>();
    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 15000;
    private static int textureIdCounter;
    private static Consumer<String> onImageLoaded;

    private GuideBookImageCache() {
    }

    public static void setOnImageLoadedCallback(@Nullable Consumer<String> callback) {
        onImageLoaded = callback;
    }

    public static @Nonnull ImageEntry getOrLoad(@Nonnull String imageUrl) {
        ImageEntry cached = CACHE.get(imageUrl);
        if (cached != null) {
            return cached;
        }
        CompletableFuture<ImageEntry> future = LOADING.get(imageUrl);
        if (future != null && !future.isDone()) {
            return ImageEntry.missing();
        }
        LOADING.remove(imageUrl);
        if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
            loadRemoteImage(imageUrl);
        } else {
            loadLocalImage(imageUrl);
        }
        return CACHE.getOrDefault(imageUrl, ImageEntry.missing());
    }

    public static void clear() {
        for (ImageEntry entry : CACHE.values()) {
            if (entry.texture != null) {
                ResourceLocation texture = entry.texture;
                Minecraft.getInstance().execute(() -> Minecraft.getInstance().getTextureManager().release(texture));
            }
        }
        CACHE.clear();
        LOADING.clear();
        textureIdCounter = 0;
    }

    private static void loadLocalImage(String path) {
        ResourceLocation location = parseResourceLocation(path);
        if (location == null) {
            CACHE.put(path, ImageEntry.missing());
            return;
        }
        var resource = Minecraft.getInstance().getResourceManager().getResource(location);
        if (resource.isEmpty()) {
            SimuKraft.LOGGER.warn("GuideBook local image not found: {}", path);
            CACHE.put(path, ImageEntry.missing());
            return;
        }
        try (InputStream stream = resource.get().open()) {
            NativeImage image = NativeImage.read(stream);
            ResourceLocation texture = registerTexture(image, path);
            CACHE.put(path, new ImageEntry(texture, image.getWidth(), image.getHeight(), true, false));
            image.close();
        } catch (Exception exception) {
            SimuKraft.LOGGER.warn("GuideBook failed to load local image {}", path, exception);
            CACHE.put(path, ImageEntry.missing());
        }
    }

    private static void loadRemoteImage(String url) {
        CompletableFuture<ImageEntry> future = CompletableFuture.supplyAsync(() -> download(url));
        LOADING.put(url, future);
    }

    private static ImageEntry download(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", "Simukraft-GuideBook/1.0");
            connection.setInstanceFollowRedirects(true);
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return ImageEntry.missing();
            }
            String contentType = connection.getContentType();
            if (contentType != null && !contentType.startsWith("image/")) {
                return ImageEntry.missing();
            }
            try (InputStream stream = connection.getInputStream()) {
                NativeImage image = NativeImage.read(stream);
                int width = image.getWidth();
                int height = image.getHeight();
                Minecraft.getInstance().execute(() -> {
                    try {
                        ResourceLocation texture = registerTexture(image, url);
                        CACHE.put(url, new ImageEntry(texture, width, height, true, true));
                        if (onImageLoaded != null) {
                            onImageLoaded.accept(url);
                        }
                    } catch (Exception exception) {
                        SimuKraft.LOGGER.warn("GuideBook failed to register remote image {}", url, exception);
                        CACHE.put(url, ImageEntry.missing());
                    } finally {
                        image.close();
                        LOADING.remove(url);
                    }
                });
                return new ImageEntry(null, width, height, false, true);
            }
        } catch (Exception exception) {
            SimuKraft.LOGGER.warn("GuideBook failed to download image {}", url, exception);
            return ImageEntry.missing();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            LOADING.remove(url);
        }
    }

    @Nullable
    private static ResourceLocation parseResourceLocation(String path) {
        if (path.contains(":")) {
            return ResourceLocation.tryParse(path);
        }
        String fullPath = path.startsWith("guidebook/image/") ? path : "guidebook/image/" + path;
        return ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, fullPath);
    }

    private static synchronized ResourceLocation registerTexture(NativeImage image, String key) {
        String textureName = "guidebook_image_" + textureIdCounter++ + "_" + Math.abs(key.hashCode());
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, textureName);
        DynamicTexture dynamicTexture = new DynamicTexture(image);
        Minecraft.getInstance().getTextureManager().register(location, dynamicTexture);
        return location;
    }

    public static final class ImageEntry {
        public final ResourceLocation texture;
        public final int width;
        public final int height;
        public final boolean loaded;
        public final boolean remote;

        public ImageEntry(ResourceLocation texture, int width, int height, boolean loaded, boolean remote) {
            this.texture = texture;
            this.width = width;
            this.height = height;
            this.loaded = loaded;
            this.remote = remote;
        }

        public static ImageEntry missing() {
            return new ImageEntry(null, 16, 16, false, false);
        }
    }
}
