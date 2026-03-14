package com.launcher;

import com.formdev.flatlaf.FlatDarkLaf;
import com.launcher.ui.MainWindow;
import com.launcher.ui.SplashScreen;
import com.launcher.update.AutoUpdater;
import javax.swing.*;
import java.awt.*;
import java.util.logging.*;

public class Main {
    public static void main(String[] args) {
        // Suppress FlatLaf native-access warning (cosmetic only)
        Logger.getLogger("com.formdev.flatlaf").setLevel(Level.SEVERE);

        // FlatLaf dark theme — modern, flat UI like IntelliJ/VSCode
        FlatDarkLaf.setup();

        // Accent colour: Minecraft green
        UIManager.put("Component.accentColor", new Color(80, 200, 80));
        UIManager.put("Button.arc", 8);
        UIManager.put("Component.arc", 8);
        UIManager.put("ProgressBar.arc", 8);
        UIManager.put("TextComponent.arc", 6);

        SwingUtilities.invokeLater(() -> {
            SplashScreen splash = new SplashScreen();
            splash.showAndThen(2200, () -> {
                MainWindow win = new MainWindow();
                win.setVisible(true);
                AutoUpdater.checkAsync(win); // check for updates after window is shown
            });
        });
    }
}
