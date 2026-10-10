import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

// Serves the CareTrack demo (npm run build:demo — sample data, no backend, no
// database) from inside a jar, so it runs on Azure App Service's Java runtime.
// Plain JDK, no libraries. Unknown page paths get index.html so links such as
// /manage/inventory open the app; missing files are a 404.
//
// Build (see .github/workflows/main_equipment.yml):
//   npm run build:demo && javac -d build demo-server/DemoServer.java
//   cp -r dist build/site && jar --create --file caretrack-demo.jar --main-class DemoServer -C build .
public class DemoServer {

	private static final Map<String, String> TYPES = Map.ofEntries(
			Map.entry("html", "text/html; charset=utf-8"),
			Map.entry("js", "text/javascript; charset=utf-8"),
			Map.entry("css", "text/css; charset=utf-8"),
			Map.entry("json", "application/json"),
			Map.entry("png", "image/png"),
			Map.entry("svg", "image/svg+xml"),
			Map.entry("ico", "image/x-icon"),
			Map.entry("webmanifest", "application/manifest+json"));

	public static void main(String[] args) throws IOException {
		HttpServer server = HttpServer.create(new InetSocketAddress(port()), 0);
		server.createContext("/", DemoServer::handle);
		server.setExecutor(Executors.newFixedThreadPool(16));
		server.start();
		System.out.println("CareTrack demo listening on port " + server.getAddress().getPort());
	}

	// App Service passes the port in PORT (or SERVER_PORT); it expects 80 when neither is set
	private static int port() {
		String port = System.getenv("PORT") != null ? System.getenv("PORT") : System.getenv("SERVER_PORT");
		if (port != null) {
			return Integer.parseInt(port.trim());
		}
		return System.getenv("WEBSITE_SITE_NAME") != null ? 80 : 8080;
	}

	private static void handle(HttpExchange exchange) throws IOException {
		try (exchange) {
			if (!exchange.getRequestMethod().equals("GET") && !exchange.getRequestMethod().equals("HEAD")) {
				send(exchange, 405, "text/plain; charset=utf-8", "Method not allowed".getBytes(), "no-store");
				return;
			}
			String path = URI.create(exchange.getRequestURI().getRawPath()).normalize().getPath();
			if (path.contains("..")) {
				send(exchange, 400, "text/plain; charset=utf-8", "Bad request".getBytes(), "no-store");
				return;
			}
			if (path.isEmpty() || path.endsWith("/")) {
				path += "index.html";
			}
			byte[] body = read("/site" + path);
			String name = path.substring(path.lastIndexOf('/') + 1);
			if (body == null) {
				if (name.contains(".")) { // a missing file, not a page of the app
					send(exchange, 404, "text/plain; charset=utf-8", "Not found".getBytes(), "no-store");
					return;
				}
				path = "/index.html";
				name = "index.html";
				body = read("/site/index.html");
			}
			String extension = name.substring(name.lastIndexOf('.') + 1);
			// Built assets have a content hash in their name and never change; pages must stay fresh
			String cache = path.startsWith("/assets/") ? "public, max-age=31536000, immutable" : "no-cache";
			send(exchange, 200, TYPES.getOrDefault(extension, "application/octet-stream"), body, cache);
		}
	}

	private static byte[] read(String resource) throws IOException {
		try (InputStream in = DemoServer.class.getResourceAsStream(resource)) {
			return in == null ? null : in.readAllBytes();
		}
	}

	private static void send(HttpExchange exchange, int status, String type, byte[] body, String cache) throws IOException {
		exchange.getResponseHeaders().set("Content-Type", type);
		exchange.getResponseHeaders().set("Cache-Control", cache);
		exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
		boolean head = exchange.getRequestMethod().equals("HEAD");
		exchange.sendResponseHeaders(status, head ? -1 : body.length);
		if (!head) {
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(body);
			}
		}
	}

}
