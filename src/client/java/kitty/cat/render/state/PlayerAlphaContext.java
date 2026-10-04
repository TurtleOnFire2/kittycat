package kitty.cat.render.state;

public final class PlayerAlphaContext {
    private static final ThreadLocal<Integer> CURRENT = ThreadLocal.withInitial(() -> 255);

    private PlayerAlphaContext() {
    }

    public static int get() {
        return CURRENT.get();
    }

    public static void set(int alpha) {
        CURRENT.set(alpha);
    }
}
