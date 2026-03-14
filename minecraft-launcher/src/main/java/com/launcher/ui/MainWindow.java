package com.launcher.ui;

import com.google.gson.JsonObject;
import com.launcher.auth.AuthResult;
import com.launcher.game.*;
import com.launcher.model.Profile;
import com.launcher.profile.ProfileManager;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;

public class MainWindow extends JFrame {

    private static final Color GREEN      = new Color(80, 200, 80);
    private static final Color GREEN_HOV  = new Color(55, 170, 55);

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------
    private final Path gameDir;
    private final ProfileManager profileManager;
    private final VersionManager versionManager = new VersionManager();
    private List<VersionManager.VersionInfo> versionList = new ArrayList<>();
    private Profile selectedProfile;

    // -------------------------------------------------------------------------
    // UI components
    // -------------------------------------------------------------------------
    private final DefaultListModel<Profile> profileListModel = new DefaultListModel<>();
    private final JList<Profile> profileList  = new JList<>(profileListModel);

    private final JTextField profileNameField = new JTextField();
    private final JComboBox<VersionManager.VersionInfo> versionCombo = new JComboBox<>();
    private final JSpinner ramSpinner =
            new JSpinner(new SpinnerNumberModel(2048, 512, 32768, 256));

    private final JLabel     authStatusLabel      = new JLabel("Brak nicku");
    private final JLabel     avatarLabel          = new JLabel();
    private final JButton    offlineButton        = new JButton("Ustaw nick");
    private final JButton    logoutButton         = new JButton("Zmień nick");
    private final JCheckBox  fabricCheckBox       = new JCheckBox("Użyj Fabric");
    private final JCheckBox  closeOnStartCheckBox = new JCheckBox("Zamknij launcher po uruchomieniu gry");
    private final JCheckBox  showSnapshotsCheckBox = new JCheckBox("Pokaż snapshoty");
    private final JTextField customJvmArgsField   = new JTextField();
    private final JButton    playButton           = new JButton("GRAJ");

    private final JTextArea console   = new JTextArea();
    private final JLabel    statusBar = new JLabel(" Gotowy");

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------
    public MainWindow() {
        super("Minecraft Launcher");
        this.gameDir = resolveGameDir();
        this.profileManager = new ProfileManager(gameDir);
        profileManager.load();

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(920, 660);
        setMinimumSize(new Dimension(700, 520));
        setLocationRelativeTo(null);

        buildUi();
        loadProfiles();
        fetchVersionsAsync();
    }

    // =========================================================================
    // UI construction
    // =========================================================================

    private void buildUi() {
        setLayout(new BorderLayout());
        add(buildHeader(),  BorderLayout.NORTH);

        JSplitPane center = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                buildProfilePanel(), buildSettingsPanel());
        center.setDividerLocation(210);
        center.setDividerSize(3);
        add(center, BorderLayout.CENTER);

        add(buildBottomPanel(), BorderLayout.SOUTH);
    }

    // ---- Header ----
    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(28, 28, 28));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(60, 60, 60)),
                new EmptyBorder(14, 20, 14, 20)));

        JLabel icon = new JLabel("⛏");
        icon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 26));
        icon.setBorder(new EmptyBorder(0, 0, 0, 10));

        JLabel title = new JLabel("Minecraft Launcher");
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        title.setForeground(new Color(200, 200, 80));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        left.add(icon);
        left.add(title);
        header.add(left, BorderLayout.WEST);

        JLabel sub = new JLabel("Java Edition  ");
        sub.setForeground(UIManager.getColor("Label.disabledForeground"));
        sub.setFont(sub.getFont().deriveFont(11f));
        header.add(sub, BorderLayout.EAST);

        return header;
    }

    // ---- Profile list ----
    private JPanel buildProfilePanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(new EmptyBorder(12, 10, 10, 6));

        JLabel lbl = new JLabel("Profile");
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 13f));
        panel.add(lbl, BorderLayout.NORTH);

        profileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        profileList.setFixedCellHeight(32);
        profileList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) onProfileSelected(profileList.getSelectedValue());
        });
        panel.add(new JScrollPane(profileList), BorderLayout.CENTER);

        JPanel btns = new JPanel(new GridLayout(1, 2, 6, 0));
        btns.setOpaque(false);
        JButton addBtn = new JButton("+ Dodaj");
        JButton delBtn = new JButton("− Usuń");
        addBtn.addActionListener(e -> addProfile());
        delBtn.addActionListener(e -> deleteProfile());
        btns.add(addBtn);
        btns.add(delBtn);
        panel.add(btns, BorderLayout.SOUTH);

        return panel;
    }

    // ---- Settings (right side) ----
    private JPanel buildSettingsPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(12, 6, 10, 12));

        // --- Form grid ---
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.fill = GridBagConstraints.HORIZONTAL;

        addFormRow(form, c, 0, "Nazwa profilu:", profileNameField);

        // Wersja + filtr snapshot w jednym wierszu
        JPanel versionPanel = new JPanel(new BorderLayout(4, 0));
        versionPanel.setOpaque(false);
        showSnapshotsCheckBox.setOpaque(false);
        showSnapshotsCheckBox.setToolTipText("Pokaż wersje snapshot (niestabilne)");
        showSnapshotsCheckBox.addActionListener(e -> applyVersionFilter());
        versionPanel.add(versionCombo, BorderLayout.CENTER);
        versionPanel.add(showSnapshotsCheckBox, BorderLayout.EAST);
        addFormRow(form, c, 1, "Wersja:", versionPanel);

        addFormRow(form, c, 2, "RAM (MB):", ramSpinner);

        customJvmArgsField.setToolTipText("Np. -XX:+UseG1GC -Dfml.ignoreInvalidMinecraftCertificates=true");
        addFormRow(form, c, 3, "JVM args:", customJvmArgsField);

        // Auth section
        c.gridy = 4; c.gridx = 0; c.gridwidth = 2;
        form.add(new JSeparator(), c);

        c.gridy = 5; c.gridwidth = 1;
        form.add(new JLabel("Konto:"), c);
        c.gridx = 1;
        authStatusLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        avatarLabel.setPreferredSize(new Dimension(32, 32));
        avatarLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));
        JPanel accountRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        accountRow.setOpaque(false);
        accountRow.add(avatarLabel);
        accountRow.add(authStatusLabel);
        form.add(accountRow, c);

        c.gridy = 6; c.gridx = 0; c.gridwidth = 2;
        JPanel authRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        authRow.setOpaque(false);
        authRow.add(offlineButton);
        authRow.add(logoutButton);
        form.add(authRow, c);

        // Options section
        c.gridy = 7; c.gridx = 0; c.gridwidth = 2;
        form.add(new JSeparator(), c);

        c.gridy = 8;
        fabricCheckBox.setOpaque(false);
        fabricCheckBox.setToolTipText("Pobiera Fabric Loader + fabric-api + ModMenu automatycznie");
        form.add(fabricCheckBox, c);

        c.gridy = 9;
        closeOnStartCheckBox.setOpaque(false);
        form.add(closeOnStartCheckBox, c);

        offlineButton.addActionListener(e -> doOfflineMode());
        logoutButton.addActionListener(e -> doChangeNick());
        logoutButton.setVisible(false);

        panel.add(form, BorderLayout.NORTH);

        JButton saveBtn = new JButton("Zapisz profil");
        saveBtn.addActionListener(e -> saveCurrentProfile());
        JPanel saveRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        saveRow.setOpaque(false);
        saveRow.add(saveBtn);
        panel.add(saveRow, BorderLayout.SOUTH);

        return panel;
    }

    // ---- Bottom: console + play bar ----
    private JPanel buildBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // Console
        console.setEditable(false);
        console.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        console.setForeground(new Color(180, 255, 180));
        console.setBackground(new Color(18, 18, 18));
        JScrollPane consoleSp = new JScrollPane(console);
        consoleSp.setPreferredSize(new Dimension(0, 130));
        consoleSp.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0,
                        UIManager.getColor("Separator.foreground")),
                " Konsola gry ",
                TitledBorder.LEFT, TitledBorder.TOP,
                UIManager.getFont("small.font")));
        panel.add(consoleSp, BorderLayout.CENTER);

        // Play bar
        JPanel playBar = new JPanel(new BorderLayout(10, 0));
        playBar.setBorder(new EmptyBorder(8, 12, 10, 12));

        statusBar.setForeground(UIManager.getColor("Label.disabledForeground"));
        statusBar.setFont(statusBar.getFont().deriveFont(11f));

        JButton openFolderBtn = new JButton("Folder gry");
        openFolderBtn.setToolTipText(gameDir.toString());
        openFolderBtn.setFocusPainted(false);
        openFolderBtn.addActionListener(e -> openGameFolder());

        JPanel leftBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftBar.setOpaque(false);
        leftBar.add(statusBar);
        leftBar.add(openFolderBtn);
        playBar.add(leftBar, BorderLayout.WEST);

        // Big green PLAY button
        playButton.setPreferredSize(new Dimension(170, 44));
        playButton.setFont(new Font("Segoe UI", Font.BOLD, 15));
        playButton.setForeground(Color.WHITE);
        playButton.setBackground(GREEN);
        playButton.setFocusPainted(false);
        playButton.setBorderPainted(false);
        playButton.setOpaque(true);
        playButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        playButton.putClientProperty("JButton.buttonType", "roundRect");
        playButton.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { playButton.setBackground(GREEN_HOV); }
            public void mouseExited(MouseEvent  e) { playButton.setBackground(GREEN); }
        });
        playButton.addActionListener(e -> doPlay());
        playBar.add(playButton, BorderLayout.EAST);

        panel.add(playBar, BorderLayout.SOUTH);
        return panel;
    }

    // =========================================================================
    // Profile actions
    // =========================================================================

    private void loadProfiles() {
        profileListModel.clear();
        for (Profile p : profileManager.getProfiles()) profileListModel.addElement(p);
        if (profileListModel.isEmpty()) {
            Profile def = new Profile("Domyślny");
            profileManager.addProfile(def);
            profileListModel.addElement(def);
        }
        profileList.setSelectedIndex(0);
    }

    private void onProfileSelected(Profile p) {
        selectedProfile = p;
        if (p == null) return;
        profileNameField.setText(p.getName());
        ramSpinner.setValue(p.getAllocatedRam());
        fabricCheckBox.setSelected(p.isFabricEnabled());
        closeOnStartCheckBox.setSelected(p.isCloseLauncherOnStart());
        customJvmArgsField.setText(p.getCustomJvmArgs());
        updateAuthUi(p);
        selectVersionInCombo(p.getSelectedVersion());
    }

    private void addProfile() {
        String name = JOptionPane.showInputDialog(this,
                "Podaj nazwę nowego profilu:", "Nowy profil", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.isBlank()) return;
        Profile p = new Profile(name.trim());
        profileManager.addProfile(p);
        profileListModel.addElement(p);
        profileList.setSelectedValue(p, true);
    }

    private void deleteProfile() {
        Profile p = profileList.getSelectedValue();
        if (p == null) return;
        if (profileListModel.size() == 1) { showError("Nie można usunąć ostatniego profilu."); return; }
        if (JOptionPane.showConfirmDialog(this, "Usunąć profil \"" + p.getName() + "\"?",
                "Potwierdź", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        profileManager.removeProfile(p);
        profileListModel.removeElement(p);
        profileList.setSelectedIndex(Math.max(0, profileListModel.size() - 1));
    }

    private void saveCurrentProfile() {
        if (selectedProfile == null) return;
        selectedProfile.setName(profileNameField.getText().trim());
        selectedProfile.setAllocatedRam((int) ramSpinner.getValue());
        selectedProfile.setFabricEnabled(fabricCheckBox.isSelected());
        selectedProfile.setCloseLauncherOnStart(closeOnStartCheckBox.isSelected());
        selectedProfile.setCustomJvmArgs(customJvmArgsField.getText().trim());
        VersionManager.VersionInfo vi = (VersionManager.VersionInfo) versionCombo.getSelectedItem();
        if (vi != null) selectedProfile.setSelectedVersion(vi.id);
        profileManager.updateProfile();
        profileList.repaint();
        setStatus("Zapisano: " + selectedProfile.getName());
    }

    // =========================================================================
    // Versions
    // =========================================================================

    private void fetchVersionsAsync() {
        setStatus("Ładowanie listy wersji...");
        new Thread(() -> {
            try {
                List<VersionManager.VersionInfo> list = versionManager.getVersionList();
                SwingUtilities.invokeLater(() -> {
                    versionList = list;
                    applyVersionFilter();
                    setStatus("Załadowano " + list.size() + " wersji.");
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> setStatus("Błąd wersji: " + ex.getMessage()));
            }
        }, "version-fetch").start();
    }

    private void applyVersionFilter() {
        VersionManager.VersionInfo current = (VersionManager.VersionInfo) versionCombo.getSelectedItem();
        boolean snapshots = showSnapshotsCheckBox.isSelected();
        versionCombo.removeAllItems();
        for (VersionManager.VersionInfo v : versionList) {
            if ("release".equals(v.type) || snapshots) versionCombo.addItem(v);
        }
        // Restore selection
        if (current != null) {
            for (int i = 0; i < versionCombo.getItemCount(); i++) {
                if (versionCombo.getItemAt(i).id.equals(current.id)) {
                    versionCombo.setSelectedIndex(i);
                    return;
                }
            }
        }
        if (selectedProfile != null) selectVersionInCombo(selectedProfile.getSelectedVersion());
    }

    private void selectVersionInCombo(String versionId) {
        if (versionId == null) return;
        for (int i = 0; i < versionCombo.getItemCount(); i++) {
            if (versionCombo.getItemAt(i).id.equals(versionId)) {
                versionCombo.setSelectedIndex(i);
                return;
            }
        }
    }

    // =========================================================================
    // Auth
    // =========================================================================

    private void doOfflineMode() {
        String nick = JOptionPane.showInputDialog(this,
                "Podaj nick (tryb offline):", "Tryb offline", JOptionPane.PLAIN_MESSAGE);
        if (nick == null || nick.isBlank()) return;
        nick = nick.trim();
        if (nick.length() < 3 || nick.length() > 16) { showError("Nick musi mieć 3–16 znaków."); return; }

        java.util.UUID uuid = java.util.UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + nick).getBytes(StandardCharsets.UTF_8));

        if (selectedProfile == null) return;
        selectedProfile.setUsername(nick);
        selectedProfile.setUuid(uuid.toString().replace("-", ""));
        selectedProfile.setAccessToken("offline_token");
        selectedProfile.setType("offline");
        profileManager.updateProfile();
        updateAuthUi(selectedProfile);
        setStatus("Tryb offline: " + nick);
    }

    private void doChangeNick() {
        doOfflineMode();
    }

    private void updateAuthUi(Profile p) {
        if (p.getUsername() != null && !p.getUsername().isBlank()) {
            authStatusLabel.setText(p.getUsername() + "  (Offline)");
            authStatusLabel.setForeground(GREEN);
            offlineButton.setVisible(false);
            logoutButton.setVisible(true);
            loadAvatarAsync(p.getUsername());
        } else {
            authStatusLabel.setText("Brak nicku");
            authStatusLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
            offlineButton.setVisible(true);
            logoutButton.setVisible(false);
            avatarLabel.setIcon(null);
        }
    }

    private void loadAvatarAsync(String username) {
        avatarLabel.setIcon(null);
        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection)
                    new URL("https://mc-heads.net/avatar/" + username + "/32").openConnection();
                conn.setRequestProperty("User-Agent", "NextLauncher/1.0");
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);
                conn.connect();
                if (conn.getResponseCode() == 200) {
                    BufferedImage avatar = ImageIO.read(conn.getInputStream());
                    if (avatar != null) {
                        BufferedImage composite = compositeWithBadge(avatar);
                        SwingUtilities.invokeLater(() -> avatarLabel.setIcon(new ImageIcon(composite)));
                    }
                }
            } catch (Exception ignored) {}
        }, "avatar-fetch").start();
    }

    /** Overlays a small NextLauncher badge on the bottom-right of the avatar. */
    private static BufferedImage compositeWithBadge(BufferedImage avatar) {
        int size = 32;
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Draw scaled avatar
        g.drawImage(avatar.getScaledInstance(size, size, Image.SCALE_SMOOTH), 0, 0, null);

        // Badge: rounded green rect, bottom-right corner
        int bw = 12, bh = 10, bx = size - bw, by = size - bh;
        // dark outline
        g.setColor(new Color(0, 0, 0, 180));
        g.fill(new RoundRectangle2D.Float(bx - 1, by - 1, bw + 2, bh + 2, 4, 4));
        // green fill
        g.setColor(new Color(60, 185, 60));
        g.fill(new RoundRectangle2D.Float(bx, by, bw, bh, 4, 4));
        // "NL" text
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 7));
        FontMetrics fm = g.getFontMetrics();
        String txt = "NL";
        g.drawString(txt, bx + (bw - fm.stringWidth(txt)) / 2, by + (bh + fm.getAscent() - fm.getDescent()) / 2 - 1);

        g.dispose();
        return out;
    }

    // =========================================================================
    // Play
    // =========================================================================

    private void doPlay() {
        if (selectedProfile == null)
            { showError("Wybierz profil."); return; }
        if (selectedProfile.getUsername() == null || selectedProfile.getUsername().isBlank())
            { showError("Najpierw ustaw nick (przycisk \"Ustaw nick\")."); return; }
        VersionManager.VersionInfo vi = (VersionManager.VersionInfo) versionCombo.getSelectedItem();
        if (vi == null) { showError("Wybierz wersję Minecrafta."); return; }

        // Snapshot current UI state into profile before launching
        selectedProfile.setSelectedVersion(vi.id);
        selectedProfile.setAllocatedRam((int) ramSpinner.getValue());
        selectedProfile.setFabricEnabled(fabricCheckBox.isSelected());
        selectedProfile.setCloseLauncherOnStart(closeOnStartCheckBox.isSelected());
        profileManager.updateProfile();

        boolean useFabric      = selectedProfile.isFabricEnabled();
        boolean closeOnLaunch  = selectedProfile.isCloseLauncherOnStart();

        playButton.setEnabled(false);
        console.setText("");

        new Thread(() -> {
            try {
                // --- 1. Vanilla version data ---
                setStatus("Pobieranie danych wersji " + vi.id + "...");
                JsonObject versionData = versionManager.getVersionData(vi.url);

                // --- 2. Download/verify vanilla game files ---
                {
                    DownloadDialog dlg = new DownloadDialog(this);
                    SwingUtilities.invokeLater(() -> dlg.setVisible(true));

                    GameDownloader dl = new GameDownloader(gameDir);
                    dl.setStatusCallback(msg -> { dlg.setStatus(msg); setStatus(msg); });
                    dl.setProgressCallback(dlg::setProgress);
                    dl.downloadVersion(versionData, vi.id);

                    SwingUtilities.invokeLater(() -> dlg.dispose());
                    if (dlg.isCancelled()) {
                        setStatus("Pobieranie anulowane.");
                        SwingUtilities.invokeLater(() -> playButton.setEnabled(true));
                        return;
                    }
                }

                // --- 3. Fabric (optional) ---
                JsonObject launchProfile = versionData;
                if (useFabric) {
                    FabricManager fabric = new FabricManager();

                    setStatus("Pobieranie Fabric dla " + vi.id + "...");
                    String loaderVersion = fabric.getLatestLoaderVersion(vi.id);
                    appendConsole("Fabric Loader: " + loaderVersion + "\n");

                    JsonObject fabricProfile = fabric.getFabricProfile(vi.id, loaderVersion);

                    setStatus("Pobieranie bibliotek Fabric...");
                    fabric.downloadFabricLibraries(fabricProfile, gameDir,
                            msg -> { setStatus(msg); appendConsole(msg + "\n"); });

                    // Mandatory mods: fabric-api + modmenu (always present, can't be removed)
                    setStatus("Sprawdzanie obowiązkowych modów...");
                    Path modsDir = gameDir.resolve("mods");
                    new ModrinthClient(gameDir).ensureMandatoryMods(vi.id, modsDir,
                            msg -> { setStatus(msg); appendConsole(msg + "\n"); });

                    // NextLauncher mod – adds [NL] badge to nametags of other NL users
                    NLModInstaller.ensureInstalled(gameDir,
                            msg -> { setStatus(msg); appendConsole(msg + "\n"); });

                    launchProfile = fabric.mergeProfiles(versionData, fabricProfile);
                    appendConsole("Fabric gotowy. Uruchamianie...\n");
                }

                // --- 4. Launch ---
                setStatus("Uruchamianie " + vi.id + (useFabric ? " + Fabric" : "") + "...");
                AuthResult auth = new AuthResult(
                        selectedProfile.getUsername(), selectedProfile.getUuid(),
                        selectedProfile.getAccessToken(), selectedProfile.isMicrosoft());

                Process proc = new GameLauncher(gameDir).launch(launchProfile, auth, selectedProfile);

                setStatus("Minecraft uruchomiony.");
                appendConsole(">>> Minecraft " + vi.id
                        + (useFabric ? " + Fabric" : "") + " uruchomiony <<<\n");

                // --- 5. Close launcher if requested ---
                if (closeOnLaunch) SwingUtilities.invokeLater(() -> setVisible(false));

                new Thread(() -> pipeStream(proc.getInputStream()), "mc-out").start();

                int code = proc.waitFor();
                appendConsole("\n>>> Zakończono (kod: " + code + ") <<<\n");
                setStatus("Minecraft zakończył działanie (kod " + code + ").");

                // Restore window if it was hidden
                if (closeOnLaunch) SwingUtilities.invokeLater(() -> setVisible(true));

            } catch (Exception ex) {
                setStatus("Błąd: " + ex.getMessage());
                appendConsole("BŁĄD: " + ex.getMessage() + "\n");
                ex.printStackTrace();
            } finally {
                SwingUtilities.invokeLater(() -> playButton.setEnabled(true));
            }
        }, "launch-thread").start();
    }

    private void pipeStream(InputStream is) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                final String l = line;
                SwingUtilities.invokeLater(() -> appendConsole(l + "\n"));
            }
        } catch (IOException ignored) {}
    }

    private void appendConsole(String text) {
        console.append(text);
        console.setCaretPosition(console.getDocument().getLength());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void addFormRow(JPanel form, GridBagConstraints c, int row,
                             String labelText, JComponent field) {
        c.gridy = row; c.gridx = 0; c.gridwidth = 1; c.weightx = 0;
        form.add(new JLabel(labelText), c);
        c.gridx = 1; c.weightx = 1;
        form.add(field, c);
    }

    private void setStatus(String msg) {
        SwingUtilities.invokeLater(() -> statusBar.setText(" " + msg));
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Błąd", JOptionPane.ERROR_MESSAGE);
    }

    private void openGameFolder() {
        try {
            Files.createDirectories(gameDir);
            java.awt.Desktop.getDesktop().open(gameDir.toFile());
        } catch (Exception ex) {
            showError("Nie można otworzyć folderu:\n" + ex.getMessage());
        }
    }

    private static Path resolveGameDir() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win"))  return Paths.get(System.getenv("APPDATA"), ".nextlauncher");
        if (os.contains("mac"))  return Paths.get(System.getProperty("user.home"),
                "Library", "Application Support", "nextlauncher");
        return Paths.get(System.getProperty("user.home"), ".nextlauncher");
    }
}
