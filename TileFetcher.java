package org.ka2ddo.yaac.gui.tile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * HTTP tile fetcher with timeout, retry, and proper User-Agent.
 * Downloads PNG tiles from online tile servers.
 */
public class TileFetcher {

    private static final String USER_AGENT =
            "YAAC-PNGTileLayer/1.0 (EmComm-Tools; contact: va2ops@emcomm-tools.ca)";

    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 10000;
    private static final int MAX_RETRIES = 2;
    private static final int RETRY_DELAY_MS = 500;
    private static final int MAX_TILE_SIZE = 512 * 1024; // 512 KB max tile

    /**
     * Fetch a tile from the given URL.
     *
     * @param url fully resolved tile URL
     * @return raw PNG bytes, or null on failure
     */
    public byte[] fetchTile(String url) {
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                byte[] data = doFetch(url);
                if (data != null && data.length > 0) {
                    return data;
                }
            } catch (IOException e) {
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Fetch a tile for a specific tile source and coordinate.
     *
     * @param source tile source with URL template
     * @param z      zoom level
     * @param x      tile X
     * @param y      tile Y
     * @return raw PNG bytes, or null on failure
     */
    public byte[] fetchTile(TileSource source, int z, int x, int y) {
        String url = source.getTileUrl(z, x, y);
        return fetchTile(url);
    }

    /**
     * Check if a URL is reachable (HEAD request).
     *
     * @return true if server responds with 2xx
     */
    public boolean isReachable(String url) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setRequestProperty("User-Agent", USER_AGENT);

            int code = conn.getResponseCode();
            conn.disconnect();
            return code >= 200 && code < 300;
        } catch (Exception e) {
            return false;
        }
    }

    private byte[] doFetch(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Accept", "image/png,image/*;q=0.9");
            conn.setInstanceFollowRedirects(true);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                return null;
            }

            String contentType = conn.getContentType();
            if (contentType != null && !contentType.startsWith("image/")) {
                return null;
            }

            InputStream in = conn.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream(32768);
            byte[] buf = new byte[8192];
            int totalRead = 0;
            int n;

            while ((n = in.read(buf)) != -1) {
                totalRead += n;
                if (totalRead > MAX_TILE_SIZE) {
                    return null; // tile too large, abort
                }
                out.write(buf, 0, n);
            }
            in.close();

            return out.toByteArray();
        } finally {
            conn.disconnect();
        }
    }
}
