package jpg.ivan.native_screenshot;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.Window;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Date;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.embedding.engine.renderer.FlutterRenderer;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;
import io.flutter.view.FlutterView;

/**
 * NativeScreenshotPlugin
 */
public class NativeScreenshotPlugin implements MethodCallHandler, FlutterPlugin, ActivityAware {
	private static final String TAG = "NativeScreenshotPlugin";

	private Context context;
	private MethodChannel channel;
	private Activity activity;
	private Object renderer;

	private boolean ssError = false;
	private String ssPath;

	// Default constructor for old registrar
	public NativeScreenshotPlugin() {
	} // NativeScreenshotPlugin()

	// Condensed logic to initialize the plugin
	private void initPlugin(Context context, BinaryMessenger messenger, Activity activity, Object renderer) {
		this.context = context;
		this.activity = activity;
		this.renderer = renderer;

		this.channel = new MethodChannel(messenger, "native_screenshot");
		this.channel.setMethodCallHandler(this);
	} // initPlugin()

	// New v2 listener methods
	@Override
	public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
		this.channel.setMethodCallHandler(null);
		this.channel = null;
		this.context = null;
	} // onDetachedFromEngine()

	@Override
	public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
		Log.println(Log.INFO, TAG, "Using *NEW* registrar method!");

		initPlugin(
				flutterPluginBinding.getApplicationContext(),
				flutterPluginBinding.getBinaryMessenger(),
				null,
				flutterPluginBinding.getFlutterEngine().getRenderer()
		); // initPlugin()
	} // onAttachedToEngine()

	// Old v1 register method
	// FIX: Make instance variables set with the old method
	public static void registerWith(Registrar registrar) {
		Log.println(Log.INFO, TAG, "Using *OLD* registrar method!");

		NativeScreenshotPlugin instance = new NativeScreenshotPlugin();

		instance.initPlugin(
				registrar.context(),
				registrar.messenger(),
				registrar.activity(),
				registrar.view()
		); // initPlugin()
	} // registerWith()


	// Activity condensed methods
	private void attachActivity(ActivityPluginBinding binding) {
		this.activity = binding.getActivity();
	} // attachActivity()

	private void detachActivity() {
		this.activity = null;
	} // attachActivity()


	// Activity listener methods
	@Override
	public void onAttachedToActivity(ActivityPluginBinding binding) {
		attachActivity(binding);
	} // onAttachedToActivity()

	@Override
	public void onDetachedFromActivityForConfigChanges() {
		detachActivity();
	} // onDetachedFromActivityForConfigChanges()

	@Override
	public void onReattachedToActivityForConfigChanges(ActivityPluginBinding binding) {
		attachActivity(binding);
	} // onReattachedToActivityForConfigChanges()

	@Override
	public void onDetachedFromActivity() {
		detachActivity();
	} // onDetachedFromActivity()


	// MethodCall, manage stuff coming from Dart
	@Override
	public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
		if( !permissionToWrite() ) {
			Log.println(Log.INFO, TAG, "Permission to write files missing!");

			result.success(null);

			return;
		} // if cannot write

		if( !call.method.equals("takeScreenshot") ) {
			Log.println(Log.INFO, TAG, "Method not implemented!");

			result.notImplemented();

			return;
		} // if not implemented


		// Need to fix takeScreenshot()
		// it produces just a black image
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			// takeScreenshot();
			takeScreenshotOld();
		} else {
			takeScreenshotOld();
		} // if

		if( this.ssError || this.ssPath == null || this.ssPath.isEmpty() ) {
			result.success(null);

			return;
		} // if error

		result.success(this.ssPath);
	} // onMethodCall()


	// Own functions, plugin specific functionality
	private String getScreenshotName() {
		java.text.SimpleDateFormat sf = new java.text.SimpleDateFormat("yyyyMMddHHmmss");
		String sDate = sf.format(new Date());

		return "native_screenshot-" + sDate + ".png";
	} // getScreenshotName()

	private String getApplicationName() {
		ApplicationInfo appInfo = null;

		try {
			appInfo = this.context.getPackageManager()
					.getApplicationInfo(this.context.getPackageName(), 0);
		} catch (Exception ex) {
			Log.println(Log.INFO, TAG, "Error getting package name, using default. Err: " + ex.getMessage());
		}

		if(appInfo == null) {
			return "NativeScreenshot";
		} // if null

		CharSequence cs = this.context.getPackageManager().getApplicationLabel(appInfo);
		StringBuilder name = new StringBuilder( cs.length() );

		name.append(cs);

		if( name.toString().trim().isEmpty() ) {
			return "NativeScreenshot";
		}

		return name.toString();
	} // getApplicationName()

	private String getScreenshotPath() {
		String externalDir = Environment.getExternalStorageDirectory().getAbsolutePath();

		String sDir = externalDir
						+ File.separator
						+ getApplicationName();

		File dir = new File(sDir);

		String dirPath;

		if( dir.exists() || dir.mkdir()) {
			dirPath = sDir + File.separator + getScreenshotName();
		} else {
			dirPath = externalDir + File.separator + getScreenshotName();
		}

		Log.println(Log.INFO, TAG, "Built ScreeshotPath: " + dirPath);

		return dirPath;
	} // getScreenshotPath()

	private String writeBitmap(Bitmap bitmap) {
		try {
			String path = getScreenshotPath();
			File imageFile = new File(path);
			FileOutputStream oStream = new FileOutputStream(imageFile);

			bitmap.compress(Bitmap.CompressFormat.PNG, 100, oStream);
			oStream.flush();
			oStream.close();

			return path;
		} catch (Exception ex) {
			Log.println(Log.INFO, TAG, "Error writing bitmap: " + ex.getMessage());
		}

		return null;
	} // writeBitmap()

	private void reloadMedia() {
		try {
			Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
			File file = new File(this.ssPath);
			Uri uri = Uri.fromFile(file);

			intent.setData(uri);
			this.activity.sendBroadcast(intent);
		} catch (Exception ex) {
			Log.println(Log.INFO, TAG, "Error reloading media lib: " + ex.getMessage());
		}
	} // reloadMedia()

	private void takeScreenshot() {
		
	} // takeScreenshot()

	private void takeScreenshotOld() {
		
	} // takeScreenshot()

	private boolean permissionToWrite() {
		return false;
	} // permissionToWrite()
} // NativeScreenshotPlugin
