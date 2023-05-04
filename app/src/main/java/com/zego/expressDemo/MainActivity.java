package com.zego.expressDemo;

import android.app.Application;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.zego.expressDemo.adapter.UserSelectDialog;
import com.zego.expressDemo.application.BaseApplication;
import com.zego.expressDemo.data.ZegoDataCenter;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import im.zego.zegoexpress.ZegoExpressEngine;
import im.zego.zegoexpress.ZegoRangeAudio;
import im.zego.zegoexpress.callback.IZegoEventHandler;
import im.zego.zegoexpress.callback.IZegoRangeAudioEventHandler;
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

    private final static float AUDIO_RANGE_RECV_RANGE = 50f;
    private final static int AUDIO_RANGE_POSITION_UPDATE_FREQUENCY = 200;

    private ZegoExpressEngine mExpressEngine;
    private ZegoRangeAudio mRangeAudio;

    private UserSelectDialog mUserSelectDialog;
    private String mSelectUserID = null;
    private List<String> mUserList;
    private TextView mTvSelectedUser;
    private EditText mEtTeamID;
    private Button mBtnSetTeamID;

    private int mCurrentDistance;
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
                updateUserPosition();
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
                mCurrentDistance = progress;
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
    }

    private void updateUserPosition() {
        float[] position = new float[3];
        position[0] = mPositionMode < 2 ? (mPositionMode % 2 == 0 ? mCurrentDistance : -mCurrentDistance) : 0;
        position[1] = mPositionMode >= 2 ? (mPositionMode % 2 == 0 ? mCurrentDistance : -mCurrentDistance) : 0;
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
                        mUserList.add(streamInfo.user.userID);
                    }
                } else {
                    for (ZegoStream streamInfo : streamList) {
                        mUserList.remove(streamInfo.user.userID);
                    }
                }
                mUserSelectDialog.notifyDataSetChanged();
            }
        });


        mExpressEngine.enableCamera(false);
        mExpressEngine.loginRoom(AUDIO_RANGE_ROOM_ID, ZegoDataCenter.ZEGO_USER);

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