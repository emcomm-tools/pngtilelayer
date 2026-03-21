package org.ka2ddo.yaac.gui.tile;

import org.ka2ddo.yaac.ax25.StationState;
import org.ka2ddo.yaac.ax25.StationTracker;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Modeless dialog for managing station settings: visibility, color,
 * min distance, and phase-out time. Shows all tracked stations plus
 * any manually pre-configured stations.
 */
public class StationColorsDialog extends JDialog implements ActionListener {

    private final StationColorManager colorManager;
    private final StationPushpinLayer pushpinLayer;
    private final StationTableModel tableModel;
    private final JTable stationTable;

    public StationColorsDialog(Window parent, StationColorManager colorManager,
                               StationPushpinLayer pushpinLayer) {
        super(parent, "Station Settings", Dialog.ModalityType.MODELESS);
        this.colorManager = colorManager;
        this.pushpinLayer = pushpinLayer;
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        tableModel = new StationTableModel();
        stationTable = new JTable(tableModel);
        stationTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        stationTable.setRowHeight(24);

        // Column renderers
        stationTable.getColumnModel().getColumn(2).setCellRenderer(
                new ColorSwatchRenderer());

        // Column widths
        stationTable.getColumnModel().getColumn(0).setPreferredWidth(35);
        stationTable.getColumnModel().getColumn(0).setMaxWidth(40);
        stationTable.getColumnModel().getColumn(1).setPreferredWidth(100);
        stationTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        stationTable.getColumnModel().getColumn(3).setPreferredWidth(55);
        stationTable.getColumnModel().getColumn(4).setPreferredWidth(45);
        stationTable.getColumnModel().getColumn(5).setPreferredWidth(75);
        stationTable.getColumnModel().getColumn(6).setPreferredWidth(85);

        // Double-click on color column to change color
        stationTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int col = stationTable.columnAtPoint(e.getPoint());
                    if (col == 2) {
                        changeSelectedColor();
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(stationTable);
        scrollPane.setPreferredSize(new Dimension(530, 200));

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 4));

        JButton addButton = new JButton("Add");
        addButton.setActionCommand("add");
        addButton.addActionListener(this);
        buttonPanel.add(addButton);

        JButton deleteButton = new JButton("Delete");
        deleteButton.setActionCommand("delete");
        deleteButton.addActionListener(this);
        buttonPanel.add(deleteButton);

        buttonPanel.add(Box.createHorizontalStrut(8));

        JButton showAll = new JButton("Show All");
        showAll.setActionCommand("showAll");
        showAll.addActionListener(this);
        buttonPanel.add(showAll);

        JButton hideAll = new JButton("Hide All");
        hideAll.setActionCommand("hideAll");
        hideAll.addActionListener(this);
        buttonPanel.add(hideAll);

        buttonPanel.add(Box.createHorizontalStrut(8));

        JButton refresh = new JButton("Refresh");
        refresh.setActionCommand("refresh");
        refresh.addActionListener(this);
        buttonPanel.add(refresh);

        // Info label
        JLabel infoLabel = new JLabel(
                "Double-click Color to change. Unassigned = default red.");
        infoLabel.setHorizontalAlignment(SwingConstants.CENTER);
        infoLabel.setFont(infoLabel.getFont().deriveFont(Font.ITALIC, 11f));

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(buttonPanel, BorderLayout.NORTH);
        bottomPanel.add(infoLabel, BorderLayout.SOUTH);

        Container contentPane = getContentPane();
        contentPane.setLayout(new BorderLayout(4, 4));
        contentPane.add(scrollPane, BorderLayout.CENTER);
        contentPane.add(bottomPanel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(parent);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        switch (e.getActionCommand()) {
            case "add":
                addStation();
                break;
            case "delete":
                deleteSelected();
                break;
            case "showAll":
                for (int i = 0; i < tableModel.getRowCount(); i++) {
                    String cs = tableModel.getCallsignAt(i);
                    if (cs != null) pushpinLayer.setStationVisible(cs, true);
                }
                tableModel.fireTableDataChanged();
                break;
            case "hideAll":
                for (int i = 0; i < tableModel.getRowCount(); i++) {
                    String cs = tableModel.getCallsignAt(i);
                    if (cs != null) pushpinLayer.setStationVisible(cs, false);
                }
                tableModel.fireTableDataChanged();
                break;
            case "refresh":
                tableModel.refresh();
                break;
        }
    }

    private void addStation() {
        Color[] result = new Color[1];
        String callsign = showCallsignColorChooser(null, result);
        if (callsign == null) return;

        colorManager.setColor(callsign, result[0]);
        colorManager.saveColors();
        tableModel.refresh();
        if (pushpinLayer != null) pushpinLayer.repaint();
    }

    private void deleteSelected() {
        int row = stationTable.getSelectedRow();
        if (row < 0) return;

        String callsign = tableModel.getCallsignAt(row);
        if (callsign == null) return;

        int confirm = JOptionPane.showConfirmDialog(this,
                "Delete station " + callsign + "?",
                "Confirm Delete", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        // Remove from StationTracker if tracked
        StationState ss = StationTracker.getInstance().getTrackedObject(callsign);
        if (ss != null) {
            StationTracker.getInstance().deleteStation(ss);
        }

        colorManager.removeColor(callsign);
        colorManager.saveColors();
        tableModel.refresh();
        if (pushpinLayer != null) pushpinLayer.repaint();
    }

    private void changeSelectedColor() {
        int row = stationTable.getSelectedRow();
        if (row < 0) return;

        String existing = tableModel.getCallsignAt(row);
        if (existing == null) return;

        Color[] result = new Color[1];
        String callsign = showCallsignColorChooser(existing, result);
        if (callsign == null) return;

        if (!callsign.equals(existing)) {
            colorManager.removeColor(existing);
        }
        colorManager.setColor(callsign, result[0]);
        colorManager.saveColors();
        tableModel.refresh();
        if (pushpinLayer != null) pushpinLayer.repaint();
    }

    /**
     * Show a small modal dialog with callsign field + color palette.
     * Returns the callsign (uppercase) or null if cancelled.
     * The chosen color is placed in result[0].
     */
    private String showCallsignColorChooser(String existingCallsign,
                                             Color[] result) {
        JDialog chooser = new JDialog(this, "Assign Color", true);
        chooser.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Callsign input
        JPanel topRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        topRow.add(new JLabel("Callsign:"));
        JTextField callField = new JTextField(10);
        if (existingCallsign != null) {
            callField.setText(existingCallsign);
        }
        topRow.add(callField);
        panel.add(topRow, BorderLayout.NORTH);

        // Color palette grid (2 rows x 5 columns)
        JPanel palette = new JPanel(new GridLayout(2, 5, 4, 4));
        final boolean[] selected = {false};

        for (int i = 0; i < StationColorManager.PRESET_COLORS.length; i++) {
            final Color color = StationColorManager.PRESET_COLORS[i];
            final String name = StationColorManager.PRESET_NAMES[i];

            JButton btn = new JButton() {
                @Override
                protected void paintComponent(Graphics g) {
                    g.setColor(color);
                    g.fillRect(0, 0, getWidth(), getHeight());
                }
            };
            btn.setPreferredSize(new Dimension(40, 30));
            btn.setToolTipText(name);
            btn.setBorderPainted(true);

            btn.addActionListener(ev -> {
                String cs = callField.getText().trim().toUpperCase();
                if (cs.isEmpty()) {
                    callField.requestFocus();
                    return;
                }
                result[0] = color;
                selected[0] = true;
                chooser.dispose();
            });

            palette.add(btn);
        }
        panel.add(palette, BorderLayout.CENTER);

        // Hint
        JLabel hint = new JLabel("Click a color to assign.");
        hint.setFont(hint.getFont().deriveFont(Font.ITALIC, 11f));
        hint.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(hint, BorderLayout.SOUTH);

        chooser.getContentPane().add(panel);
        chooser.pack();
        chooser.setLocationRelativeTo(this);
        chooser.setVisible(true); // blocks until disposed

        if (!selected[0]) return null;
        return callField.getText().trim().toUpperCase();
    }

    // --- Table model (union of tracked + manually configured stations) ---

    private class StationTableModel extends AbstractTableModel {

        private static final String[] COLUMNS = {
                "Vis", "Callsign", "Color", "Opacity", "Trails", "Min Dist (m)", "Phase Out (min)"
        };
        private final List<String> callsigns = new ArrayList<>();

        StationTableModel() {
            refresh();
        }

        void refresh() {
            callsigns.clear();
            Set<String> seen = new LinkedHashSet<>();

            // Add all tracked stations
            StationState[] stations = StationTracker.getInstance()
                    .getCurrentTrackedObjectArray();
            if (stations != null) {
                for (StationState ss : stations) {
                    if (ss == null) continue;
                    if (ss.getLatitude() == 0.0
                            && ss.getLongitude() == 0.0) continue;
                    seen.add(ss.getIdentifier().toUpperCase().trim());
                }
            }

            // Add manually configured stations not yet tracked
            for (String cs : colorManager.getAssignments().keySet()) {
                seen.add(cs);
            }

            callsigns.addAll(seen);
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
            if (column >= 3) return Integer.class;
            return Object.class;
        }

        @Override
        public boolean isCellEditable(int row, int col) {
            return col == 0 || col >= 3;
        }

        // Note: column indices after adding "Trails" at position 4:
        // 0=Vis, 1=Callsign, 2=Color, 3=Opacity, 4=Trails, 5=Min Dist, 6=Phase Out

        @Override
        public Object getValueAt(int row, int col) {
            String cs = callsigns.get(row);
            switch (col) {
                case 0:
                    return pushpinLayer.isStationVisible(cs);
                case 1:
                    return cs;
                case 2:
                    return colorManager.getColor(cs);
                case 3:
                    return colorManager.getOpacity(cs);
                case 4:
                    return colorManager.getTrailCount(cs);
                case 5:
                    return colorManager.getMinDistance(cs);
                case 6:
                    return colorManager.getPhaseOut(cs);
                default:
                    return null;
            }
        }

        @Override
        public void setValueAt(Object value, int row, int col) {
            String cs = callsigns.get(row);
            if (col == 0 && value instanceof Boolean) {
                pushpinLayer.setStationVisible(cs, (Boolean) value);
            } else if (value instanceof Integer) {
                int val = Math.max(0, (Integer) value);
                if (col == 3) {
                    colorManager.setOpacity(cs, val);
                } else if (col == 4) {
                    colorManager.setTrailCount(cs, val);
                } else if (col == 5) {
                    colorManager.setMinDistance(cs, val);
                } else if (col == 6) {
                    colorManager.setPhaseOut(cs, val);
                }
                colorManager.saveColors();
                if (pushpinLayer != null) pushpinLayer.repaint();
            }
        }
    }

    // --- Color swatch cell renderer ---

    private static class ColorSwatchRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(
                    table, "", isSelected, hasFocus, row, column);

            if (value instanceof Color) {
                Color c = (Color) value;
                label.setOpaque(true);
                if (!isSelected) {
                    label.setBackground(c);
                } else {
                    label.setBackground(c.darker());
                }
                String name = colorName(c);
                label.setText(name != null ? name : String.format("#%02X%02X%02X",
                        c.getRed(), c.getGreen(), c.getBlue()));
                label.setForeground(isDark(c) ? Color.WHITE : Color.BLACK);
            }
            return label;
        }

        private static String colorName(Color c) {
            for (int i = 0; i < StationColorManager.PRESET_COLORS.length; i++) {
                if (StationColorManager.PRESET_COLORS[i].equals(c)) {
                    return StationColorManager.PRESET_NAMES[i];
                }
            }
            return null;
        }

        private static boolean isDark(Color c) {
            double luma = 0.299 * c.getRed() + 0.587 * c.getGreen() +
                    0.114 * c.getBlue();
            return luma < 128;
        }
    }
}
