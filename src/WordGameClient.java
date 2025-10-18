import java.io.*;
import java.net.*;
import java.util.Scanner;

public class WordGameClient {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String clientId;
    private boolean connected;
    private Scanner scanner;

    public WordGameClient(String host, int port) throws IOException {
        this.socket = new Socket(host, port);
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
        this.out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
        this.scanner = new Scanner(System.in);
        this.connected = true;
        System.out.println("Connecté au serveur " + host + ":" + port);
    }

    public void start() {
        // Thread pour recevoir les messages du serveur
        Thread receiverThread = new Thread(this::receiveMessages);
        receiverThread.start();

        // Thread pour envoyer les commandes
        sendCommands();

        // Nettoyage
        try {
            receiverThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        disconnect();
    }

    private void receiveMessages() {
        try {
            String message;
            while ((message = in.readLine()) != null && connected) {
                handleServerMessage(message);
            }
        } catch (IOException e) {
            if (connected) {
                System.err.println("Erreur de connexion au serveur: " + e.getMessage());
            }
        }
    }

    private void handleServerMessage(String message) {
        System.out.println("\n[SERVEUR] " + message);

        if (message.startsWith("WELCOME ")) {
            this.clientId = message.substring(8);
            System.out.println("Vous êtes connecté en tant que: " + clientId);
        } else if (message.startsWith("WORDLEN ")) {
            int wordLength = Integer.parseInt(message.substring(8));
            System.out.println("Longueur du mot à deviner: " + wordLength + " lettres");
        } else if (message.startsWith("FEEDBACK ")) {
            // Exemple: FEEDBACK OK_POS=2 OK_MIS=1 WRONG=2
            System.out.println("→ Résultat de votre tentative");
        } else if (message.startsWith("INVALID ")) {
            String reason = message.substring(8);
            System.out.println("❌ Tentative invalide: " + reason);
        } else if (message.startsWith("BROADCAST ")) {
            String broadcastMsg = message.substring(10);
            System.out.println("📢 " + broadcastMsg);
        } else if (message.startsWith("VICTORY ")) {
            // Exemple: VICTORY client_123456789 ANSWER TABLE
            String[] parts = message.split(" ");
            String winner = parts[1];
            String answer = parts[3];

            if (winner.equals(clientId)) {
                System.out.println("🎉 FÉLICITATIONS! Vous avez gagné! Le mot était: " + answer);
            } else {
                System.out.println("🏆 Le client " + winner + " a gagné! Le mot était: " + answer);
            }
        } else if (message.equals("BYE")) {
            System.out.println("Déconnexion du serveur...");
            disconnect();
        } else {
            System.out.println("Message non reconnu: " + message);
        }

        // Afficher à nouveau l'invite de commande
        if (connected) {
            System.out.print("\nVotre commande > ");
        }
    }

    private void sendCommands() {
        System.out.println("Commandes disponibles:");
        System.out.println("  TRY <mot>  - Tenter un mot");
        System.out.println("  QUIT       - Quitter le jeu");
        System.out.println("  HELP       - Afficher cette aide\n");

        while (connected && scanner.hasNextLine()) {
            System.out.print("Votre commande > ");
            String command = scanner.nextLine().trim();

            if (command.equalsIgnoreCase("QUIT")) {
                sendMessage("QUIT");
                break;
            } else if (command.equalsIgnoreCase("HELP")) {
                showHelp();
            } else if (command.toUpperCase().startsWith("TRY ")) {
                String word = command.substring(4).trim();
                if (word.matches("[a-zA-Z]+")) {
                    sendMessage("TRY " + word.toUpperCase());
                } else {
                    System.out.println("❌ Le mot ne doit contenir que des lettres alphabétiques");
                }
            } else if (!command.isEmpty()) {
                System.out.println("❌ Commande non reconnue. Tapez HELP pour l'aide.");
            }
        }
    }

    private void showHelp() {
        System.out.println("\n=== AIDE DU JEU ===");
        System.out.println("TRY <mot>   : Proposer un mot (ex: TRY TABLE)");
        System.out.println("QUIT        : Quitter le jeu");
        System.out.println("HELP        : Afficher cette aide");
        System.out.println("\nLe serveur répondra avec:");
        System.out.println("OK_POS  : Lettres bien placées");
        System.out.println("OK_MIS  : Lettres mal placées");
        System.out.println("WRONG   : Lettres absentes");
        System.out.println("===================\n");
    }

    private void sendMessage(String message) {
        if (connected) {
            out.println(message);
        }
    }

    private void disconnect() {
        connected = false;
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();
            if (scanner != null) scanner.close();
        } catch (IOException e) {
            System.err.println("Erreur lors de la fermeture des ressources: " + e.getMessage());
        }
        System.out.println("Déconnecté.");
    }

}
