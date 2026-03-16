package org.ka2ddo.yaac.gui.tile;

import javax.swing.table.AbstractTableModel;
import java.util.List;

/**
 * Table model for displaying tile sources in the Map Source Manager dialog.
 * Columns: Name, Cache File, Default (marker), Fallback (marker).
 */
public class TileSourceTableModel extends AbstractTableModel {

    private static final String[] COLUMN_NAMES = {"Name", "Cache File", "Def", "FB"};
    private static final Class<?>[] COLUMN_CLASSES = {String.class, String.class, String.class, String.class};

    private final TileSourceManager sourceManager;

    public TileSourceTableModel(TileSourceManager sourceManager) {
        this.sourceManager = sourceManager;
    }

    @Override
    public int getRowCount() {
        return sourceManager.getSources().size();
    }

    @Override
    public int getColumnCount() {
        return COLUMN_NAMES.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMN_NAMES[column];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return COLUMN_CLASSES[columnIndex];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        List<TileSource> sources = sourceManager.getSources();
        if (rowIndex < 0 || rowIndex >= sources.size()) return null;

        TileSource source = sources.get(rowIndex);
        switch (columnIndex) {
            case 0: return source.getName();
            case 1: return source.getCacheFile();
            case 2:
                String defName = sourceManager.getDefaultSourceName();
                return source.getName().equals(defName) ? "*" : "";
            case 3:
                String fbName = sourceManager.getFallbackSourceName();
                return source.getName().equals(fbName) ? "*" : "";
            default: return null;
        }
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }

    /**
     * Get the TileSource at a given row index.
     */
    public TileSource getSourceAt(int row) {
        List<TileSource> sources = sourceManager.getSources();
        if (row < 0 || row >= sources.size()) return null;
        return sources.get(row);
    }
}
