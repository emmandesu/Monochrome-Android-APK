package com.monochrome.app;

import android.net.Uri;
import android.util.Log;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import com.getcapacitor.Bridge;
import com.getcapacitor.BridgeWebViewClient;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Extends Capacitor's BridgeWebViewClient to intercept selected upstream
 * requests that are fragile inside Android WebView.
 *
 * Current native proxy paths:
 * - Tidal CDN audio: adds the Origin/Referer expected by Tidal.
 * - Monochrome auth API: adds CORS headers so WebView can read account state.
 */
public class TidalWebViewClient extends BridgeWebViewClient {

    private static final String TAG = "TidalWebViewClient";
    private static final String APP_ORIGIN = "https://monochrome.tf";
    private static final String AUTH_HOST = "auth.monochrome.tf";

    public TidalWebViewClient(Bridge bridge) {
        super(bridge);
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        String url = request.getUrl().toString();

        if (isTidalAudioUrl(url)) {
            try {
                return proxyWithTidalOrigin(url, request);
            } catch (Exception e) {
                Log.w(TAG, "Tidal CDN proxy failed, falling back: " + e.getMessage());
            }
        }

        if (isMonochromeAuthUrl(request.getUrl())) {
            try {
                return proxyWithAppCors(url, request);
            } catch (Exception e) {
                Log.w(TAG, "Monochrome auth proxy failed, falling back: " + e.getMessage());
            }
        }

        // Everything else: delegate to Capacitor's bridge client
        return super.shouldInterceptRequest(view, request);
    }

    private boolean isTidalAudioUrl(String url) {
        return url.contains(".audio.tidal.com/")
                || url.contains(".tidal.com/mediatracks/");
    }

    private boolean isMonochromeAuthUrl(Uri uri) {
        return uri != null && AUTH_HOST.equalsIgnoreCase(uri.getHost());
    }

    private WebResourceResponse proxyWithTidalOrigin(String url, WebResourceRequest request)
            throws Exception {
        URL audioUrl = new URL(url);
        HttpURLConnection conn = (HttpURLConnection) audioUrl.openConnection();
        conn.setRequestMethod(request.getMethod());

        // The key fix: set Origin that Tidal CDN expects
        conn.setRequestProperty("Origin", "https://listen.tidal.com");
        conn.setRequestProperty("Referer", "https://listen.tidal.com/");

        // Copy headers from original request (except Origin/Referer)
        for (Map.Entry<String, String> header : request.getRequestHeaders().entrySet()) {
            String key = header.getKey();
            if (!key.equalsIgnoreCase("Origin")
                    && !key.equalsIgnoreCase("Referer")) {
                conn.setRequestProperty(key, header.getValue());
            }
        }

        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        conn.setInstanceFollowRedirects(true);
        conn.connect();

        int responseCode = conn.getResponseCode();
        String responseMessage = conn.getResponseMessage();
        if (responseMessage == null) responseMessage = "OK";

        String contentType = conn.getContentType();
        String mimeType = (contentType != null)
                ? contentType.split(";")[0].trim()
                : "audio/flac";

        Map<String, String> responseHeaders = collectResponseHeaders(conn);
        // Allow the WebView to read the response
        responseHeaders.put("Access-Control-Allow-Origin", "*");

        InputStream stream = getResponseStream(conn, responseCode);

        return new WebResourceResponse(mimeType, "identity",
                responseCode, responseMessage, responseHeaders, stream);
    }

    private WebResourceResponse proxyWithAppCors(String url, WebResourceRequest request)
            throws Exception {
        Map<String, String> corsHeaders = buildCorsHeaders();

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return new WebResourceResponse("text/plain", "utf-8",
                    204, "No Content", corsHeaders,
                    new ByteArrayInputStream(new byte[0]));
        }

        URL authUrl = new URL(url);
        HttpURLConnection conn = (HttpURLConnection) authUrl.openConnection();
        conn.setRequestMethod(request.getMethod());

        conn.setRequestProperty("Origin", APP_ORIGIN);
        conn.setRequestProperty("Referer", APP_ORIGIN + "/");

        for (Map.Entry<String, String> header : request.getRequestHeaders().entrySet()) {
            String key = header.getKey();
            if (!key.equalsIgnoreCase("Origin")
                    && !key.equalsIgnoreCase("Referer")
                    && !key.equalsIgnoreCase("Host")) {
                conn.setRequestProperty(key, header.getValue());
            }
        }

        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setInstanceFollowRedirects(true);
        conn.connect();

        int responseCode = conn.getResponseCode();
        String responseMessage = conn.getResponseMessage();
        if (responseMessage == null) responseMessage = "OK";

        String contentType = conn.getContentType();
        String mimeType = (contentType != null)
                ? contentType.split(";")[0].trim()
                : "application/json";

        Map<String, String> responseHeaders = collectResponseHeaders(conn);
        responseHeaders.putAll(corsHeaders);

        InputStream stream = getResponseStream(conn, responseCode);

        return new WebResourceResponse(mimeType, "utf-8",
                responseCode, responseMessage, responseHeaders, stream);
    }

    private Map<String, String> collectResponseHeaders(HttpURLConnection conn) {
        Map<String, String> responseHeaders = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : conn.getHeaderFields().entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null && !entry.getValue().isEmpty()) {
                responseHeaders.put(entry.getKey(), entry.getValue().get(0));
            }
        }
        return responseHeaders;
    }

    private Map<String, String> buildCorsHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Access-Control-Allow-Origin", APP_ORIGIN);
        headers.put("Access-Control-Allow-Credentials", "true");
        headers.put("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
        headers.put("Access-Control-Allow-Headers", "Authorization, Content-Type, Accept, Origin, Referer, X-Requested-With");
        headers.put("Access-Control-Expose-Headers", "Content-Type, Authorization, Set-Cookie");
        headers.put("Vary", "Origin");
        return headers;
    }

    private InputStream getResponseStream(HttpURLConnection conn, int responseCode) throws Exception {
        if (responseCode >= 400) {
            InputStream error = conn.getErrorStream();
            if (error != null) return error;
        }
        return conn.getInputStream();
    }
}
