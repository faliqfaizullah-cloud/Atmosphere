package com.atmosphere.music;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

public class MediaService extends Service {
  static MediaService inst;
  static MediaControlPlugin plugin;
  static State st = new State();

  static class State {
    String title = "", artist = "", album = "";
    Bitmap art;
    boolean playing;
    long pos, dur;
  }

  private static final String CH = "atmosphere_playback";
  private static final int ID = 4242;
  private MediaSession session;
  private PowerManager.WakeLock wl;

  @Override
  public void onCreate() {
    super.onCreate();
    inst = this;
    if (Build.VERSION.SDK_INT >= 26) {
      NotificationChannel c = new NotificationChannel(CH, "Playback", NotificationManager.IMPORTANCE_LOW);
      c.setShowBadge(false);
      c.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
      ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);
    }
    session = new MediaSession(this, "Atmosphere");
    session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
    session.setCallback(new MediaSession.Callback() {
      @Override public void onPlay() { send("play", -1); }
      @Override public void onPause() { send("pause", -1); }
      @Override public void onSkipToNext() { send("next", -1); }
      @Override public void onSkipToPrevious() { send("prev", -1); }
      @Override public void onSeekTo(long p) { send("seek", p); }
    });
    session.setActive(true);
    PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
    wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Atmosphere:playback");
  }

  @Override
  public int onStartCommand(Intent i, int flags, int startId) {
    String a = i != null ? i.getAction() : null;
    if ("toggle".equals(a)) send(st.playing ? "pause" : "play", -1);
    else if ("next".equals(a)) send("next", -1);
    else if ("prev".equals(a)) send("prev", -1);
    Notification n = build();
    if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
    else startForeground(ID, n);
    refresh();
    return START_NOT_STICKY;
  }

  void refresh() {
    MediaMetadata.Builder mb = new MediaMetadata.Builder()
        .putString(MediaMetadata.METADATA_KEY_TITLE, st.title)
        .putString(MediaMetadata.METADATA_KEY_ARTIST, st.artist)
        .putString(MediaMetadata.METADATA_KEY_ALBUM, st.album)
        .putLong(MediaMetadata.METADATA_KEY_DURATION, st.dur);
    if (st.art != null) {
      mb.putBitmap(MediaMetadata.METADATA_KEY_ART, st.art);
      mb.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, st.art);
      mb.putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, st.art);
    }
    session.setMetadata(mb.build());
    long acts = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE
        | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SEEK_TO;
    session.setPlaybackState(new PlaybackState.Builder().setActions(acts)
        .setState(st.playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED, st.pos, st.playing ? 1f : 0f)
        .build());
    try {
      if (st.playing && !wl.isHeld()) wl.acquire();
      if (!st.playing && wl.isHeld()) wl.release();
    } catch (Exception e) { }
    ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(ID, build());
  }

  private PendingIntent act(String a, int code) {
    Intent i = new Intent(this, MediaService.class).setAction(a);
    int fl = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
    return PendingIntent.getService(this, code, i, fl);
  }

  private Notification build() {
    Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CH) : new Notification.Builder(this);
    Intent open = getPackageManager().getLaunchIntentForPackage(getPackageName());
    int fl = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
    b.setSmallIcon(android.R.drawable.ic_media_play)
        .setContentTitle(st.title.isEmpty() ? "Atmosphere" : st.title)
        .setContentText(st.artist)
        .setSubText(st.album)
        .setLargeIcon(st.art)
        .setVisibility(Notification.VISIBILITY_PUBLIC)
        .setOnlyAlertOnce(true)
        .setShowWhen(false)
        .setOngoing(st.playing)
        .addAction(android.R.drawable.ic_media_previous, "Previous", act("prev", 1))
        .addAction(st.playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play, st.playing ? "Pause" : "Play", act("toggle", 2))
        .addAction(android.R.drawable.ic_media_next, "Next", act("next", 3))
        .setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0, 1, 2));
    if (open != null) b.setContentIntent(PendingIntent.getActivity(this, 0, open, fl));
    return b.build();
  }

  private void send(String a, long p) {
    if (plugin != null) plugin.emit(a, p);
  }

  @Override
  public void onTaskRemoved(Intent rootIntent) {
    stopSelf();
    super.onTaskRemoved(rootIntent);
  }

  @Override
  public void onDestroy() {
    try { if (wl != null && wl.isHeld()) wl.release(); } catch (Exception e) { }
    if (session != null) { session.setActive(false); session.release(); }
    inst = null;
    super.onDestroy();
  }

  @Override
  public IBinder onBind(Intent i) { return null; }
}
