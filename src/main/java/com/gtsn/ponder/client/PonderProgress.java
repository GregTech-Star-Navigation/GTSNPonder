package com.gtsn.ponder.client;

import com.gtsn.ponder.catalog.ProgressStore;
import com.gtsn.ponder.catalog.WatchedProgress;
import com.gtsn.ponder.engine.model.SceneData;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 客户端观看进度（单例）：把纯逻辑 {@link WatchedProgress} 绑定到游戏目录并持久化。
 *
 * <p><b>存储位置</b>：{@code <gameDir>/gtsnponder-progress.json}（{@link ProgressStore#FILE_NAME}），
 * 经 {@link ProgressStore} 读写规范化 JSON。文件缺失 / 非法时退化为空进度，绝不崩溃。</p>
 *
 * <p>标记时机：场景在播放屏打开即视为「已看」（{@code ScenePlayerScreen} 构造时标记，幂等）；
 * 图鉴目录读取本类快照渲染已看 / 未看标记。客户端专用类。</p>
 */
public final class PonderProgress {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final PonderProgress INSTANCE = new PonderProgress();

    private WatchedProgress progress = WatchedProgress.empty();
    private boolean loaded;

    private PonderProgress() {
    }

    public static PonderProgress get() {
        return INSTANCE;
    }

    /** 进度文件（{@code <gameDir>/gtsnponder-progress.json}）。 */
    public Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(ProgressStore.FILE_NAME);
    }

    public synchronized WatchedProgress snapshot() {
        ensureLoaded();
        return progress;
    }

    public synchronized boolean isWatched(SceneData scene) {
        ensureLoaded();
        return progress.isWatched(WatchedProgress.keyOf(scene));
    }

    public synchronized int watchedCount() {
        ensureLoaded();
        return progress.size();
    }

    /** 标记场景已看并持久化（幂等；写盘失败只告警，不影响游玩）。 */
    public synchronized void markWatched(SceneData scene) {
        ensureLoaded();
        String key = WatchedProgress.keyOf(scene);
        if (key == null || progress.isWatched(key)) {
            return;
        }
        progress = progress.with(key);
        persist();
    }

    /** 开发 / 自动测试：清空进度并删除存档文件（保证断言从已知基线开始）。 */
    public synchronized void reset() {
        progress = WatchedProgress.empty();
        loaded = true;
        try {
            Files.deleteIfExists(file());
        } catch (IOException failure) {
            LOGGER.warn("[GTSNPonder] could not delete the ponder progress file", failure);
        }
    }

    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        progress = ProgressStore.load(file());
        LOGGER.info("[GTSNPonder] ponder progress loaded: {} watched scene(s) from {}",
                progress.size(), file());
    }

    private void persist() {
        try {
            ProgressStore.save(file(), progress);
        } catch (IOException failure) {
            LOGGER.warn("[GTSNPonder] failed to persist ponder progress", failure);
        }
    }
}
