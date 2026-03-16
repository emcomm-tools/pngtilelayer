package org.ka2ddo.yaac.gui.tile;

import org.ka2ddo.yaac.ax25.StationState;
import org.ka2ddo.yaac.ax25.StationTracker;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * Modeless dialog for toggling station visibility on the pushpin map.
 * Lists all currently tracked stations with a checkbox to show/hide each.
 */
public class StationVisibilityDialog extends JDialog implements ActionListener {

    private final StationPushpinLayer pushpinLayer;
    private final StationColorManager colorManager;
    private final VisibilityTableModel tableModel;

    public StationVisibilityDialog(Window parent,
                                    StationPushpinLayer pushpinLayer) {
        super(parent, "Station Visibility",
                Dialog.ModalityType.MODELESS);
        this.pushpinLayer = pushpinLayer;
        this.colorManager = pushpinLayer.getColorManager();
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        tableModel = new VisibilityTableModel();
        JTable table = new JTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(24);

        // Checkbox column width
        table.getColumnModel().getColumn(0).setPreferredWidth(50);
        table.getColumnModel().getColumn(0).setMaxWidth(50);
        // Callsign column
        table.getColumnModel().getColumn(1).setPreferredWidth(140);
        // Color swatch column
        table.getColumnModel().getColumn(2).setPreferredWidth(80);
        table.getColumnModel().getColumn(2).setCellRenderer(
                new ColorSwatchRenderer());

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(300, 200));

        // Buttons
        JPanel buttonPanel = new JPanel(
                new FlowLayout(FlowLayout.CENTER, 6, 4));

        JButton showAll = new JButton("Show All");
        showAll.setActionCommand("showAll");
        showAll.addActionListener(this);
        buttonPanel.add(showAll);

        JButton hideAll = new JButton("Hide All");
        hideAll.setActionCommand("hideAll");
        hideAll.addActionListener(this);
        buttonPanel.add(hideAll);

        JButton refresh = new JButton("Refresh");
        refresh.setActionCommand("refresh");
        refresh.addActionListener(this);
        buttonPanel.add(refresh);

        Container contentPane = getContentPane();
        contentPane.setLayout(new BorderLayout(4, 4));
        contentPane.add(scrollPane, BorderLayout.CENTER);
        contentPane.add(buttonPanel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(parent);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        switch (e.getActionCommand()) {
            case "showAll":
                for (int i = 0; i < tableModel.getRowCount(); i++) {
                    String cs = tableModel.getCallsignAt(i);
                    pushpinLayer.setStationVisible(cs, true);
                }
                tableModel.fireTableDataChanged();
                break;
            case "hideAll":
                for (int i = 0; i < tableModel.getRowCount(); i++) {
                    String cs = tableModel.getCallsignAt(i);
                    pushpinLayer.setStationVisible(cs, false);
                }
                tableModel.fireTableDataChanged();
                break;
            case "refresh":
                tableModel.refresh();
                break;
        }
    }

    // --- Table model ---

    private class VisibilityTableModel extends AbstractTableModel {

        private static final String[] COLUMNS = {"Visible", "Callsign", "Color"};
        private final List<String> callsigns = new ArrayList<>();

        VisibilityTableModel() {
            refresh();
        }

        void refresh() {
            callsigns.clear();
            StationState[] stations = StationTracker.getInstance()
                    .getCurrentTrackedObjectArray();
            if (stations != null) {
                for (StationState ss : stations) {
                    if (ss == null) continue;
                    if (ss.getLatitude() == 0.0
                            && ss.getLongitude() == 0.0) continue;
                    callsigns.add(ss.getIdentifier());
                }
            }
            callsigns.sort(String.CASE_INSENSITIVE_ORDER);
            fireTableDataChanged();
        }

        String getCallsignAt(int row) {
            if (row < 0 || row >= callsigns.size()) return null;
            return callsigns.get(row);
        }

        @Override
        public int getRowCount() {
            return callsigns.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Class<?> getColumnClass(int column) {
            if (column == 0) return Boolean.class;
            return Object.class;
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 0;
        }

        @Override
        public Object getValueAt(int row, int col) {
            String cs = callsigns.get(row);
            switch (col) {
                case 0:
                    return pushpinLayer.isStationVisible(cs);
                case 1:
                    return cs;
                case 2:
                    if (colorManager != null) {
                        return colorManager.getColor(cs);
                    }
                    return new Color(220, 40, 40);
                default:
                    return null;
            }
        }

        @Override
        public void setValueAt(Object value, int row, int col) {
            if (col == 0 && value instanceof Boolean) {
                String cs = callsigns.get(row);
                pushpinLayer.setStationVisible(cs, (Boolean) value);
            }
        }
    }

    // --- Color swatch cell renderer ---

    private static class ColorSwatchRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table,
                Object value, boolean isSelected, boolean hasFocus,
                int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(
                    table, "", isSelected, hasFocus, row, column);
            if (value instanceof Color) {
                Color c = (Color) value;
                label.setOpaque(true);
                label.setBackground(isSelected ? c.darker() : c);
                double luma = 0.299 * c.getRed() + 0.587 * c.getGreen()
                        + 0.114 * c.getBlue();
                label.setForeground(luma < 128 ? Color.WHITE : Color.BLACK);
                label.setText(colorName(c));
            }
            return label;
        }

        private static String colorName(Color c) {
            for (int i = 0; i < StationColorManager.PRESET_COLORS.length; i++) {
                if (StationColorManager.PRESET_COLORS[i].equals(c)) {
                    return StationColorManager.PRESET_NAMES[i];
                }
            }
            return String.format("#%02X%02X%02X",
                    c.getRed(), c.getGreen(), c.getBlue());
        }
    }
}
