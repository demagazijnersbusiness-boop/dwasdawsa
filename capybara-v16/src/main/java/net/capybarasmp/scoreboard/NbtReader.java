package net.capybarasmp.scoreboard;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/** Tiny reader for gzip-compressed NBT files (.litematic, .schem). Values: Byte, Short, Integer, Long, Float, Double, byte[], String, List, Map, int[], long[]. */
final class NbtReader {

    private NbtReader() {
    }

    static Map<String, Object> read(InputStream raw) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(raw), 1 << 16))) {
            byte type = in.readByte();
            if (type != 10) throw new IOException("Not an NBT file (root is not a compound).");
            in.readUTF(); // root name, not needed
            return readCompound(in);
        }
    }

    private static Map<String, Object> readCompound(DataInputStream in) throws IOException {
        Map<String, Object> map = new HashMap<>();
        while (true) {
            byte type = in.readByte();
            if (type == 0) break;
            String name = in.readUTF();
            map.put(name, readTag(in, type));
        }
        return map;
    }

    private static Object readTag(DataInputStream in, byte type) throws IOException {
        switch (type) {
            case 1:
                return in.readByte();
            case 2:
                return in.readShort();
            case 3:
                return in.readInt();
            case 4:
                return in.readLong();
            case 5:
                return in.readFloat();
            case 6:
                return in.readDouble();
            case 7: {
                int n = checkLength(in.readInt());
                byte[] a = new byte[n];
                in.readFully(a);
                return a;
            }
            case 8:
                return in.readUTF();
            case 9: {
                byte t = in.readByte();
                int n = checkLength(in.readInt());
                if (n > 0 && t == 0) throw new IOException("Broken list in NBT file.");
                List<Object> list = new ArrayList<>(Math.min(n, 1 << 16));
                for (int i = 0; i < n; i++) list.add(readTag(in, t));
                return list;
            }
            case 10:
                return readCompound(in);
            case 11: {
                int n = checkLength(in.readInt());
                int[] a = new int[n];
                for (int i = 0; i < n; i++) a[i] = in.readInt();
                return a;
            }
            case 12: {
                int n = checkLength(in.readInt());
                long[] a = new long[n];
                for (int i = 0; i < n; i++) a[i] = in.readLong();
                return a;
            }
            default:
                throw new IOException("Unknown NBT tag type " + type);
        }
    }

    private static int checkLength(int n) throws IOException {
        if (n < 0 || n > 200_000_000) throw new IOException("Broken NBT array length " + n);
        return n;
    }

    // ---- small helpers for reading values safely

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Object o) {
        return o instanceof Map<?, ?> ? (Map<String, Object>) o : null;
    }

    @SuppressWarnings("unchecked")
    static List<Object> list(Object o) {
        return o instanceof List<?> ? (List<Object>) o : null;
    }

    static int num(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }
}
