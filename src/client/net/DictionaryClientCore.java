/*
 * Name: Jing
 * Surname: Lei
 * Student ID: 1166617
 */

package client.net;

import java.io.*;
import java.net.Socket;
import com.google.gson.Gson;

public class DictionaryClientCore {
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private Gson gson;

    public DictionaryClientCore(String serverAddress, int serverPort) throws IOException {
        socket = new Socket(serverAddress, serverPort);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
        writer = new PrintWriter(socket.getOutputStream(), true);
        gson = new Gson();
    }

    public void sendRequest(String jsonRequest) {
        writer.println(jsonRequest);
    }

    public String readResponse() throws IOException {
        return reader.readLine();
    }

    public Gson getGson() {
        return gson;
    }

    public void close() throws IOException {
        reader.close();
        writer.close();
        socket.close();
    }
}
