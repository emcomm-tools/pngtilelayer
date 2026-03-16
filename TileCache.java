package org.ka2ddo.yaac.gui.tile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SQLite tile cache reader/writer compatible with existing .db files.
 * <p>
 * Schema:
 * <pre>
 * CREATE TABLE tiles (
 *     z INTEGER NOT NULL,
 *     x INTEGER NOT NULL,
 *     y INTEGER NOT NULL,
 *     data BLOB NOT NULL,
 *     extension TEXT NOT NULL,
 *     created_at INTEGER NOT NULL,
 *     updated_at INTEGER,
 *     expires_at INTEGER,
 *     PRIMARY KEY (z, x, y)
 * );
 * </pre>
 * <p>
 * Each tile source has its own .db file. Connections are pooled per file.
 */
public class TileCache {

    private static final String SQL_GET_TILE =
            "SELECT data, expires_at FROM tiles WHERE z = ? AND x = ? AND y = ?";

    private static final String SQL_PUT_TILE =
            "INSERT OR REPLACE INTO tiles (z, x, y, data, extension, created_at, updated_at, expires_at) " +
            "VALUES (?, ?, ?, ?, '.png', ?, ?, ?)";

    private static final String SQL_HAS_TILE =
            "SELECT 1 FROM tiles WHERE z = ? AND x = ? AND y = ?";

    private static final String SQL_CREATE_TABLE =
            "CREATE TABLE IF NOT EXISTS tiles (" +
            "z INTEGER NOT NULL, " +
            "x INTEGER NOT NULL, " +
            "y INTEGER NOT NULL, " +
            "data BLOB NOT NULL, " +
            "extension TEXT NOT NULL, " +
            "created_at INTEGER NOT NULL, " +
            "updated_at INTEGER, " +
            "expires_at INTEGER, " +
            "PRIMARY KEY (z, x, y))";

    private static final String SQL_CREATE_INDEX =
            "CREATE INDEX IF NOT EXISTS idx_expires ON tiles(expires_at)";

    /** Default tile expiry: 30 days in seconds */
    private static final long DEFAULT_EXPIRY_SECONDS = 30L * 24 * 60 * 60;

    /** Connection pool: cache file path -> connection */
    private final Map<String, Connection> connections = new ConcurrentHashMap<>();

    private final String cacheDirectory;

    /**
     * @param cacheDirectory base directory for cache .db files
     */
    public TileCache(String cacheDirectory) {
        this.cacheDirectory = cacheDirectory;
    }

    /**
     * Get a tile image from the cache.
     *
     * @param cacheFile the .db filename (e.g., "OpenStreet Map.db")
     * @param z         zoom level
     * @param x         tile X
     * @param y         tile Y
     * @return the tile image, or null if not found
     */
    public BufferedImage getTile(String cacheFile, int z, int x, int y) {
        if (cacheFile == null || cacheFile.isEmpty()) return null;

        try {
            Connection conn = getConnection(cacheFile);
            if (conn == null) return null;

            PreparedStatement stmt = conn.prepareStatement(SQL_GET_TILE);
            stmt.setInt(1, z);
            stmt.setInt(2, x);
            stmt.setInt(3, y);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                byte[] data = rs.getBytes(1);
                rs.close();
                stmt.close();
                if (data != null && data.length > 0) {
                    return ImageIO.read(new ByteArrayInputStream(data));
                }
            }
            rs.close();
            stmt.close();
        } catch (Exception e) {
            System.err.println("TileCache: error reading " + z + "/" + x + "/" + y +
                    " from " + cacheFile + ": " + e.getMessage());
        }
        return null;
    }

    /**
     * Get raw tile bytes from the cache (for forwarding without decode).
     *
     * @return raw PNG bytes, or null if not found
     */
    public byte[] getTileBytes(String cacheFile, int z, int x, int y) {
        if (cacheFile == null || cacheFile.isEmpty()) return null;

        try {
            Connection conn = getConnection(cacheFile);
            if (conn == null) return null;

            PreparedStatement stmt = conn.prepareStatement(SQL_GET_TILE);
            stmt.setInt(1, z);
            stmt.setInt(2, x);
            stmt.setInt(3, y);

            ResultSet rs = stmt.executeQuery();
            byte[] data = null;
            if (rs.next()) {
                data = rs.getBytes(1);
            }
            rs.close();
            stmt.close();
            return data;
        } catch (Exception e) {
            System.err.println("TileCache: error reading bytes " + z + "/" + x + "/" + y +
                    " from " + cacheFile + ": " + e.getMessage());
        }
        return null;
    }

    /**
     * Check if a tile is expired based on its expires_at field.
     *
     * @return true if expired or not found, false if still valid
     */
    public boolean isExpired(String cacheFile, int z, int x, int y) {
        if (cacheFile == null || cacheFile.isEmpty()) return true;

        try {
            Connection conn = getConnection(cacheFile);
            if (conn == null) return true;

            PreparedStatement stmt = conn.prepareStatement(SQL_GET_TILE);
            stmt.setInt(1, z);
            stmt.setInt(2, x);
            stmt.setInt(3, y);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                rs.getBytes(1); // skip data
                long expiresAt = rs.getLong(2);
                rs.close();
                stmt.close();
                if (expiresAt == 0) return false; // no expiry set
                return System.currentTimeMillis() / 1000 > expiresAt;
            }
            rs.close();
            stmt.close();
        } catch (Exception e) {
            // treat errors as expired
        }
        return true;
    }

    /**
     * Store a tile in the cache.
     *
     * @param cacheFile the .db filename
     * @param z         zoom level
     * @param x         tile X
     * @param y         tile Y
     * @param pngData   raw PNG bytes
     */
    public void putTile(String cacheFile, int z, int x, int y, byte[] pngData) {
        if (cacheFile == null || cacheFile.isEmpty()) return;
        if (pngData == null || pngData.length == 0) return;

        try {
            Connection conn = getConnection(cacheFile);
            if (conn == null) return;

            long now = System.currentTimeMillis() / 1000;
            long expiresAt = now + DEFAULT_EXPIRY_SECONDS;

            PreparedStatement stmt = conn.prepareStatement(SQL_PUT_TILE);
            stmt.setInt(1, z);
            stmt.setInt(2, x);
            stmt.setInt(3, y);
            stmt.setBytes(4, pngData);
            stmt.setLong(5, now);       // created_at
            stmt.setLong(6, now);       // updated_at
            stmt.setLong(7, expiresAt); // expires_at
            stmt.executeUpdate();
            stmt.close();
        } catch (Exception e) {
            System.err.println("TileCache: error writing " + z + "/" + x + "/" + y +
                    " to " + cacheFile + ": " + e.getMessage());
        }
    }

    /**
     * Check if a tile exists in the cache (without loading the image).
     */
    public boolean hasTile(String cacheFile, int z, int x, int y) {
        if (cacheFile == null || cacheFile.isEmpty()) return false;

        try {
            Connection conn = getConnection(cacheFile);
            if (conn == null) return false;

            PreparedStatement stmt = conn.prepareStatement(SQL_HAS_TILE);
            stmt.setInt(1, z);
            stmt.setInt(2, x);
            stmt.setInt(3, y);

            ResultSet rs = stmt.executeQuery();
            boolean found = rs.next();
            rs.close();
            stmt.close();
            return found;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get or create a JDBC connection to the specified cache database.
     * Creates the database and table if they don't exist.
     */
    private Connection getConnection(String cacheFile) {
        return connections.computeIfAbsent(cacheFile, f -> {
            try {
                String path = cacheDirectory + File.separator + f;
                File dbFile = new File(path);

                // Ensure parent directory exists
                File parentDir = dbFile.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                }

                Connection conn = DriverManager.getConnection("jdbc:sqlite:" + path);
                conn.setAutoCommit(true);

                // Enable WAL mode for better concurrent read performance
                Statement pragmaStmt = conn.createStatement();
                pragmaStmt.execute("PRAGMA journal_mode=WAL");
                pragmaStmt.execute("PRAGMA synchronous=NORMAL");
                pragmaStmt.close();

                // Create table if this is a new database
                if (!dbFile.exists() || dbFile.length() == 0) {
                    Statement createStmt = conn.createStatement();
                    createStmt.execute(SQL_CREATE_TABLE);
                    createStmt.execute(SQL_CREATE_INDEX);
                    createStmt.close();
                }

                return conn;
            } catch (SQLException e) {
                System.err.println("TileCache: cannot open " + f + ": " + e.getMessage());
                return null;
            }
        });
    }

    /**
     * Close all open database connections.
     */
    public void close() {
        for (Map.Entry<String, Connection> entry : connections.entrySet()) {
            try {
                Connection conn = entry.getValue();
                if (conn != null && !conn.isClosed()) {
                    conn.close();
                }
            } catch (SQLException e) {
                // ignore on shutdown
            }
        }
        connections.clear();
    }
}
