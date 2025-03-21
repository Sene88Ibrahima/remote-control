package server;
import server.ServerGUI;
public class ServerMain {
    public static void main(String[] args) {
        int port = 12345;
        
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("Port invalide. Utilisation du port par défaut: " + port);
            }
        }

        ServerGUI gui = new ServerGUI(port);
        gui.setVisible(true);
    }
} 