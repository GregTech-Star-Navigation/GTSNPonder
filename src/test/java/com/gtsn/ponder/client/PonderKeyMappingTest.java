package com.gtsn.ponder.client;

import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 思索快捷键默认绑定的可失败断言（工单 #17 缺陷 C）：{@code PONDER_KEY} 必须有<b>默认绑定</b>
 * （未绑定则 KeyMapping 不产生点击，物品悬停入口开箱不可用），且归属本 mod 的控制类别（故可在
 * 「控制」里改绑）。
 */
class PonderKeyMappingTest {

    @Test
    void ponderKeyHasADefaultBinding() {
        assertFalse(PonderEntrypoints.PONDER_KEY.isUnbound(),
                "PONDER_KEY must be bound by default or the item-hover entry never fires");
        assertEquals(GLFW.GLFW_KEY_G, PonderEntrypoints.PONDER_KEY.getKey().getValue(),
                "the recommended default binding is G");
        assertEquals("key.categories.gtsnponder", PonderEntrypoints.PONDER_KEY.getCategory(),
                "the key must show up under the GTSN Ponder controls category (rebindable)");
    }

    @Test
    void catalogKeyStillHasADefaultBinding() {
        assertFalse(PonderEntrypoints.CATALOG_KEY.isUnbound(),
                "CATALOG_KEY must stay bound by default");
        assertEquals(GLFW.GLFW_KEY_O, PonderEntrypoints.CATALOG_KEY.getKey().getValue());
    }
}
