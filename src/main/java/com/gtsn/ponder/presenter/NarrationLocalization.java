package com.gtsn.ponder.presenter;

import com.gtsn.ponder.generate.GeneratedKeys;
import com.gtsn.ponder.structure.StructureRole;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * 旁白模板参数的本地化解析（纯 Java，零 MC，工单 #16 缺陷 A1/A2）。
 *
 * <p>自动生成器把机器标识与仓口 / 总线角色名作为<b>字面量</b>写入 {@code narrationArgs}（冻结场景格式
 * 不变）。若直接把字面量塞进 {@code Component.translatable(key, args)}，旁白就会露出原始注册 id
 * （{@code gtceu:coke_oven}）与原始枚举名（{@code ENERGY_INPUT}）。本解析器在渲染前把每个参数切成
 * 「可翻译键 / 字面量」片段：</p>
 *
 * <ol>
 *   <li>注册 id 形态（含 {@code :}）→ 机器标题键 {@link GeneratedKeys#machineTitleKey(String)}；
 *       键不存在时回退原始 id（「没有标题键的机器照常工作」）；</li>
 *   <li>{@link StructureRole} 枚举名 → 角色语言键 {@link GeneratedKeys#roleKey(String)}；键不存在时
 *       回退枚举名；</li>
 *   <li>其余（尺寸 {@code 3x3x3}、坐标、{@code —} 占位等）原样保留。</li>
 * </ol>
 *
 * <p>仓口 / 总线角色列表以 {@code ", "} 连接，故按该分隔符逐项解析后原样保留分隔符。是否为「已知键」
 * 由调用方注入（客户端为 {@code I18n}），使本逻辑可在 headless 单测中确定性覆盖。</p>
 *
 * <p>纯 Java、零 MC 依赖。</p>
 */
public final class NarrationLocalization {

    /** 角色列表分隔符（与 {@code SceneGenerator.rolesText} 一致）。 */
    public static final String LIST_SEPARATOR = ", ";

    private NarrationLocalization() {
    }

    /** 一个已解析的旁白参数片段：要么是一个待翻译的语言键，要么是字面量文本。 */
    public record Part(boolean translatable, String value) {

        public Part {
            Objects.requireNonNull(value, "value");
        }

        public static Part key(String key) {
            return new Part(true, key);
        }

        public static Part literal(String text) {
            return new Part(false, text);
        }
    }

    /** 解析单个 {@code %s} 模板参数，返回其片段列表（永不为空）。 */
    public static List<Part> resolveArg(String arg, Predicate<String> keyExists) {
        Objects.requireNonNull(keyExists, "keyExists");
        if (arg == null || arg.isEmpty()) {
            return List.of(Part.literal(""));
        }
        String[] tokens = arg.split(LIST_SEPARATOR, -1);
        List<Part> parts = new ArrayList<>();
        for (int index = 0; index < tokens.length; index++) {
            if (index > 0) {
                parts.add(Part.literal(LIST_SEPARATOR));
            }
            parts.add(resolveToken(tokens[index], keyExists));
        }
        return List.copyOf(parts);
    }

    /** 逐个 {@code %s} 模板参数解析（保持「一个参数 = 一个替换项」的对应关系）。 */
    public static List<List<Part>> resolveArgs(List<String> args, Predicate<String> keyExists) {
        Objects.requireNonNull(args, "args");
        Objects.requireNonNull(keyExists, "keyExists");
        List<List<Part>> resolved = new ArrayList<>(args.size());
        for (String arg : args) {
            resolved.add(resolveArg(arg, keyExists));
        }
        return List.copyOf(resolved);
    }

    private static Part resolveToken(String token, Predicate<String> keyExists) {
        StructureRole role = StructureRole.fromName(token).orElse(null);
        if (role != null) {
            String roleKey = GeneratedKeys.roleKey(role.name());
            return keyExists.test(roleKey) ? Part.key(roleKey) : Part.literal(token);
        }
        if (token.indexOf(':') >= 0) {
            String titleKey = GeneratedKeys.machineTitleKey(token);
            return keyExists.test(titleKey) ? Part.key(titleKey) : Part.literal(token);
        }
        return Part.literal(token);
    }
}
