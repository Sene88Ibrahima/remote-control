package server;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Logger {
    private static final String LOG_FILE = "server.log";
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void log(String message) {
        String timestamp = LocalDateTime.now().format(formatter);
        String logMessage = String.format("[%s] %s%n", timestamp, message);
        
        try (FileWriter fw = new FileWriter(LOG_FILE, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {
            out.print(logMessage);
        } catch (IOException e) {
            System.err.println("Erreur lors de l'écriture dans le fichier de log: " + e.getMessage());
        }
    }

    public static void error(String message) {
        log("ERROR: " + message);
    }

    public static void warning(String message) {
        log("WARNING: " + message);
    }

    public static void info(String message) {
        log("INFO: " + message);
    }

    public static void debug(String message) {
        log("DEBUG: " + message);
    }
} 