package com.launcher.ui;

import javax.swing.*;
import java.awt.*;

/** Progress dialog shown while downloading Minecraft files. */
public class DownloadDialog extends JDialog {

    private final JLabel statusLabel = new JLabel("Przygotowywanie...", SwingConstants.CENTER);
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private volatile boolean cancelled = false;

    public DownloadDialog(Frame parent) {
        super(parent, "Pobieranie Minecrafta", false);
        buildUi();
    }

    private void buildUi() {
        setSize(450, 160);
        setLocationRelativeTo(getOwner());
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

        statusLabel.setFont(statusLabel.getFont().deriveFont(12f));
        panel.add(statusLabel, BorderLayout.NORTH);

        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(400, 22));
        panel.add(progressBar, BorderLayout.CENTER);

        JButton cancelButton = new JButton("Anuluj");
        cancelButton.addActionListener(e -> {
            cancelled = true;
            dispose();
        });
        JPanel bottom = new JPanel();
        bottom.add(cancelButton);
        panel.add(bottom, BorderLayout.SOUTH);

        add(panel);
    }

    public void setStatus(String msg) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(msg));
    }

    /** @param current current item, total total items (0 = indeterminate) */
    public void setProgress(int current, int total) {
        SwingUtilities.invokeLater(() -> {
            if (total <= 0) {
                progressBar.setIndeterminate(true);
                progressBar.setString("");
            } else {
                progressBar.setIndeterminate(false);
                int pct = (int) (current * 100.0 / total);
                progressBar.setValue(pct);
                progressBar.setString(current + " / " + total);
            }
        });
    }

    public boolean isCancelled() { return cancelled; }
}
