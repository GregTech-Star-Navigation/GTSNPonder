package com.gtsn.ponder.client;

import com.gtsn.ponder.GTSNPonder;
import com.gtsn.ponder.editor.DraftStore;
import com.gtsn.ponder.editor.EditorSession;
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

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 客户端场景库：扫描两个来源，用纯 Java {@link SceneDataParser} 解析，并按场景头部 {@code target}
 * 建索引（服务快捷键 / 命令的查找）。
 *
 * <ol>
 *   <li><b>资源包</b>：{@code assets/gtsnponder/ponder/*.json}（随包 / 数据包分发）；</li>
 *   <li><b>作者目录</b>（可写）：{@code <gameDir>/}{@value #AUTHOR_DIRECTORY}
 *       ——游戏内编辑器保存的草稿写到这里，{@link #saveAuthorScene(SceneData)} 保存后自动
 *       {@link #reload() 热重载}，无需重启即可重编译并重放编辑后的场景。</li>
 * </ol>
 *
 * <p>作者场景后加载：同一 {@code target} 下作者目录覆盖资源包（ADR-0003 手作以场景粒度覆盖自动 /
 * 随包场景）。单个文件解析失败只跳过并记录告警，不影响其它场景（前向兼容）。</p>
 *
 * <p>客户端专用类（依赖 {@link Minecraft} 的资源管理器与 gameDir）。</p>
 */
public final class SceneLibrary {

    /** 场景资源目录（相对 {@code assets/<namespace>}）。 */
    public static final String SCENE_DIRECTORY = "ponder";
    /** 可写作者目录（相对 gameDir）：游戏内编辑器保存 / 热重载的来源。 */
    public static final String AUTHOR_DIRECTORY = "gtsnponder-scenes";
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

    /** 可写作者目录（{@code <gameDir>/gtsnponder-scenes}）。 */
    public Path authorDirectory() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(AUTHOR_DIRECTORY);
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

    /** 强制重新扫描资源 + 作者目录（资源重载后 / 编辑器保存后调用）。 */
    public synchronized void reload() {
        loaded = false;
        byTarget.clear();
        scenes.clear();
        warnings.clear();
        ensureLoaded();
    }

    /**
     * 保存作者草稿到可写作者目录并热重载场景库（编辑器保存入口）：
     * 写出走 {@link DraftStore}（{@link com.gtsn.ponder.engine.model.SceneDataWriter}），随后
     * {@link #reload()} 让编辑后的场景立即可被查找 / 重放，无需重启。
     *
     * @return 写出的文件路径
     */
    public Path saveAuthorScene(SceneData scene) throws IOException {
        Path file = DraftStore.write(authorDirectory(), scene, EditorSession.fileStemFor(scene));
        LOGGER.info("[GTSNPonder] editor saved author scene for '{}' -> {}", scene.target(), file);
        reload();
        return file;
    }

    private synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        loadResources();
        loadAuthorDirectory();
        LOGGER.info("[GTSNPonder] scene library loaded: {} scene(s), {} warning(s)",
                scenes.size(), warnings.size());
    }

    private void loadResources() {
        ResourceManager manager = Minecraft.getInstance().getResourceManager();
        Map<ResourceLocation, Resource> resources = manager.listResources(SCENE_DIRECTORY,
                location -> location.getNamespace().equals(GTSNPonder.MODID)
                        && location.getPath().endsWith(JSON_SUFFIX));
        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            try (Reader reader = entry.getValue().openAsReader()) {
                StringBuilder builder = new StringBuilder();
                char[] buffer = new char[4096];
                int read;
                while ((read = reader.read(buffer)) >= 0) {
                    builder.append(buffer, 0, read);
                }
                register(entry.getKey().toString(), builder.toString());
            } catch (SceneFormatException error) {
                warn(entry.getKey() + ": " + error.getMessage());
            } catch (Exception error) {
                warn(entry.getKey() + ": " + error);
            }
        }
    }

    /** 作者目录后加载，故同一 target 下作者场景覆盖随包场景。 */
    private void loadAuthorDirectory() {
        Path root = authorDirectory();
        for (Path file : DraftStore.list(root)) {
            try {
                String json = java.nio.file.Files.readString(file, java.nio.charset.StandardCharsets.UTF_8);
                register(file.toString(), json);
            } catch (SceneFormatException error) {
                warn(file + ": " + error.getMessage());
            } catch (Exception error) {
                warn(file + ": " + error);
            }
        }
    }

    private void register(String origin, String json) {
        SceneParseResult result = SceneDataParser.parse(json);
        for (String warning : result.warnings()) {
            warn(origin + ": " + warning);
        }
        SceneData scene = result.scene();
        scenes.add(scene);
        if (scene.target() != null) {
            SceneData previous = byTarget.put(scene.target(), scene);
            if (previous != null) {
                LOGGER.info("[GTSNPonder] scene target '{}' overridden by {} (previous id={})",
                        scene.target(), origin, previous.id());
            }
        } else {
            LOGGER.warn("[GTSNPonder] scene {} has no 'target'; it cannot be opened by target", origin);
        }
    }

    private void warn(String message) {
        warnings.add(message);
        LOGGER.warn("[GTSNPonder] scene warning: {}", message);
    }
}
