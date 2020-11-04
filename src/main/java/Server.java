import org.apache.http.client.utils.URLEncodedUtils;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private ServerSocket serverSocket;
    private List<String> allowedMethods;
    private List<String> validPath = new ArrayList<>();
    private static final int poolSize = 64;
    private final ExecutorService threadPool = Executors.newFixedThreadPool(poolSize);
    private static final int limit = 4096;
    private static final String GET = "GET";
    private static final String POST = "POST";

    public Server() {
        System.out.println("Server started!");
        allowedMethods = List.of(GET, POST);
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
             final var in = new BufferedInputStream(socket.getInputStream());
             final var out = new BufferedOutputStream(socket.getOutputStream())) {
            in.mark(limit);
            final var buffer = new byte[limit];
            final var read = in.read(buffer);
            final var requestLineDelimiter = new byte[]{'\r', '\n'};
            final var requestLineEnd = indexOf(buffer, requestLineDelimiter, 0, read);
            if (requestLineEnd == -1) {
                badRequest(out);
                socket.close();
            }

            final var requestLine = new String(Arrays.copyOf(buffer, requestLineEnd)).split(" ");
            if (requestLine.length != 3) {
                badRequest(out);
                socket.close();
            }

            final var method = requestLine[0];
            if (!allowedMethods.contains(method)) {
                badRequest(out);
                socket.close();
            }
            System.out.println(method);

            final var path = requestLine[1];
            checkValidPath(requestLine[1], socket, out);

            final var headersDelimiter = new byte[]{'\r', '\n', '\r', '\n'};
            final var headersStart = requestLineEnd + requestLineDelimiter.length;
            final var headersEnd = indexOf(buffer, headersDelimiter, headersStart, read);
            if (headersEnd == -1) {
                badRequest(out);
                socket.close();
            }

            in.reset();
            in.skip(headersStart);

            final var headersByte = in.readNBytes(headersEnd - headersStart);
            final var headers = Arrays.asList(new String(headersByte).split("\r\n"));
            System.out.println(headers);

            if (!method.equals(GET)){
                in.skip(headersDelimiter.length);
                final var contentLength = extractHeader(headers, "Content-Length");
                if (contentLength.isPresent()){
                    final var length = Integer.parseInt(contentLength.get());
                    final var bodyBytes = in.readNBytes(length);
                    final var body = new String(bodyBytes);
                    System.out.println(body);
                }
            }

            out.write((
                    "HTTP/1.1 200 OK\r\n" +
                            "Content-Length: 0\r\n" +
                            "Connection: close\r\n" +
                            "\r\n"
                    ).getBytes());
            out.flush();

            final var filePath = Path.of(".", "public", path);
            final var mimeType = Files.probeContentType(filePath);
            readFile(path, filePath, mimeType, socket, out);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void checkValidPath(String path, Socket socket, BufferedOutputStream out) {
        try {
            if (!validPath.contains(path) || path.startsWith("/")) {
                badRequest(out);
                System.out.println(path);
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

    private void getQueryParam(String name) {

    }

    private void getQueryParams() {

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

    private static int indexOf(byte[] array, byte[] target, int start, int max) {
        outer:
        for (int i = start; i < max - target.length + 1; i++) {
            for (int j = 0; j < target.length; j++) {
                if (array[i + j] != target[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static Optional<String> extractHeader(List<String> headers, String header){
        return headers.stream()
                .filter(o -> o.startsWith(header))
                .map(o -> o.substring(o.indexOf(" ")))
                .map(String::trim)
                .findFirst();
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
