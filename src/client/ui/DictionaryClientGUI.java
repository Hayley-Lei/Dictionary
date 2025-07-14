/*
 * Name: Jing
 * Surname: Lei
 * Student ID: 1166617
 */

package client.ui;

import javax.swing.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.awt.*;
import java.util.LinkedHashSet;
import client.net.DictionaryClientCore;
import com.google.gson.Gson;

public class DictionaryClientGUI extends JFrame {
    // GUI components created by the GUI Designer
    private JPanel rootPanel;
    private JTextField txtWord;
    private JTextField txtMeanings;
    private JTextArea txtOutput;
    private JButton btnQuery;
    private JButton btnAdd;
    private JButton btnRemove;
    private JButton btnUpdate;
    private JButton btnAddMeaning;

    private DictionaryClientCore clientCore;
    private Gson gson;

    // Request and Response message classes for JSON communication
    class RequestMessage {
        String type;
        String word;
        List<String> meanings; // for "add"
        String meaning;        // for "addmeaning"
        String oldMeaning;     // for "update"
        String newMeaning;     // for "update"
    }

    class ResponseMessage {
        String status;  // "success" or "error"
        String message;
        List<String> data;
    }

    public DictionaryClientGUI(DictionaryClientCore clientCore) {
        this.clientCore = clientCore;
        this.gson = clientCore.getGson();
        setContentPane(rootPanel);
        setTitle("Dictionary Client GUI");
        setSize(800, 600);
        setMinimumSize(new Dimension(1000, 800));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        initListeners();
    }

    // Initialize button listeners
    private void initListeners() {
        btnQuery.addActionListener(e -> queryWord());
        btnAdd.addActionListener(e -> addWord());
        btnRemove.addActionListener(e -> removeWord());
        btnUpdate.addActionListener(e -> updateWord());
        btnAddMeaning.addActionListener(e -> addMeaning());

        txtWord.setBorder(BorderFactory.createEmptyBorder());
        txtMeanings.setBorder(BorderFactory.createEmptyBorder());

        btnQuery.setContentAreaFilled(true);
        btnAdd.setContentAreaFilled(true);
        btnRemove.setContentAreaFilled(true);
        btnAddMeaning.setContentAreaFilled(true);
        btnUpdate.setContentAreaFilled(true);

        btnAdd.setOpaque(true);
        btnUpdate.setOpaque(true);
        btnRemove.setOpaque(true);
        btnAddMeaning.setOpaque(true);
        btnQuery.setOpaque(true);

        btnQuery.setBackground(new Color(255,248,227));
        btnAdd.setBackground(new Color(248,217,196));
        btnRemove.setBackground(new Color(242,198,222));
        btnAddMeaning.setBackground(new Color(198,222,241));
        btnUpdate.setBackground(new Color(219,205,240));
    }

    // Query word from the server
    private void queryWord() {
        String word = txtWord.getText().trim();
        if (word.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a word.");
            return;
        }
        RequestMessage req = new RequestMessage();
        req.type = "query";
        req.word = word;
        String jsonReq = gson.toJson(req);
        clientCore.sendRequest(jsonReq);
        try {
            String response = clientCore.readResponse();
            if (response == null) {
                txtOutput.setText("Server disconnected.");
                return;
            }
            ResponseMessage res = gson.fromJson(response, ResponseMessage.class);
            txtOutput.setText(res.message);
            if (res.data != null && !res.data.isEmpty()) {
                txtOutput.append("\nResult: " + String.join(", ", res.data));
            }
        } catch (IOException ex) {
            txtOutput.setText("Error reading response: " + ex.getMessage());
        }
        clearInputs();
    }

    // Add a new word to the dictionary
    private void addWord() {
        String word = txtWord.getText().trim();
        String meaningsText = txtMeanings.getText().trim();
        if (word.isEmpty() || meaningsText.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter both word and meanings separated by '~'.");
            return;
        }
        // Split meanings using "~" and remove duplicates
        String[] meaningsArray = meaningsText.split("~");
        List<String> inputMeanings = new ArrayList<>();
        for (String m : meaningsArray) {
            if (!m.trim().isEmpty()) {
                inputMeanings.add(m.trim());
            }
        }
        if (inputMeanings.isEmpty()) {
            txtOutput.setText("No valid meaning provided.");
            return;
        }
        List<String> uniqueMeanings = new ArrayList<>(new LinkedHashSet<>(inputMeanings));
        if (uniqueMeanings.size() < inputMeanings.size()) {
            JOptionPane.showMessageDialog(this, "Duplicate meanings detected; duplicates will be ignored.");
        }
        // Check if word exists before adding
        RequestMessage queryReq = new RequestMessage();
        queryReq.type = "query";
        queryReq.word = word;
        String jsonQuery = gson.toJson(queryReq);
        clientCore.sendRequest(jsonQuery);
        try {
            String queryResponseStr = clientCore.readResponse();
            if (queryResponseStr == null) {
                txtOutput.setText("Server disconnected.");
                return;
            }
            ResponseMessage queryRes = gson.fromJson(queryResponseStr, ResponseMessage.class);
            txtOutput.setText(""); // Clear previous output
            if ("error".equals(queryRes.status)) {
                // Word does not exist, so add it
                RequestMessage addReq = new RequestMessage();
                addReq.type = "add";
                addReq.word = word;
                addReq.meanings = uniqueMeanings;
                String jsonAdd = gson.toJson(addReq);
                clientCore.sendRequest(jsonAdd);
                String addResponse = clientCore.readResponse();
                if (addResponse == null) {
                    txtOutput.setText("Server disconnected.");
                    return;
                }
                ResponseMessage addRes = gson.fromJson(addResponse, ResponseMessage.class);
                txtOutput.setText(addRes.message);
            } else {
                txtOutput.setText("Word already exists. Use 'Add Meaning' to append new meanings.");
            }
        } catch (IOException ex) {
            txtOutput.setText("Error communicating with server: " + ex.getMessage());
        }
        clearInputs();
    }

    // Remove a word from the dictionary
    private void removeWord() {
        String word = txtWord.getText().trim();
        if (word.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a word.");
            return;
        }
        RequestMessage req = new RequestMessage();
        req.type = "remove";
        req.word = word;
        String jsonReq = gson.toJson(req);
        clientCore.sendRequest(jsonReq);
        try {
            String response = clientCore.readResponse();
            if (response == null) {
                txtOutput.setText("Server disconnected.");
                return;
            }
            ResponseMessage res = gson.fromJson(response, ResponseMessage.class);
            txtOutput.setText(res.message);
        } catch (IOException ex) {
            txtOutput.setText("Error reading response: " + ex.getMessage());
        }
        clearInputs();
    }

    // Update an existing meaning of a word
    private void updateWord() {
        String word = txtWord.getText().trim();
        String meaningsText = txtMeanings.getText().trim();
        // Split input using "*" to separate old and new meanings
        String[] parts = meaningsText.split("\\*");
        if (word.isEmpty() || parts.length < 2) {
            JOptionPane.showMessageDialog(this, "Please enter a word and update text in format: oldMeaning*newMeaning(s) (new meanings separated by '~').");
            return;
        }
        RequestMessage req = new RequestMessage();
        req.type = "update";
        req.word = word;
        req.oldMeaning = parts[0].trim();
        req.newMeaning = parts[1].trim();  // May contain multiple meanings separated by "~"
        String jsonReq = gson.toJson(req);
        clientCore.sendRequest(jsonReq);
        try {
            String response = clientCore.readResponse();
            if (response == null) {
                txtOutput.setText("Server disconnected.");
                return;
            }
            ResponseMessage res = gson.fromJson(response, ResponseMessage.class);
            txtOutput.setText(res.message);
        } catch (IOException ex) {
            txtOutput.setText("Error reading response: " + ex.getMessage());
        }
        clearInputs();
    }

    // Add additional meaning(s) to an existing word
    private void addMeaning() {
        String word = txtWord.getText().trim();
        String meaningsText = txtMeanings.getText().trim();
        if (word.isEmpty() || meaningsText.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter both word and new meaning(s) separated by '~'.");
            return;
        }
        // Split meanings using "~" and remove duplicates
        String[] meaningsArray = meaningsText.split("~");
        List<String> inputMeanings = new ArrayList<>();
        for (String m : meaningsArray) {
            if (!m.trim().isEmpty()) {
                inputMeanings.add(m.trim());
            }
        }
        if (inputMeanings.isEmpty()) {
            txtOutput.setText("No valid meaning provided.");
            return;
        }
        List<String> uniqueMeanings = new ArrayList<>(new LinkedHashSet<>(inputMeanings));
        if (uniqueMeanings.size() < inputMeanings.size()) {
            JOptionPane.showMessageDialog(this, "Duplicate meanings detected; duplicates will be ignored.");
        }
        // Check if the word exists first
        RequestMessage queryReq = new RequestMessage();
        queryReq.type = "query";
        queryReq.word = word;
        String jsonQuery = gson.toJson(queryReq);
        clientCore.sendRequest(jsonQuery);
        try {
            String queryResponseStr = clientCore.readResponse();
            if (queryResponseStr == null) {
                txtOutput.setText("Server disconnected.");
                return;
            }
            ResponseMessage queryRes = gson.fromJson(queryResponseStr, ResponseMessage.class);
            txtOutput.setText(""); // Clear previous output
            if ("error".equals(queryRes.status)) {
                txtOutput.setText("Word not found. Please add the word first.");
            } else {
                // Add each new meaning
                for (String m : uniqueMeanings) {
                    RequestMessage addMeaningReq = new RequestMessage();
                    addMeaningReq.type = "addmeaning";
                    addMeaningReq.word = word;
                    addMeaningReq.meaning = m;
                    String jsonAddMeaning = gson.toJson(addMeaningReq);
                    clientCore.sendRequest(jsonAddMeaning);
                    String addMeaningResponse = clientCore.readResponse();
                    if (addMeaningResponse == null) {
                        txtOutput.setText("Server disconnected.");
                        return;
                    }
                    ResponseMessage addMeaningRes = gson.fromJson(addMeaningResponse, ResponseMessage.class);
                    txtOutput.append(addMeaningRes.status + "\n");
                    txtOutput.append(addMeaningRes.message + m + "\n");
                }
            }
        } catch (IOException ex) {
            txtOutput.setText("Error communicating with server: " + ex.getMessage());
        }
        clearInputs();
    }

    // Clear text fields
    private void clearInputs() {
        txtWord.setText("");
        txtMeanings.setText("");
    }
}
