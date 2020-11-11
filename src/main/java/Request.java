import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Request {
    private final String method;
    private final String path;
    private List<NameValuePair> par;
    private final Map<String, String> headers;
    private final InputStream is;

    private Request(String method, String path, List<NameValuePair> par, Map<String, String> headers, InputStream is) {
        this.method = method;
        this.path = path;
        this.par = par;
        this.headers = headers;
        this.is = is;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public List<NameValuePair> getPar() {
        return par;
    }

    public InputStream getIs() {
        return is;
    }

    public static Request fromInputStream(InputStream is) throws IOException {
        var reader = new BufferedReader(new InputStreamReader(is));
        final var requestLine = reader.readLine();
        final var parts = requestLine.split(" ");
        final var limit = 4096;
        is.mark(limit);

        var method = parts[0];
        String path;
        List<NameValuePair> par = new ArrayList<>();
        if (!parts[1].contains("?")) {
            path = parts[1];
        } else {
            path = parts[1].substring(0, parts[1].indexOf("?"));
            par = getQueryParam(parts[1].substring(parts[1].indexOf("?") + 1));
        }

        Map<String, String> headers = new HashMap<>();
        String headerLine;
        while (!(headerLine = reader.readLine()).equals("")) {
            var i = headerLine.indexOf(":");
            var headerName = headerLine.substring(0, i);
            var headerValue = headerLine.substring(i + 2);
            headers.put(headerName, headerValue);
        }

        return new Request(method, path, par, headers, is);
    }

    private static List<NameValuePair> getQueryParam(String name) {
        return URLEncodedUtils.parse(name, StandardCharsets.UTF_8);
    }

    private List<NameValuePair> getQueryParams() throws IOException {
        return null;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Request.class.getSimpleName() + "[", "]")
                .add("method='" + method + "'")
                .add("path='" + path + "'")
                .add("headers=" + headers)
                .toString();
    }
}
