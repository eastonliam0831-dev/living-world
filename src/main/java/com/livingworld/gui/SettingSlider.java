package com.livingworld.gui;

import com.livingworld.config.ConfigManager;
import com.livingworld.config.Setting;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/** Slider bound directly to a Setting; changes apply live because the engine reads settings every update. */
final class SettingSlider extends AbstractSliderButton {
    private final Setting setting;
    private final Runnable onChange;

    SettingSlider(int x, int y, int w, int h, Setting setting, Runnable onChange) {
        super(x, y, w, h, Component.empty(), setting.norm());
        this.setting = setting;
        this.onChange = onChange;
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        setMessage(Component.literal(setting.display()));
    }

    @Override
    protected void applyValue() {
        setting.set(setting.min + (float) this.value * (setting.max - setting.min));
        ConfigManager.markDirty();
        onChange.run();
    }
}
