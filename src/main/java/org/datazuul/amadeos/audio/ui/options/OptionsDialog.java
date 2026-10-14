package org.datazuul.amadeos.audio.ui.options;

import org.datazuul.amadeos.audio.domain.AudioPreferences;
import org.datazuul.amadeos.audio.domain.ProxySettings;
import org.datazuul.amadeos.audio.domain.RecordingSettings;
import org.datazuul.amadeos.audio.service.net.NetworkProxy;
import org.datazuul.amadeos.commons.swing.EdtExecutor;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/** Settings with a Category menu, as in the CDE applications: Recording, Display and Network. */
public final class OptionsDialog extends JDialog {
    private static final Map<String, String> LCD_COLORS = new LinkedHashMap<>();

    static {
        LCD_COLORS.put("theme", "Follow the desktop theme");
        LCD_COLORS.put("green", "Green");
        LCD_COLORS.put("amber", "Amber");
        LCD_COLORS.put("blue", "Blue");
        LCD_COLORS.put("red", "Red");
        LCD_COLORS.put("white", "White");
    }

    private final CardLayout cards = new CardLayout();
    private final JPanel pages = new JPanel(cards);
    private final JComboBox<String> category = new JComboBox<>(new String[]{"Recording", "Display", "Network"});
    private final JTextField directory = new JTextField(30);
    private final JTextField pattern = new JTextField(30);
    private final JCheckBox split = new JCheckBox("Start a new file when the title changes (MP3 and AAC)");
    private final JSpinner maxMinutes = new JSpinner(new SpinnerNumberModel(120, 0, 24 * 60, 15));
    private final JComboBox<String> lcdColor = new JComboBox<>(LCD_COLORS.values().toArray(new String[0]));
    private final JRadioButton proxySystem = new JRadioButton("Use the system proxy (http_proxy, https_proxy)");
    private final JRadioButton proxyNone = new JRadioButton("No proxy");
    private final JRadioButton proxyManual = new JRadioButton("Manual proxy:");
    private final JTextField proxyHost = new JTextField(22);
    private final JSpinner proxyPort = new JSpinner(new SpinnerNumberModel(3128, 1, 65535, 1));
    private final JTextField proxyUser = new JTextField(16);
    private final JPasswordField proxyPassword = new JPasswordField(16);
    private final JTextField noProxy = new JTextField(30);
    private final JLabel testResult = new JLabel(" ");
    private final BiFunction<ProxySettings, String, String> tester;
    private final ExecutorService executor;
    private final EdtExecutor edt;

    /**
     * @param apply  receives the new settings and the proxy password (for the keyring)
     * @param tester tries proxy settings, returns a message; called on the executor
     */
    public OptionsDialog(Window owner, AudioPreferences prefs, String page, String currentProxy,
                         BiConsumer<AudioPreferences, String> apply, BiFunction<ProxySettings, String, String> tester,
                         ExecutorService executor, EdtExecutor edt) {
        super(owner, "Audio Station - Settings", ModalityType.APPLICATION_MODAL);
        this.tester = tester;
        this.executor = executor;
        this.edt = edt;
        RecordingSettings r = prefs.recording();
        directory.setText(r.directory().toString());
        pattern.setText(r.pattern());
        split.setSelected(r.splitByTitle());
        maxMinutes.setValue(r.maxMinutes());
        lcdColor.setSelectedIndex(Math.max(0, LCD_COLORS.keySet().stream().toList().indexOf(prefs.lcdColor())));

        pages.add(recordingPage(), "Recording");
        pages.add(displayPage(), "Display");
        pages.add(networkPage(prefs.proxy(), currentProxy), "Network");
        category.addActionListener(e -> cards.show(pages, (String) category.getSelectedItem()));
        category.setSelectedItem(page != null && (page.equals("Display") || page.equals("Network")) ? page : "Recording");
        cards.show(pages, (String) category.getSelectedItem());

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JLabel categoryLabel = new JLabel("Category:");
        categoryLabel.setLabelFor(category);
        top.add(categoryLabel);
        top.add(category);

        JButton ok = new JButton("OK");
        JButton cancel = new JButton("Cancel");
        ok.addActionListener(e -> {
            Path dir;
            try {
                dir = Path.of(directory.getText().strip());
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(this, "Invalid folder: " + directory.getText(), getTitle(), JOptionPane.ERROR_MESSAGE);
                return;
            }
            RecordingSettings settings = new RecordingSettings(dir, pattern.getText(), split.isSelected(),
                    (Integer) maxMinutes.getValue());
            String color = LCD_COLORS.keySet().stream().toList().get(Math.max(0, lcdColor.getSelectedIndex()));
            apply.accept(prefs.withRecording(settings).withLcdColor(color).withProxy(proxySettings()),
                    new String(proxyPassword.getPassword()));
            dispose();
        });
        cancel.addActionListener(e -> dispose());
        getRootPane().setDefaultButton(ok);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        buttons.add(ok);
        buttons.add(cancel);

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(top, BorderLayout.NORTH);
        content.add(pages, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        pack();
        setLocationRelativeTo(owner);
    }

    private JPanel recordingPage() {
        JButton browse = new JButton("Browse...");
        browse.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(directory.getText());
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                directory.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        JPanel dirRow = new JPanel(new BorderLayout(6, 0));
        dirRow.add(directory, BorderLayout.CENTER);
        dirRow.add(browse, BorderLayout.EAST);
        JLabel hint = new JLabel("<html>{station} {date} {time} {artist} {title}<br>"
                + "Files go into a folder per station. MP3 stays MP3, AAC is saved as .aac, Ogg as .ogg.</html>");
        hint.setFont(hint.getFont().deriveFont(Font.PLAIN, 11f));
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        row(form, c, 0, "Folder:", dirRow);
        row(form, c, 1, "File name:", pattern);
        row(form, c, 2, "", hint);
        row(form, c, 3, "", split);
        JPanel minutes = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        minutes.add(maxMinutes);
        minutes.add(new JLabel("minutes (0 = no limit)"));
        row(form, c, 4, "Stop after:", minutes);
        return form;
    }

    private JPanel displayPage() {
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.NORTHWEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        row(form, c, 0, "LCD colour:", lcdColor);
        c.gridy = 1;
        c.weighty = 1;
        form.add(Box.createVerticalGlue(), c);
        return form;
    }

    /** Password currently in use, shown masked so the user can keep or change it. */
    public void setProxyPassword(String password) {
        proxyPassword.setText(password == null ? "" : password);
    }

    private JPanel networkPage(ProxySettings proxy, String currentProxy) {
        ButtonGroup group = new ButtonGroup();
        for (JRadioButton b : new JRadioButton[]{proxySystem, proxyNone, proxyManual}) group.add(b);
        switch (proxy.mode()) {
            case NONE -> proxyNone.setSelected(true);
            case MANUAL -> proxyManual.setSelected(true);
            default -> proxySystem.setSelected(true);
        }
        proxyHost.setText(proxy.host());
        proxyPort.setValue(proxy.port());
        proxyUser.setText(proxy.user());
        noProxy.setText(proxy.noProxy());
        for (JRadioButton b : new JRadioButton[]{proxySystem, proxyNone, proxyManual}) b.addActionListener(e -> enableProxyFields());
        enableProxyFields();

        NetworkProxy detected = new NetworkProxy(System.getenv());
        detected.configure(new ProxySettings(ProxySettings.Mode.SYSTEM, "", 0, "", ""), "");
        JLabel systemHint = new JLabel("Found in the environment: " + detected.describe());
        systemHint.setFont(systemHint.getFont().deriveFont(Font.PLAIN, 11f));

        JPanel hostRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        hostRow.add(proxyHost);
        JLabel portLabel = new JLabel("Port:");
        portLabel.setLabelFor(proxyPort);
        hostRow.add(portLabel);
        hostRow.add(proxyPort);
        JButton test = new JButton("Test Connection");
        test.addActionListener(e -> {
            test.setEnabled(false);
            testResult.setText("Testing...");
            ProxySettings candidate = proxySettings();
            String password = new String(proxyPassword.getPassword());
            executor.execute(() -> {
                String result = tester.apply(candidate, password);
                edt.execute(() -> {
                    testResult.setText(result);
                    test.setEnabled(true);
                    pack();
                });
            });
        });
        JLabel noProxyHint = new JLabel("Comma-separated, e.g. localhost, .intranet.example");
        noProxyHint.setFont(noProxyHint.getFont().deriveFont(Font.PLAIN, 11f));
        JLabel inUse = new JLabel("In use now: " + currentProxy);
        inUse.setFont(inUse.getFont().deriveFont(Font.PLAIN, 11f));

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridwidth = 2;
        c.gridy = 0;
        form.add(proxySystem, c);
        c.gridy = 1;
        c.insets = new Insets(0, 28, 6, 4);
        form.add(systemHint, c);
        c.insets = new Insets(4, 4, 4, 4);
        c.gridy = 2;
        form.add(proxyNone, c);
        c.gridy = 3;
        form.add(proxyManual, c);
        c.gridwidth = 1;
        row(form, c, 4, "      Host:", hostRow);
        row(form, c, 5, "      User:", proxyUser);
        row(form, c, 6, "      Password:", proxyPassword);
        row(form, c, 7, "      No proxy for:", noProxy);
        row(form, c, 8, "", noProxyHint);
        JPanel testRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        testRow.add(test);
        testRow.add(testResult);
        c.gridy = 9;
        c.gridx = 0;
        c.gridwidth = 2;
        c.insets = new Insets(12, 4, 4, 4);
        form.add(testRow, c);
        c.gridy = 10;
        c.insets = new Insets(4, 4, 4, 4);
        form.add(inUse, c);
        return form;
    }

    private void enableProxyFields() {
        boolean manual = proxyManual.isSelected();
        for (JComponent f : new JComponent[]{proxyHost, proxyPort, proxyUser, proxyPassword, noProxy}) f.setEnabled(manual);
    }

    private ProxySettings proxySettings() {
        ProxySettings.Mode mode = proxyManual.isSelected() ? ProxySettings.Mode.MANUAL
                : proxyNone.isSelected() ? ProxySettings.Mode.NONE : ProxySettings.Mode.SYSTEM;
        return new ProxySettings(mode, proxyHost.getText(), (Integer) proxyPort.getValue(), proxyUser.getText(), noProxy.getText());
    }

    private static void row(JPanel form, GridBagConstraints c, int y, String label, JComponent field) {
        c.gridy = y;
        c.gridx = 0;
        c.weightx = 0;
        JLabel l = new JLabel(label);
        l.setLabelFor(field);
        form.add(l, c);
        c.gridx = 1;
        c.weightx = 1;
        form.add(field, c);
    }
}
