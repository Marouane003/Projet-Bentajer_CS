package server;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class WordGameServer {
    private static final int DEFAULT_PORT = 12345;
    private static final int DEFAULT_WORD_LENGTH = 5;

    private ServerSocket serverSocket;
    private int wordLength;
    private String secretWord;
    private boolean gameRunning;
    private final Set<ClientHandler> clients;
    private final ExecutorService threadPool;
    private final List<String> dictionary;

    public WordGameServer(int port, int wordLength) throws IOException {
        this.serverSocket = new ServerSocket(port);
        this.wordLength = wordLength;
        this.clients = ConcurrentHashMap.newKeySet();
        this.threadPool = Executors.newCachedThreadPool();
        this.gameRunning = false;
        this.dictionary = loadDictionary();
        System.out.println("Serveur démarré sur le port " + port);
        System.out.println("Dictionnaire chargé avec " + dictionary.size() + " mots de " + wordLength + " lettres");
    }

    public void startServer() {
        new Thread(this::startNewRound).start();

        while (true) {
            try {
                Socket clientSocket = serverSocket.accept();
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                clients.add(clientHandler);
                threadPool.execute(clientHandler);
                System.out.println("Nouveau client connecté: " + clientSocket.getInetAddress());
            } catch (IOException e) {
                System.err.println("Erreur lors de l'acceptation d'un client: " + e.getMessage());
            }
        }
    }

    private void startNewRound() {
        this.secretWord = generateRandomWord(wordLength);
        this.gameRunning = true;
        System.out.println("=== NOUVELLE MANCHE ===");
        System.out.println("Mot secret: " + secretWord);
        System.out.println("=======================");

        broadcast("BROADCAST Nouvelle manche commencée! Devinez un mot de " + wordLength + " lettres");
    }

    private String generateRandomWord(int length) {
        if (dictionary.isEmpty()) {
            return "TABLE";
        }
        Random random = new Random();
        return dictionary.get(random.nextInt(dictionary.size()));
    }

    /**
     * Charge le dictionnaire de mots français
     */
    private List<String> loadDictionary() {
        List<String> words = Arrays.asList(
                "TABLE", "CHAISE", "FRUIT", "MAISON", "SOLEIL", "FLEUR", "ARBRE",
                "LIVRE", "ECOLE", "JARDIN", "PIANO", "CAMION", "HERBE", "MONTRE",
                "SACRE", "TIGRE", "PAPIER", "CIEL", "MERCI", "FORET", "ROUGE",
                "VERTE", "BLANC", "NOIRE", "JAUNE", "BLEUE", "GRISE", "ROSE",
                "TERRE", "PLAGE", "MONT", "VALLE", "RIVIERE", "FLEUVE", "SOURCE"
        );
        Collections.shuffle(words);
        return words;
    }

    private synchronized void broadcast(String message) {
        Iterator<ClientHandler> iterator = clients.iterator();
        while (iterator.hasNext()) {
            ClientHandler client = iterator.next();
            try {
                client.sendMessage(message);
            } catch (IOException e) {
                System.err.println("Erreur d'envoi au client " + client.getClientId() + ": " + e.getMessage());
                iterator.remove();
            }
        }
    }

    private synchronized void handleVictory(ClientHandler winner, String guess) {
        if (gameRunning) {
            gameRunning = false;
            String victoryMessage = "VICTORY " + winner.getClientId() + " ANSWER " + secretWord;
            broadcast(victoryMessage);
            System.out.println("🎉 " + winner.getClientId() + " a gagné avec: " + secretWord);

            // Redémarrer une nouvelle manche après un délai
            new Thread(() -> {
                try {
                    Thread.sleep(5000);
                    startNewRound();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        }
    }

    // MÉTHODE CORRIGÉE pour calculer le feedback
    private Feedback calculateFeedback(String guess, String secret) {
        if (guess == null || secret == null || guess.length() != secret.length()) {
            return new Feedback(0, 0, secret.length());
        }

        int length = secret.length();
        int okPos = 0;
        int okMis = 0;

        boolean[] secretUsed = new boolean[length];
        boolean[] guessUsed = new boolean[length];

        // Étape 1 : Lettres bien placées
        for (int i = 0; i < length; i++) {
            if (guess.charAt(i) == secret.charAt(i)) {
                okPos++;
                secretUsed[i] = true;
                guessUsed[i] = true;
            }
        }

        // Étape 2 : Lettres mal placées
        for (int i = 0; i < length; i++) {
            if (!guessUsed[i]) {
                for (int j = 0; j < length; j++) {
                    if (!secretUsed[j] && guess.charAt(i) == secret.charAt(j)) {
                        okMis++;
                        secretUsed[j] = true;
                        break;
                    }
                }
            }
        }

        int wrong = length - okPos - okMis;
        return new Feedback(okPos, okMis, wrong);
    }

    private class ClientHandler implements Runnable {
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String clientId;
        private boolean connected;

        public ClientHandler(Socket socket) throws IOException {
            this.socket = socket;
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            this.out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
            this.clientId = "client_" + System.currentTimeMillis() + "_" + socket.getPort();
            this.connected = true;
        }

        public String getClientId() {
            return clientId;
        }

        public void sendMessage(String message) throws IOException {
            if (connected) {
                out.println(message);
            }
        }

        @Override
        public void run() {
            try {
                // Envoi du message de bienvenue
                sendMessage("WELCOME " + clientId);
                sendMessage("WORDLEN " + wordLength);

                String inputLine;
                while ((inputLine = in.readLine()) != null && connected) {
                    System.out.println("Reçu de " + clientId + ": " + inputLine);
                    processClientMessage(inputLine);
                }
            } catch (IOException e) {
                System.err.println("Erreur de communication avec " + clientId + ": " + e.getMessage());
            } finally {
                disconnect();
            }
        }

        private void processClientMessage(String message) {
            if (message.startsWith("TRY ")) {
                handleTryCommand(message.substring(4).trim().toUpperCase());
            } else if (message.equals("QUIT")) {
                handleQuitCommand();
            } else {
                try {
                    sendMessage("INVALID unknown_command");
                } catch (IOException e) {
                    System.err.println("Erreur d'envoi: " + e.getMessage());
                }
            }
        }

        private void handleTryCommand(String guess) {
            if (!gameRunning) {
                try {
                    sendMessage("INVALID game_not_running");
                } catch (IOException e) {
                    System.err.println("Erreur d'envoi: " + e.getMessage());
                }
                return;
            }

            // Validation du mot
            if (guess.length() != wordLength) {
                try {
                    sendMessage("INVALID wrong_length");
                } catch (IOException e) {
                    System.err.println("Erreur d'envoi: " + e.getMessage());
                }
                return;
            }

            if (!guess.matches("[A-Z]+")) {
                try {
                    sendMessage("INVALID bad_chars");
                } catch (IOException e) {
                    System.err.println("Erreur d'envoi: " + e.getMessage());
                }
                return;
            }

            // Vérification si le client a gagné
            if (guess.equals(secretWord)) {
                handleVictory(this, guess);
                return;
            }

            // Calcul du feedback
            Feedback feedback = calculateFeedback(guess, secretWord);
            try {
                sendMessage("FEEDBACK OK_POS=" + feedback.okPos + " OK_MIS=" + feedback.okMis + " WRONG=" + feedback.wrong);
            } catch (IOException e) {
                System.err.println("Erreur d'envoi: " + e.getMessage());
            }
        }

        private void handleQuitCommand() {
            try {
                sendMessage("BYE");
            } catch (IOException e) {
                System.err.println("Erreur d'envoi: " + e.getMessage());
            }
            disconnect();
        }

        private void disconnect() {
            connected = false;
            clients.remove(this);
            try {
                if (in != null) in.close();
                if (out != null) out.close();
                if (socket != null) socket.close();
            } catch (IOException e) {
                System.err.println("Erreur lors de la fermeture des ressources: " + e.getMessage());
            }
            System.out.println("Client " + clientId + " déconnecté");
        }
    }

    private static class Feedback {
        final int okPos;
        final int okMis;
        final int wrong;

        Feedback(int okPos, int okMis, int wrong) {
            this.okPos = okPos;
            this.okMis = okMis;
            this.wrong = wrong;
        }
    }

    public static void main(String[] args) {
        System.out.println("====================================");
        System.out.println("       SERVEUR JEU DE MOTS");
        System.out.println("====================================");

        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        int wordLength = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_WORD_LENGTH;

        System.out.println("Port: " + port);
        System.out.println("Longueur des mots: " + wordLength);
        System.out.println("En attente de connexions...");
        System.out.println("====================================\n");

        try {
            WordGameServer server = new WordGameServer(port, wordLength);
            server.startServer();
        } catch (IOException e) {
            System.err.println("❌ Impossible de démarrer le serveur: " + e.getMessage());
            System.err.println("Vérifiez que le port " + port + " n'est pas déjà utilisé.");
        }
    }
}