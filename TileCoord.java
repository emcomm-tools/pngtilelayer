package org.ka2ddo.yaac.gui.tile;

/**
 * Slippy map tile coordinate (z/x/y) with conversion utilities
 * for Web Mercator (EPSG:3857) tile math.
 */
public class TileCoord {

    public final int z;
    public final int x;
    public final int y;

    public TileCoord(int z, int x, int y) {
        this.z = z;
        this.x = x;
        this.y = y;
    }

    /**
     * Convert longitude to tile X coordinate at given zoom level.
     */
    public static int lonToTileX(double lon, int zoom) {
        return (int) Math.floor((lon + 180.0) / 360.0 * (1 << zoom));
    }

    /**
     * Convert latitude to tile Y coordinate at given zoom level (Web Mercator).
     */
    public static int latToTileY(double lat, int zoom) {
        double latRad = Math.toRadians(lat);
        return (int) Math.floor((1.0 - Math.log(Math.tan(latRad) +
                1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * (1 << zoom));
    }

    /**
     * Convert tile X coordinate to longitude of tile's west edge.
     */
    public static double tileXToLon(int x, int zoom) {
        return x / (double) (1 << zoom) * 360.0 - 180.0;
    }

    /**
     * Convert tile Y coordinate to latitude of tile's north edge.
     */
    public static double tileYToLat(int y, int zoom) {
        double n = Math.PI - 2.0 * Math.PI * y / (double) (1 << zoom);
        return Math.toDegrees(Math.atan(Math.sinh(n)));
    }

    /**
     * Convert OpenMap scale (meters/pixel at equator) to web Mercator zoom level.
     * OpenMap Mercator scale at zoom 0 is approximately 559,082,264 m/pixel.
     */
    public static int scaleToZoom(float scale, int minZoom, int maxZoom) {
        if (scale <= 0) return minZoom;
        int zoom = (int) (Math.log(559082264.0 / scale) / Math.log(2));
        return Math.max(minZoom, Math.min(maxZoom, zoom));
    }

    /**
     * Clamp tile X to valid range for given zoom.
     */
    public static int clampTileX(int x, int zoom) {
        int max = (1 << zoom) - 1;
        if (x < 0) return 0;
        if (x > max) return max;
        return x;
    }

    /**
     * Clamp tile Y to valid range for given zoom.
     */
    public static int clampTileY(int y, int zoom) {
        int max = (1 << zoom) - 1;
        if (y < 0) return 0;
        if (y > max) return max;
        return y;
    }

    /**
     * Cache key for use in memory cache maps.
     */
    public String toKey() {
        return z + "/" + x + "/" + y;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TileCoord)) return false;
        TileCoord tc = (TileCoord) o;
        return z == tc.z && x == tc.x && y == tc.y;
    }

    @Override
    public int hashCode() {
        return 31 * (31 * z + x) + y;
    }

    @Override
    public String toString() {
        return z + "/" + x + "/" + y;
    }
}
