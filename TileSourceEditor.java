package org.ka2ddo.yaac.gui.tile;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Modal dialog for editing a single tile source's properties.
 */
public class TileSourceEditor extends JDialog implements ActionListener {

    private final JTextField nameField;
    private final JTextField cacheFileField;
    private final JTextField urlTemplateField;
    private final JTextField apiKeyField;
    private final JTextField attributionField;
    private final JSpinner minZoomSpinner;
    private final JSpinner maxZoomSpinner;
    private final JCheckBox enabledCheckbox;
    private boolean saved = false;
    private final TileSource source;

    /**
     * Create editor dialog for a tile source.
     *
     * @param parent  parent window
     * @param source  the source to edit (fields will be modified in place if saved)
     * @param isNew   true if adding a new source (changes title)
     */
    public TileSourceEditor(Window parent, TileSource source, boolean isNew) {
        super(parent, isNew ? "Add Map Source" : "Edit Map Source",
                Dialog.ModalityType.APPLICATION_MODAL);
        this.source = source;
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        // Name
        gbc.gridx = 0; gbc.gridy = 0; gbc.fill = GridBagConstraints.NONE;
        formPanel.add(new JLabel("Name:"), gbc);
        nameField = new JTextField(source.getName(), 30);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        formPanel.add(nameField, gbc);

        // Cache File
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        formPanel.add(new JLabel("Cache File:"), gbc);
        cacheFileField = new JTextField(source.getCacheFile(), 30);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        formPanel.add(cacheFileField, gbc);

        // URL Template
        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        formPanel.add(new JLabel("URL Template:"), gbc);
        urlTemplateField = new JTextField(source.getUrlTemplate(), 30);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        formPanel.add(urlTemplateField, gbc);

        // API Key
        gbc.gridx = 0; gbc.gridy = 3; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        formPanel.add(new JLabel("API Key:"), gbc);
        apiKeyField = new JTextField(source.getApiKey(), 30);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        formPanel.add(apiKeyField, gbc);

        // Attribution
        gbc.gridx = 0; gbc.gridy = 4; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        formPanel.add(new JLabel("Attribution:"), gbc);
        attributionField = new JTextField(source.getAttribution(), 30);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        formPanel.add(attributionField, gbc);

        // Zoom range
        gbc.gridx = 0; gbc.gridy = 5; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        formPanel.add(new JLabel("Min Zoom:"), gbc);
        JPanel zoomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        minZoomSpinner = new JSpinner(new SpinnerNumberModel(source.getMinZoom(), 0, 25, 1));
        zoomPanel.add(minZoomSpinner);
        zoomPanel.add(new JLabel("  Max Zoom:"));
        maxZoomSpinner = new JSpinner(new SpinnerNumberModel(source.getMaxZoom(), 0, 25, 1));
        zoomPanel.add(maxZoomSpinner);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        formPanel.add(zoomPanel, gbc);

        // Enabled
        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 2;
        enabledCheckbox = new JCheckBox("Enabled", source.isEnabled());
        formPanel.add(enabledCheckbox, gbc);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveButton = new JButton("Save");
        saveButton.setActionCommand("save");
        saveButton.addActionListener(this);
        JButton cancelButton = new JButton("Cancel");
        cancelButton.setActionCommand("cancel");
        cancelButton.addActionListener(this);
        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);

        Container contentPane = getContentPane();
        contentPane.setLayout(new BorderLayout());
        contentPane.add(formPanel, BorderLayout.CENTER);
        contentPane.add(buttonPanel, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(saveButton);

        pack();
        setLocationRelativeTo(parent);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if ("save".equals(e.getActionCommand())) {
            String name = nameField.getText().trim();
            String urlTemplate = urlTemplateField.getText().trim();

            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Name is required.",
                        "Validation", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (urlTemplate.isEmpty()) {
                JOptionPane.showMessageDialog(this, "URL Template is required.",
                        "Validation", JOptionPane.WARNING_MESSAGE);
                return;
            }

            source.setName(name);
            source.setCacheFile(cacheFileField.getText().trim());
            source.setUrlTemplate(urlTemplate);
            source.setApiKey(apiKeyField.getText().trim());
            source.setAttribution(attributionField.getText().trim());
            source.setMinZoom((Integer) minZoomSpinner.getValue());
            source.setMaxZoom((Integer) maxZoomSpinner.getValue());
            source.setEnabled(enabledCheckbox.isSelected());
            saved = true;
        }
        setVisible(false);
        dispose();
    }

    /**
     * Whether the user clicked Save (vs Cancel or close).
     */
    public boolean isSaved() {
        return saved;
    }
}
