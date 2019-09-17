package com.bufeotec.radioastoria;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ui.PlayerNotificationManager;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;

import java.util.concurrent.ExecutionException;

import static androidx.core.app.NotificationCompat.BADGE_ICON_NONE;
import static androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC;
import static com.bufeotec.radioastoria.Config.STREAMING_URL;

//import wseemann.media.FFmpegMediaMetadataRetriever;

public class MainActivity extends AppCompatActivity {

    private static final String CHANNEL_ID = "media_playback_channel";

    private String nowPlaying;
    private ImageView playStopBtn;
    private SimpleExoPlayer player;
    private boolean isPlaying = false;
    private TextView radioStationNowPlaying;
    private String streamUrl = STREAMING_URL;
    private static final int READ_PHONE_STATE_REQUEST_CODE = 22;

    PlayerNotificationManager playerNotificationManager;


    Notification notification;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        notification = new Notification();
        notification.setTitle("Radio Astoria");
        notification.setDescription("La Calientitaaaa");
        notification.setImage("https://www.guabba.com/capitan/media/foro/24_01.jpg");
        playStopBtn = findViewById(R.id.playStopBtn);
        radioStationNowPlaying = findViewById(R.id.radioStationNowPlaying);

        initExoPlayer();

        processPhoneListenerPermission();
        BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                TelephonyManager tm = (TelephonyManager) context.getSystemService(Service.TELEPHONY_SERVICE);
                if (tm != null) {
                    if (tm.getCallState() == TelephonyManager.CALL_STATE_RINGING) {
                        if (isPlaying) {
                            stop();
                            playStopBtn.setImageResource(R.drawable.ic_play);
                        }
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.intent.action.PHONE_STATE");
        registerReceiver(broadcastReceiver, filter);

        if (Config.IS_LOADING_NOW_PLAYING) {
            Thread t = new Thread() {
                public void run() {
                    try {
                        while (!isInterrupted()) {

                            runOnUiThread(() -> reloadShoutCastInfo());
                            Thread.sleep(20000);
                        }
                    } catch (InterruptedException ignored) {
                    }
                }
            };
            t.start();
        }
    }


    private void initExoPlayer() {
        DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
        AdaptiveTrackSelection.Factory trackSelectionFactory = new AdaptiveTrackSelection.Factory(bandwidthMeter);
        DefaultTrackSelector trackSelector = new DefaultTrackSelector(trackSelectionFactory);
        player = ExoPlayerFactory.newSimpleInstance(getApplicationContext(), trackSelector);
    }

    @SuppressLint("WrongConstant")
    public void play(String channelUrl) {
        if (isNetworkAvailable()) {
            DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(getApplicationContext(), "ExoPlayerDemo");
            ExtractorsFactory extractorsFactory = new DefaultExtractorsFactory();
            MediaSource mediaSource = new ExtractorMediaSource.Factory(dataSourceFactory).setExtractorsFactory(extractorsFactory).createMediaSource(Uri.parse(channelUrl));
            player.prepare(mediaSource);
            player.setPlayWhenReady(true);
            playerNotificationManager = PlayerNotificationManager.createWithNotificationChannel(this, CHANNEL_ID, R.string.channel_name, 2,
                    new PlayerNotificationManager.MediaDescriptionAdapter() {
                        @Override
                        public String getCurrentContentTitle(Player player) {
                            return notification.getTitle();
                        }

                        @Nullable
                        @Override
                        public PendingIntent createCurrentContentIntent(Player player) {
                            return null;
                        }

                        @Nullable
                        @Override
                        public String getCurrentContentText(Player player) {
                            return notification.getDescription();
                        }

                        @Nullable
                        @Override
                        public Bitmap getCurrentLargeIcon(Player player, PlayerNotificationManager.BitmapCallback callback) {
                            Thread thread = new Thread(() -> {
                                try {
                                    Uri uri = Uri.parse(notification.getImage());
                                    Bitmap bitmap = Glide.with(getApplicationContext())
                                            .asBitmap()
                                            .load(uri)
                                            .submit().get();
                                    callback.onBitmap(bitmap);
                                } catch (ExecutionException e) {
                                    e.printStackTrace();
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            });
                            thread.start();
                            return null;
                        }
                    });

            playerNotificationManager.setPlayer(player);

            playerNotificationManager.setBadgeIconType(BADGE_ICON_NONE);

            playerNotificationManager.setVisibility(VISIBILITY_PUBLIC);
            playerNotificationManager.setUseNavigationActions(false);
            playerNotificationManager.setFastForwardIncrementMs(0);
            playerNotificationManager.setRewindIncrementMs(0);
            //playerNotificationManager.setStopAction(null);
            playerNotificationManager.setUseChronometer(false);
            playerNotificationManager.setSmallIcon(R.drawable.ic_logo);
            playerNotificationManager.setPriority(NotificationCompat.PRIORITY_LOW);

            isPlaying = true;
            playStopBtn.setImageResource(R.drawable.ic_pause);
        } else {
            Toast.makeText(this, "No internet", Toast.LENGTH_SHORT).show();
            playStopBtn.setImageResource(R.drawable.ic_play);
        }
    }


    public void stop() {
        player.setPlayWhenReady(false);
        player.stop();
        isPlaying = false;
        playStopBtn.setImageResource(R.drawable.ic_play);
    }

    private void reloadShoutCastInfo() {
        if (isNetworkAvailable()) {
            //AsyncTaskRunner runner = new AsyncTaskRunner();
            //runner.execute();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        isPlaying = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    public void playStop(View view) {
        if (!isPlaying) {
            play(streamUrl);
        } else {
            stop();
        }
    }

    private void processPhoneListenerPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE}, READ_PHONE_STATE_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == READ_PHONE_STATE_REQUEST_CODE) {
            if (!(grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                Toast.makeText(getApplicationContext(), "Permission not granted.\nWe can't pause music when phone ringing.", Toast.LENGTH_LONG).show();
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    public boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = null;
        if (cm != null) {
            networkInfo = cm.getActiveNetworkInfo();
        }
        return networkInfo != null && networkInfo.isConnectedOrConnecting();
    }

    @Override
    public void onBackPressed() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Are you sure you want to exit?")
                .setCancelable(false)
                .setPositiveButton("Yes", (dialog, id) -> {
                    stop();
                    finish();
                })
                .setNegativeButton("No", (dialog, id) -> dialog.cancel());
        AlertDialog alert = builder.create();
        alert.show();
    }

    private class AsyncTaskRunner extends AsyncTask<String, String, String> {

        @Override
        protected String doInBackground(String... params) {
            /*FFmpegMediaMetadataRetriever mmr = new FFmpegMediaMetadataRetriever();
            mmr.setDataSource(streamUrl);
            nowPlaying = mmr.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_ICY_METADATA).replaceAll("StreamTitle", "").replaceAll("[=,';]+", "");
            mmr.release();
            return null;*/
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            radioStationNowPlaying.setText(nowPlaying);
        }
    }
}
