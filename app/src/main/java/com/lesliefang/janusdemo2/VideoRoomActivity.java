package com.lesliefang.janusdemo2;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.lesliefang.janusdemo2.entity.Publisher;
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
import org.webrtc.RendererCommon;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoDecoderFactory;
import org.webrtc.VideoEncoderFactory;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
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
    final String userName = "fangniu";

    private List<VideoItem> videoItemList = new ArrayList<>();
    private VideoItemAdapter adapter;
    RecyclerView recyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_room);
        recyclerView = findViewById(R.id.recyclerview);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));

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

        peerConnection = createPeerConnection(new CreatePeerConnectionCallback() {
            @Override
            public void onIceGatheringComplete() {
                janusClient.trickleCandidateComplete(videoRoomHandlerId);
            }

            @Override
            public void onIceCandidate(IceCandidate candidate) {
                janusClient.trickleCandidate(videoRoomHandlerId, candidate);
            }

            @Override
            public void onIceCandidatesRemoved(IceCandidate[] candidates) {
                peerConnection.removeIceCandidates(candidates);
            }

            @Override
            public void onAddStream(MediaStream stream) {

            }

            @Override
            public void onRemoveStream(MediaStream stream) {

            }
        });
        peerConnection.addTrack(audioTrack);
        peerConnection.addTrack(videoTrack);

        adapter = new VideoItemAdapter();
        recyclerView.setAdapter(adapter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        videoCapturer.dispose();
        surfaceTextureHelper.dispose();
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
            janusClient.joinRoom(handleId, room.getId(), userName);
        }

        @Override
        public void onSubscribeAttached(BigInteger subscriptionHandleId, BigInteger feedId) {
            Publisher publisher = room.findPublisherById(feedId);
            if (publisher != null) {
                publisher.setHandleId(subscriptionHandleId);
                // 订阅发布者
                janusClient.subscribe(subscriptionHandleId, room.getId(), feedId);
            }
        }

        @Override
        public void onDetached(BigInteger handleId) {
            videoRoomHandlerId = null;
        }

        @Override
        public void onHangup(BigInteger handleId) {

        }

        @Override
        public void onMessage(BigInteger sender, BigInteger handleId, JSONObject msg, JSONObject jsep) {
            if (!msg.has("videoroom")) {
                return;
            }
            try {
                String type = msg.getString("videoroom");
                if ("joined".equals(type)) {
                    // 加入房间成功
                    // 发送 offer 和网关建立连接
                    createOffer(peerConnection, new CreateOfferCallback() {
                        @Override
                        public void onCreateOfferSuccess(SessionDescription sdp) {
                            if (videoRoomHandlerId != null) {
                                // 发布
                                janusClient.publish(videoRoomHandlerId, sdp);
                            }
                        }

                        @Override
                        public void onCreateFailed(String error) {

                        }
                    });

                    JSONArray publishers = msg.getJSONArray("publishers");
                    handleNewPublishers(publishers);
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
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        videoCapturer.startCapture(1280, 720, 30);
                                        VideoItem videoItem = addNewVideoItem(null, userName);
                                        videoItem.peerConnection = peerConnection;
                                        videoItem.videoTrack = videoTrack;
                                        adapter.notifyItemInserted(videoItemList.size() - 1);
                                    }
                                });
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
                        // 离开
                        BigInteger leavingUserId = new BigInteger(msg.getString("leaving"));
                        room.removePublisherById(leavingUserId);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Iterator<VideoItem> it = videoItemList.iterator();
                                int index = 0;
                                while (it.hasNext()) {
                                    VideoItem next = it.next();
                                    if (leavingUserId.equals(next.userId)) {
                                        it.remove();
                                        adapter.notifyItemRemoved(index);
                                    }
                                    index++;
                                }
                            }
                        });
                    } else if (msg.has("publishers")) {
                        // 新用户开始发布
                        JSONArray publishers = msg.getJSONArray("publishers");
                        handleNewPublishers(publishers);
                    } else if (msg.has("started") && msg.getString("started").equals("ok")) {
                        // 订阅 start 成功
                        Log.d(TAG, "subscription started ok");
                    }
                } else if ("attached".equals(type) && jsep != null) {
                    // attach 到了一个Publisher 上,会收到网关转发来的sdp offer
                    String sdp = jsep.getString("sdp");
                    BigInteger feedId = new BigInteger(msg.getString("id"));
                    String display = msg.getString("display");
                    Publisher publisher = room.findPublisherById(feedId);

                    // 添加用户到界面
                    VideoItem videoItem = addNewVideoItem(feedId, display);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            adapter.notifyItemInserted(videoItemList.size() - 1);
                        }
                    });

                    PeerConnection peerConnection = createPeerConnection(new CreatePeerConnectionCallback() {
                        @Override
                        public void onIceGatheringComplete() {
                            janusClient.trickleCandidateComplete(sender);
                        }

                        @Override
                        public void onIceCandidate(IceCandidate candidate) {
                            janusClient.trickleCandidate(sender, candidate);
                        }

                        @Override
                        public void onIceCandidatesRemoved(IceCandidate[] candidates) {

                        }

                        @Override
                        public void onAddStream(MediaStream stream) {
                            if (stream.videoTracks.size() > 0) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        videoItem.videoTrack = stream.videoTracks.get(0);
                                        adapter.notifyDataSetChanged();
                                    }
                                });
                            }
                        }

                        @Override
                        public void onRemoveStream(MediaStream stream) {
                            videoItem.videoTrack = null;
                        }
                    });
                    videoItem.peerConnection = peerConnection;
                    peerConnection.setRemoteDescription(new SdpObserver() {
                        @Override
                        public void onCreateSuccess(SessionDescription sdp) {
                            Log.d(TAG, "setRemoteDescription onCreateSuccess");
                        }

                        @Override
                        public void onSetSuccess() {
                            Log.d(TAG, "setRemoteDescription onSetSuccess");
                            // 这时应该回复网关一个 start ，附带自己的 sdp answer
                            createAnswer(peerConnection, new CreateAnswerCallback() {
                                @Override
                                public void onSetAnswerSuccess(SessionDescription sdp) {
                                    janusClient.subscriptionStart(publisher.getHandleId(), room.getId(), sdp);
                                }

                                @Override
                                public void onSetAnswerFailed(String error) {

                                }
                            });
                        }

                        @Override
                        public void onCreateFailure(String error) {
                            Log.d(TAG, "setRemoteDescription onCreateFailure " + error);
                        }

                        @Override
                        public void onSetFailure(String error) {
                            Log.d(TAG, "setRemoteDescription onSetFailure " + error);
                        }
                    }, new SessionDescription(SessionDescription.Type.OFFER, sdp));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onIceCandidate(BigInteger handleId, JSONObject candidate) {
//            try {
//                if (!candidate.has("completed")) {
//                    peerConnection.addIceCandidate(new IceCandidate(candidate.getString("sdpMid"),
//                            candidate.getInt("sdpMLineIndex"), candidate.getString("candidate")));
//                }
//            } catch (JSONException e) {
//                e.printStackTrace();
//            }
        }

        @Override
        public void onDestroySession(BigInteger sessionId) {

        }

        @Override
        public void onError(String error) {

        }
    };

    private void handleNewPublishers(JSONArray publishers) {
        for (int i = 0; i < publishers.length(); i++) {
            try {
                JSONObject publishObj = publishers.getJSONObject(i);
                BigInteger feedId = new BigInteger(publishObj.getString("id"));
                String display = publishObj.getString("display");
                // attach 到发布者的 handle 上
                janusClient.subscribeAttach(feedId);

                room.addPublisher(new Publisher(feedId, display));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

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

    private PeerConnection createPeerConnection(CreatePeerConnectionCallback callback) {
        List<PeerConnection.IceServer> iceServerList = new ArrayList<>();
        iceServerList.add(new PeerConnection.IceServer("stun:stun.l.google.com:19302"));
        iceServerList.add(new PeerConnection.IceServer("stun:webrtc.encmed.cn:5349"));
        return peerConnectionFactory.createPeerConnection(iceServerList, new PeerConnection.Observer() {
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
                    if (callback != null) {
                        callback.onIceGatheringComplete();
                    }
                }
            }

            @Override
            public void onIceCandidate(IceCandidate candidate) {
                Log.d(TAG, "onIceCandidate");
                if (callback != null) {
                    callback.onIceCandidate(candidate);
                }
            }

            @Override
            public void onIceCandidatesRemoved(IceCandidate[] candidates) {
                Log.d(TAG, "onIceCandidatesRemoved");
                if (callback != null) {
                    callback.onIceCandidatesRemoved(candidates);
                }
            }

            @Override
            public void onAddStream(MediaStream stream) {
                Log.d(TAG, "onAddStream");
//            stream.videoTracks.get(0).addSink(surfaceViewRendererRemote);
                if (callback != null) {
                    callback.onAddStream(stream);
                }
            }

            @Override
            public void onRemoveStream(MediaStream stream) {
                Log.d(TAG, "onRemoveStream");
                if (callback != null) {
                    callback.onRemoveStream(stream);
                }
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
        });
    }

    private void createOffer(PeerConnection peerConnection, CreateOfferCallback callback) {
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

//                if (videoRoomHandlerId != null) {
//                    janusClient.publish(videoRoomHandlerId, sdp);
//                }
                if (callback != null) {
                    callback.onCreateOfferSuccess(sdp);
                }
            }

            @Override
            public void onSetSuccess() {
                Log.d(TAG, "createOffer onSetSuccess");
            }

            @Override
            public void onCreateFailure(String error) {
                Log.d(TAG, "createOffer onCreateFailure " + error);
                if (callback != null) {
                    callback.onCreateFailed(error);
                }
            }

            @Override
            public void onSetFailure(String error) {
                Log.d(TAG, "createOffer onSetFailure " + error);
                if (callback != null) {
                    callback.onCreateFailed(error);
                }
            }
        }, mediaConstraints);
    }

    private void createAnswer(PeerConnection peerConnection, CreateAnswerCallback callback) {
        MediaConstraints mediaConstraints = new MediaConstraints();
        peerConnection.createAnswer(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sdp) {
                peerConnection.setLocalDescription(new SdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription sdp) {
                    }

                    @Override
                    public void onSetSuccess() {
                        // send answer sdp
                        Log.d(TAG, "createAnswer setLocalDescription onSetSuccess");
                        if (callback != null) {
                            callback.onSetAnswerSuccess(sdp);
                        }
                    }

                    @Override
                    public void onCreateFailure(String s) {
                        Log.d(TAG, "createAnswer setLocalDescription onCreateFailure " + s);
                        if (callback != null) {
                            callback.onSetAnswerFailed(s);
                        }
                    }

                    @Override
                    public void onSetFailure(String s) {
                        Log.d(TAG, "createAnswer setLocalDescription onSetFailure " + s);
                        if (callback != null) {
                            callback.onSetAnswerFailed(s);
                        }
                    }
                }, sdp);
            }

            @Override
            public void onSetSuccess() {
                Log.d(TAG, "createAnswer onSetSuccess");
            }

            @Override
            public void onCreateFailure(String s) {
                Log.d(TAG, "createAnswer onCreateFailure " + s);
            }

            @Override
            public void onSetFailure(String s) {
                Log.d(TAG, "createAnswer onSetFailure " + s);
            }
        }, mediaConstraints);
    }

    class VideoItem {
        PeerConnection peerConnection;
        BigInteger userId;
        String display;
        VideoTrack videoTrack;
        SurfaceViewRenderer surfaceViewRenderer;
    }

    class VideoItemAdapter extends RecyclerView.Adapter<VideoItemHolder> {

        @NonNull
        @Override
        public VideoItemHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.videoroom_item, parent, false);
            return new VideoItemHolder(itemView);
        }

        @Override
        public void onBindViewHolder(@NonNull VideoItemHolder holder, int position) {
            VideoItem videoItem = videoItemList.get(position);
            if (videoItem.videoTrack != null) {
                videoItem.videoTrack.addSink(holder.surfaceViewRenderer);
            }
            videoItem.surfaceViewRenderer = holder.surfaceViewRenderer;
            if (videoItem.display != null) {
                holder.tvUserId.setText(videoItem.display);
            }
            if (userName.equals(videoItem.display)) {
                holder.tvMute.setVisibility(View.VISIBLE);
                holder.tvMute.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        boolean enabled = audioTrack.enabled();
                        if (enabled) {
                            holder.tvMute.setText("静音");
                        } else {
                            holder.tvMute.setText("取消静音");
                        }
                        audioTrack.setEnabled(!enabled);
                    }
                });
            } else {
                holder.tvMute.setVisibility(View.INVISIBLE);
            }
        }

        @Override
        public int getItemCount() {
            return videoItemList.size();
        }
    }

    class VideoItemHolder extends RecyclerView.ViewHolder {
        SurfaceViewRenderer surfaceViewRenderer;
        TextView tvUserId;
        TextView tvMute;

        VideoItemHolder(@NonNull View itemView) {
            super(itemView);
            surfaceViewRenderer = itemView.findViewById(R.id.surfaceviewrender);
            surfaceViewRenderer.init(eglBaseContext, null);
            surfaceViewRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
            surfaceViewRenderer.setKeepScreenOn(true);
            tvUserId = itemView.findViewById(R.id.tv_userid);
            tvMute = findViewById(R.id.tv_mute);
        }
    }

    VideoItem addNewVideoItem(BigInteger userId, String display) {
        VideoItem videoItem = new VideoItem();
        videoItem.userId = userId;
        videoItem.display = display;
        videoItemList.add(videoItem);
        return videoItem;
    }

    interface CreateAnswerCallback {
        void onSetAnswerSuccess(SessionDescription sdp);

        void onSetAnswerFailed(String error);
    }

    interface CreateOfferCallback {
        void onCreateOfferSuccess(SessionDescription sdp);

        void onCreateFailed(String error);
    }

    interface CreatePeerConnectionCallback {
        void onIceGatheringComplete();

        void onIceCandidate(IceCandidate candidate);

        void onIceCandidatesRemoved(IceCandidate[] candidates);

        void onAddStream(MediaStream stream);

        void onRemoveStream(MediaStream stream);
    }
}