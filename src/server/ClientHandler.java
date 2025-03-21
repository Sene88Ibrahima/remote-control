package server;
import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import server.FileTransferHandler;
import server.Logger;

public class ClientHandler implements Runnable {
    private Socket clientSocket;
    private BufferedReader in;
    private PrintWriter out;
    private Server server;
    private String clientInfo;
    private boolean isAuthenticated = false;
    private File currentDirectory = new File(System.getProperty("user.dir"));
    private static final int COMMAND_TIMEOUT = 300000; // 5 minutes
    private static final int FILE_TRANSFER_TIMEOUT = 300000; // 5 minutes
    private static final int KEEP_ALIVE_INTERVAL = 60000; // 60 secondes
    private static final int KEEP_ALIVE_TIMEOUT = 180000; // 3 minutes
    private volatile boolean isRunning = true;
    private volatile long lastKeepAliveResponse = System.currentTimeMillis();
    private String currentUsername;
    private final Object commandLock = new Object();
    private final Object transferLock = new Object();
    private volatile boolean isTransferring = false;

    // Liste des commandes dangereuses à bloquer
    private static final Set<String> DANGEROUS_COMMANDS = new HashSet<>(Arrays.asList(
        "rm", "rmdir", "del", "rd", "format", "mkfs", "dd", "shutdown", "reboot",
        "sudo", "su", "chmod", "chown", "passwd", "useradd", "userdel", "groupadd",
        "groupdel", "mount", "umount", "mkfs", "fsck", "dd", "mkfs.ext", "mkfs.ntfs",
        "mkfs.vfat", "mkfs.fat", "mkfs.exfat", "mkfs.ufs", "mkfs.zfs", "mkfs.xfs",
        "mkfs.btrfs", "mkfs.reiserfs", "mkfs.jfs", "mkfs.hfs", "mkfs.hfsplus",
        "mkfs.ntfs", "mkfs.fat", "mkfs.exfat", "mkfs.ufs", "mkfs.zfs", "mkfs.xfs",
        "mkfs.btrfs", "mkfs.reiserfs", "mkfs.jfs", "mkfs.hfs", "mkfs.hfsplus"
    ));

    // Liste des commandes autorisées
    private static final Set<String> ALLOWED_COMMANDS = new HashSet<>(Arrays.asList(
        "dir", "ls", "cd", "pwd", "echo", "type", "cat", "more", "less", "head",
        "tail", "find", "grep", "sort", "wc", "date", "time", "ver", "hostname",
        "ipconfig", "ifconfig", "netstat", "ping", "tracert", "nslookup", "whoami",
        "echo", "type", "cat", "more", "less", "head", "tail", "find", "grep",
        "sort", "wc", "date", "time", "ver", "hostname", "ipconfig", "ifconfig",
        "netstat", "ping", "tracert", "nslookup", "whoami"
    ));

    public ClientHandler(Socket socket, Server server) {
        this.clientSocket = socket;
        this.server = server;
        this.clientInfo = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
        try {
            socket.setKeepAlive(true);
            socket.setTcpNoDelay(true);
            
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            out = new PrintWriter(clientSocket.getOutputStream(), true);
        } catch (IOException e) {
            Logger.error("Erreur lors de l'initialisation du ClientHandler: " + e.getMessage());
        }
    }

    public String getClientInfo() {
        return clientInfo;
    }

    private boolean authenticate() throws IOException {
        try {
            // Pas de timeout pour l'authentification, on utilise le keep-alive
            out.println("LOGIN_REQUIRED");
            out.flush();
            
            String username = in.readLine();
            if (username == null) {
                throw new IOException("Pas de nom d'utilisateur reçu");
            }
            
            String password = in.readLine();
            if (password == null) {
                throw new IOException("Pas de mot de passe reçu");
            }
            
            boolean auth = Authentication.authenticate(username, password);
            if (auth) {
                currentUsername = username;
                out.println("AUTH_SUCCESS");
                Logger.log("Authentification réussie pour " + username + " depuis " + clientInfo);
            } else {
                out.println("AUTH_FAILED");
                Logger.log("Échec d'authentification pour " + username + " depuis " + clientInfo);
                // Incrémenter les tentatives échouées
                server.incrementLoginAttempts(clientSocket.getInetAddress().getHostAddress());
            }
            out.flush();
            return auth;
        } catch (Exception e) {
            Logger.error("Erreur d'authentification pour " + clientInfo + ": " + e.getMessage());
            throw new IOException("Erreur d'authentification: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        try {
            // Authentification
            if (!authenticate()) {
                Logger.warning("Authentification échouée pour " + clientInfo);
                closeConnection();
                return;
            }
            
            isAuthenticated = true;
            Logger.log("Client authentifié: " + clientInfo);
            server.logConnection(clientInfo);
            
            // Démarrer le thread de keep-alive dans un thread séparé
            Thread keepAliveThread = new Thread(() -> {
                while (isRunning && !clientSocket.isClosed()) {
                    try {
                        Thread.sleep(KEEP_ALIVE_INTERVAL);
                        if (isRunning && !clientSocket.isClosed()) {
                            // Ne pas envoyer de keep-alive pendant un transfert
                            if (isTransferring) {
                                continue;
                            }
                            
                            // Vérifier si le client a répondu au dernier keep-alive
                            long currentTime = System.currentTimeMillis();
                            if (currentTime - lastKeepAliveResponse > KEEP_ALIVE_TIMEOUT) {
                                Logger.warning("Client " + clientInfo + " n'a pas répondu au keep-alive");
                                closeConnection();
                                break;
                            }
                            
                            synchronized(out) {
                                if (!isTransferring) {
                                    out.println("KEEP_ALIVE");
                                    out.flush();
                                }
                            }
                        }
                    } catch (InterruptedException e) {
                        break;
                    } catch (Exception e) {
                        if (isRunning) {
                            Logger.error("Erreur lors du keep-alive pour " + clientInfo + ": " + e.getMessage());
                        }
                        break;
                    }
                }
            });
            keepAliveThread.setDaemon(true);
            keepAliveThread.start();

            String command;
            while (isRunning && (command = in.readLine()) != null) {
                try {
                    // Mettre à jour le timestamp du dernier keep-alive
                    lastKeepAliveResponse = System.currentTimeMillis();
                    
                    if (command.equals("KEEP_ALIVE")) {
                        synchronized(out) {
                            if (!isTransferring) {
                                out.println("KEEP_ALIVE");
                                out.flush();
                            }
                        }
                        continue;
                    }
                    
                    // Logger le début de l'exécution de la commande
                    server.logCommand(clientInfo, command);
                    
                    if (command.startsWith("upload ")) {
                        isTransferring = true;
                        handleUpload(command.substring(7));
                    } else if (command.startsWith("download ")) {
                        isTransferring = true;
                        handleDownload(command.substring(9));
                    } else if (command.equals("list_files")) {
                        handleListFiles();
                    } else if (command.startsWith("cd ")) {
                        handleCd(command.substring(3).trim());
                    } else {
                        executeCommand(command);
                    }
                    
                } catch (Exception e) {
                    if (!e.getMessage().contains("Connection reset")) {
                        Logger.error("Erreur lors de l'exécution de la commande pour " + clientInfo + ": " + e.getMessage());
                        synchronized(out) {
                            out.println("ERROR");
                            out.println("Erreur lors de l'exécution de la commande: " + e.getMessage());
                            out.println("END_OF_COMMAND");
                            out.flush();
                        }
                    } else {
                        Logger.warning("Connexion perdue pour " + clientInfo);
                        break;
                    }
                } finally {
                    isTransferring = false;
                }
            }
        } catch (Exception e) {
            if (!e.getMessage().contains("Connection reset")) {
                Logger.error("Erreur avec le client " + clientInfo + ": " + e.getMessage());
            }
        } finally {
            isRunning = false;
            closeConnection();
            server.removeClient(this);
            server.logDisconnection(clientInfo);
        }
    }

    private void handleDownload(String fileName) {
        synchronized(transferLock) {
            try {
                isTransferring = true;
                lastKeepAliveResponse = System.currentTimeMillis(); // Reset keep-alive timer
                clientSocket.setSoTimeout(FILE_TRANSFER_TIMEOUT);
                
                if (fileName == null || fileName.trim().isEmpty()) {
                    synchronized(out) {
                        out.println("ERROR");
                        out.println("Nom de fichier invalide");
                        out.println("END_OF_COMMAND");
                        out.flush();
                    }
                    return;
                }

                File file = new File(currentDirectory, fileName);
                if (!file.exists()) {
                    synchronized(out) {
                        out.println("ERROR");
                        out.println("Le fichier n'existe pas: " + fileName);
                        out.println("END_OF_COMMAND");
                        out.flush();
                    }
                    return;
                }

                // Envoyer la réponse de succès immédiatement
                synchronized(out) {
                    out.println("DOWNLOAD_START");
                    out.println(file.length());
                    out.flush();
                }

                // Attendre la confirmation du client
                String response = in.readLine();
                if (!"READY".equals(response)) {
                    throw new IOException("Le client n'est pas prêt à recevoir le fichier");
                }

                // Envoyer le fichier
                FileTransferHandler.sendFile(clientSocket, file.getAbsolutePath());
                Logger.log("Téléchargement du fichier: " + fileName + " pour " + clientInfo);
                
                // Réinitialiser les flux avant d'envoyer la réponse finale
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                
                synchronized(out) {
                    out.println("DOWNLOAD_SUCCESS");
                    out.println("END_OF_COMMAND");
                    out.flush();
                }
                
            } catch (Exception e) {
                Logger.error("Erreur lors du téléchargement pour " + clientInfo + ": " + e.getMessage());
                synchronized(out) {
                    out.println("ERROR");
                    out.println("Erreur lors du téléchargement: " + e.getMessage());
                    out.println("END_OF_COMMAND");
                    out.flush();
                }
            } finally {
                try {
                    clientSocket.setSoTimeout(COMMAND_TIMEOUT);
                    isTransferring = false;
                    lastKeepAliveResponse = System.currentTimeMillis(); // Reset keep-alive timer après le transfert
                } catch (SocketException e) {
                    Logger.error("Erreur lors de la réinitialisation du timeout: " + e.getMessage());
                }
            }
        }
    }

    private void handleUpload(String fileName) {
        synchronized(transferLock) {
            FileOutputStream fos = null;
            try {
                isTransferring = true;
                lastKeepAliveResponse = System.currentTimeMillis(); // Reset keep-alive timer
                clientSocket.setSoTimeout(FILE_TRANSFER_TIMEOUT);
                
                // Désactiver temporairement le keep-alive pendant le transfert
                synchronized(out) {
                    out.println("READY_FOR_UPLOAD");
                    out.flush();
                }
                
                // Attendre la taille du fichier
                String fileSizeStr = in.readLine();
                if (fileSizeStr == null) {
                    throw new IOException("Connexion perdue pendant l'upload");
                }
                
                long fileSize;
                try {
                    fileSize = Long.parseLong(fileSizeStr);
                    if (fileSize <= 0) {
                        throw new NumberFormatException("Taille invalide");
                    }
                } catch (NumberFormatException e) {
                    throw new IOException("Taille de fichier invalide reçue: " + fileSizeStr);
                }
                
                // Confirmer qu'on est prêt à recevoir
                synchronized(out) {
                    out.println("READY");
                    out.flush();
                }
                
                // Créer le fichier de destination dans le répertoire courant
                File destFile = new File(currentDirectory, fileName);
                
                // Vérifier si le répertoire parent existe, sinon le créer
                File parentDir = destFile.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                }
                
                // Recevoir le fichier
                fos = new FileOutputStream(destFile);
                BufferedOutputStream bos = new BufferedOutputStream(fos);
                InputStream is = clientSocket.getInputStream();
                byte[] buffer = new byte[8192];
                long totalRead = 0;
                
                while (totalRead < fileSize) {
                    int bytesToRead = (int) Math.min(buffer.length, fileSize - totalRead);
                    int read = is.read(buffer, 0, bytesToRead);
                    
                    if (read == -1) {
                        throw new IOException("Fin de flux inattendue");
                    }
                    
                    bos.write(buffer, 0, read);
                    totalRead += read;
                    lastKeepAliveResponse = System.currentTimeMillis(); // Reset keep-alive timer pendant le transfert
                }
                
                bos.flush();
                bos.close();
                fos = null;
                
                String result = "Fichier uploadé avec succès: " + destFile.getAbsolutePath();
                Logger.log(result);
                
                // Réinitialiser les flux avant d'envoyer la réponse
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                
                synchronized(out) {
                    out.println("UPLOAD_SUCCESS");
                    out.println(result);
                    out.println("END_OF_COMMAND");
                    out.flush();
                }
                
            } catch (Exception e) {
                Logger.error("Erreur lors de l'upload pour " + clientInfo + ": " + e.getMessage());
                synchronized(out) {
                    out.println("UPLOAD_ERROR");
                    out.println("Erreur lors de la réception du fichier: " + e.getMessage());
                    out.println("END_OF_COMMAND");
                    out.flush();
                }
            } finally {
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (IOException e) {
                        Logger.error("Erreur lors de la fermeture du fichier: " + e.getMessage());
                    }
                }
                try {
                    clientSocket.setSoTimeout(COMMAND_TIMEOUT);
                    isTransferring = false;
                    lastKeepAliveResponse = System.currentTimeMillis(); // Reset keep-alive timer après le transfert
                } catch (SocketException e) {
                    Logger.error("Erreur lors de la réinitialisation du timeout: " + e.getMessage());
                }
            }
        }
    }

    private void handleListFiles() {
        try {
            // Lister les fichiers du répertoire courant
            File[] files = currentDirectory.listFiles();
            StringBuilder fileList = new StringBuilder();
            if (files != null) {
                for (File file : files) {
                    fileList.append(file.getName())
                           .append(" (")
                           .append(file.isDirectory() ? "Dossier" : "Fichier")
                           .append(")\n");
                }
            }
            out.println(fileList.toString());
            out.println("END_OF_COMMAND");
            out.flush();
        } catch (Exception e) {
            Logger.error("Erreur lors de la liste des fichiers pour " + clientInfo + ": " + e.getMessage());
            out.println("ERROR");
            out.println("Erreur lors de la liste des fichiers: " + e.getMessage());
            out.println("END_OF_COMMAND");
            out.flush();
        }
    }

    private void handleCd(String path) {
        try {
            Logger.log("Changement de répertoire demandé: " + path + " pour " + clientInfo);
            File newDir;
            
            if (path.equals("..")) {
                newDir = currentDirectory.getParentFile();
            } else if (path.equals(".")) {
                newDir = currentDirectory;
            } else if (path.isEmpty() || path.equals("~")) {
                newDir = new File(System.getProperty("user.dir"));
            } else {
                newDir = new File(currentDirectory, path);
            }
            
            if (newDir != null && newDir.exists() && newDir.isDirectory()) {
                currentDirectory = newDir;
                Logger.log("Répertoire changé vers: " + currentDirectory.getAbsolutePath() + " pour " + clientInfo);
                out.println("Répertoire changé vers: " + currentDirectory.getAbsolutePath());
            } else {
                Logger.warning("Tentative d'accès à un répertoire invalide: " + path + " pour " + clientInfo);
                out.println("Répertoire invalide: " + path);
            }
            
            out.println("END_OF_COMMAND");
            out.flush();
        } catch (Exception e) {
            Logger.error("Erreur lors du changement de répertoire pour " + clientInfo + ": " + e.getMessage());
            out.println("ERROR");
            out.println("Erreur lors du changement de répertoire: " + e.getMessage());
            out.println("END_OF_COMMAND");
            out.flush();
        }
    }

    private boolean isCommandDangerous(String command) {
        if ("admin".equals(currentUsername)) {
            Logger.info("Admin exécute la commande: " + command);
            return false;
        }

        String[] parts = command.trim().split("\\s+");
        if (parts.length == 0) return false;
        
        String baseCommand = parts[0].toLowerCase();
        
        // Vérifier si la commande est dans la liste des commandes dangereuses
        if (DANGEROUS_COMMANDS.contains(baseCommand)) {
            Logger.warning("Tentative d'exécution de commande dangereuse: " + command + " par " + clientInfo);
            return true;
        }
        
        // Vérifier si la commande n'est pas dans la liste des commandes autorisées
        if (!ALLOWED_COMMANDS.contains(baseCommand)) {
            Logger.warning("Tentative d'exécution de commande non autorisée: " + command + " par " + clientInfo);
            return true;
        }
        
        // Vérifier les arguments dangereux
        for (String arg : parts) {
            if (arg.contains("..") || arg.contains("&&") || arg.contains("||") || 
                arg.contains(";") || arg.contains("|") || arg.contains(">") || 
                arg.contains("<") || arg.contains("`") || arg.contains("$")) {
                Logger.warning("Tentative d'injection de commande détectée: " + command + " par " + clientInfo);
                return true;
            }
        }
        
        return false;
    }

    private String executeCommand(String command) {
        synchronized(commandLock) {
            try {
                // Réinitialiser le timeout à la valeur par défaut
                clientSocket.setSoTimeout(COMMAND_TIMEOUT);

                // Vérifier si la commande est dangereuse
                if (isCommandDangerous(command)) {
                    String errorMsg = "Commande non autorisée pour des raisons de sécurité";
                    synchronized(out) {
                        out.println("ERROR");
                        out.println(errorMsg);
                        out.println("END_OF_COMMAND");
                        out.flush();
                    }
                    return errorMsg;
                }

                ProcessBuilder processBuilder = new ProcessBuilder();
                if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
                    processBuilder.command("cmd.exe", "/c", command);
                } else {
                    processBuilder.command("/bin/sh", "-c", command);
                }
                
                processBuilder.directory(currentDirectory);
                Process process = processBuilder.start();
                
                StringBuilder output = new StringBuilder();
                
                // Lire la sortie standard et d'erreur de manière synchrone
                try (BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
                     BufferedReader stdError = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    
                    // Thread pour la sortie standard
                    Thread outputThread = new Thread(() -> {
                        try {
                            String s;
                            while ((s = stdInput.readLine()) != null) {
                                synchronized(out) {
                                    out.println(s);
                                    out.flush();
                                }
                                output.append(s).append("\n");
                            }
                        } catch (IOException e) {
                            Logger.error("Erreur lors de la lecture de la sortie standard: " + e.getMessage());
                        }
                    });
                    
                    // Thread pour la sortie d'erreur
                    Thread errorThread = new Thread(() -> {
                        try {
                            String s;
                            while ((s = stdError.readLine()) != null) {
                                synchronized(out) {
                                    out.println("ERROR: " + s);
                                    out.flush();
                                }
                                output.append("ERROR: ").append(s).append("\n");
                            }
                        } catch (IOException e) {
                            Logger.error("Erreur lors de la lecture de la sortie d'erreur: " + e.getMessage());
                        }
                    });
                    
                    // Démarrer les threads
                    outputThread.start();
                    errorThread.start();
                    
                    // Attendre la fin du processus avec timeout
                    if (!process.waitFor(COMMAND_TIMEOUT / 1000, java.util.concurrent.TimeUnit.SECONDS)) {
                        process.destroyForcibly();
                        String errorMsg = "La commande a pris trop de temps à s'exécuter";
                        synchronized(out) {
                            out.println("ERROR");
                            out.println(errorMsg);
                            out.println("END_OF_COMMAND");
                            out.flush();
                        }
                        return errorMsg;
                    }
                    
                    // Attendre que les threads de lecture soient terminés
                    outputThread.join(5000); // Timeout de 5 secondes pour les threads
                    errorThread.join(5000);
                    
                    // Envoyer le marqueur de fin
                    synchronized(out) {
                        out.println("END_OF_COMMAND");
                        out.flush();
                    }
                }
                
                return output.toString();
            } catch (Exception e) {
                String errorMsg = "Erreur lors de l'exécution de la commande: " + e.getMessage();
                synchronized(out) {
                    out.println("ERROR");
                    out.println(errorMsg);
                    out.println("END_OF_COMMAND");
                    out.flush();
                }
                return errorMsg;
            }
        }
    }

    public void closeConnection() {
        isRunning = false;
        try {
            if (in != null) {
                in.close();
                in = null;
            }
            if (out != null) {
                out.close();
                out = null;
            }
            if (clientSocket != null) {
                try {
                    clientSocket.shutdownInput();
                    clientSocket.shutdownOutput();
                } catch (Exception e) {
                    // Ignorer les erreurs de shutdown
                }
                clientSocket.close();
                clientSocket = null;
            }
            isAuthenticated = false;
        } catch (IOException e) {
            Logger.error("Erreur lors de la fermeture de la connexion pour " + clientInfo + ": " + e.getMessage());
        }
    }
} 