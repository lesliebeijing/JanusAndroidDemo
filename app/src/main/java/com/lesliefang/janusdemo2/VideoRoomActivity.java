package com.lesliefang.janusdemo2;

import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.lesliefang.janusdemo2.entity.Room;
import com.lesliefang.janusdemo2.janus.JanusClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoDecoderFactory;
import org.webrtc.VideoEncoderFactory;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class VideoRoomActivity extends AppCompatActivity {
    private static final String TAG = "VideoRoomActivity";
    static final String JANUS_URL = "wss://janus.conf.meetecho.com/ws";

    PeerConnectionFactory peerConnectionFactory;
    PeerConnection peerConnection;
    private AudioTrack audioTrack;
    private VideoTrack videoTrack;
    private VideoCapturer videoCapturer;
    private SurfaceTextureHelper surfaceTextureHelper;

    EglBase.Context eglBaseContext;

    JanusClient janusClient;
    BigInteger videoRoomHandlerId;

    Room room = new Room(1234); // 默认房间

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_room);
        videoCapturer = createVideoCapturer();
        if (videoCapturer == null) {
            return;
        }

        eglBaseContext = EglBase.create().getEglBaseContext();

        peerConnectionFactory = createPeerConnectionFactory();
        AudioSource audioSource = peerConnectionFactory.createAudioSource(new MediaConstraints());
        audioTrack = peerConnectionFactory.createAudioTrack("101", audioSource);

        surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBaseContext);
        VideoSource videoSource = peerConnectionFactory.createVideoSource(videoCapturer.isScreencast());
        videoCapturer.initialize(surfaceTextureHelper, this, videoSource.getCapturerObserver());
        videoCapturer.startCapture(1280, 720, 30);

        videoTrack = peerConnectionFactory.createVideoTrack("102", videoSource);
//        videoTrack.addSink(surfaceViewRendererLocal);

        janusClient = new JanusClient(JANUS_URL);
        janusClient.setJanusCallback(janusCallback);
        janusClient.connect();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        janusClient.disConnect();
    }

    private JanusClient.JanusCallback janusCallback = new JanusClient.JanusCallback() {
        @Override
        public void onCreateSession(BigInteger sessionId) {
            janusClient.attachPlugin("janus.plugin.videoroom");
        }

        @Override
        public void onAttached(BigInteger handleId) {
            Log.d(TAG, "onAttached");
            videoRoomHandlerId = handleId;
            janusClient.joinRoom(handleId, room.getId(), "fangniu");
        }

        @Override
        public void onDetached(BigInteger handleId) {
            videoRoomHandlerId = null;
        }

        @Override
        public void onHangup(BigInteger handleId) {

        }

        @Override
        public void onMessage(BigInteger handleId, JSONObject msg, JSONObject jsep) {
            if (msg.has("videoroom")) {
                try {
                    String type = msg.getString("videoroom");
                    if ("joined".equals(type)) {
                        // 加入房间成功
                        Long roomId = msg.getLong("id");
                        // create offer
                        createOffer();

                        JSONArray publishers = msg.getJSONArray("publishers");
                        for (int i = 0; i < publishers.length(); i++) {
                            JSONObject publishObj = publishers.getJSONObject(i);
                            Long id = publishObj.getLong("id");
                            String display = publishObj.getString("display");
                        }
                    } else if ("event".equals(type)) {
                        if (msg.has("configured") && msg.getString("configured").equals("ok")
                                && jsep != null) {
                            // sdp 协商成功，收到网关发来的 sdp answer
                            String sdp = jsep.getString("sdp");
                            peerConnection.setRemoteDescription(new SdpObserver() {
                                @Override
                                public void onCreateSuccess(SessionDescription sdp) {
                                    Log.d(TAG, "setRemoteDescription onCreateSuccess");
                                }

                                @Override
                                public void onSetSuccess() {
                                    Log.d(TAG, "setRemoteDescription onSetSuccess");
                                }

                                @Override
                                public void onCreateFailure(String error) {
                                    Log.d(TAG, "setRemoteDescription onCreateFailure " + error);
                                }

                                @Override
                                public void onSetFailure(String error) {
                                    Log.d(TAG, "setRemoteDescription onSetFailure " + error);
                                }
                            }, new SessionDescription(SessionDescription.Type.ANSWER, sdp));
                        } else if (msg.has("unpublished")) {
                            Long unPublishdUserId = msg.getLong("unpublished");
                        } else if (msg.has("leaving")) {
                            Long leavingUserId = msg.getLong("leaving");
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void onIceCandidate(BigInteger handleId, JSONObject candidate) {
            try {
                if (!candidate.has("completed")) {
                    peerConnection.addIceCandidate(new IceCandidate(candidate.getString("sdpMid"),
                            candidate.getInt("sdpMLineIndex"), candidate.getString("candidate")));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onDestroySession(BigInteger sessionId) {

        }

        @Override
        public void onError(String error) {

        }
    };

    private VideoCapturer createVideoCapturer() {
        if (Camera2Enumerator.isSupported(this)) {
            return createCameraCapturer(new Camera2Enumerator(this));
        } else {
            return createCameraCapturer(new Camera1Enumerator(true));
        }
    }

    private VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();

        // First, try to find front facing camera
        Log.d(TAG, "Looking for front facing cameras.");
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        // Front facing camera not found, try something else
        Log.d(TAG, "Looking for other cameras.");
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }
        return null;
    }

    private PeerConnectionFactory createPeerConnectionFactory() {
        final VideoEncoderFactory encoderFactory;
        final VideoDecoderFactory decoderFactory;

        encoderFactory = new DefaultVideoEncoderFactory(
                eglBaseContext, false /* enableIntelVp8Encoder */, true);
        decoderFactory = new DefaultVideoDecoderFactory(eglBaseContext);

        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(this)
                .setEnableInternalTracer(true)
                .createInitializationOptions());

        PeerConnectionFactory.Builder builder = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory);
        builder.setOptions(null);

        return builder.createPeerConnectionFactory();
    }

    private PeerConnection createPeerConnection() {
        List<PeerConnection.IceServer> iceServerList = new ArrayList<>();
        iceServerList.add(new PeerConnection.IceServer("stun:webrtc.encmed.cn:5349"));
        iceServerList.add(new PeerConnection.IceServer("stun:stun.l.google.com:19302"));
        PeerConnection peerConnection = peerConnectionFactory.createPeerConnection(iceServerList, peerConnectionObserver);
        peerConnection.addTrack(audioTrack);
        peerConnection.addTrack(videoTrack);
        return peerConnection;
    }

    private PeerConnection.Observer peerConnectionObserver = new PeerConnection.Observer() {
        @Override
        public void onSignalingChange(PeerConnection.SignalingState newState) {
            Log.d(TAG, "onSignalingChange " + newState);
        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState newState) {
            Log.d(TAG, "onIceConnectionChange " + newState);
        }

        @Override
        public void onIceConnectionReceivingChange(boolean receiving) {
            Log.d(TAG, "onIceConnectionReceivingChange " + receiving);
        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState newState) {
            Log.d(TAG, "onIceGatheringChange " + newState);
            if (newState == PeerConnection.IceGatheringState.COMPLETE) {
                janusClient.trickleCandidateComplete(videoRoomHandlerId);
            }
        }

        @Override
        public void onIceCandidate(IceCandidate candidate) {
            Log.d(TAG, "onIceCandidate");
            janusClient.trickleCandidate(videoRoomHandlerId, candidate);
        }

        @Override
        public void onIceCandidatesRemoved(IceCandidate[] candidates) {
            Log.d(TAG, "onIceCandidatesRemoved");
            peerConnection.removeIceCandidates(candidates);
        }

        @Override
        public void onAddStream(MediaStream stream) {
            Log.d(TAG, "onAddStream");
//            stream.videoTracks.get(0).addSink(surfaceViewRendererRemote);
        }

        @Override
        public void onRemoveStream(MediaStream stream) {
            Log.d(TAG, "onRemoveStream");
        }

        @Override
        public void onDataChannel(DataChannel dataChannel) {

        }

        @Override
        public void onRenegotiationNeeded() {

        }

        @Override
        public void onAddTrack(RtpReceiver receiver, MediaStream[] mediaStreams) {
            Log.d(TAG, "onAddTrack ");
        }
    };

    private void createOffer() {
        if (peerConnection == null) {
            peerConnection = createPeerConnection();
        }

        MediaConstraints mediaConstraints = new MediaConstraints();
//                mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
//                mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
//                mediaConstraints.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));
        peerConnection.createOffer(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sdp) {
                Log.d(TAG, "createOffer onCreateSuccess " + sdp.toString());
                peerConnection.setLocalDescription(new SdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription sdp) {
                        Log.d(TAG, "setLocalDescription onCreateSuccess");
                    }

                    @Override
                    public void onSetSuccess() {
                        Log.d(TAG, "setLocalDescription onSetSuccess");
                    }

                    @Override
                    public void onCreateFailure(String error) {
                        Log.d(TAG, "setLocalDescription onCreateFailure " + error);
                    }

                    @Override
                    public void onSetFailure(String error) {
                        Log.d(TAG, "setLocalDescription onSetFailure " + error);
                    }
                }, sdp);

                if (videoRoomHandlerId != null) {
                    janusClient.publish(videoRoomHandlerId, sdp);
                }
            }

            @Override
            public void onSetSuccess() {
                Log.d(TAG, "createOffer onSetSuccess");
            }

            @Override
            public void onCreateFailure(String error) {
                Log.d(TAG, "createOffer onCreateFailure " + error);
            }

            @Override
            public void onSetFailure(String error) {
                Log.d(TAG, "createOffer onSetFailure " + error);
            }
        }, mediaConstraints);
    }
}