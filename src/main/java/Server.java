import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private ServerSocket serverSocket;
    private List<Socket> clientsSockets;
    private List<String> validPath = new ArrayList<>();
    private final static int poolSize = 64;
    private final ExecutorService threadPool = Executors.newFixedThreadPool(poolSize);

    public Server() {
        System.out.println("Server started!");
        validPath.add("/classic.html");
    }

    public List<String> getValidPath() {
        return validPath;
    }

    public void setValidPath(List<String> validPath) {
        this.validPath = validPath;
    }

    private void handle() {
        try (var socket = serverSocket.accept();
             final var in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             final var out = new BufferedOutputStream(socket.getOutputStream())) {
            final var requestLine = in.readLine();
            final var parts = requestLine.split(" ");
            if (parts.length != 3) {
                socket.close();
            }

            final var path = parts[1];
            checkValidPath(parts[1], socket, out);

            final var filePath = Path.of(".", "public", path);
            final var mimeType = Files.probeContentType(filePath);
            readFile(path, filePath, mimeType, socket, out);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void checkValidPath(String path, Socket socket, BufferedOutputStream out) {
        try {
            if (!validPath.contains(path)) {
                out.write(("HTTP/1.1 404Not Found\r\n" +
                        "Content-Length: 0\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
                ).getBytes());
                out.flush();
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void readFile(String path, Path filePath, String mimeType, Socket socket, BufferedOutputStream out) {
        try {
            if (path.equals("/classic.html")) {
                final var length = Files.size(filePath);
                out.write(("HTTP/1.1 200 OK\r\n" +
                        "Content-Type: " + mimeType + "\r\n" +
                        "Content-Length: " + length + "\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
                ).getBytes());
                Files.copy(filePath, out);
                out.flush();
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public void listen(int port) {
        try {
            serverSocket = new ServerSocket(port);
            while (true) {
                threadPool.submit(this::handle).get();
            }
        } catch (IOException | InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }
}
