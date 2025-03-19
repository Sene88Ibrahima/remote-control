import java.util.HashMap;
import java.util.Map;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.io.*;
import server.Logger;

public class Authentication {
    private static final Map<String, UserCredentials> users = new HashMap<>();
    private static final String SALT_FILE = "users.salt";
    private static final String CREDENTIALS_FILE = "users.credentials";
    private static final SecureRandom secureRandom = new SecureRandom();
    
    static {
        loadUsers();
    }
    
    private static class UserCredentials implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String salt;
        private final String hashedPassword;
        
        public UserCredentials(String salt, String hashedPassword) {
            this.salt = salt;
            this.hashedPassword = hashedPassword;
        }
    }
    
    public static void addUser(String username, String password) {
        try {
            // Générer un sel unique pour l'utilisateur
            byte[] salt = generateSalt();
            String saltHex = bytesToHex(salt);
            
            // Hasher le mot de passe avec le sel
            String hashedPassword = hashPassword(password, salt);
            
            // Sauvegarder les credentials
            users.put(username, new UserCredentials(saltHex, hashedPassword));
            saveUsers();
            
            Logger.info("Nouvel utilisateur ajouté: " + username);
        } catch (Exception e) {
            Logger.error("Erreur lors de l'ajout de l'utilisateur: " + e.getMessage());
        }
    }
    
    public static boolean authenticate(String username, String password) {
        try {
            UserCredentials credentials = users.get(username);
            if (credentials == null) {
                Logger.warning("Tentative d'authentification échouée - Utilisateur inconnu: " + username);
                return false;
            }
            
            // Vérifier le mot de passe
            String hashedInput = hashPassword(password, hexToBytes(credentials.salt));
            boolean isValid = hashedInput.equals(credentials.hashedPassword);
            
            if (isValid) {
                Logger.info("Authentification réussie pour: " + username);
            } else {
                Logger.warning("Tentative d'authentification échouée - Mot de passe incorrect pour: " + username);
            }
            
            return isValid;
        } catch (Exception e) {
            Logger.error("Erreur lors de l'authentification: " + e.getMessage());
            return false;
        }
    }
    
    private static byte[] generateSalt() {
        byte[] salt = new byte[16];
        secureRandom.nextBytes(salt);
        return salt;
    }
    
    private static String hashPassword(String password, byte[] salt) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(salt);
        byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hash);
    }
    
    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
    
    private static byte[] hexToBytes(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        }
        return bytes;
    }
    
    private static void saveUsers() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(CREDENTIALS_FILE))) {
            oos.writeObject(users);
            Logger.debug("Utilisateurs sauvegardés avec succès");
        } catch (IOException e) {
            Logger.error("Erreur lors de la sauvegarde des utilisateurs: " + e.getMessage());
        }
    }
    
    @SuppressWarnings("unchecked")
    private static void loadUsers() {
        File file = new File(CREDENTIALS_FILE);
        if (file.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                users.putAll((Map<String, UserCredentials>) ois.readObject());
                Logger.info("Utilisateurs chargés avec succès");
            } catch (Exception e) {
                Logger.error("Erreur lors du chargement des utilisateurs: " + e.getMessage());
                // Créer des utilisateurs par défaut si le fichier est corrompu
                createDefaultUsers();
            }
        } else {
            createDefaultUsers();
        }
    }
    
    private static void createDefaultUsers() {
        addUser("admin", "admin123");
        addUser("user", "user123");
        Logger.info("Utilisateurs par défaut créés");
    }
    
    public static void deleteUser(String username) {
        try {
            if (users.remove(username) != null) {
                saveUsers();
                Logger.info("Utilisateur supprimé: " + username);
            } else {
                Logger.warning("Tentative de suppression d'un utilisateur inexistant: " + username);
            }
        } catch (Exception e) {
            Logger.error("Erreur lors de la suppression de l'utilisateur: " + e.getMessage());
        }
    }
} 