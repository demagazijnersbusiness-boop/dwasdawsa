package net.capybarasmp.scoreboard;

/** A loaded schematic: blocks[(y * sizeZ + z) * sizeX + x] is an index into palette (or -1 = nothing here). */
final class SchematicData {
    final int sizeX;
    final int sizeY;
    final int sizeZ;
    final String[] palette;
    final int[] blocks;

    SchematicData(int sizeX, int sizeY, int sizeZ, String[] palette, int[] blocks) {
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.palette = palette;
        this.blocks = blocks;
    }

    long volume() {
        return (long) sizeX * sizeY * sizeZ;
    }

    static boolean isAir(String state) {
        String n = state;
        int br = n.indexOf('[');
        if (br >= 0) n = n.substring(0, br);
        return n.equals("minecraft:air") || n.equals("minecraft:cave_air") || n.equals("minecraft:void_air");
    }
}
