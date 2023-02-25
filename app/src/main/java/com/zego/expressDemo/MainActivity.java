package com.zego.expressDemo;

import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Toast;

import com.zego.expressDemo.bean.ZegoVideoCanvas;
import com.zego.expressDemo.data.ZegoDataCenter;

import java.io.File;

import im.zego.zegoexpress.constants.ZegoDataRecordType;
import im.zego.zegoexpress.constants.ZegoPublishChannel;
import im.zego.zegoexpress.entity.ZegoDataRecordConfig;

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
                String roomID = mEtRoomID.getText().toString().trim();
                if (roomID.isEmpty()) {
                    Toast.makeText(MainActivity.this, "房间ID不能为 null", Toast.LENGTH_SHORT).show();
                    return;
                }

                isJoinLive = true;

                ZegoEngine.getEngine().setVideoConfig(240, 432, 15, 350, ZegoPublishChannel.MAIN);
                ZegoEngine.getEngine().setVideoConfig(544, 960, 15, 900, ZegoPublishChannel.AUX);
                ZegoEngine.getEngine().setVideoConfig(360, 640, 15, 400, ZegoPublishChannel.THIRD);
                ZegoEngine.getEngine().setVideoConfig(270, 480, 15, 250, ZegoPublishChannel.FOURTH);

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

                ZegoEngine.getEngine().setRemoteVideoCanvas(new ZegoVideoCanvas(findViewById(R.id.ttv_main), ZegoPublishChannel.MAIN.value()));
                ZegoEngine.getEngine().setRemoteVideoCanvas(new ZegoVideoCanvas(findViewById(R.id.ttv_aux), ZegoPublishChannel.AUX.value()));
                ZegoEngine.getEngine().setRemoteVideoCanvas(new ZegoVideoCanvas(findViewById(R.id.ttv_third), ZegoPublishChannel.THIRD.value()));
                ZegoEngine.getEngine().setRemoteVideoCanvas(new ZegoVideoCanvas(findViewById(R.id.ttv_fourth), ZegoPublishChannel.FOURTH.value()));
            }
        });

        findViewById(R.id.btn_start_record).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isRecord = !isRecord;
                if (isRecord) {
                    ZegoDataRecordConfig recordConfig = new ZegoDataRecordConfig();
                    recordConfig.recordType = ZegoDataRecordType.AUDIO_AND_VIDEO;
                    recordConfig.filePath = new File(getExternalCacheDir(), "temp.mp4").getAbsolutePath();
                    ZegoEngine.getEngine().startRecordingCaptured(recordConfig);
                } else {
                    ZegoEngine.getEngine().stopRecordingCaptured();
                }
            }
        });
    }

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
            } else {
                ZegoEngine.getEngine().stopPlayStream(playStreamInfo);
            }
        }
        if (buttonView.getId() == R.id.cb_play_aux) {
            ZegoEngine.UserStreamInfo playStreamInfo = getPlayStreamInfo(ZegoPublishChannel.AUX);
            if (isChecked) {
                ZegoEngine.getEngine().startPlayStream(playStreamInfo);
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
        ZegoEngine.UserStreamInfo streamInfo = new ZegoEngine.UserStreamInfo(ZegoDataCenter.ZEGO_USER.getUserId(), getPublishTarget(channel), channel == ZegoPublishChannel.MAIN ? ZegoEngine.StreamType.RTC : ZegoEngine.StreamType.CDN);
        streamInfo.setPublishChannel(channel);
        return streamInfo;
    }

    public ZegoEngine.UserStreamInfo getPlayStreamInfo(ZegoPublishChannel channel) {
        return new ZegoEngine.UserStreamInfo(channel.value(), getPlayTarget(channel), channel == ZegoPublishChannel.MAIN ? ZegoEngine.StreamType.RTC : ZegoEngine.StreamType.CDN);
    }

    public String getPublishTarget(ZegoPublishChannel channel) {
        if (channel == ZegoPublishChannel.MAIN) {
            return "rtc_stream";
        } else {
            return "rtmp://wsdemo.zego.im/miniapp/" + channel.name();
        }
    }

    public String getPlayTarget(ZegoPublishChannel channel) {
        if (channel == ZegoPublishChannel.MAIN) {
            return "rtc_stream";
        } else {
            return "rtmp://rtmp.wsdemo.zego.im/miniapp/" + channel.name();
        }
    }
}