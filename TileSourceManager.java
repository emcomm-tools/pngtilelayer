package org.ka2ddo.yaac.gui.tile;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages multiple tile sources with active source switching
 * and JSON config persistence.
 * <p>
 * Config file: ~/.yaac/tile-sources.json
 * Cache directory: ~/YAAC/mapcache/ (symlinks to USB EMCOMM-DATA/YAAC-MapCache .db files)
 */
public class TileSourceManager {

    private static final String DEFAULT_CONFIG_FILE = "tile-sources.json";
    private static final String DEFAULT_CACHE_DIR =
            System.getProperty("user.home") + File.separator + "YAAC" + File.separator + "mapcache";

    private final List<TileSource> sources = new ArrayList<>();
    private TileSource activeSource;
    private TileSource fallbackSource;
    private String defaultSourceName;
    private String fallbackSourceName;
    private String cacheDirectory;
    private String configPath;

    public TileSourceManager() {
        this.cacheDirectory = DEFAULT_CACHE_DIR;
    }

    /**
     * Load sources from JSON config file.
     * Falls back to built-in defaults if file doesn't exist.
     */
    public void loadSources(String yaacConfigDir) {
        this.configPath = yaacConfigDir + File.separator + DEFAULT_CONFIG_FILE;
        File configFile = new File(configPath);

        if (configFile.exists()) {
            try {
                String json = readFile(configFile);
                parseSourcesJson(json);
            } catch (Exception e) {
                System.err.println("TileSourceManager: error loading " + configPath +
                        ": " + e.getMessage() + ", using defaults");
                loadDefaults();
            }
        } else {
            loadDefaults();
            saveSources(); // save defaults for future editing
        }

        // Set active source from persisted default name
        if (defaultSourceName != null) {
            for (TileSource s : sources) {
                if (s.getName().equals(defaultSourceName) && s.isEnabled()) {
                    activeSource = s;
                    break;
                }
            }
        }
        // Fall back to first enabled source
        if (activeSource == null) {
            for (TileSource s : sources) {
                if (s.isEnabled()) {
                    activeSource = s;
                    break;
                }
            }
        }
        // Set fallback source from persisted name
        if (fallbackSourceName != null) {
            for (TileSource s : sources) {
                if (s.getName().equals(fallbackSourceName) && s.isEnabled()) {
                    fallbackSource = s;
                    break;
                }
            }
        }
    }

    /**
     * Save current sources to JSON config file.
     */
    public void saveSources() {
        if (configPath == null) return;

        try {
            File configFile = new File(configPath);
            File parentDir = configFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            String json = sourcesToJson();
            BufferedWriter writer = new BufferedWriter(new FileWriter(configFile));
            writer.write(json);
            writer.close();
        } catch (IOException e) {
            System.err.println("TileSourceManager: error saving config: " + e.getMessage());
        }
    }

    /**
     * Load built-in default tile sources.
     */
    private void loadDefaults() {
        sources.clear();

        TileSource osm = new TileSource(
                "OpenStreetMap",
                "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
                "OpenStreetMap.db",
                "\u00A9 OpenStreetMap contributors",
                1, 19);
        sources.add(osm);

        TileSource tracestrack = new TileSource(
                "Tracestrack",
                "https://tile.tracestrack.com/topo_fr/{z}/{x}/{y}.png?key={api_key}",
                "TracesTrack.db",
                "\u00A9 Tracestrack / OSM contributors",
                1, 19);
        sources.add(tracestrack);

        TileSource thunderforest = new TileSource(
                "Thunderforest",
                "https://tile.thunderforest.com/outdoors/{z}/{x}/{y}.png?apikey={api_key}",
                "Thunderforest.db",
                "\u00A9 Thunderforest / OSM contributors",
                1, 22);
        sources.add(thunderforest);

        TileSource openTopo = new TileSource(
                "OpenTopoMap",
                "https://tile.opentopomap.org/{z}/{x}/{y}.png",
                "OpenTopoMap.db",
                "\u00A9 OpenTopoMap / OSM contributors",
                1, 17);
        sources.add(openTopo);

        TileSource googleMap = new TileSource(
                "Google Map",
                "https://mt0.google.com/vt/lyrs=m&x={x}&y={y}&z={z}",
                "GoogleMap.db",
                "\u00A9 Google",
                1, 21);
        sources.add(googleMap);

        TileSource googleSat = new TileSource(
                "Google Satellite",
                "https://mt0.google.com/vt/lyrs=s&x={x}&y={y}&z={z}",
                "GoogleSatellite.db",
                "\u00A9 Google",
                1, 21);
        sources.add(googleSat);

        TileSource mbtileserver = new TileSource(
                "mbtileserver (local)",
                "http://localhost:1982/services/{tileset}/tiles/{z}/{x}/{y}.png",
                "",
                "Local tile server",
                1, 14);
        sources.add(mbtileserver);

        defaultSourceName = "OpenStreetMap";
        fallbackSourceName = "OpenStreetMap";
    }

    // --- Active source management ---

    public TileSource getActiveSource() {
        return activeSource;
    }

    public void setActiveSource(TileSource source) {
        this.activeSource = source;
        this.defaultSourceName = source != null ? source.getName() : null;
    }

    public void setActiveSourceByName(String name) {
        for (TileSource s : sources) {
            if (s.getName().equals(name)) {
                this.activeSource = s;
                this.defaultSourceName = name;
                return;
            }
        }
    }

    public TileSource getFallbackSource() {
        return fallbackSource;
    }

    public void setFallbackSource(TileSource fallbackSource) {
        this.fallbackSource = fallbackSource;
        this.fallbackSourceName = fallbackSource != null ? fallbackSource.getName() : null;
    }

    public String getDefaultSourceName() {
        return defaultSourceName;
    }

    public void setDefaultSourceName(String name) {
        this.defaultSourceName = name;
    }

    public String getFallbackSourceName() {
        return fallbackSourceName;
    }

    public void setFallbackSourceName(String name) {
        this.fallbackSourceName = name;
    }

    public List<TileSource> getSources() {
        return Collections.unmodifiableList(sources);
    }

    /**
     * Get the mutable sources list for table model operations.
     */
    public List<TileSource> getMutableSources() {
        return sources;
    }

    public List<TileSource> getEnabledSources() {
        List<TileSource> enabled = new ArrayList<>();
        for (TileSource s : sources) {
            if (s.isEnabled()) {
                enabled.add(s);
            }
        }
        return enabled;
    }

    public void addSource(TileSource source) {
        sources.add(source);
    }

    public void removeSource(TileSource source) {
        sources.remove(source);
        if (activeSource == source) {
            activeSource = sources.isEmpty() ? null : sources.get(0);
        }
    }

    public String getCacheDirectory() {
        return cacheDirectory;
    }

    public void setCacheDirectory(String cacheDirectory) {
        this.cacheDirectory = cacheDirectory;
    }

    // --- Simple JSON serialization (no external dependency) ---

    private String sourcesToJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"cacheDirectory\": ").append(jsonString(cacheDirectory)).append(",\n");
        sb.append("  \"defaultSource\": ").append(jsonString(defaultSourceName)).append(",\n");
        sb.append("  \"fallbackSource\": ").append(jsonString(fallbackSourceName)).append(",\n");
        sb.append("  \"sources\": [\n");

        for (int i = 0; i < sources.size(); i++) {
            TileSource s = sources.get(i);
            sb.append("    {\n");
            sb.append("      \"name\": ").append(jsonString(s.getName())).append(",\n");
            sb.append("      \"urlTemplate\": ").append(jsonString(s.getUrlTemplate())).append(",\n");
            sb.append("      \"cacheFile\": ").append(jsonString(s.getCacheFile())).append(",\n");
            sb.append("      \"attribution\": ").append(jsonString(s.getAttribution())).append(",\n");
            sb.append("      \"apiKey\": ").append(jsonString(s.getApiKey())).append(",\n");
            sb.append("      \"minZoom\": ").append(s.getMinZoom()).append(",\n");
            sb.append("      \"maxZoom\": ").append(s.getMaxZoom()).append(",\n");
            sb.append("      \"enabled\": ").append(s.isEnabled()).append("\n");
            sb.append("    }");
            if (i < sources.size() - 1) sb.append(",");
            sb.append("\n");
        }

        sb.append("  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    private void parseSourcesJson(String json) {
        sources.clear();

        // Parse cacheDirectory
        String cacheDir = extractJsonString(json, "cacheDirectory");
        if (cacheDir != null) {
            this.cacheDirectory = cacheDir;
        }

        // Parse default/fallback source names
        String defSrc = extractJsonString(json, "defaultSource");
        if (defSrc != null) {
            this.defaultSourceName = defSrc;
        }
        String fbSrc = extractJsonString(json, "fallbackSource");
        if (fbSrc != null) {
            this.fallbackSourceName = fbSrc;
        }

        // Parse sources array
        int sourcesStart = json.indexOf("\"sources\"");
        if (sourcesStart == -1) return;

        int arrayStart = json.indexOf('[', sourcesStart);
        int arrayEnd = findMatchingBracket(json, arrayStart);
        if (arrayStart == -1 || arrayEnd == -1) return;

        String arrayContent = json.substring(arrayStart + 1, arrayEnd);

        // Split into individual source objects
        int depth = 0;
        int objStart = -1;
        for (int i = 0; i < arrayContent.length(); i++) {
            char c = arrayContent.charAt(i);
            if (c == '{') {
                if (depth == 0) objStart = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && objStart >= 0) {
                    String objJson = arrayContent.substring(objStart, i + 1);
                    TileSource source = parseSourceObject(objJson);
                    if (source != null) {
                        sources.add(source);
                    }
                    objStart = -1;
                }
            }
        }
    }

    private TileSource parseSourceObject(String json) {
        TileSource s = new TileSource();
        String name = extractJsonString(json, "name");
        if (name == null) return null;
        s.setName(name);
        s.setUrlTemplate(extractJsonString(json, "urlTemplate"));
        s.setCacheFile(extractJsonString(json, "cacheFile"));

        String attribution = extractJsonString(json, "attribution");
        s.setAttribution(attribution != null ? attribution : "");

        String apiKey = extractJsonString(json, "apiKey");
        s.setApiKey(apiKey != null ? apiKey : "");

        Integer minZoom = extractJsonInt(json, "minZoom");
        if (minZoom != null) s.setMinZoom(minZoom);

        Integer maxZoom = extractJsonInt(json, "maxZoom");
        if (maxZoom != null) s.setMaxZoom(maxZoom);

        Boolean enabled = extractJsonBool(json, "enabled");
        if (enabled != null) s.setEnabled(enabled);

        return s;
    }

    // --- Minimal JSON helpers (no dependency on javax.json or Gson) ---

    private static String jsonString(String value) {
        if (value == null) return "\"\"";
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String extractJsonString(String json, String key) {
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx == -1) return null;

        int colonIdx = json.indexOf(':', idx + pattern.length());
        if (colonIdx == -1) return null;

        int quoteStart = json.indexOf('"', colonIdx + 1);
        if (quoteStart == -1) return null;

        int quoteEnd = quoteStart + 1;
        while (quoteEnd < json.length()) {
            char c = json.charAt(quoteEnd);
            if (c == '\\') {
                quoteEnd += 2; // skip escaped char
            } else if (c == '"') {
                break;
            } else {
                quoteEnd++;
            }
        }

        return json.substring(quoteStart + 1, quoteEnd)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private static Integer extractJsonInt(String json, String key) {
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx == -1) return null;

        int colonIdx = json.indexOf(':', idx + pattern.length());
        if (colonIdx == -1) return null;

        int start = colonIdx + 1;
        while (start < json.length() && json.charAt(start) == ' ') start++;

        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) end++;

        if (end == start) return null;
        try {
            return Integer.parseInt(json.substring(start, end));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Boolean extractJsonBool(String json, String key) {
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx == -1) return null;

        int colonIdx = json.indexOf(':', idx + pattern.length());
        if (colonIdx == -1) return null;

        String rest = json.substring(colonIdx + 1).trim();
        if (rest.startsWith("true")) return true;
        if (rest.startsWith("false")) return false;
        return null;
    }

    private static int findMatchingBracket(String json, int openPos) {
        if (openPos < 0 || openPos >= json.length()) return -1;
        char open = json.charAt(openPos);
        char close = (open == '[') ? ']' : '}';
        int depth = 1;
        boolean inString = false;

        for (int i = openPos + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && inString) {
                i++; // skip escaped char
                continue;
            }
            if (c == '"') {
                inString = !inString;
            } else if (!inString) {
                if (c == open) depth++;
                else if (c == close) {
                    depth--;
                    if (depth == 0) return i;
                }
            }
        }
        return -1;
    }

    private static String readFile(File file) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(file));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append('\n');
        }
        reader.close();
        return sb.toString();
    }
}
