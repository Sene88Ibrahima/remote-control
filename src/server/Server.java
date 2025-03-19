import javax.swing.*;
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import server.Logger;
import common.SSLConfig;

public class Server {
    private int port;
    private boolean running;
    private SSLServerSocket serverSocket;
    private List<ClientHandler> clients;
    private ServerGUI gui;
    private String uploadDirectory;
    private static final int MAX_LOGIN_ATTEMPTS = 3;
    private static final int LOGIN_TIMEOUT = 300000; // 5 minutes
    private Map<String, LoginAttempt> loginAttempts = new ConcurrentHashMap<>();
    private final Object clientsLock = new Object();

    private static class LoginAttempt {
        int attempts;
        long lastAttempt;

        LoginAttempt() {
            this.attempts = 1;
            this.lastAttempt = System.currentTimeMillis();
        }

        boolean isBlocked() {
            if (attempts >= MAX_LOGIN_ATTEMPTS) {
                if (System.currentTimeMillis() - lastAttempt < LOGIN_TIMEOUT) {
                    return true;
                } else {
                    attempts = 0;
                    return false;
                }
            }
            return false;
        }

        void increment() {
            attempts++;
            lastAttempt = System.currentTimeMillis();
            Logger.warning("Tentative de connexion " + attempts + "/" + MAX_LOGIN_ATTEMPTS + " pour cette IP");
        }
    }

    public Server(int port) {
        this.port = port;
        this.clients = new ArrayList<>();
        this.uploadDirectory = System.getProperty("user.dir");
    }

    public String getUploadDirectory() {
        return uploadDirectory;
    }

    public void setUploadDirectory(String directory) {
        this.uploadDirectory = directory;
    }

    public void setGUI(ServerGUI gui) {
        this.gui = gui;
    }

    public void start() throws Exception {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Le port doit être entre 1 et 65535");
        }

        try {
            SSLServerSocketFactory ssf = SSLConfig.createServerSocketFactory();
            serverSocket = (SSLServerSocket) ssf.createServerSocket(port, 50, InetAddress.getByName("0.0.0.0"));
            running = true;
            Logger.log("Serveur SSL démarré sur le port " + port + " (écoute sur toutes les interfaces)");
            
            new Thread(() -> {
                while (running) {
                    try {
                        SSLSocket clientSocket = (SSLSocket) serverSocket.accept();
                        String clientIP = clientSocket.getInetAddress().getHostAddress();
                        
                        // Vérifier les tentatives de connexion
                        LoginAttempt attempt = loginAttempts.computeIfAbsent(clientIP, k -> new LoginAttempt());
                        if (attempt.isBlocked()) {
                            Logger.warning("Tentative de connexion bloquée pour " + clientIP + " (trop de tentatives)");
                            clientSocket.close();
                            continue;
                        }
                        
                        ClientHandler clientHandler = new ClientHandler(clientSocket, this);
                        synchronized(clientsLock) {
                            clients.add(clientHandler);
                        }
                        new Thread(clientHandler).start();
                        
                        if (gui != null) {
                            SwingUtilities.invokeLater(() -> {
                                gui.updateClientList(getClientList());
                                gui.addLogMessage("Nouveau client connecté: " + clientHandler.getClientInfo());
                            });
                        }
                        
                        Logger.log("Nouveau client connecté: " + clientIP);
                    } catch (IOException e) {
                        if (running) {
                            Logger.error("Erreur lors de l'acceptation d'une connexion: " + e.getMessage());
                        }
                    }
                }
            }).start();
        } catch (IOException e) {
            throw new IOException("Le port " + port + " est déjà utilisé par une autre application");
        }
    }

    public void stop() {
        running = false;
        synchronized(clientsLock) {
            for (ClientHandler client : new ArrayList<>(clients)) {
                try {
                    client.closeConnection();
                } catch (Exception e) {
                    Logger.error("Erreur lors de la déconnexion d'un client: " + e.getMessage());
                }
            }
            clients.clear();
        }
        
        try {
            if (serverSocket != null) {
                serverSocket.close();
                serverSocket = null;
            }
            Logger.log("Serveur arrêté");
        } catch (IOException e) {
            Logger.error("Erreur lors de l'arrêt du serveur: " + e.getMessage());
        }
    }

    public void removeClient(ClientHandler client) {
        synchronized(clientsLock) {
            clients.remove(client);
            if (gui != null) {
                SwingUtilities.invokeLater(() -> {
                    List<String> clientList = getClientList();
                    gui.updateClientList(clientList);
                });
            }
        }
    }

    public List<String> getClientList() {
        synchronized(clientsLock) {
            List<String> clientList = new ArrayList<>();
            for (ClientHandler client : clients) {
                clientList.add(client.getClientInfo());
            }
            return clientList;
        }
    }

    public void logCommand(String clientInfo, String command) {
        String logEntry = "Commande reçue de " + clientInfo + ": " + command;
        System.out.println(logEntry);
        Logger.log(logEntry);
        if (gui != null) {
            SwingUtilities.invokeLater(() -> gui.addLogMessage(logEntry));
        }
    }

    public void logConnection(String clientInfo) {
        String logEntry = "Connexion de " + clientInfo;
        System.out.println(logEntry);
        Logger.log(logEntry);
        if (gui != null) {
            SwingUtilities.invokeLater(() -> gui.addLogMessage(logEntry));
        }
    }

    public void logDisconnection(String clientInfo) {
        String logEntry = "Déconnexion de " + clientInfo;
        System.out.println(logEntry);
        Logger.log(logEntry);
        if (gui != null) {
            SwingUtilities.invokeLater(() -> gui.addLogMessage(logEntry));
        }
    }

    public void incrementLoginAttempts(String clientIP) {
        LoginAttempt attempt = loginAttempts.computeIfAbsent(clientIP, k -> new LoginAttempt());
        attempt.increment();
        Logger.warning("IP " + clientIP + " a maintenant " + attempt.attempts + " tentatives échouées");
    }

    public boolean isIPBlocked(String clientIP) {
        LoginAttempt attempt = loginAttempts.get(clientIP);
        if (attempt != null && attempt.isBlocked()) {
            Logger.warning("IP " + clientIP + " est actuellement bloquée");
            return true;
        }
        return false;
    }
} 