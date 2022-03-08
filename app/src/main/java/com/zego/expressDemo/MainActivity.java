package com.zego.expressDemo;

import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.TextureView;
import android.view.View;
import android.widget.RelativeLayout;

import com.zego.expressDemo.data.ZegoDataCenter;
import com.zego.expressDemo.videocapture.VideoCaptureFromCamera2;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import im.zego.zegoexpress.ZegoExpressEngine;
import im.zego.zegoexpress.callback.IZegoEventHandler;
import im.zego.zegoexpress.constants.ZegoAECMode;
import im.zego.zegoexpress.constants.ZegoAudioChannel;
import im.zego.zegoexpress.constants.ZegoAudioCodecID;
import im.zego.zegoexpress.constants.ZegoPublisherState;
import im.zego.zegoexpress.constants.ZegoRoomState;
import im.zego.zegoexpress.constants.ZegoScenario;
import im.zego.zegoexpress.constants.ZegoUpdateType;
import im.zego.zegoexpress.constants.ZegoVideoBufferType;
import im.zego.zegoexpress.constants.ZegoVideoMirrorMode;
import im.zego.zegoexpress.entity.ZegoAudioConfig;
import im.zego.zegoexpress.entity.ZegoCanvas;
import im.zego.zegoexpress.entity.ZegoCustomVideoCaptureConfig;
import im.zego.zegoexpress.entity.ZegoStream;

public class MainActivity extends BaseActivity {

    private final static String TAG = "zohar";

    private ZegoExpressEngine mExpressEngine;

    private TextureView mTtv;

    private RelativeLayout mRlBtn;

    private boolean isBtnVisible = true;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.i(TAG, "onCreateOptionsMenu: ");
        MenuInflater inflater = new MenuInflater(this);
        inflater.inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.hide_btn:
                isBtnVisible = !isBtnVisible;
                mRlBtn.setVisibility(isBtnVisible ? View.VISIBLE : View.GONE);
                break;
        }

        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTtv = findViewById(R.id.ttv);

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
        audioConfig.codecID = ZegoAudioCodecID.LOW3;
        mExpressEngine.setAudioConfig(audioConfig);

        mExpressEngine.enableAEC(true);
        mExpressEngine.setAECMode(ZegoAECMode.SOFT);
        mExpressEngine.enableANS(false);
        mExpressEngine.enableAGC(false);
        mExpressEngine.enableHeadphoneAEC(false);

        ZegoCustomVideoCaptureConfig config = new ZegoCustomVideoCaptureConfig();
        config.bufferType = ZegoVideoBufferType.GL_TEXTURE_2D;
        mExpressEngine.enableCustomVideoCapture(true, config);
        mExpressEngine.setCustomVideoCaptureHandler(new VideoCaptureFromCamera2());

        mExpressEngine.setVideoMirrorMode(ZegoVideoMirrorMode.NO_MIRROR);
    }

    public void startPublish(View view) {

        ZegoCanvas canvas = new ZegoCanvas(mTtv);
        mExpressEngine.startPreview(canvas);

        mExpressEngine.startPublishingStream(STREAM_ID);
        mExpressEngine.startMixerTask();
    }

    public void startPublish2(View view) {
        ZegoCanvas canvas = new ZegoCanvas(mTtv);
        mExpressEngine.startPreview(canvas);

        mExpressEngine.startPublishingStream(STREAM_ID_2);
    }

    public void stopPublish(View view) {
        mExpressEngine.stopPreview();

        mExpressEngine.stopPublishingStream();
    }

    public void startPlay(View view) {
        ZegoCanvas canvas = new ZegoCanvas(mTtv);

        mExpressEngine.startPlayingStream(STREAM_ID, canvas, null);
    }

    public void startPlay2(View view) {
        ZegoCanvas canvas = new ZegoCanvas(mTtv);

        mExpressEngine.startPlayingStream(STREAM_ID_2, canvas, null);
    }

    public void stopPlay(View view) {
        mExpressEngine.stopPlayingStream(STREAM_ID);
    }

    public void stopPlay2(View view) {
        mExpressEngine.stopPlayingStream(STREAM_ID_2);
    }

    boolean isMuteMic = false;
    public void customFeature(View view) {
        isMuteMic = !isMuteMic;
        mExpressEngine.muteMicrophone(isMuteMic);
    }

    public void loginRoom(View view) {
        mExpressEngine.loginRoom(ROOM_ID, ZegoDataCenter.ZEGO_USER);
    }

    public void logoutRoom(View view) {
        mExpressEngine.logoutRoom(ROOM_ID);
    }
}