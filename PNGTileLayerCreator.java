package org.ka2ddo.yaac.gui.tile;

import com.bbn.openmap.Layer;
import com.bbn.openmap.MapBean;
import com.bbn.openmap.MouseDelegator;
import org.ka2ddo.yaac.gui.GeoMapGuiIfc2;
import org.ka2ddo.yaac.gui.LayerCreator;
import org.ka2ddo.yaac.YAAC;

import java.util.prefs.Preferences;

/**
 * LayerCreator for PNGTileLayer.
 * Z-order 15: above OSM vectors (10), below raster overlay (20).
 */
public class PNGTileLayerCreator extends LayerCreator {

    private static PNGTileLayer lastCreatedLayer;

    public PNGTileLayerCreator() {
        super(15);
    }

    @Override
    public Layer getLayer(MapBean mapBean, GeoMapGuiIfc2 guiIfc,
                          MouseDelegator mouseDelegator, Preferences prefs) {
        TileSourceManager sourceManager = new TileSourceManager();
        String yaacConfigDir = System.getProperty("user.home") + "/.yaac";
        sourceManager.loadSources(yaacConfigDir);

        // Auto-disable OSM vector layer when our tile plugin is active
        try {
            Preferences wayTypeNode = YAAC.getPreferences().node("WayTypeLayers");
            wayTypeNode.putBoolean("ShowMap", false);
            wayTypeNode.flush();
        } catch (Exception e) {
            System.err.println("PNGTileLayerCreator: could not disable OSM layer: " + e.getMessage());
        }

        PNGTileLayer layer = new PNGTileLayer(mapBean, sourceManager);
        lastCreatedLayer = layer;
        return layer;
    }

    /**
     * Get the most recently created PNGTileLayer instance.
     * Used by the menu action to open the Map Sources dialog.
     */
    public static PNGTileLayer getLastCreatedLayer() {
        return lastCreatedLayer;
    }
}
