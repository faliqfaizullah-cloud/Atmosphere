package com.atmosphere.music;

import android.Manifest;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.util.Base64;
import androidx.core.content.ContextCompat;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

@CapacitorPlugin(name = "MediaControl", permissions = {
    @Permission(alias = "notif", strings = { Manifest.permission.POST_NOTIFICATIONS })
})
public class MediaControlPlugin extends Plugin {
  private boolean asked = false;

  @Override
  public void load() { MediaService.plugin = this; }

  @PluginMethod
  public void update(PluginCall call) {
    if (Build.VERSION.SDK_INT >= 33 && !asked && getPermissionState("notif") != PermissionState.GRANTED) {
      asked = true;
      requestPermissionForAlias("notif", call, "notifResult");
      return;
    }
    apply(call);
  }

  @PermissionCallback
  private void notifResult(PluginCall call) { apply(call); }

  private void apply(PluginCall call) {
    try {
      MediaService.State s = new MediaService.State();
      s.title = call.getString("title", "");
      s.artist = call.getString("artist", "");
      s.album = call.getString("album", "");
      s.playing = Boolean.TRUE.equals(call.getBoolean("playing", false));
      s.pos = call.getDouble("position", 0.0).longValue();
      s.dur = call.getDouble("duration", 0.0).longValue();
      String b64 = call.getString("art", "");
      if (b64 != null && b64.length() > 0) {
        byte[] d = Base64.decode(b64, Base64.DEFAULT);
        Bitmap bm = BitmapFactory.decodeByteArray(d, 0, d.length);
        s.art = bm;
      }
      MediaService.st = s;
      if (MediaService.inst != null) MediaService.inst.refresh();
      else ContextCompat.startForegroundService(getContext(), new Intent(getContext(), MediaService.class));
      call.resolve();
    } catch (Exception e) {
      call.reject(e.getMessage());
    }
  }

  @PluginMethod
  public void stop(PluginCall call) {
    getContext().stopService(new Intent(getContext(), MediaService.class));
    call.resolve();
  }

  void emit(String action, long pos) {
    JSObject o = new JSObject();
    o.put("action", action);
    o.put("position", pos);
    notifyListeners("action", o);
  }
}
