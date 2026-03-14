package com.launcher.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class SplashScreen extends JWindow {

    public SplashScreen() {
        setSize(420, 230);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(new Color(22, 22, 22));
        root.setBorder(BorderFactory.createLineBorder(new Color(80, 200, 80), 2));

        // Center content
        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        center.setBorder(new EmptyBorder(24, 30, 16, 30));

        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0; gc.gridy = 0; gc.insets = new Insets(0, 0, 6, 0);

        JLabel icon = new JLabel("⛏");
        icon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 52));
        icon.setForeground(new Color(200, 200, 80));
        icon.setHorizontalAlignment(SwingConstants.CENTER);
        center.add(icon, gc);

        gc.gridy = 1; gc.insets = new Insets(0, 0, 4, 0);
        JLabel title = new JLabel("NextLauncher");
        title.setFont(new Font("Segoe UI", Font.BOLD, 30));
        title.setForeground(new Color(200, 200, 80));
        center.add(title, gc);

        gc.gridy = 2; gc.insets = new Insets(0, 0, 0, 0);
        JLabel sub = new JLabel("Minecraft Java Edition");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        sub.setForeground(new Color(120, 120, 120));
        center.add(sub, gc);

        root.add(center, BorderLayout.CENTER);

        // Progress bar at bottom
        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);
        bar.setBackground(new Color(35, 35, 35));
        bar.setForeground(new Color(80, 200, 80));
        bar.setBorderPainted(false);
        bar.setPreferredSize(new Dimension(0, 5));
        root.add(bar, BorderLayout.SOUTH);

        // Version label bottom-right
        JLabel ver = new JLabel("v1.0  ");
        ver.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        ver.setForeground(new Color(70, 70, 70));
        ver.setHorizontalAlignment(SwingConstants.RIGHT);
        root.add(ver, BorderLayout.NORTH);

        add(root);
    }

    /**
     * Shows the splash for {@code millis} ms, then runs {@code onDone} on the EDT.
     */
    public void showAndThen(int millis, Runnable onDone) {
        setVisible(true);
        new Thread(() -> {
            try { Thread.sleep(millis); } catch (InterruptedException ignored) {}
            SwingUtilities.invokeLater(() -> {
                dispose();
                onDone.run();
            });
        }, "splash-timer").start();
    }
}
