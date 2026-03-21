package org.ka2ddo.yaac.gui.tile;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * Modeless dialog for managing all placed SAR evidence objects.
 * Shows a table of all objects with delete and refresh controls.
 */
public class SARObjectsDialog extends JDialog implements ActionListener {

    private static final SimpleDateFormat UTC_FORMAT;
    static {
        UTC_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm z");
        UTC_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    private final SARObjectManager sarManager;
    private final StationPushpinLayer pushpinLayer;
    private final SARTableModel tableModel;
    private final JTable objectTable;

    public SARObjectsDialog(Window parent, SARObjectManager sarManager,
                             StationPushpinLayer pushpinLayer) {
        super(parent, "SAR Objects", Dialog.ModalityType.MODELESS);
        this.sarManager = sarManager;
        this.pushpinLayer = pushpinLayer;
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        tableModel = new SARTableModel();
        objectTable = new JTable(tableModel);
        objectTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        objectTable.setRowHeight(22);

        // Column widths
        objectTable.getColumnModel().getColumn(0).setPreferredWidth(90);
        objectTable.getColumnModel().getColumn(1).setPreferredWidth(100);
        objectTable.getColumnModel().getColumn(2).setPreferredWidth(70);
        objectTable.getColumnModel().getColumn(3).setPreferredWidth(130);
        objectTable.getColumnModel().getColumn(4).setPreferredWidth(120);

        // Category column color renderer
        objectTable.getColumnModel().getColumn(1).setCellRenderer(
                new CategoryColorRenderer());

        JScrollPane scrollPane = new JScrollPane(objectTable);
        scrollPane.setPreferredSize(new Dimension(540, 200));

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 4));

        JButton deleteButton = new JButton("Delete");
        deleteButton.setActionCommand("delete");
        deleteButton.addActionListener(this);
        buttonPanel.add(deleteButton);

        JButton refreshButton = new JButton("Refresh");
        refreshButton.setActionCommand("refresh");
        refreshButton.addActionListener(this);
        buttonPanel.add(refreshButton);

        // Count label
        JLabel countLabel = new JLabel(sarManager.getObjects().size() + " object(s)");
        countLabel.setFont(countLabel.getFont().deriveFont(Font.ITALIC, 11f));
        countLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(buttonPanel, BorderLayout.NORTH);
        bottomPanel.add(countLabel, BorderLayout.SOUTH);

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
            case "delete":
                deleteSelected();
                break;
            case "refresh":
                tableModel.fireTableDataChanged();
                break;
        }
    }

    private void deleteSelected() {
        int row = objectTable.getSelectedRow();
        if (row < 0 || row >= sarManager.getObjects().size()) return;

        SARObject obj = sarManager.getObjects().get(row);
        int confirm = JOptionPane.showConfirmDialog(this,
                "Delete " + obj.getType() + " (" + obj.getCategory() + ")?",
                "Confirm Delete", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        sarManager.removeObject(obj.getId());
        tableModel.fireTableDataChanged();
        if (pushpinLayer != null) pushpinLayer.repaint();
    }

    // --- Table model ---

    private class SARTableModel extends AbstractTableModel {

        private static final String[] COLUMNS = {
                "Type", "Category", "Found By", "Time (UTC)", "Notes"
        };

        @Override
        public int getRowCount() {
            return sarManager.getObjects().size();
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
            SARObject obj = sarManager.getObjects().get(row);
            switch (col) {
                case 0: return obj.getType();
                case 1: return obj.getCategory();
                case 2: return obj.getFoundBy();
                case 3:
                    if (obj.getTimestamp() > 0) {
                        synchronized (UTC_FORMAT) {
                            return UTC_FORMAT.format(new Date(obj.getTimestamp()));
                        }
                    }
                    return "";
                case 4: return obj.getNotes();
                default: return null;
            }
        }
    }

    // --- Category color swatch renderer ---

    private static class CategoryColorRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);

            if (value instanceof String) {
                String category = (String) value;
                Color c = SARObjectManager.getCategoryColor(category);
                label.setOpaque(true);
                if (!isSelected) {
                    label.setBackground(c);
                } else {
                    label.setBackground(c.darker());
                }
                label.setForeground(isDark(c) ? Color.WHITE : Color.BLACK);
            }
            return label;
        }

        private static boolean isDark(Color c) {
            double luma = 0.299 * c.getRed() + 0.587 * c.getGreen()
                    + 0.114 * c.getBlue();
            return luma < 128;
        }
    }
}
