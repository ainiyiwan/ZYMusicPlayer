package com.zhimadai.cctvmall.zymusicplayer;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.zhimadai.cctvmall.zymusicplayer.adapter.LocalMusicAdapter;
import com.zhimadai.cctvmall.zymusicplayer.adapter.OnMoreClickListener;
import com.zhimadai.cctvmall.zymusicplayer.application.AppCache;
import com.zhimadai.cctvmall.zymusicplayer.enums.PlayModeEnum;
import com.zhimadai.cctvmall.zymusicplayer.model.Music;
import com.zhimadai.cctvmall.zymusicplayer.receiver.RemoteControlReceiver;
import com.zhimadai.cctvmall.zymusicplayer.service.EventCallback;
import com.zhimadai.cctvmall.zymusicplayer.service.OnPlayerEventListener;
import com.zhimadai.cctvmall.zymusicplayer.service.PlayService;
import com.zhimadai.cctvmall.zymusicplayer.util.PermissionReq;
import com.zhimadai.cctvmall.zymusicplayer.util.Preferences;
import com.zhimadai.cctvmall.zymusicplayer.util.SystemUtils;
import com.zhimadai.cctvmall.zymusicplayer.util.ToastUtils;

import butterknife.BindView;
import butterknife.ButterKnife;

import static com.zhimadai.cctvmall.zymusicplayer.application.AppCache.getPlayService;

public class MainActivity extends AppCompatActivity implements OnMoreClickListener, OnPlayerEventListener, View
        .OnClickListener {

    @BindView(R.id.lv_local_music)
    ListView lvLocalMusic;
    @BindView(R.id.tv_empty)
    TextView tvEmpty;
    @BindView(R.id.tv_current_time)
    TextView tvCurrentTime;
    @BindView(R.id.sb_progress)
    SeekBar sbProgress;
    @BindView(R.id.tv_total_time)
    TextView tvTotalTime;
    @BindView(R.id.iv_mode)
    ImageView ivMode;
    @BindView(R.id.iv_prev)
    ImageView ivPrev;
    @BindView(R.id.iv_play)
    ImageView ivPlay;
    @BindView(R.id.iv_next)
    ImageView ivNext;
    private ServiceConnection mPlayServiceConnection;
    protected Handler mHandler = new Handler(Looper.getMainLooper());
    private LocalMusicAdapter mAdapter;

    private AudioManager mAudioManager;
    private ComponentName mRemoteReceiver;

    private int mLastProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        //1 扫描本地视频
        checkService();
        //2 播放音乐

        registerReceiver();
    }

    private void registerReceiver() {
        mAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        mRemoteReceiver = new ComponentName(getPackageName(), RemoteControlReceiver.class.getName());
        mAudioManager.registerMediaButtonEventReceiver(mRemoteReceiver);
    }


    private void checkService() {
        if (getPlayService() == null) {
            startService();

            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    bindService();
                }
            }, 1000);
        } else {
            scanMusic(getPlayService());
        }
//        else {
//            startMusicActivity();
//            finish();
//        }
    }

    private void startService() {
        Intent intent = new Intent(this, PlayService.class);
        startService(intent);
    }

    private void bindService() {
        Intent intent = new Intent();
        intent.setClass(this, PlayService.class);
        mPlayServiceConnection = new PlayServiceConnection();
        bindService(intent, mPlayServiceConnection, Context.BIND_AUTO_CREATE);
    }


    @Override
    public void onMoreClick(int position) {

    }


    private class PlayServiceConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            final PlayService playService = ((PlayService.PlayBinder) service).getService();
            AppCache.setPlayService(playService);
            PermissionReq.with(MainActivity.this)
                    .permissions(Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    .result(new PermissionReq.Result() {
                        @Override
                        public void onGranted() {
                            scanMusic(playService);
                        }

                        @Override
                        public void onDenied() {
                            ToastUtils.show("没有存储空间权限，无法扫描本地歌曲！");
                            finish();
                            playService.quit();
                        }
                    })
                    .request();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }
    }

    private void scanMusic(final PlayService playService) {
        playService.updateMusicList(new EventCallback<Void>() {
            @Override
            public void onEvent(Void aVoid) {
                //扫描完毕 显示数据
                showView();
            }
        });
    }

    private void showView() {
        // TODO: 2017/10/16
        //3 播放状态控制
        getPlayService().setOnPlayEventListener(this);


        initPlayMode();
        onChangeImpl(getPlayService().getPlayingMusic());

        mAdapter = new LocalMusicAdapter();
        mAdapter.setOnMoreClickListener(this);
        lvLocalMusic.setAdapter(mAdapter);
        if (getPlayService().getPlayingMusic() != null && getPlayService().getPlayingMusic().getType() == Music.Type
                .LOCAL) {
            lvLocalMusic.setSelection(getPlayService().getPlayingPosition());
        }
        updateView();
        lvLocalMusic.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                //播放视频
                getPlayService().play(position);
            }
        });
    }

    /**
     * 播放过程监听变化 后期考虑放到onchange中去
     * @param music
     */
    private void onChangeImpl(Music music) {
        if (music == null) {
            return;
        }

        sbProgress.setProgress((int) getPlayService().getCurrentPosition());
        sbProgress.setSecondaryProgress(0);
        sbProgress.setMax((int) music.getDuration());
        mLastProgress = 0;
        tvCurrentTime.setText(R.string.play_time_start);
        tvTotalTime.setText(formatTime(music.getDuration()));

        ivMode.setOnClickListener(this);
        ivPlay.setOnClickListener(this);
        ivPrev.setOnClickListener(this);
        ivNext.setOnClickListener(this);
        sbProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (seekBar == sbProgress) {
                    if (getPlayService().isPlaying() || getPlayService().isPausing()) {
                        int progress = seekBar.getProgress();
                        getPlayService().seekTo(progress);
                        tvCurrentTime.setText(formatTime(progress));
                        mLastProgress = progress;
                    } else {
                        seekBar.setProgress(0);
                    }
                }
            }
        });
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.iv_mode:
                switchPlayMode();
                break;
            case R.id.iv_play:
                play();
                break;
            case R.id.iv_next:
                next();
                break;
            case R.id.iv_prev:
                prev();
                break;
        }
    }

    private void play() {
        getPlayService().playPause();
    }

    private void next() {
        getPlayService().next();
    }

    private void prev() {
        getPlayService().prev();
    }

    private void switchPlayMode() {
        PlayModeEnum mode = PlayModeEnum.valueOf(Preferences.getPlayMode());
        switch (mode) {
            case LOOP:
                mode = PlayModeEnum.SHUFFLE;
                ToastUtils.show(R.string.mode_shuffle);
                break;
            case SHUFFLE:
                mode = PlayModeEnum.SINGLE;
                ToastUtils.show(R.string.mode_one);
                break;
            case SINGLE:
                mode = PlayModeEnum.LOOP;
                ToastUtils.show(R.string.mode_loop);
                break;
        }
        Preferences.savePlayMode(mode.value());
        initPlayMode();
    }

    private String formatTime(long time) {
        return SystemUtils.formatTime("mm:ss", time);
    }

    private void initPlayMode() {
        int mode = Preferences.getPlayMode();
        ivMode.setImageLevel(mode);
    }



    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[]
            grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        PermissionReq.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void updateView() {
        if (AppCache.getMusicList().isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
        } else {
            tvEmpty.setVisibility(View.GONE);
        }
        mAdapter.updatePlayingPosition(getPlayService());
        mAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onDestroy() {
        if (mPlayServiceConnection != null) {
            unbindService(mPlayServiceConnection);
        }
        super.onDestroy();
    }

    private void onControlChangeImpl(Music music) {
        if (music == null) {
            return;
        }

        sbProgress.setProgress((int) getPlayService().getCurrentPosition());
        sbProgress.setSecondaryProgress(0);
        sbProgress.setMax((int) music.getDuration());
        mLastProgress = 0;
        tvCurrentTime.setText(R.string.play_time_start);
        tvTotalTime.setText(formatTime(music.getDuration()));
    }

    @Override
    public void onChange(Music music) {
        //更新列表
        updateView();
        if (getPlayService().getPlayingMusic().getType() == Music.Type.LOCAL) {
            lvLocalMusic.smoothScrollToPosition(getPlayService().getPlayingPosition());
        }
        //更新控制台
        onControlChangeImpl(music);

    }

    @Override
    public void onPlayerStart() {
        //更新列表
        //更新控制台
        ivPlay.setSelected(true);
    }

    @Override
    public void onPlayerPause() {
        //更新列表
        //更新控制台
        ivPlay.setSelected(false);
    }

    @Override
    public void onPublish(int progress) {
        //更新列表
        //更新控制台
        sbProgress.setProgress(progress);
        //更新当前播放时间
        if (progress - mLastProgress >= 1000) {
            tvCurrentTime.setText(formatTime(progress));
            mLastProgress = progress;
        }
    }

    @Override
    public void onBufferingUpdate(int percent) {
        //更新列表
        //更新控制台
        sbProgress.setSecondaryProgress(sbProgress.getMax() * 100 / percent);
    }

    @Override
    public void onTimer(long remain) {
        //更新列表
        //更新控制台
    }

    @Override
    public void onMusicListUpdate() {
        //更新列表
        //更新控制台
        updateView();
    }

}
