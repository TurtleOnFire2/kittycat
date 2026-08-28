package kitty.cat.utils;

import net.minecraft.network.chat.Style;

/** Internal bridge between gradient-styled components and glyph rendering. */
public final class SmoothGradientRenderer {
    private static final String MARKER_PREFIX = "\u0000kittycat-gradient:";
    private static final ThreadLocal<GradientContext> CONTEXT = new ThreadLocal<>();

    private SmoothGradientRenderer() {
    }

    public static String marker(int leftColor, int rightColor) {
        return MARKER_PREFIX
                + String.format("%06X", leftColor & 0xFFFFFF)
                + ':'
                + String.format("%06X", rightColor & 0xFFFFFF);
    }

    public static void beginGlyph(Style style, int color, int shadowColor) {
        String insertion = style.getInsertion();
        if (insertion == null || !insertion.startsWith(MARKER_PREFIX)) {
            CONTEXT.remove();
            return;
        }

        int separator = MARKER_PREFIX.length() + 6;
        if (insertion.length() != separator + 7 || insertion.charAt(separator) != ':') {
            CONTEXT.remove();
            return;
        }

        try {
            int left = Integer.parseInt(
                    insertion.substring(MARKER_PREFIX.length(), separator),
                    16
            );
            int right = Integer.parseInt(insertion.substring(separator + 1), 16);
            CONTEXT.set(new GradientContext(left, right, color, shadowColor));
        } catch (NumberFormatException ignored) {
            CONTEXT.remove();
        }
    }

    public static void beginQuad() {
        GradientContext context = CONTEXT.get();
        if (context != null) {
            context.vertex = 0;
        }
    }

    public static int vertexColor(int originalColor) {
        GradientContext context = CONTEXT.get();
        if (context == null) return originalColor;

        int rgb = context.vertex++ < 2 ? context.leftColor : context.rightColor;
        int alpha = originalColor >>> 24;

        if (originalColor == context.color) {
            return alpha << 24 | rgb;
        }

        // NameChanger's gradient styles use Minecraft's normal 25%-brightness
        // shadow. Keep that shadow smooth as well.
        if (originalColor == context.shadowColor) {
            return alpha << 24 | scaleRgb(rgb, 0.25F);
        }

        return originalColor;
    }

    public static void endGlyph() {
        CONTEXT.remove();
    }

    private static int scaleRgb(int color, float scale) {
        int red = (int) (((color >>> 16) & 0xFF) * scale);
        int green = (int) (((color >>> 8) & 0xFF) * scale);
        int blue = (int) ((color & 0xFF) * scale);
        return red << 16 | green << 8 | blue;
    }

    private static final class GradientContext {
        private final int leftColor;
        private final int rightColor;
        private final int color;
        private final int shadowColor;
        private int vertex;

        private GradientContext(int leftColor, int rightColor, int color, int shadowColor) {
            this.leftColor = leftColor;
            this.rightColor = rightColor;
            this.color = color;
            this.shadowColor = shadowColor;
        }
    }
}
