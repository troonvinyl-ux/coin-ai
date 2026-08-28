package com.troonvinyl.iphoneshare;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.text.format.Formatter;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final int PORT = 8080;

    ImageView image;
    WebView web;
    LinearLayout info;
    TextView address;

    final ArrayList<File> files = new ArrayList<>();
    int index = -1;

    HttpServer server;
    ExecutorService pool = Executors.newCachedThreadPool();

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);

        getWindow().setFlags(
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        setContentView(R.layout.activity_main);

        image = findViewById(R.id.image);
        web = findViewById(R.id.web);
        info = findViewById(R.id.info);
        address = findViewById(R.id.address);

        web.getSettings().setJavaScriptEnabled(true);
        web.setWebViewClient(new WebViewClient());

        String ip = getIp();

        address.setText(
                "Open http://" + ip + ":" + PORT + " on your iPhone"
        );

        server = new HttpServer(PORT);
        pool.execute(server);
    }

    private String getIp() {
        try {
            WifiManager wifi =
                    (WifiManager) getApplicationContext()
                            .getSystemService(WIFI_SERVICE);

            WifiInfo connectionInfo = wifi.getConnectionInfo();

            return Formatter.formatIpAddress(
                    connectionInfo.getIpAddress()
            );

        } catch (Exception e) {
            return "TV-IP";
        }
    }

    private void showFile(File f) {
        runOnUiThread(() -> {
            web.setVisibility(View.GONE);
            image.setVisibility(View.VISIBLE);
            info.setVisibility(View.GONE);

            Bitmap bm = BitmapFactory.decodeFile(
                    f.getAbsolutePath()
            );

            image.setImageBitmap(bm);
        });
    }

    private void showUrl(String url) {
        runOnUiThread(() -> {
            image.setVisibility(View.GONE);
            info.setVisibility(View.GONE);
            web.setVisibility(View.VISIBLE);

            web.loadUrl(url);
        });
    }

    private void next(int direction) {
        if (files.isEmpty()) {
            return;
        }

        index = (index + direction + files.size()) % files.size();

        showFile(files.get(index));
    }

    private void clear() {
        for (File f : files) {
            try {
                f.delete();
            } catch (Exception ignored) {
            }
        }

        files.clear();
        index = -1;

        runOnUiThread(() -> {
            image.setImageDrawable(null);
            web.setVisibility(View.GONE);
            info.setVisibility(View.VISIBLE);
        });
    }

    @Override
    public boolean onKeyDown(int key, KeyEvent event) {

        if (key == KeyEvent.KEYCODE_DPAD_RIGHT) {
            next(1);
            return true;
        }

        if (key == KeyEvent.KEYCODE_DPAD_LEFT) {
            next(-1);
            return true;
        }

        return super.onKeyDown(key, event);
    }

    /*
     * Simple HTTP server used by the iPhone.
     */
    class HttpServer implements Runnable {

        private final int port;
        private ServerSocket serverSocket;

        HttpServer(int port) {
            this.port = port;
        }

        @Override
        public void run() {

            try {

                /*
                 * Explicitly bind to every network interface.
                 */
                serverSocket = new ServerSocket(
                        port,
                        50,
                        InetAddress.getByName("0.0.0.0")
                );

                serverSocket.setReuseAddress(true);

                while (!Thread.currentThread().isInterrupted()) {

                    Socket socket = serverSocket.accept();

                    socket.setKeepAlive(false);
                    socket.setSoTimeout(30000);

                    pool.execute(() -> handle(socket));
                }

            } catch (Exception e) {

                /*
                 * Show the server error on the TV instead of
                 * silently failing.
                 */
                runOnUiThread(() -> {
                    address.setText(
                            "SERVER ERROR: " + e.getClass().getSimpleName()
                                    + " - " + e.getMessage()
                    );
                });
            }
        }

        private void send(
                OutputStream output,
                String status,
                String contentType,
                byte[] body
        ) throws Exception {

            String headers =
                    "HTTP/1.1 " + status + "\r\n" +
                    "Content-Type: " + contentType + "\r\n" +
                    "Content-Length: " + body.length + "\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                    "Access-Control-Allow-Headers: Content-Type\r\n" +
                    "Connection: close\r\n" +
                    "\r\n";

            output.write(headers.getBytes("UTF-8"));
            output.write(body);
            output.flush();
        }

        private void handle(Socket socket) {

            try {

                BufferedInputStream input =
                        new BufferedInputStream(
                                socket.getInputStream()
                        );

                String requestLine = readLine(input);

                if (requestLine == null || requestLine.isEmpty()) {
                    socket.close();
                    return;
                }

                String[] parts = requestLine.split(" ");

                if (parts.length < 2) {
                    send(
                            socket.getOutputStream(),
                            "400 Bad Request",
                            "text/plain; charset=utf-8",
                            "Bad Request".getBytes("UTF-8")
                    );

                    socket.close();
                    return;
                }

                String method = parts[0];
                String path = parts[1];

                int contentLength = 0;

                String line;

                while ((line = readLine(input)) != null) {

                    if (line.isEmpty()) {
                        break;
                    }

                    String lower = line.toLowerCase();

                    if (lower.startsWith("content-length:")) {

                        try {
                            contentLength = Integer.parseInt(
                                    line.substring(15).trim()
                            );
                        } catch (Exception ignored) {
                            contentLength = 0;
                        }
                    }
                }

                byte[] body = new byte[contentLength];

                int received = 0;

                while (received < contentLength) {

                    int read = input.read(
                            body,
                            received,
                            contentLength - received
                    );

                    if (read < 0) {
                        break;
                    }

                    received += read;
                }

                OutputStream output =
                        socket.getOutputStream();

                /*
                 * CORS pre-flight.
                 */
                if ("OPTIONS".equalsIgnoreCase(method)) {

                    send(
                            output,
                            "204 No Content",
                            "text/plain; charset=utf-8",
                            new byte[0]
                    );

                    socket.close();
                    return;
                }

                /*
                 * Health check.
                 *
                 * Opening:
                 * http://TV-IP:8080/health
                 *
                 * should return:
                 * OK
                 */
                if ("GET".equalsIgnoreCase(method)
                        && path.equals("/health")) {

                    send(
                            output,
                            "200 OK",
                            "text/plain; charset=utf-8",
                            "OK".getBytes("UTF-8")
                    );

                    socket.close();
                    return;
                }

                /*
                 * Main iPhone control page.
                 */
                if ("GET".equalsIgnoreCase(method)
                        && path.equals("/")) {

                    send(
                            output,
                            "200 OK",
                            "text/html; charset=utf-8",
                            html().getBytes("UTF-8")
                    );

                    socket.close();
                    return;
                }

                /*
                 * Upload image.
                 */
                if ("POST".equalsIgnoreCase(method)
                        && path.startsWith("/upload")) {

                    String name = "image";

                    int query = path.indexOf("name=");

                    if (query >= 0) {

                        name = URLDecoder.decode(
                                path.substring(query + 5),
                                "UTF-8"
                        );
                    }

                    File file = new File(
                            getCacheDir(),
                            System.currentTimeMillis()
                                    + "_"
                                    + safe(name)
                    );

                    try (FileOutputStream fos =
                                 new FileOutputStream(file)) {

                        fos.write(body);
                    }

                    files.add(file);
                    index = files.size() - 1;

                    showFile(file);

                    send(
                            output,
                            "200 OK",
                            "text/plain; charset=utf-8",
                            "OK".getBytes("UTF-8")
                    );

                    socket.close();
                    return;
                }

                /*
                 * Next image.
                 */
                if ("POST".equalsIgnoreCase(method)
                        && path.equals("/next")) {

                    next(1);

                    send(
                            output,
                            "200 OK",
                            "text/plain; charset=utf-8",
                            "OK".getBytes("UTF-8")
                    );

                    socket.close();
                    return;
                }

                /*
                 * Previous image.
                 */
                if ("POST".equalsIgnoreCase(method)
                        && path.equals("/prev")) {

                    next(-1);

                    send(
                            output,
                            "200 OK",
                            "text/plain; charset=utf-8",
                            "OK".getBytes("UTF-8")
                    );

                    socket.close();
                    return;
                }

                /*
                 * Clear images.
                 */
                if ("POST".equalsIgnoreCase(method)
                        && path.equals("/clear")) {

                    clear();

                    send(
                            output,
                            "200 OK",
                            "text/plain; charset=utf-8",
                            "OK".getBytes("UTF-8")
                    );

                    socket.close();
                    return;
                }

                /*
                 * Tell TV WebView to open a URL.
                 */
                if ("POST".equalsIgnoreCase(method)
                        && path.equals("/url")) {

                    String url =
                            new String(body, "UTF-8").trim();

                    showUrl(url);

                    send(
                            output,
                            "200 OK",
                            "text/plain; charset=utf-8",
                            "OK".getBytes("UTF-8")
                    );

                    socket.close();
                    return;
                }

                /*
                 * Anything else.
                 */
                send(
                        output,
                        "404 Not Found",
                        "text/plain; charset=utf-8",
                        "Not found".getBytes("UTF-8")
                );

                socket.close();

            } catch (Exception e) {

                try {
                    socket.close();
                } catch (Exception ignored) {
                }
            }
        }

        private String readLine(InputStream input)
                throws Exception {

            StringBuilder result = new StringBuilder();

            int c;

            while ((c = input.read()) != -1) {

                if (c == '\n') {
                    break;
                }

                if (c != '\r') {
                    result.append((char) c);
                }
            }

            if (c == -1 && result.length() == 0) {
                return null;
            }

            return result.toString();
        }

        private String safe(String value) {

            return value.replaceAll(
                    "[^A-Za-z0-9._-]",
                    "_"
            );
        }

        private String html() {

            return "<!doctype html>" +
                    "<html>" +
                    "<head>" +
                    "<meta name='viewport' content='width=device-width,initial-scale=1'>" +
                    "<title>iPhone Share TV</title>" +

                    "<style>" +
                    "body{" +
                    "font-family:system-ui;" +
                    "background:#0b0d12;" +
                    "color:white;" +
                    "padding:20px;" +
                    "max-width:700px;" +
                    "margin:auto" +
                    "}" +

                    "button,input{" +
                    "font:inherit;" +
                    "padding:14px;" +
                    "margin:6px 0;" +
                    "width:100%;" +
                    "border-radius:12px;" +
                    "border:0" +
                    "}" +

                    "button{" +
                    "font-weight:700" +
                    "}" +

                    "</style>" +
                    "</head>" +

                    "<body>" +

                    "<h1>📺 iPhone Share</h1>" +

                    "<p>Select photos/screenshots and send them directly to this TV.</p>" +

                    "<input id='f' type='file' accept='image/*' multiple>" +

                    "<button onclick='send()'>Send to TV</button>" +

                    "<input id='u' placeholder='https://example.com'>" +

                    "<button onclick='url()'>Show web page</button>" +

                    "<button onclick=\"fetch('/prev',{method:'POST'})\">Previous</button>" +

                    "<button onclick=\"fetch('/next',{method:'POST'})\">Next</button>" +

                    "<button onclick=\"fetch('/clear',{method:'POST'})\">Clear</button>" +

                    "<script>" +

                    "async function send(){" +
                    "for(const file of document.getElementById('f').files)" +
                    "await fetch('/upload?name='+encodeURIComponent(file.name)," +
                    "{method:'POST'," +
                    "headers:{'Content-Type':file.type}," +
                    "body:file});" +
                    "}" +

                    "async function url(){" +
                    "await fetch('/url'," +
                    "{method:'POST'," +
                    "headers:{'Content-Type':'text/plain'}," +
                    "body:document.getElementById('u').value});" +
                    "}" +

                    "</script>" +

                    "</body>" +
                    "</html>";
        }
    }
}