package jpg.ivan.native_screenshot;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.embedding.engine.renderer.FlutterRenderer;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;

public class NativeScreenshotPlugin implements MethodCallHandler, FlutterPlugin, ActivityAware {
    private static final String TAG = "NativeScreenshotPlugin";

    private Context context;
    private MethodChannel channel;
    private Activity activity;
    private FlutterRenderer renderer;

    private void initPlugin(Context context, BinaryMessenger messenger, FlutterRenderer renderer) {
        this.context = context;
        this.renderer = renderer;
        this.channel = new MethodChannel(messenger, "native_screenshot_ext");
        this.channel.setMethodCallHandler(this);
    }

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        initPlugin(
                flutterPluginBinding.getApplicationContext(),
                flutterPluginBinding.getBinaryMessenger(),
                flutterPluginBinding.getFlutterEngine().getRenderer()
        );
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        channel.setMethodCallHandler(null);
        channel = null;
        context = null;
    }

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        activity = binding.getActivity();
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        activity = null;
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        activity = binding.getActivity();
    }

    @Override
    public void onDetachedFromActivity() {
        activity = null;
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        switch (call.method) {
            case "takeScreenshot":
                handleTakeScreenshot(result);
                break;
            case "takeScreenshotImage":
                int quality = call.hasArgument("quality") ? (int) call.argument("quality") : 100;
                handleTakeScreenshotImage(result, quality);
                break;
            default:
                result.notImplemented();
                break;
        }
    }

    private void handleTakeScreenshot(Result result) {
        if (!permissionToWrite()) {
            result.success(null);
            return;
        }

        Bitmap bitmap = renderer.getBitmap();
        if (bitmap == null) {
            result.success(null);
            return;
        }

        String path = writeBitmap(bitmap);
        if (path == null) {
            result.success(null);
            return;
        }

        reloadMedia(path);
        result.success(path);
    }

    private void handleTakeScreenshotImage(Result result, int quality) {
        try {
            Bitmap bitmap = renderer.getBitmap();
            if (bitmap == null) {
                result.success(null);
                return;
            }

            ByteArrayOutputStream oStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, quality, oStream);
            oStream.flush();
            oStream.close();

            result.success(oStream.toByteArray());
        } catch (Exception ex) {
            Log.e(TAG, "Error: " + ex.getMessage());
            result.success(null);
        }
    }

    private String getScreenshotName() {
        SimpleDateFormat sf = new SimpleDateFormat("yyyyMMddHHmmss");
        return "native_screenshot_ext-" + sf.format(new Date()) + ".png";
    }

    private String getApplicationName() {
        try {
            ApplicationInfo appInfo = context.getPackageManager().getApplicationInfo(context.getPackageName(), 0);
            return context.getPackageManager().getApplicationLabel(appInfo).toString();
        } catch (Exception ex) {
            return "NativeScreenshot";
        }
    }

    private String getScreenshotPath() {
        String externalDir = Environment.getExternalStorageDirectory().getAbsolutePath();
        String sDir = externalDir + File.separator + getApplicationName();
        File dir = new File(sDir);
        if (dir.exists() || dir.mkdirs()) {
            return sDir + File.separator + getScreenshotName();
        } else {
            return externalDir + File.separator + getScreenshotName();
        }
    }

    private String writeBitmap(Bitmap bitmap) {
        try {
            String path = getScreenshotPath();
            FileOutputStream oStream = new FileOutputStream(path);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, oStream);
            oStream.flush();
            oStream.close();
            return path;
        } catch (Exception ex) {
            Log.e(TAG, "Error writing bitmap: " + ex.getMessage());
        }
        return null;
    }

    private void reloadMedia(String path) {
        Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        intent.setData(Uri.fromFile(new File(path)));
        activity.sendBroadcast(intent);
    }

    private boolean permissionToWrite() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int perm = activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            if (perm == PackageManager.PERMISSION_GRANTED) {
                return true;
            }
            activity.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 11);
            return false;
        }
        return true;
    }
}
