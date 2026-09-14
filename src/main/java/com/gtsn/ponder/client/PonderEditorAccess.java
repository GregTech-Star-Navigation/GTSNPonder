package com.gtsn.ponder.client;

import com.gtsn.ponder.PonderConfig;
import com.gtsn.ponder.editor.EditorGate;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * 运行时解析编辑器门控（ADR-0003 / 约束 §3「编辑器为开发 / 作者门控，正式玩家不可见」）。
 *
 * <p>三条输入：① 开发态 {@code FMLEnvironment.production=false}（{@code runClient} / 客户端自动测试）
 * 恒可见；② 配置 {@code editor.editorEnabled=true} 显式选择加入；③ 系统属性
 * {@code -Dgtsnponder.editor=true} 开发覆盖。纯判定逻辑见 {@link EditorGate}。</p>
 *
 * <p>客户端专用类（读客户端配置）。</p>
 */
public final class PonderEditorAccess {

    /** 开发者覆盖开关（系统属性）。 */
    public static final String EDITOR_SYSTEM_PROPERTY = "gtsnponder.editor";

    private PonderEditorAccess() {
    }

    /** 编辑器当前是否可见 / 可用。 */
    public static boolean isEditorEnabled() {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return false;
        }
        boolean configOptIn = PonderConfig.EDITOR_ENABLED.get();
        boolean devOverride = Boolean.getBoolean(EDITOR_SYSTEM_PROPERTY);
        return EditorGate.isEnabled(FMLEnvironment.production, configOptIn, devOverride);
    }
}
