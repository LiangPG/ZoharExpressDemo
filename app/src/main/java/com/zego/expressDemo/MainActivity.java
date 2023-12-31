package com.zego.expressDemo;

import android.app.Application;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.zego.expressDemo.adapter.UserSelectDialog;
import com.zego.expressDemo.application.BaseApplication;
import com.zego.expressDemo.data.ZegoDataCenter;
import com.zego.expressDemo.helper.FileHelper;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import im.zego.zegoexpress.ZegoExpressEngine;
import im.zego.zegoexpress.ZegoMediaPlayer;
import im.zego.zegoexpress.ZegoRangeAudio;
import im.zego.zegoexpress.callback.IZegoEventHandler;
import im.zego.zegoexpress.callback.IZegoMediaPlayerLoadResourceCallback;
import im.zego.zegoexpress.callback.IZegoRangeAudioEventHandler;
import im.zego.zegoexpress.constants.ZegoAudioSourceType;
import im.zego.zegoexpress.constants.ZegoMediaPlayerState;
import im.zego.zegoexpress.constants.ZegoPublishChannel;
import im.zego.zegoexpress.constants.ZegoRangeAudioListenMode;
import im.zego.zegoexpress.constants.ZegoRangeAudioMicrophoneState;
import im.zego.zegoexpress.constants.ZegoRangeAudioMode;
import im.zego.zegoexpress.constants.ZegoRangeAudioSpeakMode;
import im.zego.zegoexpress.constants.ZegoScenario;
import im.zego.zegoexpress.constants.ZegoUpdateType;
import im.zego.zegoexpress.entity.ZegoEngineProfile;
import im.zego.zegoexpress.entity.ZegoStream;

public class MainActivity extends BaseActivity {

    private final static String TAG = MainActivity.class.getSimpleName();

    private final static String AUDIO_RANGE_ROOM_ID = "audio_range";
    private final static String PLAYER_A_NAME = "player_a";
    private final static String PLAYER_B_NAME = "player_b";
    private final static String AUX_PREFIX = "aux_";

    private final static float AUDIO_RANGE_RECV_RANGE = 50f;
    private final static int AUDIO_RANGE_POSITION_UPDATE_FREQUENCY = 200;

    private ZegoExpressEngine mExpressEngine;
    private ZegoMediaPlayer mMediaPlayerA;
    private ZegoMediaPlayer mMediaPlayerB;
    private ZegoMediaPlayer mAuxMediaPlayer;
    private ZegoRangeAudio mRangeAudio;

    private UserSelectDialog mUserSelectDialog;
    private String mSelectUserID = null;
    private List<String> mUserList;
    private TextView mTvSelectedUser;
    private EditText mEtTeamID;
    private Button mBtnSetTeamID;

    private int mCurrentDistanceX;
    private int mCurrentDistanceY;
    private int mOrientationMode; // 0: +x, 1: -x, 2: +y, 3: -y
    private int mPositionMode; // 0: +x, 1: -x, 2: +y, 3: -y

    private ZegoRangeAudioSpeakMode mSpeakMode = ZegoRangeAudioSpeakMode.ALL;
    private ZegoRangeAudioListenMode mListenMode = ZegoRangeAudioListenMode.ALL;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initData();

        initSDK();

        initView();
    }

    private void updateCurrentSelectUser(String selectedUser) {
        mSelectUserID = selectedUser;
        mTvSelectedUser.setText(TextUtils.isEmpty(selectedUser) ? "我" : selectedUser);
    }

    private void initData() {
        mUserList = new ArrayList<>();
        mUserList.add(""); // 本地用户
    }

    private void initView() {
        mUserSelectDialog = new UserSelectDialog(this, mUserList);
        mUserSelectDialog.setOnPickUpUserListener(new UserSelectDialog.OnPickUserUpListener() {
            @Override
            public void onPickUpUser(String user) {
                updateCurrentSelectUser(user);
            }
        });

        RadioGroup rgRangeAudioMode = findViewById(R.id.rg_range_audio_mode);
        rgRangeAudioMode.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.rb_range_audio_world) {
                    mRangeAudio.setRangeAudioMode(ZegoRangeAudioMode.WORLD);
                } else if (checkedId == R.id.rb_range_audio_team) {
                    mRangeAudio.setRangeAudioMode(ZegoRangeAudioMode.TEAM);
                } else if (checkedId == R.id.rb_range_audio_secret_team) {
                    mRangeAudio.setRangeAudioMode(ZegoRangeAudioMode.SECRET_TEAM);
                }
            }
        });
        rgRangeAudioMode.check(R.id.rb_range_audio_world);

        RadioGroup rgOrientationMode = findViewById(R.id.rg_orientation);
        rgOrientationMode.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.rb_orientation_positive_x) {
                    mOrientationMode = 0;
                } else if (checkedId == R.id.rb_orientation_negative_x) {
                    mOrientationMode = 1;
                } else if (checkedId == R.id.rb_orientation_positive_y) {
                    mOrientationMode = 2;
                } else if (checkedId == R.id.rb_orientation_negative_y) {
                    mOrientationMode = 3;
                }
                // 只有自己的情况下，才更新 orientation
                if (TextUtils.isEmpty(mSelectUserID)) {
                    updateUserPosition();
                }
            }
        });
        rgOrientationMode.check(R.id.rb_orientation_positive_x);

        RadioGroup rgPositionMode = findViewById(R.id.rg_position_mode);
        rgPositionMode.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                // 0: +x, 1: -x, 2: +y, 3: -y
                if (checkedId == R.id.rb_position_positive_x) {
                    mPositionMode = 0;
                } else if (checkedId == R.id.rb_position_negative_x) {
                    mPositionMode = 1;
                } else if (checkedId == R.id.rb_position_positive_y) {
                    mPositionMode = 2;
                } else if (checkedId == R.id.rb_position_negative_y) {
                    mPositionMode = 3;
                }
            }
        });
        rgPositionMode.check(R.id.rb_position_positive_x);

        RadioGroup rgSpeakMode = findViewById(R.id.rg_speak_mode);
        rgSpeakMode.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                // 0: +x, 1: -x, 2: +y, 3: -y
                if (checkedId == R.id.rb_speak_mode_all) {
                    mSpeakMode = ZegoRangeAudioSpeakMode.ALL;
                } else if (checkedId == R.id.rb_speak_mode_world) {
                    mSpeakMode = ZegoRangeAudioSpeakMode.WORLD;
                } else if (checkedId == R.id.rb_speak_mode_team) {
                    mSpeakMode = ZegoRangeAudioSpeakMode.TEAM;
                }
                updateRangeCustomMode();
            }
        });
        rgSpeakMode.check(R.id.rb_speak_mode_all);

        RadioGroup rgListenMode = findViewById(R.id.rg_listen_mode);
        rgListenMode.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                // 0: +x, 1: -x, 2: +y, 3: -y
                if (checkedId == R.id.rb_listen_mode_all) {
                    mListenMode = ZegoRangeAudioListenMode.ALL;
                } else if (checkedId == R.id.rb_listen_mode_world) {
                    mListenMode = ZegoRangeAudioListenMode.WORLD;
                } else if (checkedId == R.id.rb_listen_mode_team) {
                    mListenMode = ZegoRangeAudioListenMode.TEAM;
                }
                updateRangeCustomMode();
            }
        });
        rgListenMode.check(R.id.rb_listen_mode_all);

        SeekBar sbDistance = findViewById(R.id.sb_distance);
        sbDistance.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // 0: +x, 1: -x, 2: +y, 3: -y
                switch (mPositionMode) {
                    case 0:
                        mCurrentDistanceX = progress;
                        break;
                    case 1:
                        mCurrentDistanceX = -progress;
                        break;
                    case 2:
                        mCurrentDistanceY = progress;
                        break;
                    case 3:
                        mCurrentDistanceY = -progress;
                        break;
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                updateUserPosition();
            }
        });

        Button btnUserSelect = findViewById(R.id.btn_select_user);
        btnUserSelect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mUserSelectDialog.show();
            }
        });

        mTvSelectedUser = findViewById(R.id.tv_current_select_stream);
        mEtTeamID = findViewById(R.id.et_team_id);
        mBtnSetTeamID = findViewById(R.id.btn_set_team_id);
        mBtnSetTeamID.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String teamID = mEtTeamID.getEditableText().toString().trim();
                mRangeAudio.setTeamID(teamID);
                mBtnSetTeamID.setText("设置小队名称，当前：" + teamID);
            }
        });

        ((CheckBox) findViewById(R.id.cb_player_a)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    mMediaPlayerA.enableRepeat(true);
                    mMediaPlayerA.loadResource(FileHelper.copyAssetsFile2Phone(MainActivity.this, "111.aac"), new IZegoMediaPlayerLoadResourceCallback() {
                        @Override
                        public void onLoadResourceCallback(int errorCode) {
                            mMediaPlayerA.start();
                        }
                    });
                    mUserList.add(PLAYER_A_NAME);
                } else {
                    mMediaPlayerA.stop();
                    mUserList.remove(PLAYER_A_NAME);
                }
                mUserSelectDialog.notifyDataSetChanged();
            }
        });

        ((CheckBox) findViewById(R.id.cb_player_b)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    mMediaPlayerB.enableRepeat(true);
                    mMediaPlayerB.loadResource(FileHelper.copyAssetsFile2Phone(MainActivity.this, "zhuoniqiu.mp3"), new IZegoMediaPlayerLoadResourceCallback() {
                        @Override
                        public void onLoadResourceCallback(int errorCode) {
                            mMediaPlayerB.start();
                        }
                    });
                    mUserList.add(PLAYER_B_NAME);
                } else {
                    mMediaPlayerB.stop();
                    mUserList.remove(PLAYER_B_NAME);
                }
                mUserSelectDialog.notifyDataSetChanged();
            }
        });

        // 使用媒体播放器作为音频采集源
        mExpressEngine.setAudioSource(ZegoAudioSourceType.MEDIA_PLAYER, ZegoPublishChannel.AUX);
        initMediaPlayer();
        Button btStartPublishAux = findViewById(R.id.btn_start_publish_aux);
        btStartPublishAux.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 开始推流
                mExpressEngine.startPublishingStream(
                        AUX_PREFIX + AUDIO_RANGE_ROOM_ID + "_" + ZegoDataCenter.ZEGO_USER.userID,
                        ZegoPublishChannel.AUX);
                // 播放音乐
                loadMediaPlayerResourceAndStart();
            }
        });

        Button btStopPublishAux = findViewById(R.id.btn_stop_publish_aux);
        btStopPublishAux.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 停止推流
                mAuxMediaPlayer.stop();
                mExpressEngine.stopPublishingStream(ZegoPublishChannel.AUX);
            }
        });
    }

    private void initMediaPlayer() {
        mAuxMediaPlayer = mExpressEngine.createMediaPlayer();
        mAuxMediaPlayer.enableRepeat(true);
    }

    private void loadMediaPlayerResourceAndStart() {
        mAuxMediaPlayer.stop();

        mAuxMediaPlayer.loadResource(FileHelper.copyAssetsFile2Phone(MainActivity.this, "sample.mp3"), new IZegoMediaPlayerLoadResourceCallback() {
            @Override
            public void onLoadResourceCallback(int errorCode) {
                if (errorCode == 0) {
                    Log.d(TAG, "loadResource success");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mAuxMediaPlayer.start();
                        }
                    });
                } else {
                    Log.d(TAG, "loadResource error");
                }
            }
        });
    }

    private void updateUserPosition() {
        float[] position = new float[3];
        position[0] = mCurrentDistanceY;
        position[1] = mCurrentDistanceX;
        position[2] = 0;
        if (TextUtils.isEmpty(mSelectUserID)) {
            float[] axisForward = new float[]{
                    mOrientationMode < 2 ? (mOrientationMode % 2 == 0 ? 1 : -1) : 0,
                    mOrientationMode >= 2 ? (mOrientationMode % 2 == 0 ? 1 : -1) : 0,
                    0};
            float[] axisRight = new float[]{
                    mOrientationMode % 2 == 0 ? 0 : (mOrientationMode < 2 ? 1 : -1),
                    mOrientationMode % 2 != 0 ? 0 : (mOrientationMode < 2 ? -1 : 1),
                    0};
            float[] axisUp = new float[]{0, 0, 1};
            mRangeAudio.updateSelfPosition(position, axisForward, axisRight, axisUp);
        } else if (PLAYER_A_NAME.equals(mSelectUserID)) {
            mMediaPlayerA.updatePosition(position);
        } else if (PLAYER_B_NAME.equals(mSelectUserID)) {
            mMediaPlayerB.updatePosition(position);
        } else {
            mRangeAudio.updateAudioSource(mSelectUserID, position);
        }
    }

    private void updateRangeCustomMode() {
        mRangeAudio.setRangeAudioCustomMode(mSpeakMode, mListenMode);
    }

    private void initSDK() {
        ZegoEngineProfile engineProfile = new ZegoEngineProfile();
        engineProfile.appID = ZegoDataCenter.APP_ID;
        engineProfile.appSign = ZegoDataCenter.APP_SIGN;
        engineProfile.application = (Application) BaseApplication.sApplication.getApplicationContext();
        engineProfile.scenario = ZegoScenario.GENERAL;
        mExpressEngine = ZegoExpressEngine.createEngine(engineProfile, new IZegoEventHandler() {
            @Override
            public void onRoomStreamUpdate(String roomID, ZegoUpdateType updateType, ArrayList<ZegoStream> streamList, JSONObject extendedData) {
                if (updateType == ZegoUpdateType.ADD) {
                    for (ZegoStream streamInfo : streamList) {
                        Log.d(TAG, "onRoomStreamUpdate " + streamInfo.streamID + " add");
                        if (streamInfo.streamID.startsWith(AUX_PREFIX)) {
                            // 辅路流 开始拉流
                            mExpressEngine.startPlayingStream(streamInfo.streamID);
                        } else {
                            mUserList.add(streamInfo.user.userID);
                        }
                    }
                } else {
                    for (ZegoStream streamInfo : streamList) {
                        Log.d(TAG, "onRoomStreamUpdate " + streamInfo.streamID + " delete");
                        if (streamInfo.streamID.startsWith(AUX_PREFIX)) {
                            // 辅路流 停止拉流
                            mExpressEngine.stopPlayingStream(streamInfo.streamID);
                        } else {
                            mUserList.remove(streamInfo.user.userID);
                        }
                    }
                }
                mUserSelectDialog.notifyDataSetChanged();
            }
        });


        mExpressEngine.enableCamera(false);
        mExpressEngine.loginRoom(AUDIO_RANGE_ROOM_ID, ZegoDataCenter.ZEGO_USER);

        mMediaPlayerA = mExpressEngine.createMediaPlayer();
        // mExpressEngine.createMediaPlayer(); // 过滤两个 player，为了测试
        // mExpressEngine.createMediaPlayer();
        mMediaPlayerB = mExpressEngine.createMediaPlayer();

        mRangeAudio = mExpressEngine.createRangeAudio();
        mRangeAudio.setEventHandler(new IZegoRangeAudioEventHandler() {
            @Override
            public void onRangeAudioMicrophoneStateUpdate(ZegoRangeAudio rangeAudio, ZegoRangeAudioMicrophoneState state, int errorCode) {
                Log.d(TAG, "-->:: onRangAudioMicrophone state: " + state + ", errorCode: " + errorCode);
            }
        });


        mRangeAudio.enableMicrophone(true);
        mRangeAudio.enableSpeaker(true);

        mRangeAudio.setAudioReceiveRange(AUDIO_RANGE_RECV_RANGE);
        mRangeAudio.setPositionUpdateFrequency(AUDIO_RANGE_POSITION_UPDATE_FREQUENCY);
        mRangeAudio.enableSpatializer(true);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();

        mExpressEngine.destroyRangeAudio(mRangeAudio);
        mExpressEngine.logoutRoom();

        ZegoExpressEngine.destroyEngine(null);
    }
}