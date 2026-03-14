package com.launcher.ui;

import com.launcher.auth.AuthResult;
import com.launcher.auth.MicrosoftAuth;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Modal dialog that opens the system browser for Microsoft login and
 * waits for the localhost OAuth callback to complete.
 */
public class AuthDialog extends JDialog {

    private AuthResult result;
    private Exception  error;

    private final JLabel        statusLabel = new JLabel("Otwieranie przeglądarki...", SwingConstants.CENTER);
    private final JProgressBar  progress    = new JProgressBar();
    private final JButton       cancelBtn   = new JButton("Anuluj");
    private final MicrosoftAuth auth        = new MicrosoftAuth();

    public AuthDialog(Frame parent) {
        super(parent, "Logowanie Microsoft", true);
        setSize(440, 190);
        setResizable(false);
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        buildUi();
    }

    private void buildUi() {
        JPanel panel = new JPanel(new BorderLayout(10, 14));
        panel.setBorder(new EmptyBorder(24, 24, 16, 24));

        JLabel title = new JLabel("Logowanie przez konto Microsoft", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        panel.add(title, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridLayout(3, 1, 0, 6));
        center.setOpaque(false);

        statusLabel.setFont(statusLabel.getFont().deriveFont(12f));
        center.add(statusLabel);

        JLabel hint = new JLabel(
                "<html><center><small>Zaloguj się na konto Microsoft w otwartej przeglądarce.</small></center></html>",
                SwingConstants.CENTER);
        hint.setForeground(UIManager.getColor("Label.disabledForeground"));
        center.add(hint);

        progress.setIndeterminate(true);
        center.add(progress);

        panel.add(center, BorderLayout.CENTER);

        cancelBtn.addActionListener(e -> { auth.cancel(); dispose(); });
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        bottom.add(cancelBtn);
        panel.add(bottom, BorderLayout.SOUTH);

        add(panel);
    }

    /**
     * Starts the browser OAuth flow in a background thread, then shows the
     * modal dialog (blocks until auth completes or is cancelled).
     */
    public void startAuth() {
        auth.setStatusCallback(msg ->
                SwingUtilities.invokeLater(() -> statusLabel.setText(msg)));

        new Thread(() -> {
            try   { result = auth.authenticate(); }
            catch (Exception ex) { error = ex; }
            SwingUtilities.invokeLater(this::dispose);
        }, "auth-thread").start();

        setVisible(true); // blocks until dispose()
    }

    public AuthResult getResult() { return result; }
    public Exception  getError()  { return error;  }
}
