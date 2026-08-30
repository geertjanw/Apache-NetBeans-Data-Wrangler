package org.data.wrangler.connection;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.openide.filesystems.FileChooserBuilder;

/** Swing form for the "New DuckDB Connection..." dialog. */
public final class DuckDBConnectionPanel extends JPanel {

 private final JRadioButton inMemory = new JRadioButton("In-memory database", true);
 private final JRadioButton file = new JRadioButton("Database file:");
 private final JTextField path = new JTextField(30);
 private final JButton browse = new JButton("Browse...");
 private final JCheckBox readOnly = new JCheckBox("Open read-only");
 private final JCheckBox limitThreads = new JCheckBox("Threads:");
 private final JSpinner threads = new JSpinner(new SpinnerNumberModel(
            Math.max(1, Runtime.getRuntime().availableProcessors()), 1, 1024, 1));
 private final JTextField memoryLimit = new JTextField(10);
 private final JTextField displayName = new JTextField(30);

 public DuckDBConnectionPanel() {
 super(new GridBagLayout());
        ButtonGroup g = new ButtonGroup();
 g.add(inMemory); g.add(file);
 threads.setEnabled(false);
 path.setEnabled(false); browse.setEnabled(false); readOnly.setEnabled(false);

        Runnable sync = () -> {
 boolean f = file.isSelected();
 path.setEnabled(f); browse.setEnabled(f); readOnly.setEnabled(f);
        };
 inMemory.addActionListener(e -> sync.run());
 file.addActionListener(e -> sync.run());
 limitThreads.addActionListener(e -> threads.setEnabled(limitThreads.isSelected()));
 browse.addActionListener(e -> {
            File f = new FileChooserBuilder(DuckDBConnectionPanel.class)
                    .setTitle("Select DuckDB database")
                    .setFilesOnly(true)
                    .setFileFilter(new FileNameExtensionFilter("DuckDB (*.duckdb, *.db)", "duckdb", "db"))
                    .setAcceptAllFileFilterUsed(true)
                    .showOpenDialog();
 if (f != null) path.setText(f.getAbsolutePath());
        });

 int row = 0;
 add(inMemory, gbc(0, row++, 3));
 add(file, gbc(0, row, 1)); add(path, gbc(1, row, 1)); add(browse, gbc(2, row++, 1));
 add(readOnly, gbc(1, row++, 2));
 add(new JLabel("Options"), gbc(0, row++, 3));
 add(limitThreads, gbc(0, row, 1)); add(threads, gbc(1, row++, 1));
 add(new JLabel("Memory limit (e.g. 4GB):"), gbc(0, row, 1)); add(memoryLimit, gbc(1, row++, 1));
 add(new JLabel("Display name:"), gbc(0, row, 1)); add(displayName, gbc(1, row++, 2));
    }

 public DuckDBConnectionSettings toSettings() {
        DuckDBConnectionSettings s = new DuckDBConnectionSettings();
 s.setMode(inMemory.isSelected() ? DuckDBConnectionSettings.Mode.IN_MEMORY : DuckDBConnectionSettings.Mode.FILE);
 s.setDatabasePath(path.getText());
 s.setReadOnly(readOnly.isSelected());
 s.setThreads(limitThreads.isSelected() ? (Integer) threads.getValue() : null);
 s.setMemoryLimit(memoryLimit.getText());
 s.setDisplayName(displayName.getText());
 return s;
    }

 private static GridBagConstraints gbc(int x, int y, int w) {
        GridBagConstraints c = new GridBagConstraints();
 c.gridx = x; c.gridy = y; c.gridwidth = w;
 c.anchor = GridBagConstraints.WEST;
 c.fill = GridBagConstraints.HORIZONTAL;
 c.weightx = x == 1 ? 1 : 0;
 c.insets = new Insets(4, 6, 4, 6);
 return c;
    }
}
