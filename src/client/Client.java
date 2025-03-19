import java.io.*;
import java.net.*;
import javax.swing.SwingUtilities;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import server.FileTransferHandler;
import common.SSLConfig;

public class Client {
    private SSLSocket socket;
    private PrintWriter out;
    private BufferedReader in;
    private boolean isAuthenticated = false;
    private int connectionTimeout = 5000; // 5 secondes
    private int commandTimeout = 60000; // 60 secondes (augmenté pour correspondre au serveur)
    private int fileTransferTimeout = 60000; // 60 secondes

    public Client(String host, int port) throws IOException {
        try {
            // Créer le socket avec un timeout de connexion
            SSLSocketFactory ssf = SSLConfig.createClientSocketFactory();
            socket = (SSLSocket) ssf.createSocket();
            socket.connect(new InetSocketAddress(host, port), connectionTimeout);
            
            // Configurer les timeouts
            socket.setSoTimeout(commandTimeout);
            socket.setKeepAlive(true);
            
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (SocketTimeoutException e) {
            throw new IOException("Délai d'attente de connexion dépassé. Vérifiez que le serveur est en cours d'exécution.");
        } catch (Exception e) {
            if (socket != null) {
                socket.close();
            }
            throw new IOException("Erreur de connexion: " + e.getMessage());
        }
    }

    public void updateTimeouts(int connectionTimeout, int commandTimeout, int fileTransferTimeout) {
        this.connectionTimeout = connectionTimeout;
        this.commandTimeout = commandTimeout;
        this.fileTransferTimeout = fileTransferTimeout;
        
        if (socket != null && !socket.isClosed()) {
            try {
                socket.setSoTimeout(commandTimeout);
            } catch (SocketException e) {
                e.printStackTrace();
            }
        }
    }

    public void connect() throws IOException {
        if (socket == null || socket.isClosed()) {
            throw new IOException("Socket non initialisé ou fermé");
        }
    }

    public void disconnect() {
        try {
            if (socket != null) {
                try {
                    // D'abord, on arrête la lecture et l'écriture
                    socket.shutdownInput();
                    socket.shutdownOutput();
                } catch (Exception e) {
                    // Ignorer les erreurs de shutdown
                }
                
                // Ensuite, on ferme les flux
                if (in != null) {
                    in.close();
                    in = null;
                }
                if (out != null) {
                    out.close();
                    out = null;
                }
                
                // Enfin, on ferme le socket
                socket.close();
                socket = null;
            }
            isAuthenticated = false;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean authenticate(String username, String password) throws IOException {
        try {
            socket.setSoTimeout(commandTimeout);
            String response = in.readLine();
            if (response == null) {
                throw new IOException("Pas de réponse du serveur");
            }
            
            if ("LOGIN_REQUIRED".equals(response)) {
                out.println(username);
                out.println(password);
                out.flush();
                
                response = in.readLine();
                if (response == null) {
                    throw new IOException("Pas de réponse du serveur");
                }
                
                isAuthenticated = "AUTH_SUCCESS".equals(response);
                return isAuthenticated;
            }
            return false;
        } catch (SocketTimeoutException e) {
            throw new IOException("Délai d'attente d'authentification dépassé");
        }
    }

    public String sendCommand(String command) throws IOException {
        if (!isAuthenticated) {
            throw new IOException("Non authentifié");
        }
        
        try {
            // Définir un timeout pour les commandes
            socket.setSoTimeout(commandTimeout);
            
            out.println(command);
            out.flush();
            
            StringBuilder response = new StringBuilder();
            String line;
            boolean hasContent = false;
            boolean isError = false;
            long startTime = System.currentTimeMillis();
            
            while ((line = in.readLine()) != null) {
                // Vérifier le timeout
                if (System.currentTimeMillis() - startTime > commandTimeout) {
                    throw new SocketTimeoutException("La commande a pris trop de temps à s'exécuter");
                }
                
                // Ignorer les messages de keep-alive
                if (line.equals("KEEP_ALIVE")) {
                    continue;
                }
                
                if (line.equals("ERROR")) {
                    isError = true;
                    continue;
                }
                
                if (line.equals("END_OF_COMMAND")) {
                    break;
                }
                
                if (!line.trim().isEmpty()) {
                    hasContent = true;
                    response.append(line).append("\n");
                } else if (hasContent) {
                    response.append(line).append("\n");
                }
            }
            
            // Réinitialiser le timeout
            socket.setSoTimeout(commandTimeout);
            
            if (isError) {
                throw new IOException(response.toString().trim());
            }
            
            return response.toString().trim();
        } catch (SocketTimeoutException e) {
            // Ne pas déconnecter en cas de timeout
            throw new IOException("La commande a pris trop de temps à s'exécuter");
        } catch (IOException e) {
            if (e.getMessage().contains("Connection reset")) {
                isAuthenticated = false;
                throw new IOException("Connexion perdue avec le serveur. Veuillez vous reconnecter.");
            }
            throw e;
        }
    }

    public void uploadFile(String localPath) throws IOException {
        if (!isAuthenticated) {
            throw new IOException("Non authentifié");
        }

        File file = new File(localPath);
        if (!file.exists()) {
            throw new IOException("Fichier non trouvé: " + localPath);
        }

        FileInputStream fis = null;
        try {
            // Configurer le timeout pour le transfert de fichier
            socket.setSoTimeout(fileTransferTimeout);
            
            // Envoyer la commande d'upload avec le nom du fichier uniquement
            System.out.println("Envoi de la commande d'upload pour: " + file.getName());
            out.println("upload " + file.getName());
            out.flush();
            
            // Attendre le signal du serveur
            System.out.println("Attente du signal READY_FOR_UPLOAD...");
            String response = in.readLine();
            
            if (response == null) {
                throw new IOException("Pas de réponse du serveur");
            }

            if (response.equals("ERROR")) {
                String errorMessage = in.readLine();
                throw new IOException(errorMessage != null ? errorMessage : "Erreur inconnue");
            }

            if (!response.equals("READY_FOR_UPLOAD")) {
                throw new IOException("Réponse inattendue du serveur: " + response);
            }

            System.out.println("Signal reçu: " + response);

            // Envoyer la taille du fichier
            long fileSize = file.length();
            out.println(String.valueOf(fileSize));
            out.flush();

            // Attendre la confirmation du serveur
            response = in.readLine();
            if (!"READY".equals(response)) {
                throw new IOException("Le serveur n'est pas prêt à recevoir le fichier");
            }

            // Envoyer le fichier
            System.out.println("Début de l'envoi du fichier...");
            fis = new FileInputStream(file);
            BufferedInputStream bis = new BufferedInputStream(fis);
            OutputStream os = socket.getOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            long totalSent = 0;
            
            while ((read = bis.read(buffer)) != -1) {
                os.write(buffer, 0, read);
                totalSent += read;
                System.out.println("Progression: " + totalSent + "/" + fileSize + " octets");
            }
            os.flush();
            
            // Fermer le flux d'entrée du fichier
            bis.close();
            fis.close();
            fis = null;
            
            System.out.println("Fichier envoyé, attente de la confirmation...");

            // Réinitialiser les flux
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Attendre la confirmation du serveur
            response = in.readLine();
            if (!"UPLOAD_SUCCESS".equals(response)) {
                throw new IOException("Erreur lors de l'upload: " + response);
            }

            // Lire le message de résultat
            String resultMessage = in.readLine();
            System.out.println("Message de résultat reçu: " + resultMessage);

            // Lire le marqueur de fin
            response = in.readLine();
            if (!"END_OF_COMMAND".equals(response)) {
                throw new IOException("Pas de marqueur de fin reçu");
            }

            System.out.println("Upload terminé avec succès");
            
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    // Ignorer l'erreur de fermeture
                }
            }
            // Réinitialiser le timeout
            socket.setSoTimeout(commandTimeout);
        }
    }

    public void downloadFile(String fileName, String localPath) throws IOException {
        if (!isAuthenticated) {
            throw new IOException("Non authentifié");
        }

        FileOutputStream fos = null;
        try {
            // Configurer le timeout pour le transfert de fichier
            socket.setSoTimeout(fileTransferTimeout);
            
            System.out.println("Envoi de la commande de téléchargement pour: " + fileName);
            out.println("download " + fileName);
            out.flush();

            // Lire la réponse initiale du serveur
            String response = in.readLine();
            System.out.println("Réponse initiale du serveur: " + response);
            
            if (response == null) {
                throw new IOException("Pas de réponse du serveur");
            }

            if (response.equals("ERROR")) {
                String errorMessage = in.readLine();
                System.out.println("Message d'erreur reçu: " + errorMessage);
                throw new IOException(errorMessage != null ? errorMessage : "Erreur inconnue");
            }

            if (!response.equals("DOWNLOAD_START")) {
                throw new IOException("Réponse inattendue du serveur: " + response);
            }

            // Lire la taille du fichier
            String fileSizeStr = in.readLine();
            long fileSize;
            try {
                fileSize = Long.parseLong(fileSizeStr);
                System.out.println("Taille du fichier à recevoir: " + fileSize + " octets");
            } catch (NumberFormatException e) {
                throw new IOException("Taille de fichier invalide reçue: " + fileSizeStr);
            }

            // Envoyer la confirmation qu'on est prêt à recevoir
            out.println("READY");
            out.flush();

            // Recevoir le fichier
            File targetFile = new File(localPath);
            fos = new FileOutputStream(targetFile);
            BufferedOutputStream bos = new BufferedOutputStream(fos);
            BufferedInputStream bis = new BufferedInputStream(socket.getInputStream());
            byte[] buffer = new byte[4096];
            long totalRead = 0;

            while (totalRead < fileSize) {
                int bytesToRead = (int) Math.min(buffer.length, fileSize - totalRead);
                int read = bis.read(buffer, 0, bytesToRead);
                
                if (read == -1) {
                    throw new IOException("Fin de flux inattendue");
                }
                
                bos.write(buffer, 0, read);
                totalRead += read;
                System.out.println("Progression: " + totalRead + "/" + fileSize + " octets");
            }

            bos.flush();
            System.out.println("Fichier reçu avec succès");

            // Attendre la confirmation finale
            response = in.readLine();
            if (!"DOWNLOAD_SUCCESS".equals(response)) {
                throw new IOException("Erreur lors du téléchargement: " + response);
            }

            // Lire le marqueur de fin
            response = in.readLine();
            if (!"END_OF_COMMAND".equals(response)) {
                throw new IOException("Pas de marqueur de fin reçu");
            }

            System.out.println("Téléchargement terminé avec succès");
            
        } catch (Exception e) {
            throw new IOException("Erreur lors du téléchargement: " + e.getMessage());
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    // Ignorer l'erreur de fermeture
                }
            }
            // Réinitialiser le timeout
            socket.setSoTimeout(commandTimeout);
        }
    }

    private void startKeepAliveHandler() {
        new Thread(() -> {
            while (!socket.isClosed()) {
                try {
                    String response = in.readLine();
                    if (response == null) {
                        break;
                    }
                    if (response.equals("KEEP_ALIVE")) {
                        out.println("KEEP_ALIVE");
                        out.flush();
                        System.out.println("Keep-alive envoyé au serveur");
                    }
                } catch (Exception e) {
                    System.out.println("Erreur dans le keep-alive handler: " + e.getMessage());
                    break;
                }
            }
        }).start();
    }

    public void connect(String host, int port) throws IOException {
        try {
            SSLSocketFactory ssf = SSLConfig.createClientSocketFactory();
            socket = (SSLSocket) ssf.createSocket();
            socket.connect(new InetSocketAddress(host, port), connectionTimeout);
            socket.setSoTimeout(commandTimeout);
            socket.setKeepAlive(true);
            socket.setTcpNoDelay(true);
            
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            
            // Démarrer le gestionnaire de keep-alive
            startKeepAliveHandler();
        } catch (Exception e) {
            throw new IOException("Erreur de connexion: " + e.getMessage());
        }
    }
} 