/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  kotlin.Metadata
 *  kotlin.Unit
 *  kotlin.collections.CollectionsKt
 *  kotlin.jvm.functions.Function0
 *  kotlin.jvm.internal.DefaultConstructorMarker
 *  kotlin.jvm.internal.Intrinsics
 *  kotlin.jvm.internal.SourceDebugExtension
 *  net.minecraft.client.gui.GuiGraphics
 *  net.minecraft.client.gui.screens.Screen
 *  net.minecraft.client.input.CharacterEvent
 *  net.minecraft.client.input.KeyEvent
 *  net.minecraft.client.input.MouseButtonEvent
 *  net.minecraft.client.player.LocalPlayer
 *  net.minecraft.network.chat.Component
 *  net.minecraft.sounds.SoundEvents
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 *  org.reflections.Reflections
 *  org.reflections.scanners.Scanner
 */
package kitty.cat.gui.clickgui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import kitty.cat.gui.categories.Categories;
import kitty.cat.gui.features.Feature;
import kitty.cat.gui.features.settings.ActionSetting;
import kitty.cat.gui.features.settings.BooleanSetting;
import kitty.cat.gui.features.settings.ColorSetting;
import kitty.cat.gui.features.settings.NumberSetting;
import kitty.cat.gui.features.settings.SelectorSetting;
import kitty.cat.gui.features.settings.Setting;
import kitty.cat.render.nanovg.NVGPIPRenderer;
import kitty.cat.render.nanovg.NVGRenderer;
import kitty.cat.utils.GuiUtils;
import kotlin.Metadata;
import kotlin.Unit;
import kotlin.collections.CollectionsKt;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.internal.DefaultConstructorMarker;
import kotlin.jvm.internal.Intrinsics;
import kotlin.jvm.internal.SourceDebugExtension;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.reflections.Reflections;
import org.reflections.scanners.Scanner;

@Metadata(mv={2, 3, 0}, k=1, xi=48, d1={"\u0000\u0088\u0001\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010!\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0007\n\u0002\u0010#\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0007\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\t\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u0006\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0004\u0018\u0000 L2\u00020\u0001:\u0002KLB\u0007\u00a2\u0006\u0004\b\u0002\u0010\u0003J\b\u0010\u0019\u001a\u00020\u001aH\u0014J\b\u0010\u001b\u001a\u00020\u001aH\u0016J(\u0010\u001c\u001a\u00020\u001a2\u0006\u0010\u001d\u001a\u00020\u001e2\u0006\u0010\u001f\u001a\u00020\u00052\u0006\u0010 \u001a\u00020\u00052\u0006\u0010!\u001a\u00020\"H\u0016J@\u0010#\u001a\u00020\u001a2\u0006\u0010\u001d\u001a\u00020\u001e2\u0006\u0010$\u001a\u00020\u00052\u0006\u0010%\u001a\u00020\u00052\u0006\u0010&\u001a\u00020\"2\u0006\u0010'\u001a\u00020(2\u0006\u0010)\u001a\u00020\u00052\u0006\u0010*\u001a\u00020+H\u0002J(\u0010,\u001a\u00020\u001a2\u0006\u0010\u001d\u001a\u00020\u001e2\u0006\u0010'\u001a\u00020(2\u0006\u0010)\u001a\u00020\u00052\u0006\u0010-\u001a\u00020.H\u0002J\u001e\u0010/\u001a\b\u0012\u0004\u0012\u00020(0\u000f2\u0006\u00100\u001a\u00020\u00052\u0006\u00101\u001a\u00020\u0005H\u0002J \u00102\u001a\u00020\u00052\u0006\u00103\u001a\u00020\u00052\u0006\u00104\u001a\u00020\u00052\u0006\u00105\u001a\u00020\u0005H\u0002J\u0018\u00106\u001a\u00020\b2\u0006\u00107\u001a\u0002082\u0006\u00109\u001a\u00020\bH\u0016J\u0010\u0010:\u001a\u00020\b2\u0006\u0010;\u001a\u000208H\u0016J \u0010<\u001a\u00020\b2\u0006\u0010;\u001a\u0002082\u0006\u0010=\u001a\u00020>2\u0006\u0010?\u001a\u00020>H\u0016J(\u0010@\u001a\u00020\b2\u0006\u0010=\u001a\u00020>2\u0006\u0010?\u001a\u00020>2\u0006\u0010A\u001a\u00020>2\u0006\u0010B\u001a\u00020>H\u0016J\u0010\u0010C\u001a\u00020\b2\u0006\u0010D\u001a\u00020EH\u0016J\u0010\u0010F\u001a\u00020\b2\u0006\u0010D\u001a\u00020EH\u0016J\u0010\u0010G\u001a\u00020\b2\u0006\u0010H\u001a\u00020IH\u0016J\b\u0010J\u001a\u00020\u001aH\u0016R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0005X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\bX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0014\u0010\t\u001a\b\u0012\u0004\u0012\u00020\u000b0\nX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\f\u001a\u00020\u0005X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\r\u001a\u00020\u0005X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0017\u0010\u000e\u001a\b\u0012\u0004\u0012\u00020\u00100\u000f\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0012R \u0010\u0013\u001a\b\u0012\u0004\u0012\u00020\u00100\u000fX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0014\u0010\u0012\"\u0004\b\u0015\u0010\u0016R\u0014\u0010\u0017\u001a\b\u0012\u0004\u0012\u00020\u00100\u0018X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006M"}, d2={"Lkitty/cat/gui/clickgui/ClickGui;", "Lnet/minecraft/client/gui/screens/Screen;", "<init>", "()V", "offsetX", "", "offsetY", "dragging", "", "categoryList", "", "Lkitty/cat/gui/categories/Categories$Category;", "selectedIndex", "cooldown", "featureList", "", "Lkitty/cat/gui/features/Feature;", "getFeatureList", "()Ljava/util/List;", "activeFeatures", "getActiveFeatures", "setActiveFeatures", "(Ljava/util/List;)V", "expandedFeatures", "", "init", "", "tick", "render", "guiGraphics", "Lnet/minecraft/client/gui/GuiGraphics;", "mouseX", "mouseY", "partialTicks", "", "renderValueText", "sw", "sh", "scale", "layout", "Lkitty/cat/gui/clickgui/ClickGui$FeatureLayout;", "settingY", "value", "", "renderBooleanSwitch", "setting", "Lkitty/cat/gui/features/settings/BooleanSetting;", "buildFeatureLayouts", "panelX", "panelY", "circularRelativeOffset", "index", "selected", "size", "mouseClicked", "mbe", "Lnet/minecraft/client/input/MouseButtonEvent;", "bl", "mouseReleased", "mouseButtonEvent", "mouseDragged", "d", "", "e", "mouseScrolled", "f", "g", "keyPressed", "keyEvent", "Lnet/minecraft/client/input/KeyEvent;", "keyReleased", "charTyped", "characterEvent", "Lnet/minecraft/client/input/CharacterEvent;", "onClose", "FeatureLayout", "Companion", "kittycat_client"})
@SourceDebugExtension(value={"SMAP\nClickGui.kt\nKotlin\n*S Kotlin\n*F\n+ 1 ClickGui.kt\nkitty/cat/gui/clickgui/ClickGui\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 3 fake.kt\nkotlin/jvm/internal/FakeKt\n*L\n1#1,464:1\n1642#2,10:465\n1915#2:475\n1916#2:477\n1652#2:478\n777#2:479\n873#2,2:480\n1924#2,3:482\n1915#2:485\n1924#2,3:486\n1916#2:489\n1924#2,3:490\n296#2,2:493\n296#2,2:495\n1915#2,2:497\n777#2:499\n873#2,2:500\n1#3:476\n*S KotlinDebug\n*F\n+ 1 ClickGui.kt\nkitty/cat/gui/clickgui/ClickGui\n*L\n84#1:465,10\n84#1:475\n84#1:477\n84#1:478\n98#1:479\n98#1:480,2\n123#1:482,3\n143#1:485\n186#1:486,3\n143#1:489\n318#1:490,3\n379#1:493,2\n385#1:495,2\n392#1:497,2\n453#1:499\n453#1:500,2\n84#1:476\n*E\n"})
public final class ClickGui
extends Screen {
    @NotNull
    private static final Companion Companion = new Companion(null);
    private int offsetX;
    private int offsetY;
    private boolean dragging;
    @NotNull
    private List<Categories.Category> categoryList = new ArrayList();
    private int selectedIndex;
    private int cooldown;
    @NotNull
    private final List<Feature> featureList;
    @NotNull
    private List<? extends Feature> activeFeatures;
    @NotNull
    private final Set<Feature> expandedFeatures;
    @Deprecated
    public static final int PANEL_WIDTH = 400;
    @Deprecated
    public static final int PANEL_HEIGHT = 300;
    @Deprecated
    public static final int FEATURE_START_Y = 50;
    @Deprecated
    public static final int FEATURE_CARD_WIDTH = 190;
    @Deprecated
    public static final int FEATURE_CARD_GAP = 6;
    @Deprecated
    public static final int FEATURE_HEADER_HEIGHT = 20;
    @Deprecated
    public static final int FEATURE_SETTINGS_TOP_PADDING = 6;
    @Deprecated
    public static final int FEATURE_SETTING_ROW_HEIGHT = 14;
    @Deprecated
    public static final int FEATURE_SWITCH_WIDTH = 24;
    @Deprecated
    public static final int FEATURE_SWITCH_HEIGHT = 12;
    @Deprecated
    public static final int FEATURE_SWITCH_RIGHT_PADDING = 8;
    @Deprecated
    public static final int FEATURE_SWITCH_KNOB_MARGIN = 2;
    @Deprecated
    public static final int FEATURE_SWITCH_Y_OFFSET = -3;
    @Deprecated
    public static final int FEATURE_ACTION_BUTTON_WIDTH = 34;
    @Deprecated
    public static final int FEATURE_ACTION_BUTTON_HEIGHT = 12;
    @Deprecated
    public static final int LEFT_MOUSE_BUTTON = 0;
    @Deprecated
    public static final int RIGHT_MOUSE_BUTTON = 1;

    /*
     * WARNING - void declaration
     */
    public ClickGui() {
        super((Component)Component.literal((String)"Kittycat Gui"));
        void $this$mapNotNullTo$iv$iv;
        void $this$mapNotNull$iv;
        Set set = new Reflections("kitty.cat.features", new Scanner[0]).getSubTypesOf(Feature.class);
        Intrinsics.checkNotNullExpressionValue((Object)set, (String)"getSubTypesOf(...)");
        Iterable iterable = set;
        ClickGui clickGui = this;
        boolean $i$f$mapNotNull = false;
        void var3_4 = $this$mapNotNull$iv;
        Collection destination$iv$iv = new ArrayList();
        boolean $i$f$mapNotNullTo = false;
        void $this$forEach$iv$iv$iv = $this$mapNotNullTo$iv$iv;
        boolean $i$f$forEach = false;
        Iterator iterator = $this$forEach$iv$iv$iv.iterator();
        while (iterator.hasNext()) {
            Feature it$iv$iv;
            Feature feature;
            Object element$iv$iv$iv;
            Object element$iv$iv = element$iv$iv$iv = iterator.next();
            boolean bl = false;
            Class clazz = (Class)element$iv$iv;
            boolean bl2 = false;
            try {
                Object object = clazz.getField("INSTANCE").get(null);
                Intrinsics.checkNotNull((Object)object, (String)"null cannot be cast to non-null type kitty.cat.gui.features.Feature");
                feature = (Feature)object;
            }
            catch (Exception e) {
                e.printStackTrace();
                feature = null;
            }
            if (feature == null) continue;
            boolean bl3 = false;
            destination$iv$iv.add(it$iv$iv);
        }
        clickGui.featureList = (List)destination$iv$iv;
        this.activeFeatures = CollectionsKt.emptyList();
        this.expandedFeatures = new LinkedHashSet();
    }

    @NotNull
    public final List<Feature> getFeatureList() {
        return this.featureList;
    }

    @NotNull
    public final List<Feature> getActiveFeatures() {
        return this.activeFeatures;
    }

    public final void setActiveFeatures(@NotNull List<? extends Feature> list) {
        Intrinsics.checkNotNullParameter(list, (String)"<set-?>");
        this.activeFeatures = list;
    }

    /*
     * WARNING - void declaration
     */
    protected void init() {
        void $this$filterTo$iv$iv;
        void $this$filter$iv;
        this.categoryList = CollectionsKt.toMutableList((Collection)((Collection)Categories.Category.getEntries()));
        Iterable iterable = this.featureList;
        ClickGui clickGui = this;
        boolean $i$f$filter = false;
        void var3_4 = $this$filter$iv;
        Collection destination$iv$iv = new ArrayList();
        boolean $i$f$filterTo = false;
        for (Object element$iv$iv : $this$filterTo$iv$iv) {
            Feature it = (Feature)element$iv$iv;
            boolean bl = false;
            if (!(it.getCategory$kittycat_client() == this.categoryList.get(this.selectedIndex))) continue;
            destination$iv$iv.add(element$iv$iv);
        }
        clickGui.activeFeatures = (List)destination$iv$iv;
        this.expandedFeatures.clear();
    }

    public void tick() {
        int n = this.cooldown;
        this.cooldown = n + -1;
        super.tick();
    }

    /*
     * WARNING - void declaration
     */
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        Intrinsics.checkNotNullParameter((Object)guiGraphics, (String)"guiGraphics");
        int x = this.width / 2 - 200 + this.offsetX;
        int y = this.height / 2 - 150 + this.offsetY;
        int sw = this.minecraft.getWindow().getGuiScaledWidth();
        int sh = this.minecraft.getWindow().getGuiScaledHeight();
        float scale = this.minecraft.getWindow().getGuiScale();
        GuiUtils.INSTANCE.renderRoundedRectangle(guiGraphics, x, y, 400, 300, 5, 1688437651);
        GuiUtils.INSTANCE.renderRectangle(guiGraphics, x, y, 400, 10, -1772812941);
        if (!((Collection)this.categoryList).isEmpty()) {
            int centerX = x + 200;
            int categoryY = y + 20;
            int spacing = 70;
            Iterable $this$forEachIndexed$iv = this.categoryList;
            boolean $i$f$forEachIndexed = false;
            int index$iv = 0;
            for (Object item$iv : $this$forEachIndexed$iv) {
                void category;
                int n;
                if ((n = index$iv++) < 0) {
                    CollectionsKt.throwIndexOverflow();
                }
                Categories.Category category2 = (Categories.Category)((Object)item$iv);
                int index = n;
                boolean bl = false;
                int offset = this.circularRelativeOffset(index, this.selectedIndex, this.categoryList.size());
                int distance = Math.abs(offset);
                int color = distance == 0 ? Color.WHITE.getRGB() : Color.DARK_GRAY.getRGB();
                NVGPIPRenderer.Companion.draw(guiGraphics, 0, 0, sw, sh, (Function0<Unit>)((Function0)() -> ClickGui.render$lambda$0$0((Categories.Category)category, centerX, offset, spacing, scale, categoryY, color)));
            }
        }
        GuiUtils.INSTANCE.renderRectangle(guiGraphics, x, y + 40, 400, 2, Color.DARK_GRAY.getRGB());
        List<FeatureLayout> featureLayouts = this.buildFeatureLayouts(x, y);
        Iterable $this$forEach$iv = featureLayouts;
        boolean $i$f$forEach = false;
        for (Object element$iv : $this$forEach$iv) {
            FeatureLayout layout = (FeatureLayout)element$iv;
            boolean bl = false;
            int borderColor = layout.getFeature().getEnabled() ? new Color(89, 191, 113).getRGB() : (layout.getExpanded() ? Color.WHITE.getRGB() : Color.DARK_GRAY.getRGB());
            GuiUtils.INSTANCE.renderRoundedOutline(guiGraphics, layout.getX(), layout.getY(), layout.getWidth(), layout.getTotalHeight(), 5, 1, borderColor);
            NVGPIPRenderer.Companion.draw(guiGraphics, 0, 0, sw, sh, (Function0<Unit>)((Function0)() -> ClickGui.render$lambda$1$0(layout, scale)));
            if (!layout.getExpanded()) continue;
            GuiUtils.INSTANCE.renderRectangle(guiGraphics, layout.getX() + 4, layout.getY() + 20, layout.getWidth() - 8, 1, Color.DARK_GRAY.getRGB());
            List<Setting> settings = layout.getFeature().getSettings();
            if (settings.isEmpty()) {
                NVGPIPRenderer.Companion.draw(guiGraphics, 0, 0, sw, sh, (Function0<Unit>)((Function0)() -> ClickGui.render$lambda$1$1(layout, scale)));
                continue;
            }
            Iterable $this$forEachIndexed$iv = settings;
            boolean $i$f$forEachIndexed = false;
            int index$iv = 0;
            for (Object item$iv : $this$forEachIndexed$iv) {
                void setting;
                int n;
                if ((n = index$iv++) < 0) {
                    CollectionsKt.throwIndexOverflow();
                }
                Setting setting2 = (Setting)item$iv;
                int settingIndex = n;
                boolean bl2 = false;
                int settingY = layout.getSettingStartY() + settingIndex * 14;
                NVGPIPRenderer.Companion.draw(guiGraphics, 0, 0, sw, sh, (Function0<Unit>)((Function0)() -> ClickGui.render$lambda$1$2$0((Setting)setting, layout, scale, settingY)));
                void var29_38 = setting;
                if (var29_38 instanceof BooleanSetting) {
                    this.renderBooleanSwitch(guiGraphics, layout, settingY, (BooleanSetting)setting);
                    continue;
                }
                if (var29_38 instanceof NumberSetting) {
                    this.renderValueText(guiGraphics, sw, sh, scale, layout, settingY, ((NumberSetting)setting).textValue(true));
                    continue;
                }
                if (var29_38 instanceof SelectorSetting) {
                    String selectedText = ((SelectorSetting)setting).getAllowMultiple() ? CollectionsKt.joinToString$default((Iterable)((SelectorSetting)setting).getSelected(), (CharSequence)", ", null, null, (int)0, null, null, (int)62, null) : ((SelectorSetting)setting).getSelectedSingle();
                    this.renderValueText(guiGraphics, sw, sh, scale, layout, settingY, selectedText);
                    continue;
                }
                if (var29_38 instanceof ColorSetting) {
                    this.renderValueText(guiGraphics, sw, sh, scale, layout, settingY, ((ColorSetting)setting).rgbaText());
                    int swatchSize = 10;
                    GuiUtils.INSTANCE.renderRoundedRectangle(guiGraphics, layout.getX() + layout.getWidth() - 8 - swatchSize, settingY + 2, swatchSize, swatchSize, 2, ((ColorSetting)setting).getAlpha() << 24 | ((ColorSetting)setting).getRed() << 16 | ((ColorSetting)setting).getGreen() << 8 | ((ColorSetting)setting).getBlue());
                    continue;
                }
                if (!(var29_38 instanceof ActionSetting)) continue;
                int buttonX = layout.getX() + layout.getWidth() - 8 - 34;
                int buttonY = settingY + 1;
                GuiUtils.INSTANCE.renderRoundedOutline(guiGraphics, buttonX, buttonY, 34, 12, 4, 1, Color.GRAY.getRGB());
                NVGPIPRenderer.Companion.draw(guiGraphics, 0, 0, sw, sh, (Function0<Unit>)((Function0)() -> ClickGui.render$lambda$1$2$1(buttonX, scale, buttonY)));
            }
        }
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
    }

    private final void renderValueText(GuiGraphics guiGraphics, int sw, int sh, float scale, FeatureLayout layout, int settingY, String value) {
        NVGPIPRenderer.Companion.draw(guiGraphics, 0, 0, sw, sh, (Function0<Unit>)((Function0)() -> ClickGui.renderValueText$lambda$0(value, layout, scale, settingY)));
    }

    private final void renderBooleanSwitch(GuiGraphics guiGraphics, FeatureLayout layout, int settingY, BooleanSetting setting) {
        int switchX = layout.getX() + layout.getWidth() - 8 - 24;
        int switchY = settingY + 1 + -3;
        int knobSize = 8;
        int knobX = setting.getValue() ? switchX + 24 - knobSize - 2 : switchX + 2;
        int knobY = switchY + 2;
        int trackColor = setting.getValue() ? new Color(89, 191, 113).getRGB() : new Color(70, 70, 70).getRGB();
        GuiUtils.INSTANCE.renderRoundedRectangle(guiGraphics, switchX, switchY, 24, 12, 6, trackColor);
        GuiUtils.INSTANCE.renderRoundedRectangle(guiGraphics, knobX, knobY, knobSize, knobSize, knobSize / 2, Color.WHITE.getRGB());
    }

    /*
     * WARNING - void declaration
     */
    private final List<FeatureLayout> buildFeatureLayouts(int panelX, int panelY) {
        List layouts = new ArrayList();
        int nextLeftY = 0;
        nextLeftY = panelY + 50;
        int nextRightY = 0;
        nextRightY = panelY + 50;
        Iterable $this$forEachIndexed$iv = this.activeFeatures;
        boolean $i$f$forEachIndexed = false;
        int index$iv = 0;
        for (Object item$iv : $this$forEachIndexed$iv) {
            void feature;
            int n;
            if ((n = index$iv++) < 0) {
                CollectionsKt.throwIndexOverflow();
            }
            Feature feature2 = (Feature)item$iv;
            int index = n;
            boolean bl = false;
            boolean leftColumn = index % 2 == 0;
            int cardX = panelX + 5 + (leftColumn ? 0 : 200);
            int cardY = leftColumn ? nextLeftY : nextRightY;
            boolean expanded = this.expandedFeatures.contains(feature);
            int visibleSettingRows = expanded ? Math.max(feature.getSettings().size(), 1) : 0;
            int settingsSectionHeight = visibleSettingRows > 0 ? 6 + visibleSettingRows * 14 : 0;
            int totalHeight = 20 + settingsSectionHeight;
            FeatureLayout layout = new FeatureLayout((Feature)feature, cardX, cardY, 190, 20, totalHeight, cardY + 20 + 6, 14, expanded);
            ((Collection)layouts).add(layout);
            if (leftColumn) {
                nextLeftY += totalHeight + 6;
                continue;
            }
            nextRightY += totalHeight + 6;
        }
        return layouts;
    }

    private final int circularRelativeOffset(int index, int selected, int size) {
        int offset = index - selected;
        int half = size / 2;
        if (offset > half) {
            offset -= size;
        }
        if (offset < -half) {
            offset += size;
        }
        return offset;
    }

    public boolean mouseClicked(@NotNull MouseButtonEvent mbe, boolean bl) {
        Intrinsics.checkNotNullParameter((Object)mbe, (String)"mbe");
        int x = this.width / 2 - 200 + this.offsetX;
        int y = this.height / 2 - 150 + this.offsetY;
        if (mbe.button() == 0) {
            double d = x;
            double d2 = x + 400;
            double d3 = mbe.x();
            boolean bl2 = d <= d3 ? d3 <= d2 : false;
            if (bl2) {
                d = y;
                d2 = y + 10;
                d3 = mbe.y();
                boolean bl3 = d <= d3 ? d3 <= d2 : false;
                if (bl3) {
                    this.dragging = true;
                    return true;
                }
            }
        }
        List<FeatureLayout> layouts = this.buildFeatureLayouts(x, y);
        if (mbe.button() == 1) {
            Object v2;
            block13: {
                Iterable $this$firstOrNull$iv = layouts;
                boolean $i$f$firstOrNull = false;
                for (Object element$iv : $this$firstOrNull$iv) {
                    FeatureLayout it = (FeatureLayout)element$iv;
                    boolean bl4 = false;
                    if (!it.isHeaderHovered(mbe.x(), mbe.y())) continue;
                    v2 = element$iv;
                    break block13;
                }
                v2 = null;
            }
            FeatureLayout featureLayout = v2;
            if (featureLayout == null) {
                return super.mouseClicked(mbe, bl);
            }
            FeatureLayout clicked = featureLayout;
            boolean bl5 = clicked.getExpanded() ? this.expandedFeatures.remove(clicked.getFeature()) : this.expandedFeatures.add(clicked.getFeature());
            return true;
        }
        if (mbe.button() == 0) {
            Object v5;
            block14: {
                Iterable $this$firstOrNull$iv = layouts;
                boolean $i$f$firstOrNull = false;
                for (Object element$iv : $this$firstOrNull$iv) {
                    FeatureLayout it = (FeatureLayout)element$iv;
                    boolean bl6 = false;
                    if (!it.isHeaderHovered(mbe.x(), mbe.y())) continue;
                    v5 = element$iv;
                    break block14;
                }
                v5 = null;
            }
            FeatureLayout clickedHeader = v5;
            if (clickedHeader != null) {
                clickedHeader.getFeature().toggle();
                LocalPlayer localPlayer = this.minecraft.player;
                if (localPlayer != null) {
                    localPlayer.playSound(SoundEvents.LEVER_CLICK, 0.1f, clickedHeader.getFeature().getEnabled() ? 1.2f : 0.8f);
                }
                return true;
            }
            Iterable $this$forEach$iv = layouts;
            boolean $i$f$forEach = false;
            for (Object element$iv : $this$forEach$iv) {
                boolean inButtonY;
                double d;
                double d4;
                double d5;
                double d6;
                double d7;
                double d8;
                FeatureLayout layout = (FeatureLayout)element$iv;
                boolean bl7 = false;
                Integer n = layout.settingIndexAt(mbe.x(), mbe.y());
                if (n == null) {
                    continue;
                }
                int settingIndex = n;
                List<Setting> settings = layout.getFeature().getSettings();
                boolean bl8 = 0 <= settingIndex ? settingIndex < ((Collection)settings).size() : false;
                if (!bl8) continue;
                Setting setting = settings.get(settingIndex);
                int settingY = layout.getSettingStartY() + settingIndex * 14;
                Setting setting2 = setting;
                if (setting2 instanceof BooleanSetting) {
                    boolean inSwitchY;
                    int switchX = layout.getX() + layout.getWidth() - 8 - 24;
                    int switchY = settingY + 1 + -3;
                    d8 = switchX;
                    d7 = switchX + 24;
                    d6 = mbe.x();
                    boolean inSwitchX = d8 <= d6 ? d6 <= d7 : false;
                    d5 = switchY;
                    d4 = switchY + 12;
                    d = mbe.y();
                    boolean bl9 = d5 <= d ? d <= d4 : (inSwitchY = false);
                    if (!inSwitchX || !inSwitchY) continue;
                    ((BooleanSetting)setting).toggle();
                    return true;
                }
                if (!(setting2 instanceof ActionSetting)) continue;
                int buttonX = layout.getX() + layout.getWidth() - 8 - 34;
                int buttonY = settingY + 1;
                d8 = buttonX;
                d7 = buttonX + 34;
                d6 = mbe.x();
                boolean inButtonX = d8 <= d6 ? d6 <= d7 : false;
                d5 = buttonY;
                d4 = buttonY + 12;
                d = mbe.y();
                boolean bl10 = d5 <= d ? d <= d4 : (inButtonY = false);
                if (!inButtonX || !inButtonY) continue;
                ((ActionSetting)setting).trigger();
                return true;
            }
        }
        return super.mouseClicked(mbe, bl);
    }

    public boolean mouseReleased(@NotNull MouseButtonEvent mouseButtonEvent) {
        Intrinsics.checkNotNullParameter((Object)mouseButtonEvent, (String)"mouseButtonEvent");
        this.dragging = false;
        return super.mouseReleased(mouseButtonEvent);
    }

    public boolean mouseDragged(@NotNull MouseButtonEvent mouseButtonEvent, double d, double e) {
        Intrinsics.checkNotNullParameter((Object)mouseButtonEvent, (String)"mouseButtonEvent");
        if (this.dragging) {
            this.offsetX += (int)d;
            this.offsetY += (int)e;
        }
        return super.mouseDragged(mouseButtonEvent, d, e);
    }

    /*
     * WARNING - void declaration
     */
    public boolean mouseScrolled(double d, double e, double f, double g) {
        block7: {
            void $this$filterTo$iv$iv;
            void $this$filter$iv;
            if (this.categoryList.isEmpty()) {
                return false;
            }
            if (this.cooldown > 0) {
                return false;
            }
            this.cooldown = 2;
            if (g > 0.0) {
                this.selectedIndex = (this.selectedIndex - 1 + this.categoryList.size()) % this.categoryList.size();
            } else if (g < 0.0) {
                this.selectedIndex = (this.selectedIndex + 1) % this.categoryList.size();
            } else {
                return false;
            }
            Iterable iterable = this.featureList;
            ClickGui clickGui = this;
            boolean $i$f$filter = false;
            void var11_8 = $this$filter$iv;
            Collection destination$iv$iv = new ArrayList();
            boolean $i$f$filterTo = false;
            for (Object element$iv$iv : $this$filterTo$iv$iv) {
                Feature it = (Feature)element$iv$iv;
                boolean bl = false;
                if (!(it.getCategory$kittycat_client() == this.categoryList.get(this.selectedIndex))) continue;
                destination$iv$iv.add(element$iv$iv);
            }
            clickGui.activeFeatures = (List)destination$iv$iv;
            this.expandedFeatures.clear();
            LocalPlayer localPlayer = this.minecraft.player;
            if (localPlayer == null) break block7;
            localPlayer.playSound(SoundEvents.LEVER_CLICK, 0.1f, 1.0f);
        }
        return true;
    }

    public boolean keyPressed(@NotNull KeyEvent keyEvent) {
        Intrinsics.checkNotNullParameter((Object)keyEvent, (String)"keyEvent");
        return super.keyPressed(keyEvent);
    }

    public boolean keyReleased(@NotNull KeyEvent keyEvent) {
        Intrinsics.checkNotNullParameter((Object)keyEvent, (String)"keyEvent");
        return super.keyReleased(keyEvent);
    }

    public boolean charTyped(@NotNull CharacterEvent characterEvent) {
        Intrinsics.checkNotNullParameter((Object)characterEvent, (String)"characterEvent");
        return super.charTyped(characterEvent);
    }

    public void onClose() {
        super.onClose();
    }

    private static final Unit render$lambda$0$0(Categories.Category $category, int $centerX, int $offset, int $spacing, float $scale, int $categoryY, int $color) {
        NVGRenderer.textCentered$default(NVGRenderer.INSTANCE, $category.name(), (float)($centerX + $offset * $spacing) * $scale, (float)$categoryY * $scale, 12.0f * $scale, $color, null, 32, null);
        return Unit.INSTANCE;
    }

    private static final Unit render$lambda$1$2$0(Setting $setting, FeatureLayout $layout, float $scale, int $settingY) {
        NVGRenderer.text$default(NVGRenderer.INSTANCE, $setting.getName(), (float)($layout.getX() + 8) * $scale, (float)$settingY * $scale, 9.0f * $scale, Color.WHITE.getRGB(), null, 32, null);
        return Unit.INSTANCE;
    }

    private static final Unit render$lambda$1$2$1(int $buttonX, float $scale, int $buttonY) {
        NVGRenderer.textCentered$default(NVGRenderer.INSTANCE, "Run", ((float)$buttonX + 17.0f) * $scale, (float)($buttonY + 2) * $scale, 9.0f * $scale, Color.WHITE.getRGB(), null, 32, null);
        return Unit.INSTANCE;
    }

    private static final Unit render$lambda$1$0(FeatureLayout $layout, float $scale) {
        NVGRenderer.text$default(NVGRenderer.INSTANCE, $layout.getFeature().getName$kittycat_client(), (float)($layout.getX() + 8) * $scale, (float)($layout.getY() + 5) * $scale, 12.0f * $scale, $layout.getFeature().getEnabled() ? new Color(170, 255, 170).getRGB() : Color.WHITE.getRGB(), null, 32, null);
        return Unit.INSTANCE;
    }

    private static final Unit render$lambda$1$1(FeatureLayout $layout, float $scale) {
        NVGRenderer.text$default(NVGRenderer.INSTANCE, "No settings", (float)($layout.getX() + 8) * $scale, (float)$layout.getSettingStartY() * $scale, 11.0f * $scale, Color.GRAY.getRGB(), null, 32, null);
        return Unit.INSTANCE;
    }

    private static final Unit renderValueText$lambda$0(String $value, FeatureLayout $layout, float $scale, int $settingY) {
        NVGRenderer.text$default(NVGRenderer.INSTANCE, $value, (float)($layout.getX() + 90) * $scale, (float)$settingY * $scale, 9.0f * $scale, Color.LIGHT_GRAY.getRGB(), null, 32, null);
        return Unit.INSTANCE;
    }

    @Metadata(mv={2, 3, 0}, k=1, xi=48, d1={"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0003\n\u0002\u0010\b\n\u0002\b\u0011\b\u0082\u0003\u0018\u00002\u00020\u0001B\t\b\u0002\u00a2\u0006\u0004\b\u0002\u0010\u0003R\u000e\u0010\u0004\u001a\u00020\u0005X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0005X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0005X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\u0005X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\u0005X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\u0005X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000b\u001a\u00020\u0005X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\f\u001a\u00020\u0005X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\r\u001a\u00020\u0005X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000e\u001a\u00020\u0005X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000f\u001a\u00020\u0005X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0010\u001a\u00020\u0005X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0011\u001a\u00020\u0005X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0012\u001a\u00020\u0005X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0013\u001a\u00020\u0005X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0014\u001a\u00020\u0005X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0015\u001a\u00020\u0005X\u0086T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0016"}, d2={"Lkitty/cat/gui/clickgui/ClickGui$Companion;", "", "<init>", "()V", "PANEL_WIDTH", "", "PANEL_HEIGHT", "FEATURE_START_Y", "FEATURE_CARD_WIDTH", "FEATURE_CARD_GAP", "FEATURE_HEADER_HEIGHT", "FEATURE_SETTINGS_TOP_PADDING", "FEATURE_SETTING_ROW_HEIGHT", "FEATURE_SWITCH_WIDTH", "FEATURE_SWITCH_HEIGHT", "FEATURE_SWITCH_RIGHT_PADDING", "FEATURE_SWITCH_KNOB_MARGIN", "FEATURE_SWITCH_Y_OFFSET", "FEATURE_ACTION_BUTTON_WIDTH", "FEATURE_ACTION_BUTTON_HEIGHT", "LEFT_MOUSE_BUTTON", "RIGHT_MOUSE_BUTTON", "kittycat_client"})
    private static final class Companion {
        private Companion() {
        }

        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }
    }

    @Metadata(mv={2, 3, 0}, k=1, xi=48, d1={"\u0000.\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0002\b\u0007\n\u0002\u0010\u000b\n\u0002\b\u0010\n\u0002\u0010\u0006\n\u0002\b\u0011\n\u0002\u0010\u000e\n\u0000\b\u0082\b\u0018\u00002\u00020\u0001BO\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0005\u0012\u0006\u0010\u0007\u001a\u00020\u0005\u0012\u0006\u0010\b\u001a\u00020\u0005\u0012\u0006\u0010\t\u001a\u00020\u0005\u0012\u0006\u0010\n\u001a\u00020\u0005\u0012\u0006\u0010\u000b\u001a\u00020\u0005\u0012\u0006\u0010\f\u001a\u00020\r\u00a2\u0006\u0004\b\u000e\u0010\u000fJ\u0016\u0010\u001c\u001a\u00020\r2\u0006\u0010\u001d\u001a\u00020\u001e2\u0006\u0010\u001f\u001a\u00020\u001eJ\u001d\u0010 \u001a\u0004\u0018\u00010\u00052\u0006\u0010\u001d\u001a\u00020\u001e2\u0006\u0010\u001f\u001a\u00020\u001e\u00a2\u0006\u0002\u0010!J\t\u0010\"\u001a\u00020\u0003H\u00c6\u0003J\t\u0010#\u001a\u00020\u0005H\u00c6\u0003J\t\u0010$\u001a\u00020\u0005H\u00c6\u0003J\t\u0010%\u001a\u00020\u0005H\u00c6\u0003J\t\u0010&\u001a\u00020\u0005H\u00c6\u0003J\t\u0010'\u001a\u00020\u0005H\u00c6\u0003J\t\u0010(\u001a\u00020\u0005H\u00c6\u0003J\t\u0010)\u001a\u00020\u0005H\u00c6\u0003J\t\u0010*\u001a\u00020\rH\u00c6\u0003Jc\u0010+\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00052\b\b\u0002\u0010\u0006\u001a\u00020\u00052\b\b\u0002\u0010\u0007\u001a\u00020\u00052\b\b\u0002\u0010\b\u001a\u00020\u00052\b\b\u0002\u0010\t\u001a\u00020\u00052\b\b\u0002\u0010\n\u001a\u00020\u00052\b\b\u0002\u0010\u000b\u001a\u00020\u00052\b\b\u0002\u0010\f\u001a\u00020\rH\u00c6\u0001J\u0014\u0010,\u001a\u00020\r2\b\u0010-\u001a\u0004\u0018\u00010\u0001H\u00d6\u0083\u0004J\n\u0010.\u001a\u00020\u0005H\u00d6\u0081\u0004J\n\u0010/\u001a\u000200H\u00d6\u0081\u0004R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0010\u0010\u0011R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0012\u0010\u0013R\u0011\u0010\u0006\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0014\u0010\u0013R\u0011\u0010\u0007\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0015\u0010\u0013R\u0011\u0010\b\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0016\u0010\u0013R\u0011\u0010\t\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0017\u0010\u0013R\u0011\u0010\n\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0018\u0010\u0013R\u0011\u0010\u000b\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0019\u0010\u0013R\u0011\u0010\f\u001a\u00020\r\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001a\u0010\u001b\u00a8\u00061"}, d2={"Lkitty/cat/gui/clickgui/ClickGui$FeatureLayout;", "", "feature", "Lkitty/cat/gui/features/Feature;", "x", "", "y", "width", "headerHeight", "totalHeight", "settingStartY", "settingRowHeight", "expanded", "", "<init>", "(Lkitty/cat/gui/features/Feature;IIIIIIIZ)V", "getFeature", "()Lkitty/cat/gui/features/Feature;", "getX", "()I", "getY", "getWidth", "getHeaderHeight", "getTotalHeight", "getSettingStartY", "getSettingRowHeight", "getExpanded", "()Z", "isHeaderHovered", "mouseX", "", "mouseY", "settingIndexAt", "(DD)Ljava/lang/Integer;", "component1", "component2", "component3", "component4", "component5", "component6", "component7", "component8", "component9", "copy", "equals", "other", "hashCode", "toString", "", "kittycat_client"})
    private static final class FeatureLayout {
        @NotNull
        private final Feature feature;
        private final int x;
        private final int y;
        private final int width;
        private final int headerHeight;
        private final int totalHeight;
        private final int settingStartY;
        private final int settingRowHeight;
        private final boolean expanded;

        public FeatureLayout(@NotNull Feature feature, int x, int y, int width, int headerHeight, int totalHeight, int settingStartY, int settingRowHeight, boolean expanded) {
            Intrinsics.checkNotNullParameter((Object)feature, (String)"feature");
            this.feature = feature;
            this.x = x;
            this.y = y;
            this.width = width;
            this.headerHeight = headerHeight;
            this.totalHeight = totalHeight;
            this.settingStartY = settingStartY;
            this.settingRowHeight = settingRowHeight;
            this.expanded = expanded;
        }

        @NotNull
        public final Feature getFeature() {
            return this.feature;
        }

        public final int getX() {
            return this.x;
        }

        public final int getY() {
            return this.y;
        }

        public final int getWidth() {
            return this.width;
        }

        public final int getHeaderHeight() {
            return this.headerHeight;
        }

        public final int getTotalHeight() {
            return this.totalHeight;
        }

        public final int getSettingStartY() {
            return this.settingStartY;
        }

        public final int getSettingRowHeight() {
            return this.settingRowHeight;
        }

        public final boolean getExpanded() {
            return this.expanded;
        }

        /*
         * Enabled force condition propagation
         * Lifted jumps to return sites
         */
        public final boolean isHeaderHovered(double mouseX, double mouseY) {
            double d = this.x;
            if (!(mouseX <= (double)(this.x + this.width))) return false;
            if (!(d <= mouseX)) return false;
            boolean bl = true;
            if (!bl) return false;
            d = this.y;
            if (!(mouseY <= (double)(this.y + this.headerHeight))) return false;
            if (!(d <= mouseY)) return false;
            return true;
        }

        @Nullable
        public final Integer settingIndexAt(double mouseX, double mouseY) {
            if (!this.expanded) {
                return null;
            }
            double d = this.x;
            if (!(mouseX <= (double)(this.x + this.width) ? d <= mouseX : false)) {
                return null;
            }
            d = this.settingStartY;
            if (!(mouseY <= (double)(this.y + this.totalHeight) ? d <= mouseY : false)) {
                return null;
            }
            int index = (int)((mouseY - (double)this.settingStartY) / (double)this.settingRowHeight);
            return index >= 0 ? Integer.valueOf(index) : null;
        }

        @NotNull
        public final Feature component1() {
            return this.feature;
        }

        public final int component2() {
            return this.x;
        }

        public final int component3() {
            return this.y;
        }

        public final int component4() {
            return this.width;
        }

        public final int component5() {
            return this.headerHeight;
        }

        public final int component6() {
            return this.totalHeight;
        }

        public final int component7() {
            return this.settingStartY;
        }

        public final int component8() {
            return this.settingRowHeight;
        }

        public final boolean component9() {
            return this.expanded;
        }

        @NotNull
        public final FeatureLayout copy(@NotNull Feature feature, int x, int y, int width, int headerHeight, int totalHeight, int settingStartY, int settingRowHeight, boolean expanded) {
            Intrinsics.checkNotNullParameter((Object)feature, (String)"feature");
            return new FeatureLayout(feature, x, y, width, headerHeight, totalHeight, settingStartY, settingRowHeight, expanded);
        }

        public static /* synthetic */ FeatureLayout copy$default(FeatureLayout featureLayout, Feature feature, int n, int n2, int n3, int n4, int n5, int n6, int n7, boolean bl, int n8, Object object) {
            if ((n8 & 1) != 0) {
                feature = featureLayout.feature;
            }
            if ((n8 & 2) != 0) {
                n = featureLayout.x;
            }
            if ((n8 & 4) != 0) {
                n2 = featureLayout.y;
            }
            if ((n8 & 8) != 0) {
                n3 = featureLayout.width;
            }
            if ((n8 & 0x10) != 0) {
                n4 = featureLayout.headerHeight;
            }
            if ((n8 & 0x20) != 0) {
                n5 = featureLayout.totalHeight;
            }
            if ((n8 & 0x40) != 0) {
                n6 = featureLayout.settingStartY;
            }
            if ((n8 & 0x80) != 0) {
                n7 = featureLayout.settingRowHeight;
            }
            if ((n8 & 0x100) != 0) {
                bl = featureLayout.expanded;
            }
            return featureLayout.copy(feature, n, n2, n3, n4, n5, n6, n7, bl);
        }

        @NotNull
        public String toString() {
            return "FeatureLayout(feature=" + this.feature + ", x=" + this.x + ", y=" + this.y + ", width=" + this.width + ", headerHeight=" + this.headerHeight + ", totalHeight=" + this.totalHeight + ", settingStartY=" + this.settingStartY + ", settingRowHeight=" + this.settingRowHeight + ", expanded=" + this.expanded + ")";
        }

        public int hashCode() {
            int result = this.feature.hashCode();
            result = result * 31 + Integer.hashCode(this.x);
            result = result * 31 + Integer.hashCode(this.y);
            result = result * 31 + Integer.hashCode(this.width);
            result = result * 31 + Integer.hashCode(this.headerHeight);
            result = result * 31 + Integer.hashCode(this.totalHeight);
            result = result * 31 + Integer.hashCode(this.settingStartY);
            result = result * 31 + Integer.hashCode(this.settingRowHeight);
            result = result * 31 + Boolean.hashCode(this.expanded);
            return result;
        }

        public boolean equals(@Nullable Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof FeatureLayout)) {
                return false;
            }
            FeatureLayout featureLayout = (FeatureLayout)other;
            if (!Intrinsics.areEqual((Object)this.feature, (Object)featureLayout.feature)) {
                return false;
            }
            if (this.x != featureLayout.x) {
                return false;
            }
            if (this.y != featureLayout.y) {
                return false;
            }
            if (this.width != featureLayout.width) {
                return false;
            }
            if (this.headerHeight != featureLayout.headerHeight) {
                return false;
            }
            if (this.totalHeight != featureLayout.totalHeight) {
                return false;
            }
            if (this.settingStartY != featureLayout.settingStartY) {
                return false;
            }
            if (this.settingRowHeight != featureLayout.settingRowHeight) {
                return false;
            }
            return this.expanded == featureLayout.expanded;
        }
    }
}
