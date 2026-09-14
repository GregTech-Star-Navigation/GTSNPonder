package com.gtsn.ponder.engine.director;

import com.gtsn.ponder.engine.model.SceneParams;
import com.gtsn.ponder.engine.model.SceneStep;
import com.gtsn.ponder.engine.model.StepType;

import java.util.EnumMap;
import java.util.Map;

/**
 * {@link StepBehavior} 的默认注册表（封闭 {@link StepType} → 行为）。
 *
 * <p>这是「反 DSL」的扩展点：数据格式不能表达逻辑，新增步骤语义只能通过在此注册 Java 实现。
 * 未注册 / 未知类型返回空操作（解析器已在数据层跳过未知类型）。</p>
 *
 * <p>所有效果均为幂等状态设置，因此可安全重放（rewind / seek 的确定性基础）。</p>
 *
 * <p>纯 Java、零 MC 依赖（导演核心纪律）。</p>
 */
public final class StepBehaviors {

    private static final StepBehavior NOOP = (step, world) -> {
        // no-op
    };

    private static final Map<StepType, StepBehavior> REGISTRY = createRegistry();

    private StepBehaviors() {
    }

    /** 返回某步骤类型的行为；未注册类型返回空操作。 */
    public static StepBehavior forType(StepType type) {
        if (type == null) {
            return NOOP;
        }
        return REGISTRY.getOrDefault(type, NOOP);
    }

    /** 该步骤是否阻塞时间轴（默认：{@code duration > 0}）。 */
    public static boolean isBlocking(SceneStep step) {
        return forType(step.type()).isBlocking(step);
    }

    private static Map<StepType, StepBehavior> createRegistry() {
        Map<StepType, StepBehavior> registry = new EnumMap<>(StepType.class);
        registry.put(StepType.SHOW_SECTION, (step, world) -> {
            for (String target : step.targets()) {
                world.setSectionVisible(target, true);
            }
        });
        registry.put(StepType.HIDE_SECTION, (step, world) -> {
            for (String target : step.targets()) {
                world.setSectionVisible(target, false);
            }
        });
        registry.put(StepType.REPLACE_BLOCKS, (step, world) -> {
            String block = SceneParams.string(step.params(), "block", null);
            for (String target : step.targets()) {
                world.replaceBlocks(target, block);
            }
        });
        registry.put(StepType.HIGHLIGHT, (step, world) -> {
            boolean visible = SceneParams.bool(step.params(), "visible", true);
            for (String target : step.targets()) {
                world.setHighlight(target, visible);
            }
        });
        registry.put(StepType.OUTLINE, (step, world) -> {
            boolean visible = SceneParams.bool(step.params(), "visible", true);
            for (String target : step.targets()) {
                world.setOutline(target, visible);
            }
        });
        registry.put(StepType.TEXT, (step, world) -> {
            String narration = step.narration() != null
                    ? step.narration()
                    : SceneParams.string(step.params(), "key", null);
            world.setNarration(narration);
        });
        registry.put(StepType.CAMERA, (step, world) -> world.setCamera(new CameraState(
                step.targets().isEmpty() ? null : step.targets().get(0),
                SceneParams.number(step.params(), "yaw", 0.0d),
                SceneParams.number(step.params(), "pitch", 0.0d),
                SceneParams.number(step.params(), "distance", 0.0d),
                // 取景自适应：fit=true 时视口按包围盒 + 纵横比反算距离，margin 为目标填充比例。
                SceneParams.bool(step.params(), "fit", false)
                        ? SceneParams.number(step.params(), "margin", 0.0d) : 0.0d)));
        registry.put(StepType.IDLE, NOOP);
        registry.put(StepType.INSTALL_MODULE, (step, world) -> {
            String module = SceneParams.string(step.params(), "module", null);
            for (String target : step.targets()) {
                world.installModule(target, module);
            }
        });
        registry.put(StepType.FORMED_PULSE, (step, world) -> {
            for (String target : step.targets()) {
                world.pulseFormed(target);
            }
        });
        registry.put(StepType.PARTICLES, (step, world) -> {
            for (String target : step.targets()) {
                world.emitParticles(target);
            }
        });
        return Map.copyOf(registry);
    }
}
