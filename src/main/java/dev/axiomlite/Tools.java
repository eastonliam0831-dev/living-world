package dev.axiomlite;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** All edits run on the integrated server thread (singleplayer), so this stays 100% client-installed. */
public final class Tools {
    public enum Tool { DIG, BUILD, PAINT, SMOOTH, MARQUEE }

    record Edit(List<BlockPos> pos, List<BlockState> before, List<BlockState> after) {}
    interface Job { void run(ServerLevel l, Map<BlockPos, BlockState> out); }

    static final Deque<Edit> UNDO = new ArrayDeque<>(), REDO = new ArrayDeque<>();
    static Map<BlockPos, BlockState> clipboard = new HashMap<>();
    static final long MAX_BOX = 2_000_000L;

    static void msg(Minecraft mc, String s) {
        mc.execute(() -> { if (mc.player != null) mc.player.displayClientMessage(Component.literal(s), true); });
    }

    static void submit(Minecraft mc, Job job) {
        MinecraftServer srv = mc.getSingleplayerServer();
        if (srv == null || mc.level == null) return;
        var dim = mc.level.dimension();
        srv.execute(() -> {
            ServerLevel l = srv.getLevel(dim);
            if (l == null) return;
            Map<BlockPos, BlockState> out = new LinkedHashMap<>();
            job.run(l, out);
            commit(l, out);
        });
    }

    static void commit(ServerLevel l, Map<BlockPos, BlockState> out) {
        List<BlockPos> p = new ArrayList<>(); List<BlockState> b = new ArrayList<>(), a = new ArrayList<>();
        for (var e : out.entrySet()) {
            BlockState old = l.getBlockState(e.getKey());
            if (old == e.getValue()) continue;
            p.add(e.getKey()); b.add(old); a.add(e.getValue());
            l.setBlock(e.getKey(), e.getValue(), Block.UPDATE_CLIENTS);
        }
        if (p.isEmpty()) return;
        UNDO.push(new Edit(p, b, a));
        REDO.clear();
        while (UNDO.size() > 50) UNDO.removeLast();
    }

    static void replay(Minecraft mc, boolean undo) {
        MinecraftServer srv = mc.getSingleplayerServer();
        if (srv == null || mc.level == null) return;
        var dim = mc.level.dimension();
        srv.execute(() -> {
            ServerLevel l = srv.getLevel(dim);
            Deque<Edit> from = undo ? UNDO : REDO, to = undo ? REDO : UNDO;
            if (l == null || from.isEmpty()) { msg(mc, "Nothing to " + (undo ? "undo" : "redo")); return; }
            Edit e = from.pop();
            List<BlockState> states = undo ? e.before() : e.after();
            for (int i = 0; i < e.pos().size(); i++) l.setBlock(e.pos().get(i), states.get(i), Block.UPDATE_CLIENTS);
            to.push(e);
            msg(mc, (undo ? "Undid " : "Redid ") + e.pos().size() + " blocks");
        });
    }

    static void sphere(BlockPos c, int r, Consumer<BlockPos> f) {
        int rr = r * r + r;
        for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++)
            if (x * x + y * y + z * z <= rr) f.accept(new BlockPos(c.getX() + x, c.getY() + y, c.getZ() + z));
    }

    static boolean exposed(ServerLevel l, BlockPos p) {
        for (Direction d : Direction.values()) if (l.getBlockState(p.relative(d)).isAir()) return true;
        return false;
    }

    static void brush(Minecraft mc, Tool tool, BlockHitResult hit, int r, BlockState hand) {
        BlockPos c = hit.getBlockPos();
        BlockPos center = tool == Tool.BUILD ? c.relative(hit.getDirection()) : c;
        BlockState air = Blocks.AIR.defaultBlockState();
        submit(mc, (l, out) -> sphere(center, r, p -> {
            if (!l.isInWorldBounds(p)) return;
            BlockState s = l.getBlockState(p);
            switch (tool) {
                case DIG -> { if (!s.isAir() && !s.is(Blocks.BEDROCK)) out.put(p, air); }
                case BUILD -> { if (s.isAir() || !s.getFluidState().isEmpty()) out.put(p, hand); }
                case PAINT -> { if (!s.isAir() && !s.is(Blocks.BEDROCK) && exposed(l, p)) out.put(p, hand); }
                case SMOOTH -> {
                    int n = 0; BlockState any = null;
                    for (Direction d : Direction.values()) {
                        BlockState ns = l.getBlockState(p.relative(d));
                        if (!ns.isAir()) { n++; any = ns; }
                    }
                    if (s.isAir() && n >= 4 && any != null) out.put(p, any);
                    else if (!s.isAir() && !s.is(Blocks.BEDROCK) && n <= 2) out.put(p, air);
                }
                default -> {}
            }
        }));
    }

    static void forBox(BlockPos a, BlockPos b, Consumer<BlockPos> f) {
        for (int x = Math.min(a.getX(), b.getX()); x <= Math.max(a.getX(), b.getX()); x++)
            for (int y = Math.min(a.getY(), b.getY()); y <= Math.max(a.getY(), b.getY()); y++)
                for (int z = Math.min(a.getZ(), b.getZ()); z <= Math.max(a.getZ(), b.getZ()); z++)
                    f.accept(new BlockPos(x, y, z));
    }

    static boolean tooBig(Minecraft mc, BlockPos a, BlockPos b) {
        long v = (long) (Math.abs(a.getX() - b.getX()) + 1) * (Math.abs(a.getY() - b.getY()) + 1) * (Math.abs(a.getZ() - b.getZ()) + 1);
        if (v > MAX_BOX) { msg(mc, "Selection too large (" + v + " blocks, max " + MAX_BOX + ")"); return true; }
        return false;
    }

    static void fill(Minecraft mc, BlockPos a, BlockPos b, BlockState state) {
        if (tooBig(mc, a, b)) return;
        submit(mc, (l, out) -> forBox(a, b, p -> { if (l.isInWorldBounds(p)) out.put(p, state); }));
    }

    static void copy(Minecraft mc, BlockPos a, BlockPos b) {
        if (tooBig(mc, a, b)) return;
        BlockPos min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        submit(mc, (l, out) -> {
            Map<BlockPos, BlockState> c = new HashMap<>();
            forBox(a, b, p -> { BlockState s = l.getBlockState(p); if (!s.isAir()) c.put(p.subtract(min), s); });
            clipboard = c;
            msg(mc, "Copied " + c.size() + " blocks");
        });
    }

    static void paste(Minecraft mc, BlockPos origin) {
        if (clipboard.isEmpty()) { msg(mc, "Clipboard empty"); return; }
        Map<BlockPos, BlockState> c = clipboard;
        submit(mc, (l, out) -> c.forEach((rel, s) -> {
            BlockPos p = origin.offset(rel);
            if (l.isInWorldBounds(p)) out.put(p, s);
        }));
    }
}
