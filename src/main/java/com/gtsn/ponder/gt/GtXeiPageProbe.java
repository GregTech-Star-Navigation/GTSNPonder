package com.gtsn.ponder.gt;

import com.gtsn.ponder.client.MachinePonderButton;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.screen.BoMScreen;
import dev.emi.emi.screen.RecipeScreen;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * EMI 页面自动测试的<b>客户端探针</b>（工单 #17 缺陷 A）：以 EMI 官方 API 打开某物品的合成 / 用途配方页，
 * 并判定当前屏是否为 EMI 页面。仅由开发态自动测试（{@code GTSNPONDER_UI_AUTOTEST=xeipage}）使用；
 * 生产路径不调用。属唯一能引用 EMI 类型的适配包 {@code com.gtsn.ponder.gt}。
 *
 * <p>EMI 缺席时本类引用其类型会抛 {@link LinkageError}；调用方（自动测试）据返回 {@code false} 记录 FAIL，
 * 不静默。</p>
 */
public final class GtXeiPageProbe {

    private GtXeiPageProbe() {
    }

    /** 经 EMI 官方 API 打开 {@code itemId} 的配方页；物品未知 / EMI 不可用时返回 {@code false}。 */
    public static boolean openEmiRecipePage(String itemId) {
        try {
            ResourceLocation id = ResourceLocation.tryParse(itemId);
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                return false;
            }
            Item item = BuiltInRegistries.ITEM.get(id);
            EmiApi.displayRecipes(EmiStack.of(new ItemStack(item)));
            return true;
        } catch (RuntimeException | LinkageError failure) {
            return false;
        }
    }

    /** 当前屏是否为 EMI 普通配方页（合成 / 用途页）或配方树页。 */
    public static boolean isEmiScreen(Screen screen) {
        return screen instanceof RecipeScreen || screen instanceof BoMScreen;
    }

    /**
     * EMI 配方面板（工单 #20 的页面入口锚定参照）的屏幕矩形——直接取 {@code RecipeScreen.getBounds()}，
     * 即配方页的整体矩形（含上方页签条；{@code workstationLocation == LEFT} 时另含左侧工作台列）。
     *
     * <p>自动测试据此断言「入口落在页面左侧页面按钮列」而非右上角：本方法只暴露 EMI 的<b>原始布局事实</b>，
     * 不含本 mod 的入口几何（避免自证）。非 EMI 配方页返回空。</p>
     */
    public static Optional<MachinePonderButton.Box> recipePageBounds(Screen screen) {
        if (!(screen instanceof RecipeScreen recipe)) {
            return Optional.empty();
        }
        try {
            dev.emi.emi.api.widget.Bounds page = recipe.getBounds();
            if (page == null || page.width() <= 0 || page.height() <= 0) {
                return Optional.empty();
            }
            return Optional.of(MachinePonderButton.Box.of(
                    page.x(), page.y(), page.width(), page.height()));
        } catch (RuntimeException | LinkageError failure) {
            return Optional.empty();
        }
    }
}
