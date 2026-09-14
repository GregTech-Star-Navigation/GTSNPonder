package com.gtsn.ponder.client;

import com.gtsn.lib.ui.input.InputEvent;
import com.gtsn.lib.ui.input.Keys;
import com.gtsn.lib.ui.layout.Insets;
import com.gtsn.lib.ui.layout.Rect;
import com.gtsn.lib.ui.layout.Size;
import com.gtsn.lib.ui.layout.Sizing;
import com.gtsn.lib.ui.render.RenderContext;
import com.gtsn.lib.ui.theme.Theme;
import com.gtsn.lib.ui.theme.ThemeColorRole;
import com.gtsn.lib.ui.widget.AbstractWidget;
import com.gtsn.lib.ui.widget.TextMetrics;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * 编辑器属性表单用的最小可编辑文本框（GTSN UI 控件，见 ADR-0004「全部二维界面统一用 GTSN UI」）。
 *
 * <p>复用 GTSNLib 的控件契约（{@link AbstractWidget}）与输入路由：点击聚焦、聚焦时
 * {@link Keys#BACKSPACE} 退格、{@link InputEvent.CharTyped 字符输入}追加，每次编辑回调
 * {@code onChange}（编辑器据实回写草稿中的字面量字段——反 DSL：只存字面量字符串）。</p>
 *
 * <p>仅依赖 GTSN UI（不依赖 Minecraft 类型），可随屏幕在客户端加载。</p>
 */
public final class EditorTextField extends AbstractWidget {

    private final TextMetrics metrics;
    private final Consumer<String> onChange;
    private final int maxLength;

    private String value;
    private boolean focused;

    public EditorTextField(String value, TextMetrics metrics, int maxLength, Consumer<String> onChange) {
        this.value = value == null ? "" : value;
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
        this.maxLength = Math.max(1, maxLength);
        this.onChange = Objects.requireNonNull(onChange, "onChange must not be null");
        node().contentMeasurer((widthSpec, heightSpec) -> Size.of(
                widthSpec.resolve(Math.max(40, this.metrics.width(this.value))),
                heightSpec.resolve(this.metrics.lineHeight() + 4)));
    }

    public String value() {
        return value;
    }

    /** 静默设置（不触发回调）：外部状态 → 控件。 */
    public EditorTextField value(String value) {
        this.value = value == null ? "" : value;
        return this;
    }

    public EditorTextField fillWidth() {
        node().params().fillWidth();
        return this;
    }

    public EditorTextField size(Sizing width, Sizing height) {
        node().params().size(width, height);
        return this;
    }

    public EditorTextField margin(Insets margin) {
        node().params().margin(margin);
        return this;
    }

    @Override
    public boolean isFocusable() {
        return true;
    }

    public boolean isFocused() {
        return focused;
    }

    @Override
    public void onFocusChanged(boolean focused) {
        this.focused = focused;
    }

    @Override
    public boolean onInput(InputEvent event) {
        if (event instanceof InputEvent.MousePressed press
                && press.button() == 0 && bounds().contains(press.x(), press.y())) {
            return true;
        }
        if (!focused) {
            return false;
        }
        if (event instanceof InputEvent.KeyPressed key && key.keyCode() == Keys.BACKSPACE) {
            if (!value.isEmpty()) {
                value = value.substring(0, value.length() - 1);
                onChange.accept(value);
            }
            return true;
        }
        if (event instanceof InputEvent.CharTyped typed) {
            char codePoint = typed.codePoint();
            if (codePoint >= 32 && codePoint != 127 && value.length() < maxLength) {
                value = value + codePoint;
                onChange.accept(value);
            }
            return true;
        }
        return false;
    }

    @Override
    protected void onRender(RenderContext context) {
        Rect box = bounds();
        if (box.isEmpty()) {
            return;
        }
        Theme theme = context.theme();
        context.fill(box.x(), box.y(), box.width(), box.height(),
                theme.color(ThemeColorRole.INPUT_BACKGROUND));
        int border = focused ? theme.color(ThemeColorRole.FOCUS_RING) : theme.color(ThemeColorRole.BORDER);
        drawBorder(context, box, border);
        String shown = value;
        int maxTextWidth = Math.max(0, box.width() - 6);
        while (shown.length() > 1 && context.textWidth(shown) > maxTextWidth) {
            shown = shown.substring(1);
        }
        int textY = box.y() + Math.max(0, (box.height() - context.textLineHeight()) / 2);
        context.text(shown, box.x() + 3, textY, theme.color(ThemeColorRole.TEXT), false);
        if (focused) {
            int cursorX = box.x() + 3 + Math.min(maxTextWidth, context.textWidth(shown));
            context.fill(cursorX, textY, 1, context.textLineHeight(), theme.color(ThemeColorRole.FOCUS_RING));
        }
    }

    private static void drawBorder(RenderContext context, Rect box, int color) {
        if ((color >>> 24) == 0) {
            return;
        }
        context.fill(box.x(), box.y(), box.width(), 1, color);
        context.fill(box.x(), box.bottom() - 1, box.width(), 1, color);
        context.fill(box.x(), box.y() + 1, 1, box.height() - 2, color);
        context.fill(box.right() - 1, box.y() + 1, 1, box.height() - 2, color);
    }
}
