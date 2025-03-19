package server;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class FileTransferHandler {
    private static final int BUFFER_SIZE = 4096;

    public static String receiveFile(Socket socket, String fileName, File targetDirectory) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        InputStream is = socket.getInputStream();
        FileOutputStream fos = null;
        
        try {
            // Recevoir la taille du fichier
            String sizeStr = reader.readLine();
            if (sizeStr == null) {
                throw new IOException("Pas de taille de fichier reçue");
            }
            
            long fileSize;
            try {
                fileSize = Long.parseLong(sizeStr);
                if (fileSize <= 0) {
                    throw new IOException("Taille de fichier invalide");
                }
            } catch (NumberFormatException e) {
                throw new IOException("Taille de fichier invalide: " + sizeStr);
            }
            
            System.out.println("Réception du fichier: " + fileName);
            System.out.println("Taille attendue: " + fileSize + " octets");
            
            // Créer le fichier dans le répertoire cible
            File targetFile = targetDirectory.isDirectory() ? new File(targetDirectory, fileName) : targetDirectory;
            System.out.println("Création du fichier à: " + targetFile.getAbsolutePath());
            
            fos = new FileOutputStream(targetFile);
            BufferedOutputStream bos = new BufferedOutputStream(fos);
            
            // Recevoir le fichier en utilisant des flux binaires
            byte[] buffer = new byte[8192];
            int read;
            long totalRead = 0;
            
            while (totalRead < fileSize) {
                int bytesToRead = (int) Math.min(buffer.length, fileSize - totalRead);
                read = is.read(buffer, 0, bytesToRead);
                
                if (read == -1) {
                    throw new IOException("Fin de flux inattendue");
                }
                
                bos.write(buffer, 0, read);
                totalRead += read;
                System.out.println("Progression: " + totalRead + "/" + fileSize + " octets");
            }
            
            bos.flush();
            System.out.println("Fichier reçu avec succès: " + targetFile.getAbsolutePath());
            System.out.println("Total reçu: " + totalRead + " octets");
            
            return "Fichier reçu avec succès: " + targetFile.getAbsolutePath();
        } finally {
            if (fos != null) {
                fos.close();
            }
        }
    }

    public static void sendFile(Socket socket, String fileName) throws IOException {
        File file = new File(fileName);
        if (!file.exists()) {
            throw new IOException("Fichier non trouvé: " + fileName);
        }
        
        System.out.println("Envoi du fichier: " + file.getAbsolutePath());
        System.out.println("Taille du fichier: " + file.length() + " octets");
        
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        BufferedOutputStream bos = new BufferedOutputStream(socket.getOutputStream());
        FileInputStream fis = null;
        
        try {
            // Envoyer la taille du fichier
            writer.println(String.valueOf(file.length()));
            System.out.println("Taille envoyée: " + file.length());
            
            // Envoyer le fichier
            fis = new FileInputStream(file);
            BufferedInputStream bis = new BufferedInputStream(fis);
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            long totalSent = 0;
            
            while ((read = bis.read(buffer)) != -1) {
                bos.write(buffer, 0, read);
                totalSent += read;
                System.out.println("Progression: " + totalSent + "/" + file.length() + " octets");
            }
            
            bos.flush();
            System.out.println("Fichier envoyé avec succès: " + fileName);
            System.out.println("Total envoyé: " + totalSent + " octets");
        } finally {
            if (fis != null) {
                fis.close();
            }
        }
    }
} 