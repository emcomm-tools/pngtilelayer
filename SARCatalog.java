package org.ka2ddo.yaac.gui.tile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads the SAR evidence catalog from the bundled sar-catalog.json resource.
 * Provides category names and their associated object types.
 */
public class SARCatalog {

    private final Map<String, List<String>> categories = new LinkedHashMap<>();

    public SARCatalog() {
        loadCatalog();
    }

    /**
     * Get ordered list of category names.
     */
    public List<String> getCategories() {
        return new ArrayList<>(categories.keySet());
    }

    /**
     * Get the object types for a given category.
     */
    public List<String> getTypes(String category) {
        List<String> types = categories.get(category);
        if (types == null) return Collections.emptyList();
        return Collections.unmodifiableList(types);
    }

    private void loadCatalog() {
        try {
            InputStream is = getClass().getResourceAsStream(
                    "/org/ka2ddo/yaac/gui/tile/sar-catalog.json");
            if (is == null) {
                System.err.println("SARCatalog: sar-catalog.json not found in JAR");
                loadDefaults();
                return;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            reader.close();
            parseCatalogJson(sb.toString());
        } catch (Exception e) {
            System.err.println("SARCatalog: error loading catalog: " + e.getMessage());
            loadDefaults();
        }
    }

    private void parseCatalogJson(String json) {
        // Find "categories" array
        int catIdx = json.indexOf("\"categories\"");
        if (catIdx == -1) {
            loadDefaults();
            return;
        }
        int arrayStart = json.indexOf('[', catIdx);
        int arrayEnd = findMatchingBracket(json, arrayStart);
        if (arrayStart == -1 || arrayEnd == -1) {
            loadDefaults();
            return;
        }

        String arrayContent = json.substring(arrayStart + 1, arrayEnd);

        // Parse each category object
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
                    parseCategoryObject(arrayContent.substring(objStart, i + 1));
                    objStart = -1;
                }
            }
        }

        if (categories.isEmpty()) {
            loadDefaults();
        }
    }

    private void parseCategoryObject(String json) {
        String name = extractJsonString(json, "name");
        if (name == null || name.trim().isEmpty()) return;

        List<String> types = extractJsonStringArray(json, "types");
        if (types.isEmpty()) return;

        categories.put(name, types);
    }

    private void loadDefaults() {
        categories.clear();
        List<String> custom = new ArrayList<>();
        custom.add("Other");
        categories.put("Custom", custom);
    }

    // --- JSON helpers (same pattern as StationColorManager) ---

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
                quoteEnd += 2;
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

    private static List<String> extractJsonStringArray(String json, String key) {
        List<String> result = new ArrayList<>();
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx == -1) return result;

        int arrStart = json.indexOf('[', idx + pattern.length());
        int arrEnd = findMatchingBracket(json, arrStart);
        if (arrStart == -1 || arrEnd == -1) return result;

        String arrContent = json.substring(arrStart + 1, arrEnd);

        // Extract each quoted string
        int pos = 0;
        while (pos < arrContent.length()) {
            int qs = arrContent.indexOf('"', pos);
            if (qs == -1) break;

            int qe = qs + 1;
            while (qe < arrContent.length()) {
                char c = arrContent.charAt(qe);
                if (c == '\\') {
                    qe += 2;
                } else if (c == '"') {
                    break;
                } else {
                    qe++;
                }
            }
            result.add(arrContent.substring(qs + 1, qe)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\"));
            pos = qe + 1;
        }
        return result;
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
                i++;
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
}
