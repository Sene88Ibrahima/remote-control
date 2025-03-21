package client;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.io.*;
import java.util.ArrayList;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import javax.swing.text.JTextComponent;

public class ClientGUI extends JFrame {
    private JTextField commandField;
    private JTextArea resultArea;
    private JButton sendButton;
    private JButton connectButton;
    private JButton disconnectButton;
    private JTextField hostField;
    private JTextField portField;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JComboBox<String> historyCombo;
    private ArrayList<String> commandHistory;
    private Client client;
    private JButton uploadButton;
    private JButton downloadButton;
    private JButton listFilesButton;
    private JFileChooser fileChooser;
    private JList<String> fileList;
    private DefaultListModel<String> fileListModel;
    private Theme currentTheme;
    private JMenuBar menuBar;
    private int port;

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

    public ClientGUI() {
        this.currentTheme = Theme.LIGHT;
        this.commandHistory = new ArrayList<>();
        
        setTitle("Remote Control Client");
        setSize(1200, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(1000, 700));

        // Création de la barre de menu
        createMenuBar();

        // Initialisation du file chooser
        fileChooser = new JFileChooser();
        fileChooser.setCurrentDirectory(new File(System.getProperty("user.home")));
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setMultiSelectionEnabled(false);

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

        setJMenuBar(menuBar);
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

    private void initializeComponents() {
        // Configuration des composants avec style moderne
        hostField = createStyledTextField("localhost", 15);
        portField = createStyledTextField("12345", 5);
        usernameField = createStyledTextField("", 10);
        passwordField = new JPasswordField(10);
        styleTextField(passwordField);

        connectButton = createStyledButton("Connecter", currentTheme.getAccentColor());
        disconnectButton = createStyledButton("Déconnecter", new Color(231, 76, 60));
        disconnectButton.setEnabled(false);
        sendButton = createStyledButton("Envoyer", new Color(46, 204, 113));
        sendButton.setEnabled(false);

        // Zone de résultat avec style moderne et taille augmentée
        resultArea = new JTextArea();
        resultArea.setEditable(false);
        resultArea.setFont(new Font("Consolas", Font.PLAIN, 14));
        resultArea.setBackground(currentTheme.getBackgroundColor());
        resultArea.setForeground(currentTheme.getTextColor());
        resultArea.setCaretColor(currentTheme.getTextColor());
        resultArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        resultArea.setLineWrap(true);
        resultArea.setWrapStyleWord(true);
        resultArea.setRows(20);

        // Historique stylisé
        historyCombo = new JComboBox<>();
        historyCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setFont(new Font("Arial", Font.PLAIN, 12));
                setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
                return c;
            }
        });

        // Initialiser les boutons de transfert de fichiers
        uploadButton = createStyledButton("Upload", currentTheme.getAccentColor());
        downloadButton = createStyledButton("Download", currentTheme.getAccentColor());
        listFilesButton = createStyledButton("Liste des fichiers", currentTheme.getAccentColor());
        fileListModel = new DefaultListModel<>();
        fileList = new JList<>(fileListModel);
        
        // Désactiver les boutons jusqu'à la connexion
        uploadButton.setEnabled(false);
        downloadButton.setEnabled(false);
        listFilesButton.setEnabled(false);
        
        // Créer le panneau pour les boutons de fichiers
        JPanel fileButtonsPanel = createStyledPanel(new FlowLayout(FlowLayout.LEFT));
        fileButtonsPanel.add(uploadButton);
        fileButtonsPanel.add(downloadButton);
        fileButtonsPanel.add(listFilesButton);
        
        // Layout principal avec padding
        setLayout(new BorderLayout(15, 15));
        
        // Panel de connexion avec effet de profondeur
        JPanel connectionPanel = createStyledPanel(new FlowLayout(FlowLayout.CENTER, 15, 15));
        connectionPanel.setBorder(BorderFactory.createCompoundBorder(
            new ShadowBorder(),
            BorderFactory.createEmptyBorder(20, 20, 20, 20)
        ));

        // Organisation des champs de connexion
        JPanel connectionFields = createStyledPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        
        addFieldWithLabel(connectionFields, "Host:", hostField, gbc, 0);
        addFieldWithLabel(connectionFields, "Port:", portField, gbc, 1);
        addFieldWithLabel(connectionFields, "Username:", usernameField, gbc, 2);
        addFieldWithLabel(connectionFields, "Password:", passwordField, gbc, 3);
        
        connectionPanel.add(connectionFields);
        connectionPanel.add(connectButton);
        connectionPanel.add(disconnectButton);
        add(connectionPanel, BorderLayout.NORTH);

        // Zone centrale avec effet de profondeur
        JPanel centerPanel = createStyledPanel(new BorderLayout(10, 10));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        
        JScrollPane scrollPane = new JScrollPane(resultArea);
        scrollPane.setBorder(new ShadowBorder());
        centerPanel.add(scrollPane, BorderLayout.CENTER);
        add(centerPanel, BorderLayout.CENTER);

        // Panel du bas avec effet de profondeur
        JPanel bottomPanel = createStyledPanel(new BorderLayout(10, 10));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(10, 15, 15, 15));

        // Panel pour l'historique
        JPanel historyPanel = createStyledPanel(new BorderLayout(10, 0));
        historyPanel.setBorder(new ShadowBorder());
        JLabel historyLabel = createStyledLabel(" Historique:");
        historyLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        historyPanel.add(historyLabel, BorderLayout.WEST);
        historyPanel.add(historyCombo, BorderLayout.CENTER);
        bottomPanel.add(historyPanel, BorderLayout.NORTH);

        // Panel pour les boutons de fichiers
        bottomPanel.add(fileButtonsPanel, BorderLayout.NORTH);

        // Ajouter la liste des fichiers
        JScrollPane scrollPaneFiles = new JScrollPane(fileList);
        scrollPaneFiles.setPreferredSize(new Dimension(300, 150));
        bottomPanel.add(scrollPaneFiles, BorderLayout.CENTER);

        // Panel pour la saisie de commande
        JPanel commandPanel = createStyledPanel(new BorderLayout(10, 0));
        commandPanel.setBorder(new ShadowBorder());
        commandField = createStyledTextField("", 20);
        commandPanel.add(commandField, BorderLayout.CENTER);
        commandPanel.add(sendButton, BorderLayout.EAST);
        bottomPanel.add(commandPanel, BorderLayout.SOUTH);

        add(bottomPanel, BorderLayout.SOUTH);

        // Actions
        connectButton.addActionListener(e -> connectToServer());
        disconnectButton.addActionListener(e -> disconnectFromServer());
        
        sendButton.addActionListener(e -> {
            String command = commandField.getText();
            if (!command.isEmpty()) {
                sendCommand(command);
            }
        });

        historyCombo.addActionListener(e -> {
            String selectedCommand = (String) historyCombo.getSelectedItem();
            if (selectedCommand != null) {
                commandField.setText(selectedCommand);
            }
        });

        // Actions pour les boutons de fichiers
        uploadButton.addActionListener(e -> uploadFile());
        downloadButton.addActionListener(e -> downloadFile());
        listFilesButton.addActionListener(e -> refreshFileList());
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

    private void applyTheme(Theme theme) {
        this.currentTheme = theme;
        
        // Mise à jour des couleurs de base
        getContentPane().setBackground(theme.getBackgroundColor());
        
        // Mise à jour des composants
        resultArea.setBackground(theme.getBackgroundColor());
        resultArea.setForeground(theme.getTextColor());
        resultArea.setCaretColor(theme.getTextColor());
        fileList.setBackground(theme.getBackgroundColor());
        fileList.setForeground(theme.getTextColor());
        
        // Mise à jour des boutons
        connectButton.setBackground(theme.getAccentColor());
        disconnectButton.setBackground(new Color(231, 76, 60));
        sendButton.setBackground(new Color(46, 204, 113));
        uploadButton.setBackground(theme.getAccentColor());
        downloadButton.setBackground(theme.getAccentColor());
        listFilesButton.setBackground(theme.getAccentColor());
        
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

    private void connectToServer() {
        String host = hostField.getText();
        String portStr = portField.getText();
        String username = usernameField.getText();
        String password = new String(passwordField.getPassword());
        
        try {
            // Vérifier le port
            int port = Integer.parseInt(portStr);
            if (port < 1 || port > 65535) {
                showError("Le port doit être entre 1 et 65535");
                return;
            }
            
            // Vérifier l'adresse IP
            if (host.isEmpty()) {
                showError("Veuillez entrer une adresse IP");
                return;
            }
            
            // Vérifier les identifiants
            if (username.isEmpty() || password.isEmpty()) {
                showError("Veuillez entrer un nom d'utilisateur et un mot de passe");
                return;
            }
            
            client = new Client(host, port);
            client.connect();
            
            // Authentifier le client
            if (client.authenticate(username, password)) {
                showSuccess("Connecté au serveur " + host + ":" + port);
                connectButton.setEnabled(false);
                disconnectButton.setEnabled(true);
                sendButton.setEnabled(true);
                uploadButton.setEnabled(true);
                downloadButton.setEnabled(true);
                listFilesButton.setEnabled(true);
                hostField.setEnabled(false);
                portField.setEnabled(false);
                usernameField.setEnabled(false);
                passwordField.setEnabled(false);
                refreshFileList();
            } else {
                showError("Échec de l'authentification");
                client.disconnect();
            }
        } catch (NumberFormatException e) {
            showError("Le port doit être un nombre");
        } catch (SocketTimeoutException e) {
            showError("Délai d'attente de connexion dépassé");
        } catch (ConnectException e) {
            showError("Impossible de se connecter au serveur. Vérifiez que le serveur est en cours d'exécution.");
        } catch (Exception e) {
            showError("Erreur de connexion: " + e.getMessage());
        }
    }

    private void disconnectFromServer() {
        if (client != null) {
            try {
                client.disconnect();
                showSuccess("Déconnecté du serveur");
                connectButton.setEnabled(true);
                disconnectButton.setEnabled(false);
                sendButton.setEnabled(false);
                uploadButton.setEnabled(false);
                downloadButton.setEnabled(false);
                listFilesButton.setEnabled(false);
                hostField.setEnabled(true);
                portField.setEnabled(true);
                usernameField.setEnabled(true);
                passwordField.setEnabled(true);
                fileListModel.clear();
            } catch (Exception e) {
                showError("Erreur lors de la déconnexion: " + e.getMessage());
            }
        }
    }

    private void sendCommand(String command) {
        if (client != null) {
            try {
                String result = client.sendCommand(command);
                SwingUtilities.invokeLater(() -> {
                    resultArea.append("> " + command + "\n");
                    if (result != null && !result.trim().isEmpty()) {
                        resultArea.append(result + "\n");
                    }
                    resultArea.setCaretPosition(resultArea.getDocument().getLength());
                    commandField.setText("");
                    
                    // Ajouter à l'historique
                    if (!commandHistory.contains(command)) {
                        commandHistory.add(command);
                        historyCombo.addItem(command);
                    }
                });
            } catch (SocketTimeoutException e) {
                showError("La commande a pris trop de temps à s'exécuter");
            } catch (Exception e) {
                showError("Erreur lors de l'envoi de la commande: " + e.getMessage());
            }
        }
    }

    private void refreshFileList() {
        if (client != null) {
            try {
                String result = client.sendCommand("list_files");
                fileListModel.clear();
                String[] files = result.split("\n");
                for (String file : files) {
                    if (!file.trim().isEmpty()) {
                        fileListModel.addElement(file.trim());
                    }
                }
            } catch (Exception e) {
                showError("Erreur lors de la mise à jour de la liste des fichiers: " + e.getMessage());
            }
        }
    }

    private void uploadFile() {
        if (client == null) {
            showError("Non connecté au serveur");
            return;
        }

        int returnVal = fileChooser.showOpenDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                client.uploadFile(file.getAbsolutePath());
                showSuccess("Fichier uploadé avec succès: " + file.getName());
                refreshFileList();
            } catch (SocketTimeoutException e) {
                showError("Délai d'attente de transfert dépassé");
            } catch (IOException e) {
                showError("Erreur lors de l'upload: " + e.getMessage());
            }
        }
    }

    private void downloadFile() {
        if (client == null) {
            showError("Non connecté au serveur");
            return;
        }

        String selectedFile = fileList.getSelectedValue();
        if (selectedFile == null) {
            showError("Veuillez sélectionner un fichier à télécharger");
            return;
        }

        // Vérifier si c'est un fichier (pas un dossier)
        if (selectedFile.contains("(Dossier)")) {
            showError("Impossible de télécharger un dossier");
            return;
        }

        // Extraire le nom du fichier (enlever le type entre parenthèses)
        String fileName = selectedFile.split(" \\(")[0].trim();
        
        // Créer un FileChooser avec le nom du fichier par défaut
        fileChooser.setSelectedFile(new File(fileName));
        
        int returnVal = fileChooser.showSaveDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                resultArea.append("Tentative de téléchargement de " + fileName + "...\n");
                resultArea.append("Destination: " + file.getAbsolutePath() + "\n");
                client.downloadFile(fileName, file.getAbsolutePath());
                showSuccess("Fichier téléchargé avec succès: " + fileName);
                resultArea.append("Emplacement: " + file.getAbsolutePath() + "\n");
                
                // Vérifier si le fichier existe réellement
                if (file.exists()) {
                    showSuccess("Le fichier a été créé avec succès");
                } else {
                    showError("Le fichier n'a pas été créé à l'emplacement spécifié");
                }
            } catch (SocketTimeoutException e) {
                showError("Délai d'attente de transfert dépassé");
            } catch (IOException e) {
                showError("Erreur lors du téléchargement: " + e.getMessage());
            }
        }
    }

    private void showError(String message) {
        SwingUtilities.invokeLater(() -> {
            resultArea.append("❌ " + message + "\n");
            resultArea.setCaretPosition(resultArea.getDocument().getLength());
        });
    }

    private void showSuccess(String message) {
        SwingUtilities.invokeLater(() -> {
            resultArea.append("✅ " + message + "\n");
            resultArea.setCaretPosition(resultArea.getDocument().getLength());
        });
    }

    private JTextField createStyledTextField(String text, int columns) {
        JTextField field = new JTextField(text, columns);
        styleTextField(field);
        return field;
    }

    private void styleTextField(JTextComponent field) {
        field.setFont(new Font("Arial", Font.PLAIN, 12));
        field.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(200, 200, 200)),
            BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));
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
        button.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        
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

    private void addFieldWithLabel(JPanel panel, String labelText, JComponent field, GridBagConstraints gbc, int row) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.anchor = GridBagConstraints.EAST;
        JLabel label = new JLabel(labelText);
        label.setFont(new Font("Arial", Font.BOLD, 12));
        panel.add(label, gbc);

        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(field, gbc);
    }

    // Classe pour l'effet d'ombre
    private class ShadowBorder extends AbstractBorder {
        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(x, y, width-1, height-1, 10, 10);
            g2.setColor(new Color(0, 0, 0, 20));
            g2.drawRoundRect(x, y, width-1, height-1, 10, 10);
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(5, 5, 5, 5);
        }
    }
} 
 