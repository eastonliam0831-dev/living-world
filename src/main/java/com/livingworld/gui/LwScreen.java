package com.livingworld.gui;

import com.livingworld.LivingWorldClient;
import com.livingworld.audio.AmbientSounds;
import com.livingworld.config.ConfigManager;
import com.livingworld.config.Presets;
import com.livingworld.config.Setting;
import com.livingworld.config.Settings;
import com.livingworld.core.Wind;
import com.livingworld.core.WorldProbe.Snapshot;
import com.livingworld.particles.ParticleDirector;
import com.livingworld.performance.PerformanceManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * The Living World Ultimate control centre. Layout: permanent sidebar, search + filter bar, paged settings rows.
 * Every row is generated from the Settings registry, so no control can exist without a working setting behind it.
 */
public final class LwScreen extends Screen {
    private static final int SIDE_W = 124;
    private static final int C_BG = 0xFF0C0F13, C_SIDE = 0xFF11151A, C_PANEL = 0xFF171C23, C_LINE = 0xFF263038;
    private static final int C_GREEN = 0xFF3DDC84, C_BLUE = 0xFF5AA9FF, C_TEXT = 0xFFE6EDF3, C_DIM = 0xFF8B98A5, C_OFF = 0xFF3A444E;
    private static final List<String> PAGES = new ArrayList<>();
    static {
        PAGES.add("Dashboard");
        PAGES.addAll(Settings.CATEGORIES);
        PAGES.add("Profiles");
        PAGES.add("About");
    }

    private enum Filter {
        ALL("All"), ENABLED("Enabled"), DISABLED("Disabled"), MODIFIED("Modified"),
        PERF("Perf heavy"), VISUAL("Visual"), AUDIO("Audio");
        final String label;
        Filter(String l) { label = l; }
        boolean test(Setting s) {
            return switch (this) {
                case ALL -> true;
                case ENABLED -> s.kind == Setting.Kind.TOGGLE ? s.on() : s.get() > s.min;
                case DISABLED -> s.kind == Setting.Kind.TOGGLE && !s.on();
                case MODIFIED -> s.modified();
                case PERF -> s.tags.contains(Setting.Tag.PERFORMANCE);
                case VISUAL -> s.tags.contains(Setting.Tag.VISUAL);
                case AUDIO -> s.tags.contains(Setting.Tag.AUDIO);
            };
        }
        Filter next() { return values()[(ordinal() + 1) % values().length]; }
    }

    private interface Builder { void build(int x, int y, int w); }
    private record Row(String label, Setting setting, Builder builder) {}

    private final Screen parent;
    private String category = "Dashboard";
    private String searchText = "";
    private Filter filter = Filter.ALL;
    private String nameText = "My Profile";
    private String status = "";
    private int page, pages = 1, selUser, selPremade;
    private long confirmUntil;

    private final List<Button> sideButtons = new ArrayList<>();
    private final List<AbstractWidget> rowWidgets = new ArrayList<>();
    private final List<Row> visibleRows = new ArrayList<>();
    private final List<Integer> visibleY = new ArrayList<>();
    private EditBox search;
    private Button filterButton;

    public LwScreen(Screen parent) {
        super(Component.literal("Living World Ultimate"));
        this.parent = parent;
    }

    // ------------------------------------------------------------------ setup

    @Override
    protected void init() {
        sideButtons.clear();
        rowWidgets.clear();

        search = new EditBox(this.font, SIDE_W + 10, 8, 170, 18, Component.literal("Search"));
        search.setHint(Component.literal("Search settings..."));
        search.setValue(searchText);
        search.setResponder(t -> { searchText = t; page = 0; rebuildRows(); });
        addRenderableWidget(search);

        filterButton = Button.builder(Component.literal("Filter: " + filter.label), b -> {
            filter = filter.next(); page = 0; rebuildRows();
        }).bounds(SIDE_W + 186, 8, 100, 18).build();
        addRenderableWidget(filterButton);

        int y = 40;
        for (String c : PAGES) {
            final String cat = c;
            Button b = Button.builder(Component.literal(c), bt -> {
                category = cat; searchText = ""; search.setValue(""); filter = Filter.ALL; page = 0; status = "";
                rebuildRows();
            }).bounds(8, y, SIDE_W - 14, 18).build();
            sideButtons.add(b);
            addRenderableWidget(b);
            y += 20;
        }

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(width - 84, height - 26, 70, 20).build());
        rebuildRows();
    }

    private <T extends AbstractWidget> T put(T w) {
        addRenderableWidget(w);
        rowWidgets.add(w);
        return w;
    }

    private boolean globalSearch() { return !searchText.trim().isEmpty() || filter != Filter.ALL; }

    private List<Row> settingsRows() {
        List<Row> rows = new ArrayList<>();
        String q = searchText.trim().toLowerCase();
        for (Setting s : Settings.ALL.values()) {
            if (!globalSearch() && !s.category.equals(category)) continue;
            if (!q.isEmpty() && !(s.label.toLowerCase().contains(q) || s.category.toLowerCase().contains(q)
                    || s.id.toLowerCase().contains(q))) continue;
            if (!filter.test(s)) continue;
            String label = globalSearch() ? s.label + "  [" + s.category + "]" : s.label;
            rows.add(new Row(label, s, (x, yy, w) -> addControl(s, x, yy, w)));
        }
        return rows;
    }

    private void backup() { ConfigManager.saveProfile("autosave-before-change"); }

    private void afterBulkChange(String msg) {
        ConfigManager.markDirty();
        status = msg;
        rebuildRows();
    }

    private List<Row> profileRows() {
        List<Row> rows = new ArrayList<>();
        List<String> users = ConfigManager.profiles();
        List<String> premade = new ArrayList<>(Presets.PREMADE.keySet());
        selUser = users.isEmpty() ? 0 : Mth.clamp(selUser, 0, users.size() - 1);
        selPremade = Mth.clamp(selPremade, 0, premade.size() - 1);
        final String sel = users.isEmpty() ? null : users.get(selUser);

        rows.add(new Row("Profile name", null, (x, y, w) -> {
            EditBox nb = new EditBox(this.font, x, y, w, 20, Component.literal("Profile name"));
            nb.setMaxLength(40);
            nb.setValue(nameText);
            nb.setResponder(t -> nameText = t);
            put(nb);
        }));
        rows.add(new Row("Save current settings under that name", null, (x, y, w) -> put(
                Button.builder(Component.literal("Save"), b -> {
                    status = ConfigManager.saveProfile(nameText) ? "Saved profile." : "Enter a valid name first.";
                    rebuildRows();
                }).bounds(x, y, w, 20).build())));
        rows.add(new Row("Your saved profile", null, (x, y, w) -> put(
                Button.builder(Component.literal(sel == null ? "(none saved yet)" : sel), b -> {
                    if (!users.isEmpty()) selUser = (selUser + 1) % users.size();
                    rebuildRows();
                }).bounds(x, y, w, 20).build())));
        rows.add(new Row("Load that profile", null, (x, y, w) -> put(
                Button.builder(Component.literal("Load"), b -> {
                    if (sel == null) { status = "Nothing to load."; rebuildRows(); return; }
                    backup();
                    afterBulkChange(ConfigManager.loadProfile(sel) ? "Loaded " + sel + "." : "Could not load.");
                }).bounds(x, y, w, 20).build())));
        rows.add(new Row("Delete that profile", null, (x, y, w) -> put(
                Button.builder(Component.literal("Delete"), b -> {
                    status = sel != null && ConfigManager.deleteProfile(sel) ? "Deleted." : "Nothing deleted.";
                    rebuildRows();
                }).bounds(x, y, w, 20).build())));
        rows.add(new Row("Duplicate that profile to the name above", null, (x, y, w) -> put(
                Button.builder(Component.literal("Duplicate"), b -> {
                    status = sel != null && ConfigManager.copyProfile(sel, nameText) ? "Duplicated." : "Could not duplicate.";
                    rebuildRows();
                }).bounds(x, y, w, 20).build())));
        rows.add(new Row("Rename that profile to the name above", null, (x, y, w) -> put(
                Button.builder(Component.literal("Rename"), b -> {
                    boolean ok = sel != null && !ConfigManager.sanitize(nameText).equals(sel)
                            && ConfigManager.copyProfile(sel, nameText) && ConfigManager.deleteProfile(sel);
                    status = ok ? "Renamed." : "Could not rename.";
                    rebuildRows();
                }).bounds(x, y, w, 20).build())));
        rows.add(new Row("Export current settings to clipboard", null, (x, y, w) -> put(
                Button.builder(Component.literal("Export"), b -> {
                    minecraft.keyboardHandler.setClipboard(ConfigManager.toJson());
                    status = "Copied settings JSON to clipboard.";
                }).bounds(x, y, w, 20).build())));
        rows.add(new Row("Import settings JSON from clipboard", null, (x, y, w) -> put(
                Button.builder(Component.literal("Import"), b -> {
                    backup();
                    afterBulkChange(ConfigManager.applyJson(minecraft.keyboardHandler.getClipboard())
                            ? "Imported from clipboard." : "Clipboard does not hold valid settings.");
                }).bounds(x, y, w, 20).build())));
        rows.add(new Row("Premade profile", null, (x, y, w) -> put(
                Button.builder(Component.literal(premade.get(selPremade)), b -> {
                    selPremade = (selPremade + 1) % premade.size();
                    rebuildRows();
                }).bounds(x, y, w, 20).build())));
        rows.add(new Row("Apply premade profile (previous settings are backed up)", null, (x, y, w) -> put(
                Button.builder(Component.literal("Apply"), b -> {
                    backup();
                    Presets.PREMADE.get(premade.get(selPremade)).run();
                    afterBulkChange("Applied " + premade.get(selPremade) + ".");
                }).bounds(x, y, w, 20).build())));
        rows.add(new Row("Reset everything to defaults", null, (x, y, w) -> {
            boolean armed = Util.getMillis() < confirmUntil;
            put(Button.builder(Component.literal(armed ? "Click again to confirm" : "Reset all").withStyle(ChatFormatting.RED), b -> {
                if (Util.getMillis() < confirmUntil) {
                    backup();
                    Settings.resetAll();
                    confirmUntil = 0;
                    afterBulkChange("Everything reset (backup: autosave-before-change).");
                } else {
                    confirmUntil = Util.getMillis() + 4000;
                    rebuildRows();
                }
            }).bounds(x, y, w, 20).build());
        }));
        return rows;
    }

    private void addControl(Setting s, int x, int y, int w) {
        switch (s.kind) {
            case TOGGLE -> {
                Button b = Button.builder(toggleText(s), bt -> {
                    s.toggle(); bt.setMessage(toggleText(s)); changed(s);
                }).bounds(x, y, w, 20).build();
                put(b);
            }
            case CHOICE -> {
                Button b = Button.builder(Component.literal(s.display()), bt -> {
                    s.cycle();
                    if (s == Settings.PRESET && s.index() > 0) { backup(); Presets.applyQuality(s.index()); }
                    else changed(s);
                    ConfigManager.markDirty();
                    rebuildRows();
                }).bounds(x, y, w, 20).build();
                put(b);
            }
            case SLIDER -> put(new SettingSlider(x, y, w, 20, s, () -> changed(s)));
        }
    }

    private static Component toggleText(Setting s) {
        return Component.literal(s.display()).withStyle(s.on() ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    private void changed(Setting s) {
        ConfigManager.markDirty();
        if (s != Settings.PRESET && Settings.PRESET.index() != 0) Settings.PRESET.set(0);
    }

    private void rebuildRows() {
        for (AbstractWidget w : rowWidgets) removeWidget(w);
        rowWidgets.clear();
        visibleRows.clear();
        visibleY.clear();
        if (filterButton != null) filterButton.setMessage(Component.literal("Filter: " + filter.label));

        for (int i = 0; i < sideButtons.size(); i++) {
            boolean cur = !globalSearch() && PAGES.get(i).equals(category);
            sideButtons.get(i).setMessage(Component.literal((cur ? "> " : "  ") + PAGES.get(i)));
        }

        List<Row> rows;
        if (globalSearch()) rows = settingsRows();
        else if (category.equals("Profiles")) rows = profileRows();
        else if (category.equals("About")) rows = List.of();
        else rows = settingsRows();

        int top = 62, bottom = height - 40, rowH = 24;
        int perPage = Math.max(1, (bottom - top) / rowH);
        pages = Math.max(1, (rows.size() + perPage - 1) / perPage);
        page = Mth.clamp(page, 0, pages - 1);

        int ctrlW = Math.min(170, (width - SIDE_W - 28) / 2);
        int ctrlX = width - 14 - 24 - ctrlW;
        int start = page * perPage;
        for (int i = start; i < Math.min(rows.size(), start + perPage); i++) {
            Row r = rows.get(i);
            int y = top + (i - start) * rowH;
            visibleRows.add(r);
            visibleY.add(y);
            r.builder().build(ctrlX, y, ctrlW);
            if (r.setting() != null) {
                final Setting s = r.setting();
                put(Button.builder(Component.literal("R"), b -> { s.reset(); changed(s); rebuildRows(); })
                        .bounds(width - 14 - 20, y, 20, 20).build());
            }
        }

        if (pages > 1) {
            put(Button.builder(Component.literal("<"), b -> { page = Math.max(0, page - 1); rebuildRows(); })
                    .bounds(SIDE_W + 14, height - 26, 20, 20).build());
            put(Button.builder(Component.literal(">"), b -> { page = Math.min(pages - 1, page + 1); rebuildRows(); })
                    .bounds(SIDE_W + 62, height - 26, 20, 20).build());
        }
        if (!globalSearch() && !category.equals("Profiles") && !category.equals("About") && !category.equals("Dashboard")) {
            put(Button.builder(Component.literal("Reset page"), b -> {
                backup(); Settings.resetCategory(category); afterBulkChange("Reset " + category + ".");
            }).bounds(SIDE_W + 90, height - 26, 80, 20).build());
        }
    }

    // ------------------------------------------------------------------ drawing

    // No @Override on purpose: if the background hook is named differently in a given Minecraft build, the
    // vanilla background is used and everything still works.
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, C_BG);
        g.fill(0, 0, SIDE_W, height, C_SIDE);
        g.fill(SIDE_W, 0, SIDE_W + 1, height, C_LINE);
        g.fill(SIDE_W + 1, 32, width, 33, C_LINE);
        int pulse = (int) (110 + 70 * Math.sin(Util.getMillis() / 600.0));
        g.fill(0, 0, SIDE_W, 2, (pulse << 24) | 0x3DDC84);
        g.fill(SIDE_W + 8, 54, width - 8, height - 34, C_PANEL);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        g.text(this.font, "LIVING WORLD", 12, 10, C_GREEN, true);
        g.text(this.font, "ULTIMATE", 12, 21, C_BLUE, true);

        String heading = globalSearch() ? "Search results (" + countRows() + ")" : category;
        g.text(this.font, heading, SIDE_W + 14, 40, C_GREEN, true);

        for (int i = 0; i < visibleRows.size(); i++) {
            Row r = visibleRows.get(i);
            int y = visibleY.get(i);
            boolean mod = r.setting() != null && r.setting().modified();
            if (mod) g.fill(SIDE_W + 12, y + 7, SIDE_W + 15, y + 12, C_GREEN);
            g.text(this.font, r.label(), SIDE_W + 20, y + 6, C_TEXT, false);
        }

        if (!globalSearch() && category.equals("Dashboard")) drawDashboard(g);
        if (!globalSearch() && category.equals("About")) drawAbout(g);

        if (pages > 1)
            g.text(this.font, (page + 1) + " / " + pages, SIDE_W + 38, height - 20, C_DIM, false);
        if (!status.isEmpty())
            g.text(this.font, status, SIDE_W + 180, height - 20, C_BLUE, false);
    }

    private int countRows() {
        int n = 0;
        String q = searchText.trim().toLowerCase();
        for (Setting s : Settings.ALL.values())
            if ((q.isEmpty() || s.label.toLowerCase().contains(q) || s.category.toLowerCase().contains(q)
                    || s.id.toLowerCase().contains(q)) && filter.test(s)) n++;
        return n;
    }

    private void drawDashboard(GuiGraphicsExtractor g) {
        int x = SIDE_W + 20, y = 62 + 2 * 24 + 8;
        Snapshot s = LivingWorldClient.snapshot;
        String mod = FabricLoader.getInstance().getModContainer(LivingWorldClient.ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
        String mc = FabricLoader.getInstance().getModContainer("minecraft")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
        List<String> lines = new ArrayList<>();
        lines.add("Mod " + mod + "   Minecraft " + mc + "   Preset: " + Settings.PRESET.display());
        if (s == null) {
            lines.add("Join an Overworld world to see live data.");
        } else {
            String weather = s.thunder() > 0.5f ? "Thunderstorm" : s.rain() > 0.1f ? (s.cold() ? "Snow" : "Rain") : "Clear";
            String time = s.night() > 0.5f ? "Night" : s.sunset() > 0.4f ? "Sunrise/Sunset" : "Day";
            lines.add("Environment: " + (s.underwater() ? "Underwater" : s.cave() ? (s.lush() ? "Lush cave" : "Cave") : s.area().label)
                    + "   Weather: " + weather + "   Time: " + time);
            lines.add("Wind " + String.format("%.2f", Wind.intensity) + (Wind.gusting() ? " (gust)" : "")
                    + "   Season: " + s.season().label + "   Sound profile: " + AmbientSounds.profile);
        }
        lines.add("Particles/s: " + ParticleDirector.perSecond + "   Quality factor: "
                + (int) (PerformanceManager.factor() * 100) + "%   FPS: " + minecraft.getFps());
        lines.add("Config: " + (ConfigManager.isDirty() ? "unsaved changes (autosaves)" : "saved"));
        for (String l : lines) { g.text(this.font, l, x, y, C_DIM, false); y += 11; }

        y += 6;
        Object[][] ind = {
                {"Sounds", Settings.SOUND_MASTER.get() > 0f}, {"Wind", Settings.WIND_ON.on()},
                {"Particles", Settings.PART_MASTER.on()}, {"Grading", Settings.GRADE_ON.on()},
                {"Droplets", Settings.DROPLETS.on()}, {"Lightning", Settings.FLASH.on()},
                {"Seasons", Settings.SEASON.index() != 0}};
        int ix = x;
        for (Object[] o : ind) {
            boolean on = (Boolean) o[1] && Settings.MASTER.on();
            g.fill(ix, y, ix + 7, y + 7, on ? C_GREEN : C_OFF);
            g.text(this.font, (String) o[0], ix + 11, y, on ? C_TEXT : C_DIM, false);
            ix += 11 + this.font.width((String) o[0]) + 12;
            if (ix > width - 80) { ix = x; y += 12; }
        }
    }

    private void drawAbout(GuiGraphicsExtractor g) {
        int x = SIDE_W + 20, y = 70;
        String[] lines = {
                "Living World Ultimate is a client-side environment mod.",
                "It adds no mobs, animals or entities and needs nothing on the server.",
                "",
                "Working systems: ambient soundscapes, dynamic wind, extra weather",
                "particles, seasons, colour grading, rain droplets, lightning flash,",
                "per-environment toggles, profiles and performance scaling.",
                "",
                "Not implemented (no honest client-only hook yet): god rays, fog",
                "distance control, vegetation animation, sky and cloud changes,",
                "dynamic held-item light, water reflections, camera bob.",
                "",
                "Config files: config/livingworld/"};
        for (String l : lines) { g.text(this.font, l, x, y, C_DIM, false); y += 11; }
    }

    // ------------------------------------------------------------------ closing

    @Override
    public void onClose() {
        ConfigManager.save();
        this.minecraft.gui.setScreen(parent);
    }
}
