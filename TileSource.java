package org.ka2ddo.yaac.gui.tile;

/**
 * Definition of a single tile source (map style).
 * URL template supports placeholders: {z}, {x}, {y}, {api_key}
 */
public class TileSource {

    private String name;
    private String urlTemplate;
    private String cacheFile;
    private String apiKey;
    private String attribution;
    private int minZoom;
    private int maxZoom;
    private boolean enabled;

    public TileSource() {
        this.minZoom = 1;
        this.maxZoom = 18;
        this.enabled = true;
        this.apiKey = "";
        this.attribution = "";
        this.cacheFile = "";
    }

    public TileSource(String name, String urlTemplate, String cacheFile,
                      String attribution, int minZoom, int maxZoom) {
        this.name = name;
        this.urlTemplate = urlTemplate;
        this.cacheFile = cacheFile;
        this.apiKey = "";
        this.attribution = attribution != null ? attribution : "";
        this.minZoom = minZoom;
        this.maxZoom = maxZoom;
        this.enabled = true;
    }

    /**
     * Build the full URL for a specific tile coordinate,
     * substituting {z}, {x}, {y}, and {api_key} placeholders.
     */
    public String getTileUrl(int z, int x, int y) {
        String url = urlTemplate
                .replace("{z}", String.valueOf(z))
                .replace("{x}", String.valueOf(x))
                .replace("{y}", String.valueOf(y));
        if (apiKey != null && !apiKey.isEmpty()) {
            url = url.replace("{api_key}", apiKey);
        }
        return url;
    }

    /**
     * Whether this source has a local SQLite cache file configured.
     */
    public boolean hasCache() {
        return cacheFile != null && !cacheFile.isEmpty();
    }

    // --- Getters and setters ---

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getUrlTemplate() { return urlTemplate; }
    public void setUrlTemplate(String urlTemplate) { this.urlTemplate = urlTemplate; }

    public String getCacheFile() { return cacheFile; }
    public void setCacheFile(String cacheFile) { this.cacheFile = cacheFile; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getAttribution() { return attribution; }
    public void setAttribution(String attribution) { this.attribution = attribution != null ? attribution : ""; }

    public int getMinZoom() { return minZoom; }
    public void setMinZoom(int minZoom) { this.minZoom = minZoom; }

    public int getMaxZoom() { return maxZoom; }
    public void setMaxZoom(int maxZoom) { this.maxZoom = maxZoom; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    @Override
    public String toString() {
        return name + " [" + minZoom + "-" + maxZoom + "]";
    }
}
