package dev.axiomlite;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public class AxiomLiteClient implements ClientModInitializer {
    static boolean active, wasAttack, wasUse;
    static Tools.Tool tool = Tools.Tool.DIG;
    static int radius = 6, ticks;
    static BlockPos pos1, pos2;
    static KeyMapping toggle, next, rUp, rDown, undo, redo, copy, paste, fill, clear;

    static KeyMapping key(String n, int k, KeyMapping.Category cat) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.axiomlite." + n, InputConstants.Type.KEYSYM, k, cat));
    }

    @Override
    public void onInitializeClient() {
        var cat = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("axiomlite", "main"));
        toggle = key("toggle", GLFW.GLFW_KEY_BACKSLASH, cat);
        next = key("next", GLFW.GLFW_KEY_V, cat);
        rUp = key("rup", GLFW.GLFW_KEY_RIGHT_BRACKET, cat);
        rDown = key("rdown", GLFW.GLFW_KEY_LEFT_BRACKET, cat);
        undo = key("undo", GLFW.GLFW_KEY_Z, cat);
        redo = key("redo", GLFW.GLFW_KEY_X, cat);
        copy = key("copy", GLFW.GLFW_KEY_C, cat);
        paste = key("paste", GLFW.GLFW_KEY_B, cat);
        fill = key("fill", GLFW.GLFW_KEY_G, cat);
        clear = key("clear", GLFW.GLFW_KEY_H, cat);

        ClientTickEvents.END_CLIENT_TICK.register(AxiomLiteClient::onTick);
        ClientPreAttackCallback.EVENT.register((c, p, n) -> active);
        UseBlockCallback.EVENT.register((p, w, h, r) -> active && w.isClientSide() ? InteractionResult.FAIL : InteractionResult.PASS);
        UseItemCallback.EVENT.register((p, w, h) -> active && w.isClientSide() ? InteractionResult.FAIL : InteractionResult.PASS);
        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS,
                Identifier.fromNamespaceAndPath("axiomlite", "panel"), AxiomLiteClient::hud);
    }

    static void setActive(Minecraft mc, boolean on) {
        if (on) {
            if (mc.getSingleplayerServer() == null) { Tools.msg(mc, "Axiom Lite is singleplayer-only (client can't edit servers)"); return; }
            if (!mc.player.getAbilities().mayfly) { Tools.msg(mc, "Switch to creative mode first"); return; }
            mc.player.getAbilities().flying = true;
            mc.player.getAbilities().setFlyingSpeed(0.15f);
        } else {
            mc.player.getAbilities().setFlyingSpeed(0.05f);
        }
        active = on;
    }

    static BlockState hand(Minecraft mc) {
        var s = mc.player.getMainHandItem();
        return s.getItem() instanceof BlockItem b ? b.getBlock().defaultBlockState() : Blocks.STONE.defaultBlockState();
    }

    static BlockHitResult ray(Minecraft mc) {
        Vec3 eye = mc.player.getEyePosition();
        Vec3 end = eye.add(mc.player.getLookAngle().scale(128));
        HitResult r = mc.level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
        return r instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK ? b : null;
    }

    static void drain() {
        for (KeyMapping k : new KeyMapping[]{next, rUp, rDown, undo, redo, copy, paste, fill, clear})
            while (k.consumeClick()) { }
    }

    static void onTick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { active = false; return; }
        while (toggle.consumeClick()) setActive(mc, !active);
        if (!active) { drain(); return; }
        ticks++;

        while (next.consumeClick()) tool = Tools.Tool.values()[(tool.ordinal() + 1) % Tools.Tool.values().length];
        while (rUp.consumeClick()) radius = Math.min(32, radius + 1);
        while (rDown.consumeClick()) radius = Math.max(1, radius - 1);
        while (undo.consumeClick()) Tools.replay(mc, true);
        while (redo.consumeClick()) Tools.replay(mc, false);

        BlockHitResult hit = ray(mc);
        BlockState hand = hand(mc);
        boolean selOk = pos1 != null && pos2 != null;
        while (copy.consumeClick()) { if (selOk) Tools.copy(mc, pos1, pos2); }
        while (fill.consumeClick()) { if (selOk) Tools.fill(mc, pos1, pos2, hand); }
        while (clear.consumeClick()) { if (selOk) Tools.fill(mc, pos1, pos2, Blocks.AIR.defaultBlockState()); }
        while (paste.consumeClick()) { if (hit != null) Tools.paste(mc, hit.getBlockPos().above()); }

        boolean free = mc.screen == null && mc.mouseHandler.isMouseGrabbed();
        boolean atk = free && mc.options.keyAttack.isDown();
        boolean use = free && mc.options.keyUse.isDown();

        if (hit != null) {
            if (tool == Tools.Tool.MARQUEE) {
                if (atk && !wasAttack) pos1 = hit.getBlockPos();
                if (use && !wasUse) pos2 = hit.getBlockPos();
            } else if (ticks % 3 == 0) {
                if (atk) Tools.brush(mc, tool, hit, radius, hand);
                else if (use) {
                    if (tool == Tools.Tool.DIG) Tools.brush(mc, Tools.Tool.BUILD, hit, radius, hand);
                    else if (tool == Tools.Tool.BUILD) Tools.brush(mc, Tools.Tool.DIG, hit, radius, hand);
                }
            }
            if (ticks % 4 == 0 && tool != Tools.Tool.MARQUEE) preview(mc, hit.getBlockPos());
        }
        wasAttack = atk; wasUse = use;
    }

    /** Cheap sphere preview: three particle rings at the cursor. */
    static void preview(Minecraft mc, BlockPos c) {
        double cx = c.getX() + 0.5, cy = c.getY() + 0.5, cz = c.getZ() + 0.5;
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI * 2 / 24, s = Math.sin(a) * radius, co = Math.cos(a) * radius;
            mc.level.addParticle(ParticleTypes.END_ROD, cx + co, cy, cz + s, 0, 0, 0);
            mc.level.addParticle(ParticleTypes.END_ROD, cx + co, cy + s, cz, 0, 0, 0);
            mc.level.addParticle(ParticleTypes.END_ROD, cx, cy + co, cz + s, 0, 0, 0);
        }
    }

    static void hud(GuiGraphics g, net.minecraft.client.DeltaTracker dt) {
        if (!active) return;
        var mc = Minecraft.getInstance();
        int x = 4, y = 30, w = 150, h = 22 + Tools.Tool.values().length * 12 + 40;
        g.fill(x, y, x + w, y + h, 0xCC1E1E1E);
        g.fill(x, y, x + w, y + 14, 0xFF2D2D2D);
        g.drawString(mc.font, "Axiom Lite - Editor", x + 4, y + 3, 0xFFFFFFFF);
        int ty = y + 20;
        for (Tools.Tool t : Tools.Tool.values()) {
            boolean sel = t == tool;
            if (sel) g.fill(x + 2, ty - 1, x + w - 2, ty + 10, 0xFF3C8527);
            g.drawString(mc.font, t.name(), x + 8, ty, sel ? 0xFFFFFFFF : 0xFFAAAAAA);
            ty += 12;
        }
        g.drawString(mc.font, "Radius: " + radius + "  ([ ])", x + 6, ty + 4, 0xFFFFFFFF);
        String sel = (pos1 != null && pos2 != null) ? "Sel: set" : "Sel: L/R click";
        g.drawString(mc.font, tool == Tools.Tool.MARQUEE ? sel : "Undo Z  Redo X", x + 6, ty + 16, 0xFFAAAAAA);
    }
}
