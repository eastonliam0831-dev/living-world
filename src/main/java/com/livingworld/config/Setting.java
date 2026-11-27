package com.livingworld.config;

import java.util.Locale;
import java.util.Set;

/** One user-facing setting. The GUI, search, profiles and persistence are all driven from these objects. */
public final class Setting {
    public enum Kind { TOGGLE, SLIDER, CHOICE }
    public enum Tag { VISUAL, AUDIO, PERFORMANCE }

    public final String id, category, label;
    public final Kind kind;
    public final float min, max, def, step;
    public final String[] choices;
    public final Set<Tag> tags;
    private float value;

    Setting(String id, String category, String label, Kind kind, float min, float max, float def,
            float step, String[] choices, Set<Tag> tags) {
        this.id = id; this.category = category; this.label = label; this.kind = kind;
        this.min = min; this.max = max; this.def = def; this.step = step;
        this.choices = choices; this.tags = tags; this.value = def;
    }

    public float get() { return value; }
    public boolean on() { return value >= 0.5f; }
    public int index() { return Math.round(value); }

    public void set(float v) {
        float c = Math.max(min, Math.min(max, v));
        if (step > 0) c = min + Math.round((c - min) / step) * step;
        value = Math.max(min, Math.min(max, c));
    }

    public void toggle() { set(on() ? 0 : 1); }
    public void cycle() { set((index() + 1) % choices.length); }
    public boolean modified() { return Math.abs(value - def) > 1e-4f; }
    public void reset() { value = def; }
    public float norm() { return (value - min) / (max - min); }

    public String display() {
        return switch (kind) {
            case TOGGLE -> on() ? "ON" : "OFF";
            case CHOICE -> choices[Math.max(0, Math.min(choices.length - 1, index()))];
            case SLIDER -> step >= 1f ? Integer.toString(Math.round(value))
                                      : String.format(Locale.ROOT, "%.2f", value);
        };
    }
}
