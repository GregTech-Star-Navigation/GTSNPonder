package com.gtsn.ponder.client;

import com.gtsn.ponder.GTSNPonder;
import com.gtsn.ponder.engine.model.SceneData;
import com.gtsn.ponder.engine.model.SceneDataParser;
import com.gtsn.ponder.engine.model.SceneFormatException;
import com.gtsn.ponder.engine.model.SceneParseResult;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 客户端场景库：从资源包扫描 {@code assets/gtsnponder/ponder/*.json}，用纯 Java
 * {@link SceneDataParser} 解析，并按场景头部 {@code target} 建索引（服务快捷键 / 命令的查找）。
 *
 * <p>首次使用时惰性加载并缓存；{@link #reload()} 可强制重扫（后续热重载工单挂到资源重载事件）。
 * 单个文件解析失败只跳过并记录告警，不影响其它场景（前向兼容）。</p>
 *
 * <p>客户端专用类（依赖 {@link Minecraft} 的资源管理器）。</p>
 */
public final class SceneLibrary {

    /** 场景资源目录（相对 {@code assets/<namespace>}）。 */
    public static final String SCENE_DIRECTORY = "ponder";
    private static final String JSON_SUFFIX = ".json";

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final SceneLibrary INSTANCE = new SceneLibrary();

    private final Map<String, SceneData> byTarget = new LinkedHashMap<>();
    private final List<SceneData> scenes = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private boolean loaded;

    private SceneLibrary() {
    }

    public static SceneLibrary get() {
        return INSTANCE;
    }

    /** 按场景 {@code target}（如 {@code gtceu:coke_oven}）查找场景。 */
    public Optional<SceneData> sceneForTarget(String target) {
        ensureLoaded();
        if (target == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byTarget.get(target));
    }

    /** 已加载的全部场景（有序）。 */
    public List<SceneData> scenes() {
        ensureLoaded();
        return List.copyOf(scenes);
    }

    public int sceneCount() {
        ensureLoaded();
        return scenes.size();
    }

    /** 解析告警（跳过未知步骤 / 失败文件）。 */
    public List<String> warnings() {
        ensureLoaded();
        return List.copyOf(warnings);
    }

    /** 强制重新扫描资源（资源重载后调用）。 */
    public synchronized void reload() {
        loaded = false;
        byTarget.clear();
        scenes.clear();
        warnings.clear();
        ensureLoaded();
    }

    private synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        ResourceManager manager = Minecraft.getInstance().getResourceManager();
        Map<ResourceLocation, Resource> resources = manager.listResources(SCENE_DIRECTORY,
                location -> location.getNamespace().equals(GTSNPonder.MODID)
                        && location.getPath().endsWith(JSON_SUFFIX));
        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            loadOne(entry.getKey(), entry.getValue());
        }
        LOGGER.info("[GTSNPonder] scene library loaded: {} scene(s) from {} file(s), {} warning(s)",
                scenes.size(), resources.size(), warnings.size());
    }

    private void loadOne(ResourceLocation location, Resource resource) {
        try (Reader reader = resource.openAsReader()) {
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) >= 0) {
                builder.append(buffer, 0, read);
            }
            SceneParseResult result = SceneDataParser.parse(builder.toString());
            for (String warning : result.warnings()) {
                warnings.add(location + ": " + warning);
                LOGGER.warn("[GTSNPonder] scene {} warning: {}", location, warning);
            }
            SceneData scene = result.scene();
            scenes.add(scene);
            if (scene.target() != null) {
                SceneData previous = byTarget.put(scene.target(), scene);
                if (previous != null) {
                    LOGGER.warn("[GTSNPonder] scene target '{}' overridden by {} (previous id={})",
                            scene.target(), location, previous.id());
                }
            } else {
                LOGGER.warn("[GTSNPonder] scene {} has no 'target'; it cannot be opened by target", location);
            }
        } catch (SceneFormatException error) {
            warnings.add(location + ": " + error.getMessage());
            LOGGER.warn("[GTSNPonder] scene {} failed to parse: {}", location, error.getMessage());
        } catch (Exception error) {
            warnings.add(location + ": " + error);
            LOGGER.warn("[GTSNPonder] scene {} failed to load", location, error);
        }
    }
}
