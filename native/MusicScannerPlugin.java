package com.atmosphere.music;

import android.Manifest;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.provider.MediaStore;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import com.getcapacitor.PluginMethod;
import java.io.File;
import java.io.FileOutputStream;

@CapacitorPlugin(name = "MusicScanner", permissions = {
    @Permission(alias = "audio", strings = { Manifest.permission.READ_MEDIA_AUDIO }),
    @Permission(alias = "legacy", strings = { Manifest.permission.READ_EXTERNAL_STORAGE })
})
public class MusicScannerPlugin extends Plugin {
  private String alias() { return Build.VERSION.SDK_INT >= 33 ? "audio" : "legacy"; }

  @PluginMethod
  public void scan(PluginCall call) {
    if (getPermissionState(alias()) != PermissionState.GRANTED) {
      requestPermissionForAlias(alias(), call, "permResult");
      return;
    }
    doScan(call);
  }

  @PermissionCallback
  private void permResult(PluginCall call) {
    if (getPermissionState(alias()) == PermissionState.GRANTED) doScan(call);
    else call.reject("Permission denied");
  }

  private void doScan(PluginCall call) {
    new Thread(() -> {
      try {
        JSArray arr = new JSArray();
        String[] proj = { MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DATA };
        String sel = MediaStore.Audio.Media.IS_MUSIC + "!=0 AND " + MediaStore.Audio.Media.DURATION + ">30000";
        Cursor c = getContext().getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj, sel, null,
            MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC");
        if (c != null) {
          while (c.moveToNext()) {
            String path = c.getString(4);
            if (path == null) continue;
            JSObject o = new JSObject();
            o.put("id", c.getLong(0));
            o.put("title", c.getString(1) == null ? new File(path).getName() : c.getString(1));
            String ar = c.getString(2), al = c.getString(3);
            o.put("artist", (ar == null || ar.equals("<unknown>")) ? "Unknown Artist" : ar);
            o.put("album", (al == null || al.equals("<unknown>")) ? "Single" : al);
            o.put("path", path);
            arr.put(o);
          }
          c.close();
        }
        JSObject ret = new JSObject();
        ret.put("tracks", arr);
        call.resolve(ret);
      } catch (Exception e) {
        call.reject(e.getMessage());
      }
    }).start();
  }

  @PluginMethod
  public void art(PluginCall call) {
    final String path = call.getString("path");
    new Thread(() -> {
      JSObject r = new JSObject();
      try {
        File dir = new File(getContext().getCacheDir(), "art");
        dir.mkdirs();
        File out = new File(dir, Integer.toHexString(path.hashCode()) + ".jpg");
        if (!out.exists()) {
          MediaMetadataRetriever m = new MediaMetadataRetriever();
          m.setDataSource(path);
          byte[] b = m.getEmbeddedPicture();
          m.release();
          if (b != null) {
            FileOutputStream f = new FileOutputStream(out);
            f.write(b);
            f.close();
          }
        }
        if (out.exists()) r.put("path", out.getAbsolutePath());
      } catch (Exception e) { }
      call.resolve(r);
    }).start();
  }

  @PluginMethod
  public void output(PluginCall call) {
    String[] acts = { "android.settings.panel.action.VOLUME", "android.settings.CAST_SETTINGS", "android.settings.BLUETOOTH_SETTINGS" };
    for (String a : acts) {
      try {
        Intent i = new Intent(a);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);
        call.resolve();
        return;
      } catch (Exception e) { }
    }
    call.reject("No output settings available");
  }
}
