package org.ka2ddo.yaac.gui.tile;

import com.bbn.openmap.Layer;
import com.bbn.openmap.MapBean;
import com.bbn.openmap.MouseDelegator;
import org.ka2ddo.yaac.gui.GeoMapGuiIfc2;
import org.ka2ddo.yaac.gui.GeographicalMap;
import org.ka2ddo.yaac.gui.LayerCreator;

import java.io.File;
import java.lang.reflect.Field;
import java.util.prefs.Preferences;

/**
 * LayerCreator for StationPushpinLayer.
 * Z-order 55: above StationRenderer (50), below NWSZone (60).
 */
public class StationPushpinLayerCreator extends LayerCreator {

    private static StationPushpinLayer lastCreatedLayer;

    public StationPushpinLayerCreator() {
        super(55);
    }

    @Override
    public Layer getLayer(MapBean mapBean, GeoMapGuiIfc2 guiIfc,
                          MouseDelegator mouseDelegator, Preferences prefs) {
        StationPushpinLayer layer = new StationPushpinLayer(mapBean, guiIfc);

        String yaacConfigDir = System.getProperty("user.home") +
                File.separator + ".yaac";
        StationColorManager colorMgr = new StationColorManager();
        colorMgr.loadColors(yaacConfigDir);
        layer.setColorManager(colorMgr);

        SARObjectManager sarMgr = new SARObjectManager();
        sarMgr.loadObjects(yaacConfigDir);
        layer.setSARObjectManager(sarMgr);

        // Disable auto-pan on alerts (hardcoded true in GeographicalMap)
        if (guiIfc instanceof GeographicalMap) {
            try {
                Field f = GeographicalMap.class.getDeclaredField("isMapCenteredOnAlerts");
                f.setAccessible(true);
                f.setBoolean(guiIfc, false);
                System.out.println("[PNGTileLayer] Disabled isMapCenteredOnAlerts");
            } catch (Exception ex) {
                System.out.println("[PNGTileLayer] Could not disable isMapCenteredOnAlerts: " + ex);
            }
        }

        lastCreatedLayer = layer;
        return layer;
    }

    /**
     * Get the most recently created StationPushpinLayer instance.
     * Used by the Map Sources dialog to toggle visibility.
     */
    public static StationPushpinLayer getLastCreatedLayer() {
        return lastCreatedLayer;
    }
}
