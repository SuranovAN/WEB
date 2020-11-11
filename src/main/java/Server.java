import com.sun.net.httpserver.HttpHandler;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private ServerSocket serverSocket;
    private final List<String> allowedMethods;
    private List<String> validPath = new ArrayList<>();
    private static final int poolSize = 64;
    private final ExecutorService threadPool = Executors.newFixedThreadPool(poolSize);
    private static final int limit = 4096;
    private static final String GET = "GET";
    private static final String POST = "POST";

    public Server() {
        System.out.println("Server started!");
        allowedMethods = List.of(GET, POST);
        validPath.add("/default-get.html");
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
             final var is = new BufferedInputStream(socket.getInputStream());
             final var out = new BufferedOutputStream(socket.getOutputStream())) {
            var request = Request.fromInputStream(is);
            if (!request.getMethod().equals(GET)) {
                String body = "<div><h3>DATA from POST = " + request.getPar().toString() + "</h3></div>";
                out.write(("HTTP/1.1 200 OK\r\n" +
                        "Content-Length: " + body.length() + "\r\n" +
                        "Connection: Keep-Alive\r\n" +
                        "\r\n").getBytes());
                out.write(body.getBytes());
                out.flush();
            } else {
                final var filePath = Path.of(".", request.getPath());
                final var content = Files.readString(filePath);
                final var mimeType = Files.probeContentType(filePath);
                out.write(("HTTP/1.1 222 OK\r\n" +
                        "Content-Type " + mimeType + "\r\n" +
                        "Content-Length: " + content.length() + "\r\n" +
                        "\r\n").getBytes());
                out.write(content.getBytes());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void checkValidPath(String path, Socket socket, BufferedOutputStream out) {
        try {
            if (!validPath.contains(path)) {
                badRequest(out);
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static Optional<String> extractHeader(List<String> headers, String header) {
        return headers.stream()
                .filter(o -> o.startsWith(header))
                .map(o -> o.substring(o.indexOf(" ")))
                .map(String::trim)
                .findFirst();
    }

    private static void badRequest(BufferedOutputStream out) throws IOException {
        out.write((
                "HTTP/1.1 400 Bad Request\r\n" +
                        "Content-Length: 0\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());
        out.flush();
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
