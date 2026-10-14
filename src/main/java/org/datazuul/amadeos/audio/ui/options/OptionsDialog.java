package org.datazuul.amadeos.audio.ui.options;

import org.datazuul.amadeos.audio.domain.AudioPreferences;
import org.datazuul.amadeos.audio.domain.RecordingSettings;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Options with a Category menu, as in the CDE applications: Recording and Display. */
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
    private final JComboBox<String> category = new JComboBox<>(new String[]{"Recording", "Display"});
    private final JTextField directory = new JTextField(30);
    private final JTextField pattern = new JTextField(30);
    private final JCheckBox split = new JCheckBox("Start a new file when the title changes (MP3 and AAC)");
    private final JSpinner maxMinutes = new JSpinner(new SpinnerNumberModel(120, 0, 24 * 60, 15));
    private final JComboBox<String> lcdColor = new JComboBox<>(LCD_COLORS.values().toArray(new String[0]));

    public OptionsDialog(Window owner, AudioPreferences prefs, String page, Consumer<AudioPreferences> apply) {
        super(owner, "Audio Station - Options", ModalityType.APPLICATION_MODAL);
        RecordingSettings r = prefs.recording();
        directory.setText(r.directory().toString());
        pattern.setText(r.pattern());
        split.setSelected(r.splitByTitle());
        maxMinutes.setValue(r.maxMinutes());
        lcdColor.setSelectedIndex(Math.max(0, LCD_COLORS.keySet().stream().toList().indexOf(prefs.lcdColor())));

        pages.add(recordingPage(), "Recording");
        pages.add(displayPage(), "Display");
        category.addActionListener(e -> cards.show(pages, (String) category.getSelectedItem()));
        category.setSelectedItem(page != null && page.equals("Display") ? "Display" : "Recording");
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
            apply.accept(prefs.withRecording(settings).withLcdColor(color));
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
