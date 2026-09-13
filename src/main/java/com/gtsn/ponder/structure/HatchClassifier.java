package com.gtsn.ponder.structure;

import java.util.Locale;

/**
 * 仓口 / 总线的**名称兜底分类器**：当 GT 没有给出明确的角色特征（{@code PartAbility}）时，
 * 仅凭方块注册名里的 {@code hatch} / {@code bus} 词元把方块识别为 {@link StructureRole#OTHER_HATCH}。
 *
 * <p><b>为什么需要兜底</b>：GT 并非所有多方块部件都注册到 {@code PartAbility}——例如
 * {@code gtceu:coke_oven_hatch} 在 {@code GTMachines} 中注册时未声明任何能力，因此
 * {@code PartAbility.isApplicable} 判定为假。适配包先用能力特征做**精确**角色判定，
 * 未命中时再用本分类器做**保守**判定（只敢说「这是仓口 / 总线」，不猜方向）。</p>
 *
 * <p><b>语义与边界</b>：按 {@code _} 切分为词元，任一词元等于 {@code hatch} 或 {@code bus}
 * 即视为仓口 / 总线；大小写不敏感，忽略命名空间。词元边界避免把 {@code busbar} 误判为总线。
 * 局限：命名不规范的部件会被漏判；反之命名含 {@code hatch}/{@code bus} 词元的非部件方块会被误判——
 * 故适配包只在「方块确为 GT 机器方块（{@code MetaMachineBlock}）」时才应用本分类器。</p>
 *
 * <p>纯 Java、零 MC / GT 依赖，可在 headless 单测中断言。</p>
 */
public final class HatchClassifier {

    private HatchClassifier() {
    }

    /**
     * 依据方块注册名做保守判定。
     *
     * @param blockId 方块注册名（如 {@code gtceu:coke_oven_hatch}）；{@code null} / 空白视为普通过
     * @return 名称含 {@code hatch}/{@code bus} 词元时为 {@link StructureRole#OTHER_HATCH}，否则 {@link StructureRole#PLAIN}
     */
    public static StructureRole classifyByName(String blockId) {
        if (blockId == null) {
            return StructureRole.PLAIN;
        }
        String path = blockId.trim().toLowerCase(Locale.ROOT);
        int colon = path.indexOf(':');
        if (colon >= 0) {
            path = path.substring(colon + 1);
        }
        for (String token : path.split("_")) {
            if (token.equals("hatch") || token.equals("bus")) {
                return StructureRole.OTHER_HATCH;
            }
        }
        return StructureRole.PLAIN;
    }
}
