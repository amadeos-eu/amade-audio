package eu.amadeos.apps.audio.ui.options;

import eu.amadeos.apps.audio.domain.AudioPreferences;
import eu.amadeos.apps.audio.domain.RecordingSettings;
import eu.amadeos.commons.i18n.Texts;
import eu.amadeos.apps.audio.ui.widget.Scale;
import eu.amadeos.commons.i18n.LanguageChooser;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;

import static eu.amadeos.commons.i18n.Texts.tr;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Settings with a Category menu, as in the CDE applications: Recording, Display (with the language) and
 * Network, which only shows the proxy of the desktop and the way to Settings &gt; Network. Pages are named by id
 * ({@code Recording}, {@code Display}, {@code Network}) and shown translated.
 */
public final class OptionsDialog extends JDialog {
    /** LCD colour ids as stored in audio.conf; the names come from {@code options.lcd.<id>}. */
    private static final String[] LCD_COLORS = {"theme", "green", "amber", "blue", "red", "white"};
    private static final String[] PAGES = {"Recording", "Display", "Network"};

    private final CardLayout cards = new CardLayout();
    private final JPanel pages = new JPanel(cards);
    private final JComboBox<String> category = new JComboBox<>(PAGES);
    private final JTextField directory = new JTextField(30);
    private final JTextField pattern = new JTextField(30);
    private final JCheckBox split = new JCheckBox(tr("options.recording.split"));
    private final JSpinner maxMinutes = new JSpinner(new SpinnerNumberModel(120, 0, 24 * 60, 15));
    private final JComboBox<String> lcdColor = new JComboBox<>(LCD_COLORS);
    private final JComboBox<Integer> size = new JComboBox<>();
    private final LanguageChooser language;

    /**
     * @param currentProxy    the proxy in use, e.g. {@code proxy.example:3128} or "direct connection"
     * @param networkSettings opens Settings &gt; Network; null outside amadeOS
     * @param apply           receives the new settings
     */
    public OptionsDialog(Window owner, AudioPreferences prefs, String page, String currentProxy,
                         Runnable networkSettings, Consumer<AudioPreferences> apply, LocaleSettings locales) {
        super(owner, tr("options.title"), ModalityType.APPLICATION_MODAL);
        language = new LanguageChooser(locales, Messages.commons(Texts.messages().locale()));
        RecordingSettings r = prefs.recording();
        directory.setText(r.directory().toString());
        pattern.setText(r.pattern());
        split.setSelected(r.splitByTitle());
        maxMinutes.setValue(r.maxMinutes());
        lcdColor.setSelectedIndex(Math.max(0, java.util.Arrays.asList(LCD_COLORS).indexOf(prefs.lcdColor())));
        lcdColor.setRenderer(translated("options.lcd."));
        category.setRenderer(translated("options.page."));
        size.addItem(AudioPreferences.AUTO);
        for (int s : AudioPreferences.SIZES) size.addItem(s);
        size.setSelectedItem(prefs.size());
        size.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                          boolean focus) {
                String text = value == null ? ""
                        : value.equals(AudioPreferences.AUTO) ? tr("options.display.size.auto", Scale.fittingMainScreen())
                        : tr("options.display.size.value", value);
                return super.getListCellRendererComponent(list, text, index, selected, focus);
            }
        });

        pages.add(recordingPage(), "Recording");
        pages.add(displayPage(), "Display");
        pages.add(networkPage(currentProxy, networkSettings), "Network");
        category.addActionListener(e -> cards.show(pages, (String) category.getSelectedItem()));
        category.setSelectedItem(page != null && (page.equals("Display") || page.equals("Network")) ? page : "Recording");
        cards.show(pages, (String) category.getSelectedItem());

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JLabel categoryLabel = new JLabel(tr("options.category"));
        categoryLabel.setLabelFor(category);
        top.add(categoryLabel);
        top.add(category);

        JButton ok = new JButton(UIManager.getString("OptionPane.okButtonText"));
        JButton cancel = new JButton(UIManager.getString("OptionPane.cancelButtonText"));
        ok.addActionListener(e -> {
            Path dir;
            try {
                dir = Path.of(directory.getText().strip());
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(this, tr("options.recording.invalid-folder", directory.getText()), getTitle(),
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
            RecordingSettings settings = new RecordingSettings(dir, pattern.getText(), split.isSelected(),
                    (Integer) maxMinutes.getValue());
            String color = LCD_COLORS[Math.max(0, lcdColor.getSelectedIndex())];
            int newSize = (Integer) size.getSelectedItem();
            apply.accept(prefs.withRecording(settings).withLcdColor(color).withSize(newSize));
            dispose();
            boolean told = applyLanguage(locales);
            if (newSize != prefs.size() && !told) {
                JOptionPane.showMessageDialog(getOwner(), tr("options.display.size.restart", tr("app.title")), getTitle(),
                        JOptionPane.INFORMATION_MESSAGE);
            }
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

    /** Stores the language and says when it takes effect; true if it said so. */
    private boolean applyLanguage(LocaleSettings locales) {
        if (language.selectedLanguage().equals(locales.applicationLanguage())) return false;
        try {
            locales.setApplicationLanguage(language.selectedLanguage());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(getOwner(), e.getMessage(), getTitle(), JOptionPane.ERROR_MESSAGE);
            return false;
        }
        if (!locales.restartRequired()) return false;
        JOptionPane.showMessageDialog(getOwner(),
                Messages.commons(Texts.messages().locale()).format("language.restart", tr("app.title")),
                getTitle(), JOptionPane.INFORMATION_MESSAGE);
        return true;
    }

    /** Shows ids through the texts {@code prefix + id}. */
    private static ListCellRenderer<Object> translated(String prefix) {
        return new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                          boolean focus) {
                return super.getListCellRendererComponent(list, value == null ? "" : tr(prefix + value), index,
                        selected, focus);
            }
        };
    }

    private JPanel recordingPage() {
        JButton browse = new JButton(tr("options.browse"));
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
                + tr("options.recording.hint") + "</html>");
        hint.setFont(hint.getFont().deriveFont(Font.PLAIN, 11f));
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        row(form, c, 0, tr("options.recording.folder"), dirRow);
        row(form, c, 1, tr("options.recording.file-name"), pattern);
        row(form, c, 2, "", hint);
        row(form, c, 3, "", split);
        JPanel minutes = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        minutes.add(maxMinutes);
        minutes.add(new JLabel(tr("options.recording.minutes")));
        row(form, c, 4, tr("options.recording.stop-after"), minutes);
        return form;
    }

    private JPanel displayPage() {
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.NORTHWEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        row(form, c, 0, tr("options.display.lcd"), lcdColor);
        row(form, c, 1, tr("options.display.size"), size);
        row(form, c, 2, Messages.commons(Texts.messages().locale()).get("language.label").replace("&", ""), language);
        c.gridy = 3;
        c.weighty = 1;
        form.add(Box.createVerticalGlue(), c);
        return form;
    }

    private JPanel networkPage(String currentProxy, Runnable networkSettings) {
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.NORTHWEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.weightx = 1;
        c.gridy = 0;
        form.add(new JLabel(tr("options.network.now", currentProxy)), c);
        c.gridy = 1;
        form.add(new JLabel("<html><body style='width: 320px'>"
                + tr(networkSettings != null ? "options.network.desktop" : "options.network.other") + "</body></html>"), c);
        if (networkSettings != null) {
            JButton open = new JButton(tr("options.network.open"));
            open.addActionListener(e -> networkSettings.run());
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            row.add(open);
            c.gridy = 2;
            form.add(row, c);
        }
        c.gridy = 3;
        c.weighty = 1;
        form.add(Box.createVerticalGlue(), c);
        return form;
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
