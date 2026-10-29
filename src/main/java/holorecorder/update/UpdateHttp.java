package holorecorder.update;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.function.LongConsumer;

/** HTTPS GET with a size cap, timeouts and a short list of hosts a redirect may lead to. Blocking: never call on the render thread. */
final class UpdateHttp {
    static final Set<String> HOSTS = Set.of("github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com");
    private static final int CONNECT_MS = 10_000, READ_MS = 60_000, MAX_REDIRECTS = 5;

    private UpdateHttp() {
    }

    /** True for an https address on an allowed host with no user info and the default port. */
    static boolean allowed(URI uri) {
        return uri != null && "https".equalsIgnoreCase(uri.getScheme()) && uri.getUserInfo() == null
                && (uri.getPort() == -1 || uri.getPort() == 443) && uri.getHost() != null
                && HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT));
    }

    /** Copies the body of {@code url} to {@code out}; fails when it is longer than {@code max} bytes. Returns the byte count. */
    static long get(String url, long max, OutputStream out, LongConsumer progress) throws IOException {
        URI uri = URI.create(url);
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            if (!allowed(uri)) throw new IOException("address not allowed");
            HttpURLConnection c = (HttpURLConnection) uri.toURL().openConnection();
            try {
                c.setInstanceFollowRedirects(false);
                c.setConnectTimeout(CONNECT_MS);
                c.setReadTimeout(READ_MS);
                c.setRequestProperty("User-Agent", "HoloplayerMod-Updater");
                int code = c.getResponseCode();
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    String next = c.getHeaderField("Location");
                    if (next == null) throw new IOException("redirect without address");
                    try {
                        uri = uri.resolve(next);
                    } catch (IllegalArgumentException e) {
                        throw new IOException("bad redirect");
                    }
                    continue;
                }
                if (code != 200) throw new IOException("HTTP " + code);
                long length = c.getContentLengthLong();
                if (length > max) throw new IOException("too large");
                long total = 0;
                try (InputStream in = c.getInputStream()) {
                    byte[] buffer = new byte[1 << 14];
                    for (int n; (n = in.read(buffer)) > 0; ) {
                        total += n;
                        if (total > max) throw new IOException("too large");
                        out.write(buffer, 0, n);
                        if (progress != null) progress.accept(total);
                    }
                }
                return total;
            } finally {
                c.disconnect();
            }
        }
        throw new IOException("too many redirects");
    }
}
