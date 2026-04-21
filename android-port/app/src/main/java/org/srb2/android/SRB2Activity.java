package org.srb2.android;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
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
        // Request "All Files Access" on Android 11+ so data lives in /sdcard/SRB2/
        // which users can browse with any file manager to add addons
        requestStoragePermission();

        // Set the game data path before SDL init
        setupGameFiles();

        // Build CA certificate bundle for curl/mbedTLS SSL
        buildCACertBundle();

        super.onCreate(savedInstanceState);

        // Go fullscreen / immersive (deferred until DecorView is ready)
        getWindow().getDecorView().post(this::hideSystemUI);

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
     * Load order matters: libGL.so (gl4es) MUST be loaded before SDL2 so that
     * SDL_GL_GetProcAddress returns gl4es function pointers (which translate
     * OpenGL 1.x/2.x calls and GLSL 1.x built-ins to OpenGL ES 2.0).
     */
    @Override
    protected String[] getLibraries() {
        return new String[]{
            "GL",     // gl4es — desktop OpenGL over GLES2, must load first
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
        // Ensure addons subfolder exists so users know where to drop pk3/wad files
        new File(dataPath, "addons").mkdirs();
        return new String[]{
            "-home", dataPath,
        };
    }

    /**
     * Request MANAGE_EXTERNAL_STORAGE on Android 11+ so we can use /sdcard/SRB2/
     * which is freely browsable by users (they can add addons with any file manager).
     */
    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception e) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivity(intent);
                }
            }
        }
    }

    /**
     * Get the path where game data files (pk3, wad, addons, saves) are stored.
     * On Android 11+ with MANAGE_EXTERNAL_STORAGE: /storage/emulated/0/SRB2/
     *   (user-accessible — can add addons with any file manager)
     * On Android 10 and below: /storage/emulated/0/Android/data/org.srb2.android/files/SRB2/
     * Falls back to internal storage only if external is unavailable.
     */
    private String getGameDataPath() {
        // Android 11+: use public directory if we have All Files Access
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            File srb2Dir = new File(Environment.getExternalStorageDirectory(), "SRB2");
            srb2Dir.mkdirs();
            return srb2Dir.getAbsolutePath();
        }
        // Android 10 and below, or if permission not yet granted
        File dir = getExternalFilesDir(null);
        if (dir != null) {
            File srb2Dir = new File(dir, "SRB2");
            return srb2Dir.getAbsolutePath();
        }
        // Fallback: internal storage (not browsable by user)
        return new File(getFilesDir(), "SRB2").getAbsolutePath();
    }

    /**
     * Set up game files in the game data directory.
     * On first run (or after an update), copies game assets from the APK.
     * Migrates data from old locations (internal storage, app-scoped external)
     * to the new public /sdcard/SRB2/ location when possible.
     */
    private void setupGameFiles() {
        String dataPath = getGameDataPath();
        File dataDir = new File(dataPath);
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }

        // Create addons dir so users can see where to drop their pk3/wad files
        new File(dataDir, "addons").mkdirs();

        // Check if we already have srb2.pk3
        File mainPk3 = new File(dataDir, "srb2.pk3");
        if (mainPk3.exists()) {
            Log.i(TAG, "setupGameFiles: srb2.pk3 already at: " + dataPath);
            return;
        }

        // Migration: try old app-scoped external storage first
        // (Android/data/org.srb2.android/files/SRB2/)
        File oldAppScopedDir = new File(getExternalFilesDir(null), "SRB2");
        if (!oldAppScopedDir.getAbsolutePath().equals(dataDir.getAbsolutePath())) {
            File oldAppPk3 = new File(oldAppScopedDir, "srb2.pk3");
            if (oldAppPk3.exists()) {
                Log.i(TAG, "setupGameFiles: migrating from app-scoped to public storage...");
                migrateDirectory(oldAppScopedDir, dataDir);
                if (mainPk3.exists()) {
                    Log.i(TAG, "setupGameFiles: migration from app-scoped complete");
                    return;
                }
            }
        }

        // Migration: try old internal storage location
        File oldInternalDir = new File(getFilesDir(), "SRB2");
        File oldPk3 = new File(oldInternalDir, "srb2.pk3");
        if (oldPk3.exists()) {
            Log.i(TAG, "setupGameFiles: migrating from internal to external storage...");
            File[] files = oldInternalDir.listFiles();
            if (files != null) {
                for (File src : files) {
                    if (src.isFile()) {
                        File dest = new File(dataDir, src.getName());
                        Log.i(TAG, "setupGameFiles: migrating " + src.getName()
                            + " (" + (src.length() / 1024 / 1024) + " MB)...");
                        try {
                            copyFile(src, dest);
                            src.delete();
                            Log.i(TAG, "setupGameFiles: migrated " + src.getName() + " OK");
                        } catch (IOException e) {
                            Log.e(TAG, "setupGameFiles: FAILED to migrate " + src.getName(), e);
                        }
                    }
                }
            }
            if (mainPk3.exists()) {
                Log.i(TAG, "setupGameFiles: migration complete");
                return;
            }
        }

        // Copy game data from APK assets bundle
        try {
            AssetManager am = getAssets();
            String[] assetFiles = am.list("srb2data");
            if (assetFiles != null) {
                for (String filename : assetFiles) {
                    File destFile = new File(dataDir, filename);
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
            Log.i(TAG, "setupGameFiles: srb2.pk3 ready at " + dataPath
                + ", size=" + mainPk3.length());
        } else {
            Log.e(TAG, "setupGameFiles: WARNING - srb2.pk3 NOT FOUND in " + dataPath);
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
     * Recursively migrate all files and directories from src to dest.
     * Existing files in dest are not overwritten.
     */
    private void migrateDirectory(File srcDir, File destDir) {
        destDir.mkdirs();
        File[] files = srcDir.listFiles();
        if (files == null) return;
        for (File src : files) {
            File dest = new File(destDir, src.getName());
            if (src.isDirectory()) {
                migrateDirectory(src, dest);
            } else if (src.isFile() && !dest.exists()) {
                Log.i(TAG, "setupGameFiles: migrating " + src.getName()
                    + " (" + (src.length() / 1024 / 1024) + " MB)...");
                try {
                    copyFile(src, dest);
                    src.delete();
                    Log.i(TAG, "setupGameFiles: migrated " + src.getName() + " OK");
                } catch (IOException e) {
                    Log.e(TAG, "setupGameFiles: FAILED to migrate " + src.getName(), e);
                }
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
            View decorView = getWindow().peekDecorView();
            if (decorView == null) return;
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
