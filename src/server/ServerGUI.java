package server;
import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.util.Map;
import server.Logger;

public class ServerGUI extends JFrame {
    private JTextArea logArea;
    private JButton startStopButton;
    private JList<String> clientList;
    private DefaultListModel<String> clientListModel;
    private Server server;
    private boolean isRunning;
    private int port;
    private JButton manageUsersButton;
    private JButton settingsButton;
    private Theme currentTheme;
    private JMenuBar menuBar;

    private enum Theme {
        LIGHT("Clair", new Color(240, 242, 245), new Color(24, 119, 242), Color.BLACK),
        DARK("Sombre", new Color(44, 62, 80), new Color(52, 152, 219), Color.WHITE),
        DARKER("Plus sombre", new Color(30, 39, 46), new Color(0, 123, 255), Color.WHITE);

        private final String name;
        private final Color backgroundColor;
        private final Color accentColor;
        private final Color textColor;

        Theme(String name, Color backgroundColor, Color accentColor, Color textColor) {
            this.name = name;
            this.backgroundColor = backgroundColor;
            this.accentColor = accentColor;
            this.textColor = textColor;
        }

        public String getName() { return name; }
        public Color getBackgroundColor() { return backgroundColor; }
        public Color getAccentColor() { return accentColor; }
        public Color getTextColor() { return textColor; }
    }

    public ServerGUI(int port) {
        this.port = port;
        this.isRunning = false;
        this.currentTheme = Theme.LIGHT;
        
        setTitle("Serveur de Contrôle à Distance");
        setSize(1024, 768);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(800, 600));

        // Création de la barre de menu
        createMenuBar();

        // Configuration des composants
        initializeComponents();
        
        // Application du thème initial
        applyTheme(currentTheme);
    }

    private void createMenuBar() {
        menuBar = new JMenuBar();
        
        // Menu Fichier
        JMenu fileMenu = new JMenu("Fichier");
        JMenuItem exitItem = new JMenuItem("Quitter");
        exitItem.addActionListener(e -> System.exit(0));
        fileMenu.add(exitItem);
        menuBar.add(fileMenu);

        // Menu Thème
        JMenu themeMenu = new JMenu("Thème");
        for (Theme theme : Theme.values()) {
            JMenuItem themeItem = new JMenuItem(theme.getName());
            themeItem.addActionListener(e -> applyTheme(theme));
            themeMenu.add(themeItem);
        }
        menuBar.add(themeMenu);

        // Menu Configuration
        JMenu configMenu = new JMenu("Configuration");
        JMenuItem settingsItem = new JMenuItem("Paramètres");
        settingsItem.addActionListener(e -> showSettingsDialog());
        configMenu.add(settingsItem);
        menuBar.add(configMenu);

        setJMenuBar(menuBar);
    }

    private void initializeComponents() {
        // Configuration des composants
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        
        clientListModel = new DefaultListModel<>();
        clientList = new JList<>(clientListModel);
        clientList.setFont(new Font("Arial", Font.PLAIN, 12));
        
        startStopButton = createStyledButton("Démarrer le serveur", currentTheme.getAccentColor());
        manageUsersButton = createStyledButton("Gérer les utilisateurs", currentTheme.getAccentColor());
        settingsButton = createStyledButton("Paramètres", currentTheme.getAccentColor());

        // Layout
        setLayout(new BorderLayout(10, 10));
        
        // Panel de gauche pour la liste des clients
        JPanel leftPanel = createStyledPanel(new BorderLayout());
        leftPanel.add(createStyledLabel("Clients connectés:"), BorderLayout.NORTH);
        leftPanel.add(new JScrollPane(clientList), BorderLayout.CENTER);
        add(leftPanel, BorderLayout.WEST);

        // Zone centrale pour les logs
        JPanel centerPanel = createStyledPanel(new BorderLayout());
        centerPanel.add(createStyledLabel("Journal des commandes:"), BorderLayout.NORTH);
        centerPanel.add(new JScrollPane(logArea), BorderLayout.CENTER);
        add(centerPanel, BorderLayout.CENTER);

        // Panel du bas pour les boutons
        JPanel bottomPanel = createStyledPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        bottomPanel.add(startStopButton);
        bottomPanel.add(manageUsersButton);
        bottomPanel.add(settingsButton);
        add(bottomPanel, BorderLayout.SOUTH);

        // Actions des boutons
        startStopButton.addActionListener(e -> toggleServer());
        manageUsersButton.addActionListener(e -> showUserManagementDialog());
        settingsButton.addActionListener(e -> showSettingsDialog());
    }

    private JPanel createStyledPanel(LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setBackground(currentTheme.getBackgroundColor());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        return panel;
    }

    private JLabel createStyledLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(currentTheme.getTextColor());
        label.setFont(new Font("Arial", Font.BOLD, 12));
        return label;
    }

    private JButton createStyledButton(String text, Color color) {
        JButton button = new JButton(text);
        button.setBackground(color);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setOpaque(true);
        button.setFont(new Font("Arial", Font.BOLD, 12));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        
        // Effet de survol
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setBackground(color.brighter());
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setBackground(color);
            }
        });
        
        return button;
    }

    private void applyTheme(Theme theme) {
        this.currentTheme = theme;
        
        // Mise à jour des couleurs de base
        getContentPane().setBackground(theme.getBackgroundColor());
        
        // Mise à jour des composants
        logArea.setBackground(theme.getBackgroundColor());
        logArea.setForeground(theme.getTextColor());
        clientList.setBackground(theme.getBackgroundColor());
        clientList.setForeground(theme.getTextColor());
        
        // Mise à jour des boutons
        startStopButton.setBackground(theme.getAccentColor());
        manageUsersButton.setBackground(theme.getAccentColor());
        settingsButton.setBackground(theme.getAccentColor());
        
        // Mise à jour des labels
        updateAllLabels();
        
        // Mise à jour des menus
        updateMenuBar();
    }

    private void updateAllLabels() {
        for (Component c : getContentPane().getComponents()) {
            if (c instanceof JPanel) {
                updatePanelLabels((JPanel) c);
            }
        }
    }

    private void updatePanelLabels(JPanel panel) {
        for (Component c : panel.getComponents()) {
            if (c instanceof JLabel) {
                ((JLabel) c).setForeground(currentTheme.getTextColor());
            } else if (c instanceof JPanel) {
                updatePanelLabels((JPanel) c);
            }
        }
    }

    private void updateMenuBar() {
        menuBar.setBackground(currentTheme.getBackgroundColor());
        menuBar.setForeground(currentTheme.getTextColor());
        
        for (int i = 0; i < menuBar.getMenuCount(); i++) {
            JMenu menu = menuBar.getMenu(i);
            menu.setBackground(currentTheme.getBackgroundColor());
            menu.setForeground(currentTheme.getTextColor());
            
            for (int j = 0; j < menu.getItemCount(); j++) {
                JMenuItem item = menu.getItem(j);
                if (item != null) {
                    item.setBackground(currentTheme.getBackgroundColor());
                    item.setForeground(currentTheme.getTextColor());
                }
            }
        }
    }

    private void showSettingsDialog() {
        JDialog dialog = new JDialog(this, "Paramètres", true);
        dialog.setSize(400, 300);
        dialog.setLocationRelativeTo(this);

        JPanel panel = createStyledPanel(new BorderLayout(10, 10));
        
        // Configuration du port
        JPanel portPanel = createStyledPanel(new FlowLayout(FlowLayout.LEFT));
        portPanel.add(createStyledLabel("Port du serveur:"));
        JTextField portField = new JTextField(String.valueOf(port), 5);
        portField.setBackground(currentTheme.getBackgroundColor());
        portField.setForeground(currentTheme.getTextColor());
        portPanel.add(portField);
        
        panel.add(portPanel, BorderLayout.CENTER);
        
        // Boutons
        JPanel buttonPanel = createStyledPanel(new FlowLayout(FlowLayout.CENTER));
        JButton saveButton = createStyledButton("Sauvegarder", currentTheme.getAccentColor());
        JButton cancelButton = createStyledButton("Annuler", currentTheme.getAccentColor());
        
        saveButton.addActionListener(e -> {
            try {
                int newPort = Integer.parseInt(portField.getText());
                if (newPort < 1 || newPort > 65535) {
                    throw new NumberFormatException("Port invalide");
                }
                port = newPort;
                dialog.dispose();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(dialog, 
                    "Veuillez entrer un port valide (1-65535)",
                    "Erreur",
                    JOptionPane.ERROR_MESSAGE);
            }
        });
        
        cancelButton.addActionListener(e -> dialog.dispose());
        
        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        dialog.add(panel);
        dialog.setVisible(true);
    }

    private void toggleServer() {
        if (!isRunning) {
            server = new Server(port);
            server.setGUI(this);
            try {
                new Thread(() -> {
                    try {
                        server.start();
                    } catch (Exception e) {
                        logArea.append("Erreur lors du démarrage du serveur: " + e.getMessage() + "\n");
                    }
                }).start();
                startStopButton.setText("Arrêter le serveur");
                logArea.append("Serveur démarré sur le port " + port + "\n");
                isRunning = true;
            } catch (Exception e) {
                logArea.append("Erreur lors de l'initialisation du serveur: " + e.getMessage() + "\n");
            }
        } else {
            server.stop();
            startStopButton.setText("Démarrer le serveur");
            logArea.append("Serveur arrêté\n");
            isRunning = false;
            clientListModel.clear();
        }
    }

    public void addClient(String clientInfo) {
        SwingUtilities.invokeLater(() -> {
            clientListModel.addElement(clientInfo);
            logArea.append("Nouveau client connecté: " + clientInfo + "\n");
        });
    }

    public void removeClient(String clientInfo) {
        SwingUtilities.invokeLater(() -> {
            clientListModel.removeElement(clientInfo);
            logArea.append("Client déconnecté: " + clientInfo + "\n");
        });
    }

    public void addLog(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public void updateClientList(List<String> clients) {
        SwingUtilities.invokeLater(() -> {
            clientListModel.clear();
            for (String client : clients) {
                clientListModel.addElement(client);
            }
        });
    }

    public void addLogMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            // Ajouter un horodatage
            String timestamp = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
            
            // Formater le message avec des couleurs et styles différents selon le type
            if (message.contains("Commande reçue")) {
                logArea.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
                logArea.append("[" + timestamp + "] 📝 " + message + "\n");
            } else if (message.contains("Résultat de")) {
                logArea.append("[" + timestamp + "] 🔄 " + message + "\n");
                logArea.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
            } else if (message.contains("Connexion de")) {
                logArea.append("[" + timestamp + "] 🟢 " + message + "\n");
            } else if (message.contains("Déconnexion de")) {
                logArea.append("[" + timestamp + "] 🔴 " + message + "\n");
            } else {
                logArea.append("[" + timestamp + "] ℹ️ " + message + "\n");
            }
            
            // Faire défiler automatiquement vers le bas
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void showUserManagementDialog() {
        JDialog dialog = new JDialog(this, "Gestion des utilisateurs", true);
        dialog.setSize(400, 300);
        dialog.setLocationRelativeTo(this);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Liste des utilisateurs
        DefaultListModel<String> userListModel = new DefaultListModel<>();
        JList<String> userList = new JList<>(userListModel);
        panel.add(new JScrollPane(userList), BorderLayout.CENTER);

        // Charger la liste des utilisateurs existants
        try {
            File file = new File("users.credentials");
            if (file.exists()) {
                try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> users = (Map<String, Object>) ois.readObject();
                    for (String username : users.keySet()) {
                        userListModel.addElement(username);
                    }
                }
            }
        } catch (Exception e) {
            Logger.log("Erreur lors du chargement de la liste des utilisateurs: " + e.getMessage());
        }

        // Panel pour les boutons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton addButton = new JButton("Ajouter un utilisateur");
        JButton deleteButton = new JButton("Supprimer");
        buttonPanel.add(addButton);
        buttonPanel.add(deleteButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        // Action pour ajouter un utilisateur
        addButton.addActionListener(e -> {
            JDialog addDialog = new JDialog(dialog, "Ajouter un utilisateur", true);
            addDialog.setSize(300, 150);
            addDialog.setLocationRelativeTo(dialog);

            JPanel addPanel = new JPanel(new GridLayout(3, 2, 5, 5));
            addPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            JTextField usernameField = new JTextField();
            JPasswordField passwordField = new JPasswordField();
            JPasswordField confirmPasswordField = new JPasswordField();

            addPanel.add(new JLabel("Nom d'utilisateur:"));
            addPanel.add(usernameField);
            addPanel.add(new JLabel("Mot de passe:"));
            addPanel.add(passwordField);
            addPanel.add(new JLabel("Confirmer:"));
            addPanel.add(confirmPasswordField);

            JButton confirmButton = new JButton("Confirmer");
            confirmButton.addActionListener(evt -> {
                String username = usernameField.getText();
                String password = new String(passwordField.getPassword());
                String confirmPassword = new String(confirmPasswordField.getPassword());

                if (username.isEmpty() || password.isEmpty()) {
                    JOptionPane.showMessageDialog(addDialog, "Veuillez remplir tous les champs");
                    return;
                }

                if (!password.equals(confirmPassword)) {
                    JOptionPane.showMessageDialog(addDialog, "Les mots de passe ne correspondent pas");
                    return;
                }

                Authentication.addUser(username, password);
                userListModel.addElement(username);
                addDialog.dispose();
            });

            addPanel.add(confirmButton);
            addDialog.add(addPanel);
            addDialog.setVisible(true);
        });

        // Action pour supprimer un utilisateur
        deleteButton.addActionListener(e -> {
            String selectedUser = userList.getSelectedValue();
            if (selectedUser != null) {
                int confirm = JOptionPane.showConfirmDialog(dialog, 
                    "Êtes-vous sûr de vouloir supprimer l'utilisateur " + selectedUser + " ?",
                    "Confirmation",
                    JOptionPane.YES_NO_OPTION);
                
                if (confirm == JOptionPane.YES_OPTION) {
                    Authentication.deleteUser(selectedUser);
                    userListModel.removeElement(selectedUser);
                }
            }
        });

        dialog.add(panel);
        dialog.setVisible(true);
    }
} 