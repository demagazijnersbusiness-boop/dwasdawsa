package net.capybarasmp.scoreboard;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Reads Litematica (.litematic) and WorldEdit/Sponge (.schem, v2 and v3) files. Does not touch the world. */
final class SchematicLoader {

    private SchematicLoader() {
    }

    static SchematicData load(File file, long maxVolume) throws IOException {
        Map<String, Object> root;
        try (InputStream in = new FileInputStream(file)) {
            root = NbtReader.read(in);
        }
        String n = file.getName().toLowerCase(Locale.ROOT);
        if (n.endsWith(".litematic")) return fromLitematic(root, maxVolume);
        if (n.endsWith(".schem")) return fromSponge(root, maxVolume);
        throw new IOException("Unknown file type (use .litematic or .schem).");
    }

    private static void checkVolume(long volume, long max) throws IOException {
        if (volume <= 0) throw new IOException("The schematic is empty.");
        if (volume > max) throw new IOException("The schematic is too big (" + volume + " blocks, max " + max + "). Raise schematics.max-blocks in config.yml.");
    }

    /** "minecraft:oak_stairs" + {facing=north, half=bottom} -> "minecraft:oak_stairs[facing=north,half=bottom]" */
    private static String state(Map<String, Object> entry) {
        Object nm = entry.get("Name");
        String name = nm instanceof String s ? s : "minecraft:air";
        Map<String, Object> props = NbtReader.map(entry.get("Properties"));
        if (props == null || props.isEmpty()) return name;
        StringBuilder sb = new StringBuilder(name).append('[');
        boolean first = true;
        for (Map.Entry<String, Object> e : new TreeMap<>(props).entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.append(']').toString();
    }

    // ------------------------------------------------------------ Litematica

    private static SchematicData fromLitematic(Map<String, Object> root, long maxVolume) throws IOException {
        Map<String, Object> regions = NbtReader.map(root.get("Regions"));
        if (regions == null || regions.isEmpty()) throw new IOException("No regions found in this .litematic file.");

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (Object ro : regions.values()) {
            Map<String, Object> r = NbtReader.map(ro);
            Map<String, Object> pos = r == null ? null : NbtReader.map(r.get("Position"));
            Map<String, Object> size = r == null ? null : NbtReader.map(r.get("Size"));
            if (pos == null || size == null) continue;
            int sx = NbtReader.num(size.get("x"));
            int sy = NbtReader.num(size.get("y"));
            int sz = NbtReader.num(size.get("z"));
            int rx = NbtReader.num(pos.get("x")) + (sx < 0 ? sx + 1 : 0);
            int ry = NbtReader.num(pos.get("y")) + (sy < 0 ? sy + 1 : 0);
            int rz = NbtReader.num(pos.get("z")) + (sz < 0 ? sz + 1 : 0);
            minX = Math.min(minX, rx);
            minY = Math.min(minY, ry);
            minZ = Math.min(minZ, rz);
            maxX = Math.max(maxX, rx + Math.abs(sx) - 1);
            maxY = Math.max(maxY, ry + Math.abs(sy) - 1);
            maxZ = Math.max(maxZ, rz + Math.abs(sz) - 1);
        }
        if (minX == Integer.MAX_VALUE) throw new IOException("This .litematic file has no usable region.");
        int gx = maxX - minX + 1;
        int gy = maxY - minY + 1;
        int gz = maxZ - minZ + 1;
        checkVolume((long) gx * gy * gz, maxVolume);

        int[] blocks = new int[gx * gy * gz];
        Arrays.fill(blocks, -1);
        Map<String, Integer> globalPalette = new LinkedHashMap<>();

        for (Object ro : regions.values()) {
            Map<String, Object> r = NbtReader.map(ro);
            if (r == null) continue;
            Map<String, Object> pos = NbtReader.map(r.get("Position"));
            Map<String, Object> size = NbtReader.map(r.get("Size"));
            List<Object> pal = NbtReader.list(r.get("BlockStatePalette"));
            Object bs = r.get("BlockStates");
            if (pos == null || size == null || pal == null || pal.isEmpty() || !(bs instanceof long[] arr)) continue;

            int sx = NbtReader.num(size.get("x"));
            int sy = NbtReader.num(size.get("y"));
            int sz = NbtReader.num(size.get("z"));
            int rsx = Math.abs(sx);
            int rsy = Math.abs(sy);
            int rsz = Math.abs(sz);
            int ox = NbtReader.num(pos.get("x")) + (sx < 0 ? sx + 1 : 0) - minX;
            int oy = NbtReader.num(pos.get("y")) + (sy < 0 ? sy + 1 : 0) - minY;
            int oz = NbtReader.num(pos.get("z")) + (sz < 0 ? sz + 1 : 0) - minZ;

            int[] remap = new int[pal.size()];
            for (int i = 0; i < pal.size(); i++) {
                Map<String, Object> e = NbtReader.map(pal.get(i));
                String st = e == null ? "minecraft:air" : state(e);
                Integer id = globalPalette.get(st);
                if (id == null) {
                    id = globalPalette.size();
                    globalPalette.put(st, id);
                }
                remap[i] = id;
            }

            int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(pal.size() - 1));
            long mask = (1L << bits) - 1L;
            long total = (long) rsx * rsy * rsz;
            for (int i = 0; i < total; i++) {
                long bitIndex = (long) i * bits;
                int li = (int) (bitIndex >>> 6);
                int off = (int) (bitIndex & 63);
                if (li >= arr.length) break;
                long v = arr[li] >>> off;
                if (off + bits > 64 && li + 1 < arr.length) v |= arr[li + 1] << (64 - off);
                int pi = (int) (v & mask);
                if (pi >= remap.length) continue;
                int x = i % rsx;
                int z = (i / rsx) % rsz;
                int y = i / (rsx * rsz);
                blocks[((y + oy) * gz + (z + oz)) * gx + (x + ox)] = remap[pi];
            }
        }
        return new SchematicData(gx, gy, gz, globalPalette.keySet().toArray(new String[0]), blocks);
    }

    // ------------------------------------------------------------ WorldEdit / Sponge .schem (v2 + v3)

    private static SchematicData fromSponge(Map<String, Object> root, long maxVolume) throws IOException {
        Map<String, Object> s = root.containsKey("Schematic") ? NbtReader.map(root.get("Schematic")) : root;
        if (s == null) throw new IOException("Broken .schem file.");
        int w = NbtReader.num(s.get("Width"));
        int h = NbtReader.num(s.get("Height"));
        int l = NbtReader.num(s.get("Length"));
        checkVolume((long) w * h * l, maxVolume);

        Map<String, Object> blocksTag = NbtReader.map(s.get("Blocks"));
        Map<String, Object> palTag = NbtReader.map(blocksTag != null ? blocksTag.get("Palette") : s.get("Palette"));
        Object dataObj = blocksTag != null ? blocksTag.get("Data") : s.get("BlockData");
        if (palTag == null || !(dataObj instanceof byte[] data)) throw new IOException("This .schem file has no block data.");

        int maxId = -1;
        for (Object v : palTag.values()) maxId = Math.max(maxId, NbtReader.num(v));
        String[] palette = new String[maxId + 1];
        Arrays.fill(palette, "minecraft:air");
        for (Map.Entry<String, Object> e : palTag.entrySet()) {
            int id = NbtReader.num(e.getValue());
            if (id >= 0 && id < palette.length) palette[id] = e.getKey();
        }

        int total = w * h * l;
        int[] blocks = new int[total];
        int p = 0;
        for (int i = 0; i < total; i++) {
            int value = 0;
            int shift = 0;
            while (true) {
                if (p >= data.length) throw new IOException("The .schem block data is cut off.");
                int b = data[p++];
                value |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) break;
                shift += 7;
                if (shift > 35) throw new IOException("Broken block data in .schem file.");
            }
            blocks[i] = value < palette.length ? value : -1;
        }
        return new SchematicData(w, h, l, palette, blocks);
    }

    /** Names of the schematic files in a folder, sorted. */
    static List<String> listFiles(File folder) {
        List<String> out = new ArrayList<>();
        File[] files = folder.listFiles();
        if (files == null) return out;
        for (File f : files) {
            String n = f.getName().toLowerCase(Locale.ROOT);
            if (f.isFile() && (n.endsWith(".litematic") || n.endsWith(".schem"))) out.add(f.getName());
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }
}
