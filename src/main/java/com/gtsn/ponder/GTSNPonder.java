package com.gtsn.ponder;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * GTSNPonder 入口。
 *
 * <p>脚手架阶段仅承载 ModID 与日志句柄；导演核心 / 世界桥 / 视口 / Presenter 等
 * 初始化由后续工单接入。本类不得出现任何 {@code com.gregtechceu} 类型（GT 访问收敛于
 * {@code com.gtsn.ponder.gt}，见 ADR-0005 与 import 隔离测试）。</p>
 */
@Mod(GTSNPonder.MODID)
public class GTSNPonder {
    public static final String MODID = "gtsnponder";
    private static final Logger LOGGER = LogUtils.getLogger();

    public GTSNPonder() {
        LOGGER.info("[GTSNPonder] Loading GTSNPonder");
    }
}
