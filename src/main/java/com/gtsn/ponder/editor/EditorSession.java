package com.gtsn.ponder.editor;

import com.gtsn.ponder.engine.model.SceneData;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * 编辑器会话（纯逻辑）：把「录制 → 保存 → 热重载 → 重放」这条主回路收敛为可 headless 单测的对象。
 *
 * <p>持有草稿与记录器，负责把草稿写进运行时作者目录（{@link DraftStore}）；客户端编辑器屏幕只驱动
 * 本会话，客户端自动测试复用同一回路并额外断言「场景库 reload 后能重放编辑后的旁白」。</p>
 *
 * <p>纯 Java、零 MC 依赖。</p>
 */
public final class EditorSession {

    private final SceneDraft draft;
    private final SceneRecorder recorder;
    private final Path authorRoot;

    private EditorSession(SceneDraft draft, Path authorRoot) {
        this.draft = draft;
        this.recorder = new SceneRecorder(draft);
        this.authorRoot = authorRoot;
    }

    /** 从已有场景（自动生成基线 / 已有手作场景）建立会话。 */
    public static EditorSession startingFrom(SceneData seed, Path authorRoot) {
        Objects.requireNonNull(authorRoot, "authorRoot must not be null");
        return new EditorSession(SceneDraft.from(seed), authorRoot);
    }

    /** 新建空会话（无种子），绑定目标。 */
    public static EditorSession blank(String target, Path authorRoot) {
        Objects.requireNonNull(authorRoot, "authorRoot must not be null");
        return new EditorSession(SceneDraft.create(target), authorRoot);
    }

    public SceneDraft draft() {
        return draft;
    }

    public SceneRecorder recorder() {
        return recorder;
    }

    public Path authorRoot() {
        return authorRoot;
    }

    /** 当前草稿的不可变 v1 快照（保存 / 导出 / 重放用）。 */
    public SceneData snapshot() {
        return draft.toSceneData();
    }

    /** 覆盖进草稿（如切换编辑目标 / 重新导出自动生成基线）。 */
    public void load(SceneData scene) {
        draft.copyFrom(scene);
    }

    /** 保存草稿到作者目录；返回写入文件路径。 */
    public Path save() throws IOException {
        return DraftStore.write(authorRoot, snapshot(), fileStem());
    }

    /** 当前草稿的稳健文件名词干（默认取 target，回退 id，再回退 {@code scene}）。 */
    public String fileStem() {
        return fileStemFor(snapshot());
    }

    /** 给定场景的稳健文件名词干。 */
    public static String fileStemFor(SceneData scene) {
        Objects.requireNonNull(scene, "scene must not be null");
        if (scene.target() != null && !scene.target().isBlank()) {
            return DraftStore.sanitizeStem(scene.target());
        }
        if (scene.id() != null && !scene.id().isBlank()) {
            return DraftStore.sanitizeStem(scene.id());
        }
        return "scene";
    }
}
