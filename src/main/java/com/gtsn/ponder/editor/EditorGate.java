package com.gtsn.ponder.editor;

/**
 * 编辑器门控谓词（纯函数，无 Forge / Minecraft 依赖，可无头单测；ADR-0003 / 约束 §3「编辑器为开发 /
 * 作者门控，正式玩家不可见」）。
 *
 * <p>门控规则：开发运行（{@code production=false}，含 {@code runClient} 与客户端自动测试）恒可见；
 * 生产安装默认隐藏，仅当配置显式选择加入（{@code configOptIn}）或开发覆盖（{@code devOverride}，
 * 如系统属性）时可见。</p>
 *
 * @see com.gtsn.ponder.client.PonderEditorAccess 运行时解析（读取 {@code FMLEnvironment.production}、
 *      配置与系统属性）
 */
public final class EditorGate {

    private EditorGate() {
    }

    /**
     * 是否启用游戏内编辑器。
     *
     * @param production  是否生产运行（{@code FMLEnvironment.production}）
     * @param configOptIn 配置是否显式选择加入（{@code editorEnabled}）
     * @param devOverride 开发覆盖开关（如系统属性 {@code gtsnponder.editor}）
     * @return 开发态恒 {@code true}；生产态仅在显式选择加入或开发覆盖时为 {@code true}
     */
    public static boolean isEnabled(boolean production, boolean configOptIn, boolean devOverride) {
        return !production || configOptIn || devOverride;
    }
}
