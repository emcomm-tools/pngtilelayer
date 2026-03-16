package org.ka2ddo.yaac.gui.tile;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Modeless dialog for managing callsign→color assignments.
 * Pre-assign colors from a 10-color SAR palette to distinguish
 * operators on the pushpin map layer.
 */
public class StationColorsDialog extends JDialog implements ActionListener {

    private final StationColorManager colorManager;
    private final StationPushpinLayer pushpinLayer;
    private final ColorTableModel tableModel;
    private final JTable colorTable;

    public StationColorsDialog(Window parent, StationColorManager colorManager,
                               StationPushpinLayer pushpinLayer) {
        super(parent, "Station Colors", Dialog.ModalityType.MODELESS);
        this.colorManager = colorManager;
        this.pushpinLayer = pushpinLayer;
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        tableModel = new ColorTableModel(colorManager);
        colorTable = new JTable(tableModel);
        colorTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        colorTable.setRowHeight(24);

        // Color swatch renderer for column 1
        colorTable.getColumnModel().getColumn(1).setCellRenderer(
                new ColorSwatchRenderer());

        // Column widths
        colorTable.getColumnModel().getColumn(0).setPreferredWidth(160);
        colorTable.getColumnModel().getColumn(1).setPreferredWidth(120);

        // Double-click to change color
        colorTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    changeSelectedColor();
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(colorTable);
        scrollPane.setPreferredSize(new Dimension(300, 160));

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

        // Info label
        JLabel infoLabel = new JLabel("Unassigned stations use default red.");
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
        int row = colorTable.getSelectedRow();
        if (row < 0) return;

        String callsign = tableModel.getCallsignAt(row);
        if (callsign == null) return;

        colorManager.removeColor(callsign);
        colorManager.saveColors();
        tableModel.refresh();
        if (pushpinLayer != null) pushpinLayer.repaint();
    }

    private void changeSelectedColor() {
        int row = colorTable.getSelectedRow();
        if (row < 0) return;

        String existing = tableModel.getCallsignAt(row);
        if (existing == null) return;

        Color[] result = new Color[1];
        String callsign = showCallsignColorChooser(existing, result);
        if (callsign == null) return;

        // If callsign changed, remove old entry
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

    // --- Table model ---

    private static class ColorTableModel extends AbstractTableModel {

        private static final String[] COLUMNS = {"Callsign", "Color"};
        private final StationColorManager manager;
        private final List<String> callsigns = new ArrayList<>();
        private final List<Color> colors = new ArrayList<>();

        ColorTableModel(StationColorManager manager) {
            this.manager = manager;
            refresh();
        }

        void refresh() {
            callsigns.clear();
            colors.clear();
            for (Map.Entry<String, Color> e : manager.getAssignments().entrySet()) {
                callsigns.add(e.getKey());
                colors.add(e.getValue());
            }
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
        public Object getValueAt(int row, int col) {
            if (col == 0) return callsigns.get(row);
            return colors.get(row);
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
                    // Darken slightly when selected so selection is visible
                    label.setBackground(c.darker());
                }
                // Find color name
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
