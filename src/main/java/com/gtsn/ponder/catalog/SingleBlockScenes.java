package com.gtsn.ponder.catalog;

import com.gtsn.ponder.engine.model.SceneData;
import com.gtsn.ponder.engine.model.Source;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * 单方块机器「使用场景」的覆盖契约（纯 Java，零 MC / GT）：工单 #15 的可断言数字与精选清单。
 *
 * <p>多方块的覆盖契约是 {@link SceneCoverage}（71 台全可播 + 5 台精选手作）。单方块机器数量庞大、
 * 形态各异，本工单不要求全部，而是定义一组<b>代表性机器</b>（蒸汽时代 + 基础电力时代，≥
 * {@link #MIN_REPRESENTATIVE} 台），要求：
 * <ul>
 *   <li>每台都能经解析缝解析到可播的使用场景（{@code deadLinks} 为空）；</li>
 *   <li>至少 {@link #MIN_HAND_AUTHORED} 台有随包<b>手作</b>讲解（{@link #CURATED}），其余由
 *       {@code SingleBlockUsageGenerator} 按需生成；</li>
 * </ul>
 * 与 {@link SceneCoverage} 一样，本类是 headless 单测、GameTest 与客户端自动测试共用的<b>单一事实源</b>：
 * 三者调用 {@link #analyze(Function)} 得到同一份 {@link Report}，断言同一套数字。</p>
 *
 * <p>解析缝只依赖 {@code target → Optional<SceneData>}（运行时 = 手作优先、否则按需生成），空即死链。</p>
 */
public final class SingleBlockScenes {

    /** 一条精选手作单方块机器及其随包使用场景资源（相对 classpath 根）。 */
    public record CuratedScene(String target, String resourcePath) {

        public CuratedScene {
            Objects.requireNonNull(target, "target must not be null");
            Objects.requireNonNull(resourcePath, "resourcePath must not be null");
        }
    }

    /**
     * 代表性单方块机器（蒸汽时代 + 基础电力时代）：目录条目与覆盖断言的目标集合。
     * id 必须是真实注册的 GT 单方块机器定义（由 GameTest 在真实注册表上校验）。
     */
    public static final List<String> REPRESENTATIVE = List.of(
            "gtceu:lp_steam_furnace",
            "gtceu:lp_steam_macerator",
            "gtceu:lp_steam_alloy_smelter",
            "gtceu:lp_steam_solid_boiler",
            "gtceu:lv_macerator",
            "gtceu:lv_electric_furnace",
            "gtceu:lv_centrifuge",
            "gtceu:lv_electrolyzer");

    /** 手作讲解的单方块机器（≥ {@link #MIN_HAND_AUTHORED} 台，随包 JSON 见 {@code src/main/resources/}）。 */
    public static final List<CuratedScene> CURATED = List.of(
            new CuratedScene("gtceu:lp_steam_furnace",
                    SceneCoverage.PONDER_RESOURCE_DIRECTORY + "steam_furnace.json"),
            new CuratedScene("gtceu:lv_macerator",
                    SceneCoverage.PONDER_RESOURCE_DIRECTORY + "lv_macerator.json"));

    /** 手作蒸汽熔炉场景的旁白键（JSON 作者文案；datagen 产出中英）。 */
    public static final String HAND_STEAM_FURNACE_INTRO = "ponder.gtsnponder.usage.lp_steam_furnace.intro";
    public static final String HAND_STEAM_FURNACE_USAGE = "ponder.gtsnponder.usage.lp_steam_furnace.usage";
    /** 手作基础粉碎机场景的旁白键。 */
    public static final String HAND_LV_MACERATOR_INTRO = "ponder.gtsnponder.usage.lv_macerator.intro";
    public static final String HAND_LV_MACERATOR_USAGE = "ponder.gtsnponder.usage.lv_macerator.usage";

    /** 全部手作单方块场景旁白键（datagen / 测试守卫用）。 */
    public static final List<String> HAND_NARRATION_KEYS = List.of(
            HAND_STEAM_FURNACE_INTRO, HAND_STEAM_FURNACE_USAGE,
            HAND_LV_MACERATOR_INTRO, HAND_LV_MACERATOR_USAGE);

    /** 代表性机器下限（工单 #15 验收：≥ 6 台）。 */
    public static final int MIN_REPRESENTATIVE = 6;
    /** 手作讲解下限（工单 #15 验收：≥ 2 台）。 */
    public static final int MIN_HAND_AUTHORED = 2;

    private SingleBlockScenes() {
    }

    /** 精选目标的稳定 id 列表（有手作讲解的单方块机器）。 */
    public static List<String> curatedTargets() {
        List<String> targets = new ArrayList<>(CURATED.size());
        for (CuratedScene scene : CURATED) {
            targets.add(scene.target());
        }
        return List.copyOf(targets);
    }

    /** 代表性机器 id 列表（目录 / 覆盖断言的稳定顺序）。 */
    public static List<String> representativeTargets() {
        return REPRESENTATIVE;
    }

    /**
     * 覆盖报告（不可变值）：代表性机器数 / 可解析数 / 手作数 / 生成数 / 死链，以及精选手作讲解情况。
     *
     * @param representative             代表性机器集合大小（固定 = {@link #REPRESENTATIVE} 大小）
     * @param resolved                   解析到可播使用场景的代表性机器数
     * @param handAuthored               解析到 {@code source=hand|mixed} 手作讲解的代表性机器数
     * @param generated                  解析到 {@code source=auto} 自动生成的代表性机器数
     * @param deadLinks                  解析不出场景的代表性机器 id（须为空）
     * @param curatedHandAuthored        解析到 {@code source=hand|mixed} 的精选目标数
     * @param curatedWithoutHandNarration 缺手作讲解的精选目标 id（须为空）
     */
    public record Report(
            int representative,
            int resolved,
            int handAuthored,
            int generated,
            List<String> deadLinks,
            int curatedHandAuthored,
            List<String> curatedWithoutHandNarration) {

        public Report {
            deadLinks = List.copyOf(deadLinks);
            curatedWithoutHandNarration = List.copyOf(curatedWithoutHandNarration);
        }

        /** 全部代表性机器都可解析（无死链）。 */
        public boolean coversEveryRepresentative() {
            return deadLinks.isEmpty();
        }

        /** 精选机器都有手作讲解。 */
        public boolean curatedNarrationComplete() {
            return curatedWithoutHandNarration.isEmpty();
        }

        /** 覆盖验收总览：代表性数量达标、无死链、精选手作齐全、手作数量达标。 */
        public boolean isComplete() {
            return representative >= MIN_REPRESENTATIVE
                    && handAuthored >= MIN_HAND_AUTHORED
                    && coversEveryRepresentative()
                    && curatedNarrationComplete();
        }

        public int deadLinkCount() {
            return deadLinks.size();
        }

        public int curatedTotal() {
            return CURATED.size();
        }

        /** 单行摘要（日志 / 证据用）。 */
        public String summary() {
            return "representative=" + representative
                    + " resolved=" + resolved
                    + " handAuthored=" + handAuthored
                    + " generated=" + generated
                    + " curated=" + curatedHandAuthored + "/" + curatedTotal()
                    + " deadLinks=" + deadLinkCount()
                    + (deadLinks.isEmpty() ? "" : " " + deadLinks)
                    + (curatedWithoutHandNarration.isEmpty() ? ""
                            : " curatedWithoutHandNarration=" + curatedWithoutHandNarration);
        }
    }

    /**
     * 分析覆盖：对每台代表性机器调用 {@code resolve}（空即死链），并核对每条精选目标是否解析到
     * {@code source=hand|mixed}。纯函数，确定性，不生成 / 不缓存。
     *
     * @param resolve 目标 → 可播使用场景（手作优先、否则生成）；返回空视为死链
     */
    public static Report analyze(Function<String, Optional<SceneData>> resolve) {
        Objects.requireNonNull(resolve, "resolve must not be null");

        Set<String> representatives = new LinkedHashSet<>(REPRESENTATIVE);
        List<String> deadLinks = new ArrayList<>();
        int handAuthored = 0;
        int generated = 0;
        for (String target : representatives) {
            Optional<SceneData> scene = resolve.apply(target);
            if (scene == null || scene.isEmpty() || scene.get() == null) {
                deadLinks.add(target);
                continue;
            }
            if (SceneCoverage.isHandNarrated(scene.get().source())) {
                handAuthored++;
            } else {
                generated++;
            }
        }

        int curatedHandAuthored = 0;
        List<String> curatedWithoutHandNarration = new ArrayList<>();
        for (CuratedScene curated : CURATED) {
            Optional<SceneData> scene = resolve.apply(curated.target());
            if (scene != null && scene.isPresent() && SceneCoverage.isHandNarrated(scene.get().source())) {
                curatedHandAuthored++;
            } else {
                curatedWithoutHandNarration.add(curated.target());
            }
        }

        return new Report(representatives.size(), representatives.size() - deadLinks.size(), handAuthored,
                generated, deadLinks, curatedHandAuthored, curatedWithoutHandNarration);
    }

    /** 手作讲解：{@code hand} 或 {@code mixed}（与 {@link SceneCoverage#isHandNarrated(Source)} 同义）。 */
    public static boolean isHandNarrated(Source source) {
        return SceneCoverage.isHandNarrated(source);
    }
}
