package com.zego.expressDemo;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.zego.expressDemo.bean.ZegoVideoCanvas;
import com.zego.expressDemo.data.ZegoDataCenter;

import java.util.Random;

import im.zego.zegoexpress.ZegoExpressEngine;
import im.zego.zegoexpress.constants.ZegoOrientation;
import im.zego.zegoexpress.constants.ZegoPublishChannel;
import im.zego.zegoexpress.constants.ZegoVideoMirrorMode;

public class MainActivity extends BaseActivity implements CompoundButton.OnCheckedChangeListener {

    private final static String TAG = "zohar";

    private EditText mEtRoomID;
    private CheckBox mCbPublishMain;
    private CheckBox mCbPublishAux;
    private CheckBox mCbPublishThird;
    private CheckBox mCbPublishFourth;
    private CheckBox mCbPlayMain;
    private CheckBox mCbPlayAux;
    private CheckBox mCbPlayThird;
    private CheckBox mCbPlayFourth;

    private boolean isJoinLive;
    private boolean isRecord;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        initView();
    }

    private void initView() {
        mEtRoomID = findViewById(R.id.et_room_id);
        mCbPublishMain = findViewById(R.id.cb_publish_main);
        mCbPublishAux = findViewById(R.id.cb_publish_aux);
        mCbPublishThird = findViewById(R.id.cb_publish_third);
        mCbPublishFourth = findViewById(R.id.cb_publish_fourth);

        mCbPlayMain = findViewById(R.id.cb_play_main);
        mCbPlayAux = findViewById(R.id.cb_play_aux);
        mCbPlayThird = findViewById(R.id.cb_play_third);
        mCbPlayFourth = findViewById(R.id.cb_play_fourth);

        mCbPublishMain.setOnCheckedChangeListener(this);
        mCbPublishAux.setOnCheckedChangeListener(this);
        mCbPublishThird.setOnCheckedChangeListener(this);
        mCbPublishFourth.setOnCheckedChangeListener(this);
        mCbPlayMain.setOnCheckedChangeListener(this);
        mCbPlayAux.setOnCheckedChangeListener(this);
        mCbPlayThird.setOnCheckedChangeListener(this);
        mCbPlayFourth.setOnCheckedChangeListener(this);

        findViewById(R.id.btn_leave_live).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ZegoEngine.getEngine().leaveLive();
                isJoinLive = false;
            }
        });

        findViewById(R.id.btn_join_live).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                joinLive();
            }
        });

        findViewById(R.id.btn_audio_observer).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isRecord = !isRecord;
                if (isRecord) {
                    ZegoEngine.getEngine().startAudioCaptureDataObserver(new IZegoAudioCaptureDataHandler() {
                        @Override
                        public void onCapturedAudioData(byte[] data, int dataLength, int sampleRate, int channel) {
                            Log.d(TAG, "-->:: onCapturedAudioData data[0]: " + data[0]);
                        }
                    });
                } else {
                    ZegoEngine.getEngine().stopAudioCaptureDataObserver();
                }
            }
        });

        findViewById(R.id.btn_add_cdn_publish).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ZegoEngine.getEngine().addPublishCdnUrl("rtmp://wsdemo.zego.im/miniapp/" + "publishCdnUrl");
            }
        });

        findViewById(R.id.btn_add_cdn_publish2).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ZegoEngine.getEngine().addPublishCdnUrl("rtmp://wsdemo.zego.im/miniapp/" + "publishCdnUrl_2");
            }
        });

        findViewById(R.id.btn_delete_cdn_publish).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ZegoEngine.getEngine().removePublishCdnUrl("rtmp://wsdemo.zego.im/miniapp/" + "publishCdnUrl");
            }
        });

        findViewById(R.id.btn_delete_cdn_publish2).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ZegoEngine.getEngine().removePublishCdnUrl("rtmp://wsdemo.zego.im/miniapp/" + "publishCdnUrl_2");
            }
        });

        findViewById(R.id.btn_switch_camera).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ZegoEngine.getEngine().switchCamera();
            }
        });

        findViewById(R.id.btn_enable_camera).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isEnable = !isEnable;
                ZegoEngine.getEngine().enableCamera(isEnable);
            }
        });

        findViewById(R.id.btn_change_resolution).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isLow = !isLow;
                if (isLow) {
                    ZegoEngine.getEngine().setVideoConfig(isPortrait()? 180 : 320, isPortrait() ? 320 : 180, 15, 80, 1);
                } else {
                    ZegoEngine.getEngine().setVideoConfig(isPortrait()? 544 : 960, isPortrait() ? 960 : 544, 15, 900, 1);
                }
            }
        });

        findViewById(R.id.btn_switch_mirror).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isBothMirror = !isBothMirror;
                if (isBothMirror) {
                    ZegoExpressEngine.getEngine().setVideoMirrorMode(ZegoVideoMirrorMode.BOTH_MIRROR);
                } else {
                    ZegoExpressEngine.getEngine().setVideoMirrorMode(ZegoVideoMirrorMode.ONLY_PREVIEW_MIRROR);
                }
            }
        });
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        joinLive();
    }

    private void joinLive() {
        String roomID = mEtRoomID.getText().toString().trim();
        if (roomID.isEmpty()) {
            Toast.makeText(MainActivity.this, "房间ID不能为 null", Toast.LENGTH_SHORT).show();
            return;
        }

        isJoinLive = true;

        mRotation = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();

        ZegoEngine.getEngine().setVideoConfig(isPortrait() ? 544 : 960, isPortrait() ? 960 : 544, 15, 900, 1);
        ZegoEngine.getEngine().setVideoConfig(isPortrait() ? 544 : 960, isPortrait() ? 960 : 544, 15, 900, 2);
//                ZegoEngine.getEngine().setVideoConfig(240, 432, 15, 350, ZegoPublishChannel.MAIN);
//                ZegoEngine.getEngine().setVideoConfig(544, 960, 15, 900, ZegoPublishChannel.AUX);
//                ZegoEngine.getEngine().setVideoConfig(360, 640, 15, 400, ZegoPublishChannel.THIRD);
//                ZegoEngine.getEngine().setVideoConfig(270, 480, 15, 250, ZegoPublishChannel.FOURTH);

        ZegoEngine.JoinLiveBuilder builder = ZegoEngine.getLiveBuilder(roomID);
        if (mCbPublishMain.isChecked()) {
            builder.putPublishStreamInfo(getPublishStreamInfo(ZegoPublishChannel.MAIN));
        }
        if (mCbPublishAux.isChecked()) {
            builder.putPublishStreamInfo(getPublishStreamInfo(ZegoPublishChannel.AUX));
        }
        if (mCbPublishThird.isChecked()) {
            builder.putPublishStreamInfo(getPublishStreamInfo(ZegoPublishChannel.THIRD));
        }
        if (mCbPublishFourth.isChecked()) {
            builder.putPublishStreamInfo(getPublishStreamInfo(ZegoPublishChannel.FOURTH));
        }
        if (mCbPlayMain.isChecked()) {
            builder.addPlayStreamInfo(getPlayStreamInfo(ZegoPublishChannel.MAIN));
        }
        if (mCbPlayAux.isChecked()) {
            builder.addPlayStreamInfo(getPlayStreamInfo(ZegoPublishChannel.AUX));
        }
        if (mCbPlayThird.isChecked()) {
            builder.addPlayStreamInfo(getPlayStreamInfo(ZegoPublishChannel.THIRD));

        }
        if (mCbPlayFourth.isChecked()) {
            builder.addPlayStreamInfo(getPlayStreamInfo(ZegoPublishChannel.FOURTH));
        }
        builder.joinLive();

        ZegoExpressEngine.getEngine().setAppOrientation(ZegoOrientation.getZegoOrientation(mRotation));

        ZegoEngine.getEngine().startPreview();
        ZegoEngine.getEngine().setLocalVideoCanvas(new ZegoVideoCanvas(findViewById(R.id.ttv_preview), 0));
        ZegoEngine.getEngine().setRemoteVideoCanvas(new ZegoVideoCanvas(findViewById(R.id.ttv_main), ZegoPublishChannel.MAIN.value()));
        ZegoEngine.getEngine().setRemoteVideoCanvas(new ZegoVideoCanvas(findViewById(R.id.ttv_aux), ZegoPublishChannel.AUX.value()));
    }

    int mRotation = 0;

    private boolean isPortrait() {
        return mRotation == 0 || mRotation == Surface.ROTATION_180;
    }

    private boolean isEnable = true;
    private boolean isLow = false;
    private boolean isBothMirror = false;

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (!isJoinLive) {
            return;
        }
        if (buttonView.getId() == R.id.cb_publish_main) {
            if (!isChecked) {
                ZegoEngine.getEngine().stopPublish(getPublishStreamInfo(ZegoPublishChannel.MAIN));
            }
        }
        if (buttonView.getId() == R.id.cb_publish_aux) {
            if (!isChecked) {
                ZegoEngine.getEngine().stopPublish(getPublishStreamInfo(ZegoPublishChannel.AUX));
            }
        }
        if (buttonView.getId() == R.id.cb_publish_third) {
            if (!isChecked) {
                ZegoEngine.getEngine().stopPublish(getPublishStreamInfo(ZegoPublishChannel.THIRD));
            }
        }
        if (buttonView.getId() == R.id.cb_publish_fourth) {
            if (!isChecked) {
                ZegoEngine.getEngine().stopPublish(getPublishStreamInfo(ZegoPublishChannel.FOURTH));
            }
        }

        if (buttonView.getId() == R.id.cb_play_main) {
            ZegoEngine.UserStreamInfo playStreamInfo = getPlayStreamInfo(ZegoPublishChannel.MAIN);
            if (isChecked) {
                ZegoEngine.getEngine().startPlayStream(playStreamInfo);
                ZegoEngine.getEngine().setRemoteVideoCanvas(new ZegoVideoCanvas(findViewById(R.id.ttv_main), ZegoPublishChannel.MAIN.value()));
            } else {
                ZegoEngine.getEngine().stopPlayStream(playStreamInfo);
            }
        }
        if (buttonView.getId() == R.id.cb_play_aux) {
            ZegoEngine.UserStreamInfo playStreamInfo = getPlayStreamInfo(ZegoPublishChannel.AUX);
            if (isChecked) {
                ZegoEngine.getEngine().startPlayStream(playStreamInfo);
                ZegoEngine.getEngine().setRemoteVideoCanvas(new ZegoVideoCanvas(findViewById(R.id.ttv_aux), ZegoPublishChannel.AUX.value()));
            } else {
                ZegoEngine.getEngine().stopPlayStream(playStreamInfo);
            }
        }
        if (buttonView.getId() == R.id.cb_play_third) {
            ZegoEngine.UserStreamInfo playStreamInfo = getPlayStreamInfo(ZegoPublishChannel.THIRD);
            if (isChecked) {
                ZegoEngine.getEngine().startPlayStream(playStreamInfo);
            } else {
                ZegoEngine.getEngine().stopPlayStream(playStreamInfo);
            }
        }
        if (buttonView.getId() == R.id.cb_play_fourth) {
            ZegoEngine.UserStreamInfo playStreamInfo = getPlayStreamInfo(ZegoPublishChannel.FOURTH);
            if (isChecked) {
                ZegoEngine.getEngine().startPlayStream(playStreamInfo);
            } else {
                ZegoEngine.getEngine().stopPlayStream(playStreamInfo);
            }
        }
    }

    public ZegoEngine.UserStreamInfo getPublishStreamInfo(ZegoPublishChannel channel) {
//        ZegoEngine.UserStreamInfo streamInfo = new ZegoEngine.UserStreamInfo(ZegoDataCenter.ZEGO_USER.getUserId(), getPublishTarget(channel), channel == ZegoPublishChannel.MAIN ? ZegoEngine.StreamType.RTC : ZegoEngine.StreamType.CDN);
//        streamInfo.setPublishChannel(channel);
//        return streamInfo;
        return new ZegoEngine.UserStreamInfo(ZegoDataCenter.ZEGO_USER.getUserId(), getPublishTarget(channel), channel == ZegoPublishChannel.MAIN ? ZegoEngine.StreamType.RTC : ZegoEngine.StreamType.CDN);
    }

    public ZegoEngine.UserStreamInfo getPlayStreamInfo(ZegoPublishChannel channel) {
        ZegoEngine.UserStreamInfo playStreamInfo = new ZegoEngine.UserStreamInfo(channel.value(), getPlayTarget(channel), channel == ZegoPublishChannel.MAIN ? ZegoEngine.StreamType.RTC : ZegoEngine.StreamType.CDN);
        playStreamInfo.setQuicFirst(new Random().nextBoolean()); // 为了测试，随机使用 quic
        return playStreamInfo;
    }

    public String getPublishTarget(ZegoPublishChannel channel) {
        if (channel == ZegoPublishChannel.MAIN) {
            return "rtc_stream";
        } else {
            return "rtmp://publish-ws-quic.zegotech.cn/quic_test/" + channel.name();
        }
    }

    public String getPlayTarget(ZegoPublishChannel channel) {
        if (channel == ZegoPublishChannel.MAIN) {
            return "rtc_stream";
        } else {
            return "rtmp://rtmp-ws-quic.zegotech.cn/quic_test/" + channel.name();
        }
    }
}