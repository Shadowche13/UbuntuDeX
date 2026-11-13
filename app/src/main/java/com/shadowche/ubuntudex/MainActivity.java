package com.shadowche.ubuntudex;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.List;

public class MainActivity extends Activity {
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, 1);
        }

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);

        webView.addJavascriptInterface(new SystemBridge(this), "AndroidOS");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());

        webView.loadUrl("file:///android_asset/ubuntu_dex.html");
        setContentView(webView);
    }

    @Override
    public void onBackPressed() { }

    public class SystemBridge {
        Context ctx;
        SystemBridge(Context c) { this.ctx = c; }

        @JavascriptInterface
        public String getRealHardwareStats() {
            try {
                ActivityManager actManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
                actManager.getMemoryInfo(memInfo);
                long totalRamMB = memInfo.totalMem / (1024 * 1024);
                long availRamMB = memInfo.availMem / (1024 * 1024);
                long usedRamMB = totalRamMB - availRamMB;

                IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                Intent batteryStatus = ctx.registerReceiver(null, ifilter);
                int level = batteryStatus != null ? batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) : 100;

                File path = Environment.getExternalStorageDirectory();
                StatFs stat = new StatFs(path.getPath());
                long totalDiskGB = (stat.getBlockCountLong() * stat.getBlockSizeLong()) / (1024 * 1024 * 1024);
                long freeDiskGB = (stat.getAvailableBlocksLong() * stat.getBlockSizeLong()) / (1024 * 1024 * 1024);
                long usedDiskGB = totalDiskGB - freeDiskGB;

                JSONObject obj = new JSONObject();
                obj.put("model", Build.MANUFACTURER + " " + Build.MODEL);
                obj.put("androidVer", Build.VERSION.RELEASE);
                obj.put("kernel", System.getProperty("os.version"));
                obj.put("arch", System.getProperty("os.arch"));
                obj.put("ramUsed", usedRamMB);
                obj.put("ramTotal", totalRamMB);
                obj.put("diskUsed", usedDiskGB);
                obj.put("diskTotal", totalDiskGB);
                obj.put("battery", level);
                return obj.toString();
            } catch (Exception e) {
                return "{}";
            }
        }

        @JavascriptInterface
        public String execRealLinuxCommand(String cmd) {
            StringBuilder output = new StringBuilder();
            try {
                Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
                while ((line = errReader.readLine()) != null) {
                    output.append(line).append("\n");
                }
                process.waitFor();
            } catch (Exception e) {
                output.append("Error: ").append(e.getMessage());
            }
            return output.toString();
        }

        @JavascriptInterface
        public String listRealFiles(String subPath) {
            try {
                File base = Environment.getExternalStorageDirectory();
                File target = subPath.isEmpty() ? base : new File(base, subPath);
                File[] files = target.listFiles();
                JSONArray arr = new JSONArray();
                if (files != null) {
                    for (File f : files) {
                        JSONObject o = new JSONObject();
                        o.put("name", f.getName());
                        o.put("isDir", f.isDirectory());
                        o.put("sizeKB", f.length() / 1024);
                        arr.put(o);
                    }
                }
                return arr.toString();
            } catch (Exception e) {
                return "[]";
            }
        }

        @JavascriptInterface
        public String getInstalledApps() {
            try {
                PackageManager pm = ctx.getPackageManager();
                Intent i = new Intent(Intent.ACTION_MAIN, null);
                i.addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> allApps = pm.queryIntentActivities(i, 0);
                JSONArray arr = new JSONArray();
                for (ResolveInfo ri : allApps) {
                    JSONObject o = new JSONObject();
                    o.put("label", ri.loadLabel(pm).toString());
                    o.put("pkg", ri.activityInfo.packageName);
                    arr.put(o);
                }
                return arr.toString();
            } catch (Exception e) {
                return "[]";
            }
        }

        @JavascriptInterface
        public void launchApp(String packageName) {
            Intent launchIntent = ctx.getPackageManager().getLaunchIntentForPackage(packageName);
            if (launchIntent != null) {
                ctx.startActivity(launchIntent);
            }
        }
    }
}