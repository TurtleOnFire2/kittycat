package kitty.cat.compat;

import kitty.cat.mixin.client.gui.GuiGraphicsAccessor;

public final class GuiGraphicsAccessorHelper {
    private GuiGraphicsAccessorHelper() {
    }

    public static Object getScissorStack(GuiGraphicsAccessor accessor) {
        return accessor.getScissorStack();
    }
}