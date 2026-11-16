package com.atmosphere.music;

import android.Manifest;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Size;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import java.io.File;
import java.io.FileInputStream;
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
        boolean g = Build.VERSION.SDK_INT >= 30;
        String[] proj = g
            ? new String[] { MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.GENRE }
            : new String[] { MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.ALBUM_ID };
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
            o.put("albumId", c.getLong(5));
            if (g && c.getString(6) != null) o.put("genre", c.getString(6));
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

  /** Extracts the song's original cover once, caches it (shared per album) and returns its path. */
  @PluginMethod
  public void art(PluginCall call) {
    final long id = call.getDouble("id", 0.0).longValue();
    final long album = call.getDouble("album", 0.0).longValue();
    final String path = call.getString("path", "");
    final boolean wantData = Boolean.TRUE.equals(call.getBoolean("data", false));
    new Thread(() -> {
      JSObject r = new JSObject();
      try {
        File dir = new File(getContext().getCacheDir(), "art2");
        dir.mkdirs();
        String key = album > 0 ? "a" + album : "p" + Integer.toHexString(path.hashCode());
        File out = new File(dir, key + ".jpg");
        File none = new File(dir, key + ".none");
        if (!out.exists() && !none.exists()) {
          Bitmap bm = null;
          Uri uri = id > 0 ? ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id) : null;
          if (Build.VERSION.SDK_INT >= 29 && uri != null) {
            try {
              bm = getContext().getContentResolver().loadThumbnail(uri, new Size(600, 600), null);
            } catch (Exception e) { }
          }
          if (bm == null) {
            try {
              MediaMetadataRetriever m = new MediaMetadataRetriever();
              if (uri != null) m.setDataSource(getContext(), uri);
              else m.setDataSource(path);
              byte[] b = m.getEmbeddedPicture();
              m.release();
              if (b != null) {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(b, 0, b.length, o);
                int sample = 1;
                while (o.outWidth / sample > 1100) sample *= 2;
                BitmapFactory.Options o2 = new BitmapFactory.Options();
                o2.inSampleSize = sample;
                bm = BitmapFactory.decodeByteArray(b, 0, b.length, o2);
              }
            } catch (Exception e) { }
          }
          if (bm != null) {
            FileOutputStream f = new FileOutputStream(out);
            bm.compress(Bitmap.CompressFormat.JPEG, 88, f);
            f.close();
          } else {
            none.createNewFile();
          }
        }
        if (out.exists()) {
          r.put("path", out.getAbsolutePath());
          if (wantData) {
            byte[] bytes = new byte[(int) out.length()];
            FileInputStream in = new FileInputStream(out);
            int off = 0;
            while (off < bytes.length) {
              int n = in.read(bytes, off, bytes.length - off);
              if (n < 0) break;
              off += n;
            }
            in.close();
            r.put("data", Base64.encodeToString(bytes, Base64.NO_WRAP));
          }
        }
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
