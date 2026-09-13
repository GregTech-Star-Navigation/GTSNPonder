package com.gtsn.ponder.engine.director;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内存假世界：记录导演核心应用的全部效果，并提供可比较的不可变快照。
 *
 * <p>测试证据：世界状态即「外部可观察行为」，据此断言时序 / seek / rewind / 重放幂等。</p>
 */
public final class FakeSceneWorld implements SceneWorld {

    private final Map<String, Boolean> sections = new LinkedHashMap<>();
    private final Map<String, String> blocks = new LinkedHashMap<>();
    private final Map<String, Boolean> highlights = new LinkedHashMap<>();
    private final Map<String, Boolean> outlines = new LinkedHashMap<>();
    private String narration;
    private CameraState camera;
    private final Map<String, String> modules = new LinkedHashMap<>();
    private final List<String> formedPulses = new ArrayList<>();
    private final List<String> particles = new ArrayList<>();

    @Override
    public void setSectionVisible(String sectionId, boolean visible) {
        sections.put(sectionId, visible);
    }

    @Override
    public void replaceBlocks(String elementId, String blockId) {
        blocks.put(elementId, blockId);
    }

    @Override
    public void setHighlight(String targetId, boolean active) {
        highlights.put(targetId, active);
    }

    @Override
    public void setOutline(String targetId, boolean active) {
        outlines.put(targetId, active);
    }

    @Override
    public void setNarration(String narrationKey) {
        this.narration = narrationKey;
    }

    @Override
    public void setCamera(CameraState camera) {
        this.camera = camera;
    }

    @Override
    public void installModule(String slotId, String moduleId) {
        modules.put(slotId, moduleId);
    }

    @Override
    public void pulseFormed(String controllerId) {
        formedPulses.add(controllerId);
    }

    @Override
    public void emitParticles(String targetId) {
        particles.add(targetId);
    }

    @Override
    public SceneWorldState snapshot() {
        return new FakeState(sections, blocks, highlights, outlines, narration, camera,
                modules, formedPulses, particles);
    }

    @Override
    public void restore(SceneWorldState state) {
        FakeState snap = (FakeState) state;
        replace(sections, snap.sections());
        replace(blocks, snap.blocks());
        replace(highlights, snap.highlights());
        replace(outlines, snap.outlines());
        this.narration = snap.narration();
        this.camera = snap.camera();
        replace(modules, snap.modules());
        formedPulses.clear();
        formedPulses.addAll(snap.formedPulses());
        particles.clear();
        particles.addAll(snap.particles());
    }

    /** 当前状态的可比较快照。 */
    public FakeState state() {
        return (FakeState) snapshot();
    }

    public String narration() {
        return narration;
    }

    public CameraState camera() {
        return camera;
    }

    private static <K, V> void replace(Map<K, V> target, Map<K, V> source) {
        target.clear();
        target.putAll(source);
    }

    /** 不可变且可比较（记录组件深比较）的世界快照。 */
    public record FakeState(
            Map<String, Boolean> sections,
            Map<String, String> blocks,
            Map<String, Boolean> highlights,
            Map<String, Boolean> outlines,
            String narration,
            CameraState camera,
            Map<String, String> modules,
            List<String> formedPulses,
            List<String> particles) implements SceneWorldState {

        public FakeState {
            sections = Map.copyOf(sections);
            blocks = Map.copyOf(blocks);
            highlights = Map.copyOf(highlights);
            outlines = Map.copyOf(outlines);
            modules = Map.copyOf(modules);
            formedPulses = List.copyOf(formedPulses);
            particles = List.copyOf(particles);
        }
    }
}
