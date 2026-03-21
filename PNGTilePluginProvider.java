package org.ka2ddo.yaac.gui.tile;

import org.ka2ddo.yaac.gui.GeographicalMap;
import org.ka2ddo.yaac.gui.MainGui;
import org.ka2ddo.yaac.pluginapi.AbstractMenuAction;
import org.ka2ddo.yaac.pluginapi.Provider;

/**
 * YAAC plugin provider that registers the PNG tile map layer.
 */
public class PNGTilePluginProvider extends Provider {

    public PNGTilePluginProvider() {
        super("PNG Tile Layer", "1.0", "VA2OPS",
                "<html>Adds slippy map PNG tile support with offline cache</html>");
    }

    @Override
    public boolean runInitializersBefore(int providerApiVersion) {
        return providerApiVersion >= 26;
    }

    @Override
    public String getInitFailureReason() {
        return buildNewerYaacNeededMsg(26);
    }

    @Override
    public int willPluginWorkAfterLiveInstallation(boolean isNewInstall) {
        return AFTERINSTALL_FULLY_READY;
    }

    @Override
    public void runInitializersAfter() {
        GeographicalMap.addMapLayer(new PNGTileLayerCreator());
        GeographicalMap.addMapLayer(new StationPushpinLayerCreator());
    }

    @Override
    public AbstractMenuAction[] getMenuItems() {
        return new AbstractMenuAction[]{
                new MapSourcesMenuAction(),
                new StationColorsMenuAction(),
                new SARObjectsMenuAction()
        };
    }

    @Override
    public String[] getAboutAttributions() {
        return new String[]{
                "PNG Tile Layer plugin \u00A9 2026 VA2OPS / EmComm-Tools"
        };
    }

    /**
     * Menu action: View → Map Sources...
     * All heavy class references deferred to actionPerformed() to avoid
     * class loading during plugin init (before GUI is ready).
     */
    private static class MapSourcesMenuAction extends AbstractMenuAction {

        MapSourcesMenuAction() {
            super("MapSources", new String[]{"menu.View"});
            putValue(NAME, "Map Sources...");
        }

        @Override
        public void actionPerformed(Object e) {
            try {
                PNGTileLayer layer = PNGTileLayerCreator.getLastCreatedLayer();
                if (layer == null) {
                    return;
                }
                TileSourceManager mgr = layer.getSourceManager();
                java.awt.Window parent = MainGui.getCurrentlyFocusedWindow();
                MapSourceManagerDialog dialog = new MapSourceManagerDialog(parent, mgr, layer);
                dialog.setVisible(true);
            } catch (Exception ex) {
                System.err.println("MapSourcesMenuAction: " + ex.getMessage());
            }
        }
    }

    /**
     * Menu action: View → Station Settings...
     */
    private static class StationColorsMenuAction extends AbstractMenuAction {

        StationColorsMenuAction() {
            super("StationColors", new String[]{"menu.View"});
            putValue(NAME, "Station Settings...");
        }

        @Override
        public void actionPerformed(Object e) {
            try {
                StationPushpinLayer layer =
                        StationPushpinLayerCreator.getLastCreatedLayer();
                if (layer == null) return;
                StationColorManager mgr = layer.getColorManager();
                if (mgr == null) return;
                java.awt.Window parent = MainGui.getCurrentlyFocusedWindow();
                StationColorsDialog dialog =
                        new StationColorsDialog(parent, mgr, layer);
                dialog.setVisible(true);
            } catch (Exception ex) {
                System.err.println("StationColorsMenuAction: " + ex.getMessage());
            }
        }
    }

    /**
     * Menu action: View → SAR Objects...
     */
    private static class SARObjectsMenuAction extends AbstractMenuAction {

        SARObjectsMenuAction() {
            super("SARObjects", new String[]{"menu.View"});
            putValue(NAME, "SAR Objects...");
        }

        @Override
        public void actionPerformed(Object e) {
            try {
                StationPushpinLayer layer =
                        StationPushpinLayerCreator.getLastCreatedLayer();
                if (layer == null) return;
                SARObjectManager mgr = layer.getSARObjectManager();
                if (mgr == null) return;
                java.awt.Window parent = MainGui.getCurrentlyFocusedWindow();
                SARObjectsDialog dialog =
                        new SARObjectsDialog(parent, mgr, layer);
                dialog.setVisible(true);
            } catch (Exception ex) {
                System.err.println("SARObjectsMenuAction: " + ex.getMessage());
            }
        }
    }

}
