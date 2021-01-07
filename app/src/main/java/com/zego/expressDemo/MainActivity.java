package com.zego.expressDemo;

import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.TextureView;
import android.view.View;

import com.zego.expressDemo.data.ZegoDataCenter;
import com.zego.expressDemo.helper.FileHelper;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import im.zego.zegoexpress.ZegoAudioEffectPlayer;
import im.zego.zegoexpress.ZegoExpressEngine;
import im.zego.zegoexpress.callback.IZegoAudioEffectPlayerLoadResourceCallback;
import im.zego.zegoexpress.callback.IZegoDataRecordEventHandler;
import im.zego.zegoexpress.callback.IZegoEventHandler;
import im.zego.zegoexpress.constants.ZegoAECMode;
import im.zego.zegoexpress.constants.ZegoAudioChannel;
import im.zego.zegoexpress.constants.ZegoAudioCodecID;
import im.zego.zegoexpress.constants.ZegoDataRecordState;
import im.zego.zegoexpress.constants.ZegoDataRecordType;
import im.zego.zegoexpress.constants.ZegoPublishChannel;
import im.zego.zegoexpress.constants.ZegoPublisherState;
import im.zego.zegoexpress.constants.ZegoRoomState;
import im.zego.zegoexpress.constants.ZegoScenario;
import im.zego.zegoexpress.constants.ZegoUpdateType;
import im.zego.zegoexpress.entity.ZegoAudioConfig;
import im.zego.zegoexpress.entity.ZegoAudioEffectPlayConfig;
import im.zego.zegoexpress.entity.ZegoDataRecordConfig;
import im.zego.zegoexpress.entity.ZegoDataRecordProgress;
import im.zego.zegoexpress.entity.ZegoStream;

public class MainActivity extends BaseActivity {

    private final static String TAG = "zohar";

    private ZegoExpressEngine mExpressEngine;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mExpressEngine = ZegoExpressEngine.createEngine(ZegoDataCenter.APP_ID, ZegoDataCenter.APP_SIGN, ZegoDataCenter.IS_TEST_ENV,
                ZegoScenario.GENERAL, getApplication(), new IZegoEventHandler() {
                    @Override
                    public void onPlayerRecvSEI(String streamID, byte[] data) {
                        Log.d(TAG, "-->:: onPlayerRecvSEI streamID: " + streamID + ", content: " + new String(data));
                    }

                    @Override
                    public void onRoomStateUpdate(String roomID, ZegoRoomState state, int errorCode, JSONObject extendedData) {
                        Log.d(TAG, "-->:: onRoomStateUpdate roomID: " + roomID + ", state: " + state + ", errorCode: " + errorCode);
                    }

                    @Override
                    public void onRoomStreamUpdate(String roomID, ZegoUpdateType updateType, ArrayList<ZegoStream> streamList) {
                        Log.d(TAG, "-->:: onRoomStreamUpdate roomID: " + roomID + ", updateType: " + updateType + ", streamList: " + streamList);
                    }

                    @Override
                    public void onCapturedSoundLevelUpdate(float soundLevel) {
//                        Log.d(TAG, "-->:: onCapturedSoundLevelUpdate soundLevel: " + soundLevel);
                    }

                    @Override
                    public void onRemoteSoundLevelUpdate(HashMap<String, Float> soundLevels) {
                        for (Map.Entry<String, Float> soundLevel : soundLevels.entrySet()) {
                            Log.d(TAG, "-->:: onRemoteSoundLevelUpdate streamID: " + soundLevel.getKey() + "soundLevel: " + soundLevel.getValue());
                        }
                    }

                    @Override
                    public void onPublisherStateUpdate(String streamID, ZegoPublisherState state, int errorCode, JSONObject extendedData) {
                        super.onPublisherStateUpdate(streamID, state, errorCode, extendedData);
                    }


                });

        ZegoAudioConfig audioConfig = new ZegoAudioConfig();
        audioConfig.codecID = ZegoAudioCodecID.NORMAL2;
        audioConfig.bitrate = 192;
        audioConfig.channel = ZegoAudioChannel.MONO;
        mExpressEngine.setAudioConfig(audioConfig);

        mExpressEngine.enableAEC(true);
        mExpressEngine.setAECMode(ZegoAECMode.SOFT);
        mExpressEngine.enableANS(false);
        mExpressEngine.enableAGC(false);
        mExpressEngine.enableHeadphoneAEC(false);

        mExpressEngine.startPreview();
        mAudioEffectPlayer = mExpressEngine.createAudioEffectPlayer();
        mAudioEffectPlayer.loadResource(1, FileHelper.copyAssetsFile2Phone(this, "111.aac"), new IZegoAudioEffectPlayerLoadResourceCallback() {
            @Override
            public void onLoadResourceCallback(int i) {
                Log.d(TAG, "onLoadResourceCallback soundID: " + i);
            }
        });
    }

    private ZegoAudioEffectPlayer mAudioEffectPlayer;


    public void startAudioPlayer(View view) {
        ZegoAudioEffectPlayConfig audioEffectPlayConfig = new ZegoAudioEffectPlayConfig();
        audioEffectPlayConfig.isPublishOut = false;
        audioEffectPlayConfig.playCount = 1;
        mAudioEffectPlayer.start(1, "", audioEffectPlayConfig);
//        mSoundPool.play(mSoundPoolSoundID, 1, 1, 0, 0, 1.0f);
    }

    public void startAudioPlayer2(View view) {
//        mSoundPool.play(mSoundPoolSoundID2, 1, 1, 0, 0, 1.0f);
    }

    public void startMediaRecord(View view) {
        ZegoDataRecordConfig recordConfig = new ZegoDataRecordConfig();
        recordConfig.recordType = ZegoDataRecordType.ONLY_AUDIO;
        recordConfig.filePath = getExternalCacheDir() + File.separator + "temp.aac";
        Log.d(TAG, "-->:: recordConfig.filePath: " + new File(recordConfig.filePath).canWrite() + new File(recordConfig.filePath).canRead());

        mExpressEngine.startRecordingCapturedData(recordConfig, ZegoPublishChannel.MAIN);
        mExpressEngine.setDataRecordEventHandler(new IZegoDataRecordEventHandler() {
            @Override
            public void onCapturedDataRecordStateUpdate(ZegoDataRecordState state, int errorCode, ZegoDataRecordConfig config, ZegoPublishChannel channel) {
                Log.d(TAG, "-->:: onCapturedDataRecordStateUpdate state: " + state + ", errorCode: " + errorCode);
            }

            @Override
            public void onCapturedDataRecordProgressUpdate(ZegoDataRecordProgress progress, ZegoDataRecordConfig config, ZegoPublishChannel channel) {
                Log.d(TAG, "-->:: onCapturedDataRecordProgressUpdate progress.duration: " + progress.duration + ", progress.currentFileSize: " + progress.currentFileSize);
            }
        });
    }

    public void stopMediaRecord(View view) {
        mExpressEngine.stopRecordingCapturedData(ZegoPublishChannel.MAIN);
    }
}