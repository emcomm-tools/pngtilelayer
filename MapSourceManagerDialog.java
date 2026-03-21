package org.ka2ddo.yaac.gui.tile;

import javax.swing.*;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;

/**
 * Main dialog for managing map tile sources.
 * Shows a table of all sources with Add/Edit/Delete and Default/Fallback controls.
 */
public class MapSourceManagerDialog extends JDialog implements ActionListener {

    private final TileSourceManager sourceManager;
    private final PNGTileLayer tileLayer;
    private final TileSourceTableModel tableModel;
    private final JTable sourceTable;

    public MapSourceManagerDialog(Window parent, TileSourceManager sourceManager,
                                  PNGTileLayer tileLayer) {
        super(parent, "Map Sources", Dialog.ModalityType.MODELESS);
        this.sourceManager = sourceManager;
        this.tileLayer = tileLayer;
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        tableModel = new TileSourceTableModel(sourceManager);
        sourceTable = new JTable(tableModel);
        sourceTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sourceTable.setRowHeight(22);

        // Set column widths
        TableColumnModel colModel = sourceTable.getColumnModel();
        colModel.getColumn(0).setPreferredWidth(160); // Name
        colModel.getColumn(1).setPreferredWidth(160); // Cache File
        colModel.getColumn(2).setPreferredWidth(35);  // Def
        colModel.getColumn(3).setPreferredWidth(35);  // FB

        // Double-click to edit
        sourceTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    editSelectedSource();
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(sourceTable);
        scrollPane.setPreferredSize(new Dimension(420, 180));

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 4));

        JButton addButton = new JButton("Add");
        addButton.setActionCommand("add");
        addButton.addActionListener(this);
        buttonPanel.add(addButton);

        JButton editButton = new JButton("Edit");
        editButton.setActionCommand("edit");
        editButton.addActionListener(this);
        buttonPanel.add(editButton);

        JButton deleteButton = new JButton("Delete");
        deleteButton.setActionCommand("delete");
        deleteButton.addActionListener(this);
        buttonPanel.add(deleteButton);

        buttonPanel.add(Box.createHorizontalStrut(12));

        JButton defaultButton = new JButton("Set Default");
        defaultButton.setActionCommand("setDefault");
        defaultButton.addActionListener(this);
        buttonPanel.add(defaultButton);

        JButton fallbackButton = new JButton("Set Fallback");
        fallbackButton.setActionCommand("setFallback");
        fallbackButton.addActionListener(this);
        buttonPanel.add(fallbackButton);

        buttonPanel.add(Box.createHorizontalStrut(12));

        JButton clearCacheButton = new JButton("Clear Cache");
        clearCacheButton.setActionCommand("clearCache");
        clearCacheButton.addActionListener(this);
        buttonPanel.add(clearCacheButton);

        JButton refreshCacheButton = new JButton("Refresh Cache");
        refreshCacheButton.setActionCommand("refreshCache");
        refreshCacheButton.addActionListener(this);
        buttonPanel.add(refreshCacheButton);

        // Layer toggle checkboxes
        JPanel checkboxPanel = new JPanel();
        checkboxPanel.setLayout(new BoxLayout(checkboxPanel, BoxLayout.Y_AXIS));

        JCheckBox osmCheckbox = new JCheckBox("Show Vector Map (OSM)",
                tileLayer != null && tileLayer.isOSMLayerVisible());
        osmCheckbox.addActionListener(ev -> {
            if (tileLayer != null) {
                tileLayer.setOSMLayerVisible(osmCheckbox.isSelected());
            }
        });
        checkboxPanel.add(osmCheckbox);

        StationPushpinLayer pinLayer = StationPushpinLayerCreator.getLastCreatedLayer();
        JCheckBox pushpinCheckbox = new JCheckBox("Show Pushpin Markers",
                pinLayer != null && pinLayer.isEnabled());
        pushpinCheckbox.addActionListener(ev -> {
            StationPushpinLayer pl = StationPushpinLayerCreator.getLastCreatedLayer();
            if (pl != null) {
                pl.setEnabled(pushpinCheckbox.isSelected());
            }
        });
        checkboxPanel.add(pushpinCheckbox);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(checkboxPanel, BorderLayout.WEST);
        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);

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
                addSource();
                break;
            case "edit":
                editSelectedSource();
                break;
            case "delete":
                deleteSelectedSource();
                break;
            case "setDefault":
                setDefault();
                break;
            case "setFallback":
                setFallback();
                break;
            case "clearCache":
                clearCache();
                break;
            case "refreshCache":
                refreshCache();
                break;
        }
    }

    private void addSource() {
        TileSource newSource = new TileSource();
        TileSourceEditor editor = new TileSourceEditor(this, newSource, true);
        editor.setVisible(true);
        if (editor.isSaved()) {
            sourceManager.addSource(newSource);
            tableModel.fireTableDataChanged();
            saveAndRefresh();
        }
    }

    private void editSelectedSource() {
        int row = sourceTable.getSelectedRow();
        if (row < 0) return;

        TileSource source = tableModel.getSourceAt(row);
        if (source == null) return;

        TileSourceEditor editor = new TileSourceEditor(this, source, false);
        editor.setVisible(true);
        if (editor.isSaved()) {
            refreshTable(row);
            saveAndRefresh();
        }
    }

    private void deleteSelectedSource() {
        int row = sourceTable.getSelectedRow();
        if (row < 0) return;

        TileSource source = tableModel.getSourceAt(row);
        if (source == null) return;

        int confirm = JOptionPane.showConfirmDialog(this,
                "Delete source \"" + source.getName() + "\"?",
                "Delete Map Source", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        sourceManager.removeSource(source);
        refreshTable(-1);
        saveAndRefresh();
    }

    private void setDefault() {
        int row = sourceTable.getSelectedRow();
        if (row < 0) return;

        TileSource source = tableModel.getSourceAt(row);
        if (source == null) return;

        sourceManager.setActiveSource(source);
        refreshTable(row);
        saveAndRefresh();

        // Switch tile layer to new default and repaint
        if (tileLayer != null) {
            tileLayer.setActiveSource(source);
        }
    }

    private void setFallback() {
        int row = sourceTable.getSelectedRow();
        if (row < 0) return;

        TileSource source = tableModel.getSourceAt(row);
        if (source == null) return;

        sourceManager.setFallbackSource(source);
        refreshTable(row);
        saveAndRefresh();
    }

    /**
     * Refresh the table and restore selection.
     */
    private void refreshTable(int selectedRow) {
        tableModel.fireTableDataChanged();
        if (selectedRow >= 0 && selectedRow < tableModel.getRowCount()) {
            sourceTable.setRowSelectionInterval(selectedRow, selectedRow);
        }
    }

    private void clearCache() {
        int row = sourceTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a source first.",
                    "Clear Cache", JOptionPane.WARNING_MESSAGE);
            return;
        }

        TileSource source = tableModel.getSourceAt(row);
        if (source == null || !source.hasCache()) {
            JOptionPane.showMessageDialog(this, "This source has no cache file.",
                    "Clear Cache", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Delete cache for \"" + source.getName() + "\"?\n" +
                "File: " + source.getCacheFile(),
                "Clear Cache", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        // Close DB connection and delete file
        if (tileLayer != null) {
            tileLayer.getTileCache().closeConnection(source.getCacheFile());
        }
        File cacheFile = new File(sourceManager.getCacheDirectory() + File.separator + source.getCacheFile());
        if (cacheFile.exists()) {
            cacheFile.delete();
        }

        if (tileLayer != null) {
            tileLayer.clearMemoryCache();
            tileLayer.startRegenerate();
        }

        JOptionPane.showMessageDialog(this, "Cache cleared for " + source.getName(),
                "Clear Cache", JOptionPane.INFORMATION_MESSAGE);
    }

    private void refreshCache() {
        if (tileLayer == null) return;

        int row = sourceTable.getSelectedRow();
        TileSource source;
        if (row >= 0) {
            source = tableModel.getSourceAt(row);
        } else {
            source = sourceManager.getActiveSource();
        }

        if (source == null) return;

        int confirm = JOptionPane.showConfirmDialog(this,
                "Re-download all visible tiles for \"" + source.getName() + "\"?\n" +
                "This will replace cached tiles with fresh downloads.",
                "Refresh Cache", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        // Clear memory cache and force re-download by clearing the DB cache for visible tiles
        if (source.hasCache()) {
            tileLayer.getTileCache().closeConnection(source.getCacheFile());
            File cacheFile = new File(sourceManager.getCacheDirectory() + File.separator + source.getCacheFile());
            if (cacheFile.exists()) {
                cacheFile.delete();
            }
        }

        tileLayer.clearMemoryCache();
        tileLayer.startRegenerate();

        JOptionPane.showMessageDialog(this, "Cache refreshed for " + source.getName() +
                "\nVisible tiles are being re-downloaded.",
                "Refresh Cache", JOptionPane.INFORMATION_MESSAGE);
    }

    private void saveAndRefresh() {
        sourceManager.saveSources();
    }
}
