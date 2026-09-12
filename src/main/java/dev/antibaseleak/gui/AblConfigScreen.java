package dev.antibaseleak.gui;

import dev.antibaseleak.FeatureStatus;
import dev.antibaseleak.config.AblConfig;
import dev.antibaseleak.config.CensorStyle;
import dev.antibaseleak.config.CoverMode;
import dev.antibaseleak.config.MaskMode;
import dev.antibaseleak.mask.BedrockMask;
import dev.antibaseleak.mask.WorldMasker;
import dev.antibaseleak.render.BlurCensor;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The mod's menu (F7 by default). Drawn by hand, with no dependency on ModMenu or
 * Cloth Config - a single class that compiles the same way on 1.21.4 and 1.21.11.
 */
public class AblConfigScreen extends Screen {

    private static final int ROW_HEIGHT = 18;
    private static final int PANEL_WIDTH = 400;

    private static final int COLOR_ON = 0xFF55FF55;
    private static final int COLOR_OFF = 0xFFFF5555;
    private static final int COLOR_ENUM = 0xFFFFD24A;
    private static final int COLOR_HEADER = 0xFF8AB4FF;
    private static final int COLOR_LABEL = 0xFFE6E6E6;
    private static final int COLOR_HINT = 0xFFA0A0A0;

    private static final String[] FIXED_BLOCKS = {
            "minecraft:stone", "minecraft:deepslate", "minecraft:netherrack",
            "minecraft:end_stone", "minecraft:obsidian", "minecraft:blackstone",
            "minecraft:basalt", "minecraft:dirt"
    };

    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();
    private int scroll;
    private int hoveredRow = -1;

    public AblConfigScreen(Screen parent) {
        super(Text.literal("AntiBaseLeak"));
        this.parent = parent;
    }

    private static String tr(String key) {
        return Text.translatable(key).getString();
    }

    // ─── building the list ──────────────────────────────────────────────────

    @Override
    protected void init() {
        rows.clear();
        AblConfig cfg = AblConfig.get();

        header("antibaseleak.section.bedrock");
        toggle("antibaseleak.option.mask", () -> cfg.maskBedrock, () -> {
            cfg.maskBedrock = !cfg.maskBedrock;
            applyMaskChange();
        });
        cycle("antibaseleak.option.mode", () -> cfg.maskMode.name() + (cfg.maskMode == MaskMode.AUTO
                ? " (" + BedrockMask.effectiveMode().name() + ")" : ""), () -> {
            cfg.maskMode = next(MaskMode.values(), cfg.maskMode);
            applyMaskChange();
        });
        cycle("antibaseleak.option.cover", () -> cfg.coverMode.name(), () -> {
            cfg.coverMode = next(CoverMode.values(), cfg.coverMode);
            applyMaskChange();
        });
        cycle("antibaseleak.option.radius", () -> String.valueOf(cfg.searchRadius), () -> {
            cfg.searchRadius = cfg.searchRadius >= 4 ? 1 : cfg.searchRadius + 1;
            applyMaskChange();
        });
        cycle("antibaseleak.option.fixed", () -> cfg.fixedBlock.replace("minecraft:", ""), () -> {
            cfg.fixedBlock = nextString(FIXED_BLOCKS, cfg.fixedBlock);
            applyMaskChange();
        });

        header("antibaseleak.section.models");
        toggle("antibaseleak.option.rotation", () -> cfg.disableModelRotation, () -> {
            cfg.disableModelRotation = !cfg.disableModelRotation;
            reloadRenderer();
        });
        toggle("antibaseleak.option.offset", () -> cfg.disableModelOffset, () -> {
            cfg.disableModelOffset = !cfg.disableModelOffset;
            reloadRenderer();
        });

        header("antibaseleak.section.debug");
        toggle("antibaseleak.option.debug", () -> cfg.censorDebug, () -> cfg.censorDebug = !cfg.censorDebug);
        toggle("antibaseleak.option.position", () -> cfg.censorPosition, () -> cfg.censorPosition = !cfg.censorPosition);
        toggle("antibaseleak.option.targetBlock", () -> cfg.censorTargetBlock, () -> cfg.censorTargetBlock = !cfg.censorTargetBlock);
        toggle("antibaseleak.option.targetFluid", () -> cfg.censorTargetFluid, () -> cfg.censorTargetFluid = !cfg.censorTargetFluid);
        toggle("antibaseleak.option.targetEntity", () -> cfg.censorTargetEntity, () -> cfg.censorTargetEntity = !cfg.censorTargetEntity);
        toggle("antibaseleak.option.biome", () -> cfg.censorBiome, () -> cfg.censorBiome = !cfg.censorBiome);
        toggle("antibaseleak.option.dimension", () -> cfg.censorDimension, () -> cfg.censorDimension = !cfg.censorDimension);
        toggle("antibaseleak.option.server", () -> cfg.censorServer, () -> cfg.censorServer = !cfg.censorServer);
        toggle("antibaseleak.option.difficulty", () -> cfg.censorDifficulty, () -> cfg.censorDifficulty = !cfg.censorDifficulty);
        toggle("antibaseleak.option.other", () -> cfg.censorOtherCoords, () -> cfg.censorOtherCoords = !cfg.censorOtherCoords);
        cycle("antibaseleak.option.style", () -> switch (cfg.censorStyle) {
            case MASK -> "MASK (" + cfg.maskText + ")";
            case HIDE -> "HIDE";
            case BLUR -> BlurCensor.isBroken() ? tr("antibaseleak.value.blurBroken") : "BLUR";
        }, () -> cfg.censorStyle = next(CensorStyle.values(), cfg.censorStyle));
        cycle("antibaseleak.option.blurStrength", () -> String.valueOf(cfg.blurStrength),
                () -> cfg.blurStrength = cfg.blurStrength >= 4 ? 1 : cfg.blurStrength + 1);

        header("antibaseleak.section.chat");
        toggle("antibaseleak.option.chat", () -> cfg.censorChat, () -> cfg.censorChat = !cfg.censorChat);

        header("antibaseleak.section.toast");
        toggle("antibaseleak.option.toast", () -> cfg.showJoinToast, () -> cfg.showJoinToast = !cfg.showJoinToast);

        header("antibaseleak.section.status");
        status("antibaseleak.status.debugHud", () -> FeatureStatus.debugHudHooked);
        status("antibaseleak.status.modelSeed", () -> FeatureStatus.modelSeedHooked);
        status("antibaseleak.status.modelOffset", () -> FeatureStatus.modelOffsetHooked);
        status("antibaseleak.status.renderMask", () -> FeatureStatus.renderMaskHooked);
        status("antibaseleak.status.chat", () -> FeatureStatus.chatHooked);
        status("antibaseleak.status.blur", () -> FeatureStatus.blurHooked);
        info("antibaseleak.status.sections", () -> String.valueOf(WorldMasker.maskedSectionCount()));

        header(null);
        action("antibaseleak.option.reset", () -> {
            AblConfig.resetToDefaults();
            applyMaskChange();
            init();
        });
    }

    private void applyMaskChange() {
        AblConfig.save();
        BedrockMask.clearCache();
        WorldMasker.onConfigChanged();
    }

    private void reloadRenderer() {
        AblConfig.save();
        if (this.client != null && this.client.worldRenderer != null) this.client.worldRenderer.reload();
    }

    private static <T extends Enum<T>> T next(T[] values, T current) {
        return values[(current.ordinal() + 1) % values.length];
    }

    private static String nextString(String[] values, String current) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) return values[(i + 1) % values.length];
        }
        return values[0];
    }

    // ─── drawing ────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int panelLeft = (this.width - PANEL_WIDTH) / 2;
        int panelRight = panelLeft + PANEL_WIDTH;
        int top = 34;
        int bottom = this.height - 30;

        context.fill(panelLeft, top, panelRight, bottom, 0xB0000000);
        context.fill(panelLeft, top, panelRight, top + 1, 0x40FFFFFF);
        context.fill(panelLeft, bottom - 1, panelRight, bottom, 0x40FFFFFF);

        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("AntiBaseLeak"), this.width / 2, 12, 0xFFFFFFFF);

        this.hoveredRow = -1;
        int visibleHeight = bottom - top;
        int y = top + 4 - scroll;

        context.enableScissor(panelLeft, top + 1, panelRight, bottom - 1);
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int rowTop = y;
            y += ROW_HEIGHT;
            if (rowTop + ROW_HEIGHT < top || rowTop > bottom) continue;

            boolean hover = row.action != null
                    && mouseX >= panelLeft && mouseX <= panelRight
                    && mouseY >= rowTop && mouseY < rowTop + ROW_HEIGHT
                    && mouseY >= top && mouseY <= bottom;
            if (hover) {
                this.hoveredRow = i;
                context.fill(panelLeft + 2, rowTop, panelRight - 2, rowTop + ROW_HEIGHT, 0x30FFFFFF);
            }

            int textY = rowTop + (ROW_HEIGHT - 8) / 2;
            String label = row.label();
            if (row.value == null) {
                // section header
                if (!label.isEmpty()) {
                    context.drawTextWithShadow(this.textRenderer, label, panelLeft + 8, textY, COLOR_HEADER);
                    context.fill(panelLeft + 8, rowTop + ROW_HEIGHT - 3,
                            panelLeft + 8 + this.textRenderer.getWidth(label), rowTop + ROW_HEIGHT - 2, 0x40FFFFFF);
                }
            } else {
                context.drawTextWithShadow(this.textRenderer, label, panelLeft + 12, textY, COLOR_LABEL);
                String value = row.value.get();
                int width = this.textRenderer.getWidth(value);
                context.drawTextWithShadow(this.textRenderer, value, panelRight - 12 - width, textY, row.color.get());
            }
        }
        context.disableScissor();

        String hint = hoveredRow >= 0 && rows.get(hoveredRow).hintKey != null
                ? tr(rows.get(hoveredRow).hintKey)
                : tr("antibaseleak.screen.footer");
        hint = this.textRenderer.trimToWidth(hint, this.width - 16);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(hint), this.width / 2, this.height - 20, COLOR_HINT);

        // scrollbar
        int contentHeight = rows.size() * ROW_HEIGHT + 8;
        if (contentHeight > visibleHeight) {
            int barHeight = Math.max(16, visibleHeight * visibleHeight / contentHeight);
            int barY = top + (visibleHeight - barHeight) * scroll / Math.max(1, maxScroll());
            context.fill(panelRight - 4, barY, panelRight - 2, barY + barHeight, 0x80FFFFFF);
        }
    }

    private int maxScroll() {
        int visibleHeight = (this.height - 30) - 34;
        return Math.max(0, rows.size() * ROW_HEIGHT + 8 - visibleHeight);
    }

    // ─── mouse ──────────────────────────────────────────────────────────────

    // 1.21.9 moved input handling to a Click object.
    //? if >=1.21.9 {
    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        if (handleRowClick(click.button())) return true;
        return super.mouseClicked(click, doubled);
    }
    //?} else {
    /*@Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (handleRowClick(button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }
    *///?}

    private boolean handleRowClick(int button) {
        if (button != 0 || hoveredRow < 0) return false;
        Row row = rows.get(hoveredRow);
        if (row.action == null) return false;
        row.action.run();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) (verticalAmount * ROW_HEIGHT)));
        return true;
    }

    @Override
    public void close() {
        AblConfig.save();
        if (this.client != null) this.client.setScreen(parent);
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private void header(String key) {
        rows.add(new Row(key, null, null, null, () -> COLOR_HEADER));
    }

    private void toggle(String key, BooleanSupplier getter, Runnable toggler) {
        rows.add(new Row(key, key + ".tooltip",
                () -> tr(getter.getAsBoolean() ? "antibaseleak.value.on" : "antibaseleak.value.off"),
                () -> {
                    toggler.run();
                    AblConfig.save();
                },
                () -> getter.getAsBoolean() ? COLOR_ON : COLOR_OFF));
    }

    private void cycle(String key, Supplier<String> value, Runnable action) {
        rows.add(new Row(key, key + ".tooltip", value, () -> {
            action.run();
            AblConfig.save();
        }, () -> COLOR_ENUM));
    }

    private void status(String key, BooleanSupplier getter) {
        rows.add(new Row(key, "antibaseleak.status.tooltip",
                () -> tr(getter.getAsBoolean() ? "antibaseleak.status.active" : "antibaseleak.status.missing"), null,
                () -> getter.getAsBoolean() ? COLOR_ON : COLOR_HINT));
    }

    private void info(String key, Supplier<String> value) {
        rows.add(new Row(key, null, value, null, () -> COLOR_HINT));
    }

    private void action(String key, Runnable action) {
        rows.add(new Row(key, key + ".tooltip", () -> tr("antibaseleak.value.click"), action, () -> COLOR_ENUM));
    }

    /** One list row. A null value marks a section header. */
    private static final class Row {
        final String labelKey;
        final String hintKey;
        final Supplier<String> value;
        final Runnable action;
        final Supplier<Integer> color;

        Row(String labelKey, String hintKey, Supplier<String> value, Runnable action, Supplier<Integer> color) {
            this.labelKey = labelKey;
            this.hintKey = hintKey;
            this.value = value;
            this.action = action;
            this.color = color;
        }

        String label() {
            return labelKey == null ? "" : tr(labelKey);
        }
    }
}
