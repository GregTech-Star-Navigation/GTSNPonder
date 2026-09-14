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
 * 覆盖分析（纯 Java，零 MC / GT）：判定「全部注册多方块都有可播场景」与「精选关键机器有手作讲解」
 * 两条工单验收契约，并把结果收敛为可断言的数字。
 *
 * <p>本类是覆盖验证的<b>唯一事实源</b>：headless 单测（夹具 + 真实随包场景文件）、GameTest（真实
 * GT 注册表枚举）与客户端自动测试（{@code GTSNPONDER_UI_AUTOTEST=coverage}）都调用 {@link #analyze}
 * 得到同一份 {@link Report}，故三者断言的是同一套数字。</p>
 *
 * <p><b>解析缝</b>：{@link #analyze(List, Function)} 只依赖一个 {@code target → Optional<SceneData>}
 * 解析函数——运行时是「手作优先、否则按需自动生成」（见 {@code PonderEntrypoints#resolveSceneForTarget}），
 * GameTest 是「随包手作场景优先、否则经结构适配器生成」。空即死链。</p>
 */
public final class SceneCoverage {

    /** 随包思索场景资源目录（classpath 根 + 该前缀 + 文件名）。 */
    public static final String PONDER_RESOURCE_DIRECTORY = "assets/gtsnponder/ponder/";

    /**
     * 一条精选（curated）关键机器及其随包手作 / 混合场景资源——「关键机器有手作讲解」的证据锚点。
     * 资源路径相对 classpath 根（等价于 {@code src/main/resources/}）。
     */
    public record CuratedScene(String target, String resourcePath) {

        public CuratedScene {
            Objects.requireNonNull(target, "target must not be null");
            Objects.requireNonNull(resourcePath, "resourcePath must not be null");
        }
    }

    /**
     * 精选关键机器：现有手作 / 混合场景的目标（焦炉 + 发电·能量网 / 物流管网 4 台）。
     * 每台都必须解析到 {@code source=hand|mixed} 的手作讲解，而非仅自动生成。
     */
    public static final List<CuratedScene> CURATED = List.of(
            new CuratedScene("gtceu:coke_oven", PONDER_RESOURCE_DIRECTORY + "coke_oven.json"),
            new CuratedScene("gtceu:large_combustion_engine",
                    PONDER_RESOURCE_DIRECTORY + "power_energy.json"),
            new CuratedScene("gtceu:active_transformer",
                    PONDER_RESOURCE_DIRECTORY + "power_transformer.json"),
            new CuratedScene("gtceu:steel_multiblock_tank",
                    PONDER_RESOURCE_DIRECTORY + "logistics_network.json"),
            new CuratedScene("gtceu:primitive_pump",
                    PONDER_RESOURCE_DIRECTORY + "logistics_pump.json"));

    private SceneCoverage() {
    }

    /** 精选目标的稳定 id 列表（有手作讲解的关键机器）。 */
    public static List<String> curatedTargets() {
        List<String> targets = new ArrayList<>(CURATED.size());
        for (CuratedScene scene : CURATED) {
            targets.add(scene.target());
        }
        return List.copyOf(targets);
    }

    /**
     * 覆盖报告（不可变值）：数字 + 死链 + 精选缺手作讲解的目标。
     *
     * @param registered              去重后的注册多方块数
     * @param resolved                解析到可播场景的注册多方块数（{@code registered - deadLinks}）
     * @param handAuthored            解析到 {@code source=hand|mixed} 手作讲解的注册多方块数
     * @param generated               解析到 {@code source=auto} 自动生成的注册多方块数
     * @param curatedHandAuthored     分辨率到 {@code source=hand|mixed} 的精选目标数
     * @param deadLinks               解析不出场景的注册多方块 id（须为空）
     * @param curatedWithoutHandNarration 缺手作讲解的精选目标 id（须为空）
     */
    public record Report(
            int registered,
            int resolved,
            int handAuthored,
            int generated,
            int curatedHandAuthored,
            List<String> deadLinks,
            List<String> curatedWithoutHandNarration) {

        public Report {
            deadLinks = List.copyOf(deadLinks);
            curatedWithoutHandNarration = List.copyOf(curatedWithoutHandNarration);
        }

        /** 全部注册多方块都可解析（无死链）。 */
        public boolean coversEveryMultiblock() {
            return deadLinks.isEmpty();
        }

        /** 精选关键机器都有手作讲解。 */
        public boolean curatedNarrationComplete() {
            return curatedWithoutHandNarration.isEmpty();
        }

        /** 覆盖验收总览：无死链且精选手作讲解齐全。 */
        public boolean isComplete() {
            return coversEveryMultiblock() && curatedNarrationComplete();
        }

        public int deadLinkCount() {
            return deadLinks.size();
        }

        public int curatedTotal() {
            return CURATED.size();
        }

        /** 单行摘要（日志 / 证据用）。 */
        public String summary() {
            return "registered=" + registered
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
     * 分析覆盖：对每个注册多方块调用 {@code resolve}（空即死链），并核对每条精选目标是否解析到
     * {@code source=hand|mixed}。纯函数，确定性，不生成 / 不缓存。
     *
     * @param registeredTargets 注册多方块目标 id（可空 / 含重复，内部去重）
     * @param resolve           目标 → 可播场景（手作优先、否则生成）；返回空视为死链
     */
    public static Report analyze(List<String> registeredTargets,
            Function<String, Optional<SceneData>> resolve) {
        Objects.requireNonNull(resolve, "resolve must not be null");

        Set<String> registered = new LinkedHashSet<>();
        if (registeredTargets != null) {
            for (String target : registeredTargets) {
                if (target != null && !target.isBlank()) {
                    registered.add(target);
                }
            }
        }

        List<String> deadLinks = new ArrayList<>();
        int handAuthored = 0;
        int generated = 0;
        for (String target : registered) {
            Optional<SceneData> scene = resolve.apply(target);
            if (scene == null || scene.isEmpty() || scene.get() == null) {
                deadLinks.add(target);
                continue;
            }
            if (isHandNarrated(scene.get().source())) {
                handAuthored++;
            } else {
                generated++;
            }
        }

        int curatedHandAuthored = 0;
        List<String> curatedWithoutHandNarration = new ArrayList<>();
        for (CuratedScene curated : CURATED) {
            Optional<SceneData> scene = resolve.apply(curated.target());
            if (scene != null && scene.isPresent() && isHandNarrated(scene.get().source())) {
                curatedHandAuthored++;
            } else {
                curatedWithoutHandNarration.add(curated.target());
            }
        }

        return new Report(registered.size(), registered.size() - deadLinks.size(), handAuthored,
                generated, curatedHandAuthored, deadLinks, curatedWithoutHandNarration);
    }

    /** 手作讲解：{@code hand}（纯手作）或 {@code mixed}（自动结构 + 手作旁白）。 */
    public static boolean isHandNarrated(Source source) {
        return source == Source.HAND || source == Source.MIXED;
    }
}
