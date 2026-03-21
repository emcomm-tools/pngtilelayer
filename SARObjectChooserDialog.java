package org.ka2ddo.yaac.gui.tile;

import com.bbn.openmap.proj.coords.LatLonPoint;
import com.bbn.openmap.proj.coords.UTMPoint;
import org.ka2ddo.yaac.YAAC;
import org.ka2ddo.yaac.ax25.StationState;
import org.ka2ddo.yaac.ax25.StationTracker;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Modal dialog for adding a SAR evidence object at a map position.
 * Category/type dropdowns driven by SARCatalog JSON.
 */
public class SARObjectChooserDialog extends JDialog implements ActionListener {

    private final SARCatalog catalog;
    private final double lat;
    private final double lon;

    private final JComboBox<String> categoryCombo;
    private final JComboBox<String> typeCombo;
    private final JComboBox<String> foundByCombo;
    private final JTextArea notesArea;
    private final JCheckBox broadcastCheckbox;

    private boolean confirmed = false;

    public SARObjectChooserDialog(Window parent, SARCatalog catalog,
                                   double lat, double lon) {
        super(parent, "Add SAR Object", Dialog.ModalityType.APPLICATION_MODAL);
        this.catalog = catalog;
        this.lat = lat;
        this.lon = lon;
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 4, 3, 4);
        gbc.anchor = GridBagConstraints.WEST;

        // Category
        gbc.gridx = 0; gbc.gridy = 0;
        mainPanel.add(new JLabel("Category:"), gbc);

        List<String> categories = catalog.getCategories();
        categoryCombo = new JComboBox<>(categories.toArray(new String[0]));
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        mainPanel.add(categoryCombo, gbc);

        // Type
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        mainPanel.add(new JLabel("Type:"), gbc);

        typeCombo = new JComboBox<>();
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        mainPanel.add(typeCombo, gbc);

        // Update type combo when category changes
        categoryCombo.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    updateTypes();
                }
            }
        });
        updateTypes();

        // Found By — editable combo populated from heard stations
        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        mainPanel.add(new JLabel("Found By:"), gbc);

        foundByCombo = new JComboBox<>();
        foundByCombo.setEditable(true);
        populateFoundByCombo();
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        mainPanel.add(foundByCombo, gbc);

        // Notes
        gbc.gridx = 0; gbc.gridy = 3; gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0; gbc.anchor = GridBagConstraints.NORTHWEST;
        mainPanel.add(new JLabel("Notes:"), gbc);

        notesArea = new JTextArea(3, 20);
        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);
        JScrollPane notesScroll = new JScrollPane(notesArea);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0; gbc.weighty = 1.0;
        mainPanel.add(notesScroll, gbc);

        // Position (UTM, read-only)
        gbc.gridx = 0; gbc.gridy = 4; gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0; gbc.weighty = 0; gbc.anchor = GridBagConstraints.WEST;
        mainPanel.add(new JLabel("Position:"), gbc);

        UTMPoint utm = new UTMPoint(new LatLonPoint.Double(lat, lon));
        String utmStr = utm.zone_number + "" + utm.zone_letter + " "
                + Math.round(utm.easting) + "E "
                + Math.round(utm.northing) + "N";
        JLabel posLabel = new JLabel(utmStr);
        posLabel.setFont(posLabel.getFont().deriveFont(Font.PLAIN));
        gbc.gridx = 1;
        mainPanel.add(posLabel, gbc);

        // Broadcast checkbox
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        broadcastCheckbox = new JCheckBox("Broadcast this object via APRS");
        mainPanel.add(broadcastCheckbox, gbc);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton okButton = new JButton("OK");
        okButton.setActionCommand("ok");
        okButton.addActionListener(this);
        JButton cancelButton = new JButton("Cancel");
        cancelButton.setActionCommand("cancel");
        cancelButton.addActionListener(this);
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);

        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.EAST;
        mainPanel.add(buttonPanel, gbc);

        getRootPane().setDefaultButton(okButton);

        getContentPane().add(mainPanel);
        setSize(380, 310);
        setLocationRelativeTo(parent);
    }

    private void populateFoundByCombo() {
        Set<String> callsigns = new LinkedHashSet<>();

        // Own callsign first
        try {
            String myCall = YAAC.getCallsign();
            if (myCall != null && !"NOCALL".equals(myCall)
                    && !myCall.trim().isEmpty()) {
                callsigns.add(myCall.toUpperCase().trim());
            }
        } catch (Exception ignored) {}

        // Heard stations
        try {
            StationState[] stations = StationTracker.getInstance()
                    .getCurrentTrackedObjectArray();
            if (stations != null) {
                for (StationState ss : stations) {
                    if (ss == null) continue;
                    String id = ss.getIdentifier();
                    if (id != null && !id.trim().isEmpty()) {
                        callsigns.add(id.toUpperCase().trim());
                    }
                }
            }
        } catch (Exception ignored) {}

        for (String cs : callsigns) {
            foundByCombo.addItem(cs);
        }
    }

    private void updateTypes() {
        String cat = (String) categoryCombo.getSelectedItem();
        typeCombo.removeAllItems();
        if (cat != null) {
            for (String type : catalog.getTypes(cat)) {
                typeCombo.addItem(type);
            }
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if ("ok".equals(e.getActionCommand())) {
            confirmed = true;
        }
        dispose();
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public String getCategory() {
        return (String) categoryCombo.getSelectedItem();
    }

    public String getSelectedType() {
        return (String) typeCombo.getSelectedItem();
    }

    public String getFoundBy() {
        Object item = foundByCombo.getEditor().getItem();
        return item != null ? item.toString().trim() : "";
    }

    public String getNotes() {
        return notesArea.getText().trim();
    }

    public boolean isBroadcast() {
        return broadcastCheckbox.isSelected();
    }

    public double getLat() { return lat; }
    public double getLon() { return lon; }
}
