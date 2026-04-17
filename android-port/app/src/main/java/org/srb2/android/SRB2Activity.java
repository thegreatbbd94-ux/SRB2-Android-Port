package org.srb2.android;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;

import org.libsdl.app.SDLActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Enumeration;

/**
 * Main Activity for SRB2 Android.
 * Extends SDL2's SDLActivity which handles the native GL surface,
 * audio, and input events.
 */
public class SRB2Activity extends SDLActivity {

    private static final String TAG = "SRB2";
    private TouchControlsView touchControls;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Set the game data path before SDL init
        setupGameFiles();

        // Build CA certificate bundle for curl/mbedTLS SSL
        buildCACertBundle();

        super.onCreate(savedInstanceState);

        // Go fullscreen / immersive
        hideSystemUI();

        // Keep screen on while playing
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Add touch controls overlay on top of SDL surface
        addTouchControls();

        Log.i(TAG, "SRB2 Activity created, data dir: " + getGameDataPath());
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
    }

    /**
     * Override to specify the shared library name.
     * SDL2 will load libsrb2.so (our native library) and libSDL2.so.
     */
    @Override
    protected String[] getLibraries() {
        return new String[]{
            "SDL2",
            "srb2"
        };
    }

    /**
     * Override to pass command line arguments to SRB2.
     * Tells SRB2 where to find its data files on Android.
     */
    @Override
    protected String[] getArguments() {
        String dataPath = getGameDataPath();
        return new String[]{
            "-home", dataPath,
            "-software",
        };
    }

    /**
     * Get the path where game data files (pk3, wad, etc.) are stored.
     * Uses app-internal files dir to avoid scoped storage issues on Android 11+.
     */
    private String getGameDataPath() {
        // Use internal files dir (no permissions needed, no scoped storage issues)
        File srb2Dir = new File(getFilesDir(), "SRB2");
        return srb2Dir.getAbsolutePath();
    }

    /**
     * Get the legacy external path where user may have pushed files via adb.
     */
    private String getExternalGameDataPath() {
        File dir = getExternalFilesDir(null);
        if (dir == null) return null;
        File srb2Dir = new File(dir, "SRB2");
        return srb2Dir.getAbsolutePath();
    }

    /**
     * Set up game files: if files exist in external storage (pushed via adb),
     * copy them to internal storage on first run. This avoids scoped storage
     * permission issues on Android 11+ / Android 16.
     */
    private void setupGameFiles() {
        String internalPath = getGameDataPath();
        File internalDir = new File(internalPath);
        if (!internalDir.exists()) {
            internalDir.mkdirs();
        }

        // Check if we already have srb2.pk3 in internal dir
        File mainPk3 = new File(internalDir, "srb2.pk3");
        if (mainPk3.exists()) {
            Log.i(TAG, "setupGameFiles: srb2.pk3 already in internal dir: " + internalPath);
            return;
        }

        // Try to copy from external storage (where user pushed files via adb)
        String externalPath = getExternalGameDataPath();
        if (externalPath != null) {
            File externalDir = new File(externalPath);
            Log.i(TAG, "setupGameFiles: checking external dir: " + externalPath + " exists=" + externalDir.exists());
            if (externalDir.exists() && externalDir.isDirectory()) {
                File[] files = externalDir.listFiles();
                if (files != null) {
                    for (File src : files) {
                        if (src.isFile()) {
                            File dest = new File(internalDir, src.getName());
                            Log.i(TAG, "setupGameFiles: copying " + src.getName()
                                + " (" + (src.length() / 1024 / 1024) + " MB) to internal...");
                            try {
                                copyFile(src, dest);
                                Log.i(TAG, "setupGameFiles: copied " + src.getName() + " OK");
                            } catch (IOException e) {
                                Log.e(TAG, "setupGameFiles: FAILED to copy " + src.getName(), e);
                            }
                        }
                    }
                }
            } else {
                Log.w(TAG, "setupGameFiles: external dir missing or not a directory");
            }
        }

        // Also try copying from APK assets (legacy path)
        try {
            AssetManager am = getAssets();
            String[] assetFiles = am.list("srb2data");
            if (assetFiles != null) {
                for (String filename : assetFiles) {
                    File destFile = new File(internalDir, filename);
                    if (!destFile.exists()) {
                        Log.i(TAG, "Copying asset: " + filename);
                        copyAsset(am, "srb2data/" + filename, destFile);
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error copying assets", e);
        }

        // Final check
        if (mainPk3.exists()) {
            Log.i(TAG, "setupGameFiles: srb2.pk3 ready in internal dir, size=" + mainPk3.length());
        } else {
            Log.e(TAG, "setupGameFiles: WARNING - srb2.pk3 NOT FOUND in " + internalPath);
        }
    }

    /**
     * Copy a file from source to destination.
     */
    private void copyFile(File src, File dest) throws IOException {
        try (InputStream in = new java.io.FileInputStream(src);
             OutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[65536];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }

    /**
     * Copy a single asset file to destination.
     */
    private void copyAsset(AssetManager am, String assetPath, File dest) throws IOException {
        try (InputStream in = am.open(assetPath);
             OutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }

    /**
     * Build a combined CA certificate bundle (PEM) from Android's trusted
     * system certificates.  Curl+mbedTLS needs a single file (CURLOPT_CAINFO)
     * because CAPATH directory scanning may fail on Android.
     *
     * The bundle is written to the app-internal files dir and its path is
     * exported as the environment variable SRB2_CA_BUNDLE so the native code
     * can find it.
     */
    private void buildCACertBundle() {
        try {
            File caFile = new File(getFilesDir(), "cacert.pem");

            // Rebuild once per app install / update (or if missing)
            if (caFile.exists() && caFile.length() > 0) {
                Log.i(TAG, "CA bundle already exists: " + caFile.getAbsolutePath()
                        + " (" + caFile.length() / 1024 + " KB)");
                sCaBundlePath = caFile.getAbsolutePath();
                return;
            }

            Log.i(TAG, "Building CA certificate bundle...");

            // Use the Android TrustManager to get the system-trusted CAs
            KeyStore ks = KeyStore.getInstance("AndroidCAStore");
            ks.load(null);

            StringBuilder pem = new StringBuilder();
            Enumeration<String> aliases = ks.aliases();
            int count = 0;

            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                Certificate cert = ks.getCertificate(alias);
                if (cert instanceof X509Certificate) {
                    pem.append("-----BEGIN CERTIFICATE-----\n");
                    String b64 = android.util.Base64.encodeToString(
                            cert.getEncoded(), android.util.Base64.NO_WRAP);
                    // Wrap at 64 chars per line (standard PEM)
                    for (int i = 0; i < b64.length(); i += 64) {
                        pem.append(b64, i, Math.min(i + 64, b64.length()));
                        pem.append('\n');
                    }
                    pem.append("-----END CERTIFICATE-----\n\n");
                    count++;
                }
            }

            try (FileOutputStream fos = new FileOutputStream(caFile)) {
                fos.write(pem.toString().getBytes("UTF-8"));
            }

            Log.i(TAG, "CA bundle created: " + count + " certificates, "
                    + caFile.length() / 1024 + " KB at " + caFile.getAbsolutePath());

            sCaBundlePath = caFile.getAbsolutePath();

        } catch (Exception e) {
            Log.e(TAG, "Failed to build CA bundle", e);
        }
    }

    /** Path to the CA bundle file, set during onCreate before SDL init. */
    private static String sCaBundlePath = null;

    /** Called from native code (JNI) to get the CA bundle path. */
    public static String getCaBundlePath() {
        return sCaBundlePath;
    }

    /**
     * Perform an HTTP GET request using Android's built-in HTTPS stack.
     * Called from native code via JNI to bypass curl/mbedTLS SSL issues.
     *
     * @param url         The full URL to GET
     * @param userAgent   User-Agent header value
     * @param timeoutSecs Connection and read timeout in seconds
     * @return "HTTP_STATUS\nBODY" on success, or "ERROR\nmessage" on failure
     */
    public static String httpRequest(String url, String postData, String userAgent, int timeoutSecs) {
        try {
            Log.i(TAG, "httpRequest: " + (postData != null ? "POST" : "GET") + " " + url);
            java.net.URL u = new java.net.URL(url);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) u.openConnection();
            conn.setRequestProperty("User-Agent", userAgent);
            conn.setConnectTimeout(timeoutSecs * 1000);
            conn.setReadTimeout(timeoutSecs * 1000);
            conn.setInstanceFollowRedirects(true);

            if (postData != null) {
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                byte[] data = postData.getBytes("UTF-8");
                conn.setRequestProperty("Content-Length", String.valueOf(data.length));
                conn.getOutputStream().write(data);
                conn.getOutputStream().close();
            } else {
                conn.setRequestMethod("GET");
            }

            int status = conn.getResponseCode();
            Log.i(TAG, "httpRequest: status=" + status);
            InputStream is = (status >= 200 && status < 400)
                    ? conn.getInputStream()
                    : conn.getErrorStream();

            StringBuilder sb = new StringBuilder();
            if (is != null) {
                java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(is, "UTF-8"));
                char[] buf = new char[4096];
                int n;
                while ((n = reader.read(buf)) != -1) {
                    sb.append(buf, 0, n);
                }
                reader.close();
            }
            conn.disconnect();

            return status + "\n" + sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "httpRequest failed for " + url, e);
            return "ERROR\n" + e.getMessage();
        }
    }

    /**
     * Add the touch controls overlay view on top of the SDL surface.
     */
    private void addTouchControls() {
        touchControls = new TouchControlsView(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        );

        // Add to the content view (SDL creates a FrameLayout as the content)
        ViewGroup contentView = (ViewGroup) getWindow().getDecorView().findViewById(android.R.id.content);
        if (contentView != null) {
            contentView.addView(touchControls, params);
        }
    }

    /**
     * Enter immersive fullscreen mode.
     */
    private void hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.systemBars());
                controller.setSystemBarsBehavior(
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                );
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            );
        }
    }
}
