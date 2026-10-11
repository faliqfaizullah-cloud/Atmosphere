package com.atmosphere.music;

import android.os.Bundle;
import android.view.WindowManager;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
  @Override
  public void onCreate(Bundle b) {
    registerPlugin(MusicScannerPlugin.class);
    registerPlugin(MediaControlPlugin.class);
    super.onCreate(b);
    WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    hideBars();
  }
  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    if (hasFocus) hideBars();
  }
  private void hideBars() {
    WindowInsetsControllerCompat c = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
    c.hide(WindowInsetsCompat.Type.systemBars());
    c.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
  }
  @Override
  public void onPause() {
    super.onPause();
    keepWeb();
  }
  @Override
  public void onStop() {
    super.onStop();
    keepWeb();
  }
  private void keepWeb() {
    try {
      android.webkit.WebView w = getBridge().getWebView();
      if (w != null) { w.onResume(); w.resumeTimers(); }
    } catch (Exception e) { }
  }
}
