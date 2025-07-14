/*
 * Name: Jing
 * Surname: Lei
 * Student ID: 1166617
 */

package client;

import client.net.DictionaryClientCore;
import client.ui.DictionaryClientGUI;
import javax.swing.SwingUtilities;
import java.io.IOException;
import javax.swing.JOptionPane;


public class DictionaryClientApp {
    public static void main(String[] args) {
        String serverAddress = "localhost";
        int serverPort = 12345;

        if (args.length >= 2) {
            serverAddress = args[0];
            try {
                serverPort = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number. Using default 12345.");
            }
        }

        try {
            DictionaryClientCore clientCore = new DictionaryClientCore(serverAddress, serverPort);
            SwingUtilities.invokeLater(() -> {
                DictionaryClientGUI gui = new DictionaryClientGUI(clientCore);
                gui.setVisible(true);
            });
        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(null, "Unable to connect to server: " + ex.getMessage(), "Connection Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
