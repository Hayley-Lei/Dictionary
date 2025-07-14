/*
 * Name: Jing
 * Surname: Lei
 * Student ID: 1166617
 */
package server;

import java.io.*;
import java.net.*;
import java.util.*;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.util.concurrent.ConcurrentHashMap;

public class DictionaryServer {
    // Synchronized map to ensure thread safety
    private static Map<String, List<String>> dictionary = new ConcurrentHashMap<>();
    private static Gson gson = new Gson();

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java -jar DictionaryServer.jar <port> <dictionary-file>");
            return;
        }

        int port = Integer.parseInt(args[0]);
        String dictFile = args[1];
        loadDictionary(dictFile);

        // Save dictionary on shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down server, saving dictionary...");
            writeDictionary(dictFile);
        }));

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Dictionary server started on port " + port);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getInetAddress());
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Load dictionary data from a file (format: word: meaning1~meaning2~...)
    private static void loadDictionary(String fileName) {
        File file = new File(fileName);
        if (!file.exists() || !file.isFile()) {
            System.err.println("Dictionary file " + fileName + " does not exist. Starting with an empty dictionary.");
            return;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty() || !line.contains(":"))
                    continue;
                try {
                    String[] parts = line.split(":", 2);
                    String word = parts[0].trim();
                    String meaningsStr = parts[1].trim();
                    String[] meanings = meaningsStr.split("~");
                    List<String> meaningList = new ArrayList<>();
                    for (String meaning : meanings) {
                        meaningList.add(meaning.trim());
                    }
                    dictionary.put(word, meaningList);
                } catch (Exception e) {
                    System.err.println("Error parsing line: " + line + " - " + e.getMessage());
                }
            }
            System.out.println("Loaded dictionary with " + dictionary.size() + " entries.");
        } catch (IOException e) {
            System.err.println("Error loading dictionary: " + e.getMessage());
        }
    }

    // Write dictionary data back to a file
    private static void writeDictionary(String fileName) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
            for (Map.Entry<String, List<String>> entry : dictionary.entrySet()) {
                String meanings = String.join("~", entry.getValue());
                writer.write(entry.getKey() + ": " + meanings);
                writer.newLine();
            }
            System.out.println("Dictionary saved successfully.");
        } catch (IOException e) {
            System.err.println("Error writing dictionary: " + e.getMessage());
        }
    }

    // Handle each client connection
    static class ClientHandler implements Runnable {
        private Socket socket;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true)
            ) {
                String line;
                while ((line = in.readLine()) != null) {
                    try {
                        RequestMessage req = gson.fromJson(line, RequestMessage.class);
                        ResponseMessage res = processRequest(req);
                        String jsonResponse = gson.toJson(res);
                        out.println(jsonResponse);
                    } catch (JsonSyntaxException e) {
                        ResponseMessage res = new ResponseMessage();
                        res.status = "error";
                        res.message = "Invalid JSON format.";
                        out.println(gson.toJson(res));
                    }
                }
            } catch (SocketException e) {
                System.out.println("Client disconnected: " + e.getMessage());
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try { socket.close(); } catch (IOException e) { }
            }
        }

        // Calculate the Levenshtein distance between two strings
        private int levenshteinDistance(String s1, String s2) {
            int[][] dp = new int[s1.length() + 1][s2.length() + 1];
            for (int i = 0; i <= s1.length(); i++) {
                dp[i][0] = i;
            }
            for (int j = 0; j <= s2.length(); j++) {
                dp[0][j] = j;
            }
            for (int i = 1; i <= s1.length(); i++) {
                for (int j = 1; j <= s2.length(); j++) {
                    int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                    dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
                }
            }
            return dp[s1.length()][s2.length()];
        }

        // Process the client request and return the response
        private ResponseMessage processRequest(RequestMessage req) {
            ResponseMessage res = new ResponseMessage();
            String type = req.type.toLowerCase();
            if ("query".equals(type)) {
                if (req.word == null || req.word.trim().isEmpty()) {
                    res.status = "error";
                    res.message = "Word not provided.";
                } else {
                    List<String> meanings = dictionary.get(req.word);
                    if (meanings == null) {
                        // No exact match found, search for similar word
                        String similarWord = null;
                        int bestDistance = Integer.MAX_VALUE;
                        int maxAllowedDistance = 2;
                        for (String key : dictionary.keySet()) {
                            int distance = levenshteinDistance(req.word, key);
                            if (distance <= maxAllowedDistance && distance < bestDistance) {
                                similarWord = key;
                                bestDistance = distance;
                            }
                        }
                        if (similarWord != null) {
                            res.status = "error";
                            res.message = "Word not found.\nSimilar word found: " + similarWord;
                        } else {
                            res.status = "error";
                            res.message = "Word not found.";
                        }
                    } else {
                        res.status = "success";
                        res.message = "Query successful.";
                        res.data = meanings;
                    }
                }
            } else if ("add".equals(type)) {
                if (req.word == null || req.meanings == null || req.meanings.isEmpty()) {
                    res.status = "error";
                    res.message = "Invalid add request. Word and meanings required.";
                } else if (dictionary.containsKey(req.word)) {
                    res.status = "error";
                    res.message = "Word already exists.";
                } else {
                    dictionary.put(req.word, new ArrayList<>(req.meanings));
                    res.status = "success";
                    res.message = "Word added successfully.";
                }
            } else if ("remove".equals(type)) {
                if (req.word == null) {
                    res.status = "error";
                    res.message = "Word not provided.";
                } else if (dictionary.remove(req.word) != null) {
                    res.status = "success";
                    res.message = "Word removed successfully.";
                } else {
                    res.status = "error";
                    res.message = "Word not found.";
                }
            } else if ("update".equals(type)) {
                if (req.word == null || req.oldMeaning == null || req.newMeaning == null) {
                    res.status = "error";
                    res.message = "Invalid update request. Word, oldMeaning, and newMeaning required.";
                } else {
                    List<String> meanings = dictionary.get(req.word);
                    if (meanings == null) {
                        res.status = "error";
                        res.message = "Word not found.";
                    } else {
                        synchronized (meanings) {
                            if (!meanings.contains(req.oldMeaning)) {
                                res.status = "error";
                                res.message = "Old meaning not found.";
                            } else {
                                String[] newMeaningsArray = req.newMeaning.split("~");
                                List<String> newMeaningsList = new ArrayList<>();
                                for (String nm : newMeaningsArray) {
                                    nm = nm.trim();
                                    if (!nm.isEmpty()) {
                                        newMeaningsList.add(nm);
                                    }
                                }
                                List<String> uniqueMeanings = new ArrayList<>(new LinkedHashSet<>(newMeaningsList));

                                meanings.remove(req.oldMeaning);

                                boolean anyAdded = false;
                                List<String> addedMeaningsList = new ArrayList<>();
                                for (String nm : uniqueMeanings) {
                                    if (!meanings.contains(nm)) {
                                        meanings.add(nm);
                                        anyAdded = true;
                                        addedMeaningsList.add(nm);
                                    }
                                }

                                if (anyAdded) {
                                    res.status = "success";
                                    res.message = "Old meaning replaced.\nNew meanings added: " + String.join("; ", addedMeaningsList);
                                } else {
                                    meanings.add(req.oldMeaning);
                                    res.status = "error";
                                    res.message = "No new meaning was added because all provided new meanings already exist.";
                                }
                            }
                        }
                    }
                }
            } else if ("addmeaning".equals(type)) {
                if (req.word == null || req.meaning == null) {
                    res.status = "error";
                    res.message = "Invalid addMeaning request. Word and meaning required.";
                } else {
                    List<String> meanings = dictionary.get(req.word);
                    if (meanings == null) {
                        res.status = "error";
                        res.message = "Word not found.";
                    } else {
                        synchronized (meanings) {
                            if (meanings.contains(req.meaning)) {
                                res.status = "error";
                                res.message = "Meaning already exists: " + req.meaning;
                            } else {
                                meanings.add(req.meaning);
                                res.status = "success";
                                res.message = "Meaning added successfully: " + req.meaning;
                            }
                        }
                    }
                }
            } else {
                res.status = "error";
                res.message = "Unknown command type.";
            }
            return res;
        }
    }

    // Request message structure
    static class RequestMessage {
        String type;           // "query", "add", "remove", "update", "addmeaning"
        String word;
        List<String> meanings; // For "add"
        String meaning;        // For "addmeaning"
        String oldMeaning;     // For "update"
        String newMeaning;     // For "update"
    }

    // Response message structure
    static class ResponseMessage {
        String status;  // "success" or "error"
        String message; // Response message
        List<String> data; // Meanings for query result
    }
}
