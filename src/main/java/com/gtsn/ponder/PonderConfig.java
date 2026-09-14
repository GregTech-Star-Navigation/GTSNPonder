package com.gtsn.ponder;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/**
 * GTSNPonder 的 Forge 配置（客户端类型）：
 *
 * <ul>
 *   <li>{@code editor.editorEnabled}（默认 {@code false}）——游戏内编辑器的作者 / 开发门控。
 *       正式玩家默认看不到编辑器；作者显式打开后可用（见 {@code EditorGate} 与
 *       {@code PonderEditorAccess}）。</li>
 * </ul>
 *
 * <p>配置类不含任何 Minecraft 客户端类型，可在 common 侧注册（CLIENT 类型仅在客户端加载）。</p>
 */
public final class PonderConfig {

    /** 编辑器门控配置项（{@code editor.editorEnabled}）。 */
    public static final ForgeConfigSpec.BooleanValue EDITOR_ENABLED;

    public static final ForgeConfigSpec SPEC;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("GTSNPonder client settings").push("editor");
        EDITOR_ENABLED = builder
                .comment("Enable the in-game scene editor (authors / developers only; hidden from normal players).",
                        "门控游戏内可视化编辑器：仅作者 / 开发可见，普通玩家默认隐藏。")
                .define("editorEnabled", false);
        builder.pop();
        SPEC = builder.build();
    }

    private PonderConfig() {
    }

    /** 注册客户端配置（在模组构造期调用）。 */
    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, SPEC);
    }
}
