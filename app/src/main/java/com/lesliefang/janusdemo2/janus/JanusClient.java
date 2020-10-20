package com.lesliefang.janusdemo2.janus;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.math.BigInteger;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by fanglin on 2020/10/18.
 * 参考1：https://blog.csdn.net/Java_lilin/article/details/104007291
 * 参考2：https://github.com/benwtrent/janus-gateway-android
 * 参考3：https://zhuanlan.zhihu.com/p/149324861?utm_source=wechat_session
 */
public class JanusClient implements WebSocketChannel.WebSocketCallback {
    private static final String TAG = "JanusClient";
    private ConcurrentHashMap<BigInteger, PluginHandle> attachedPlugins = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, Transaction> transactions = new ConcurrentHashMap<>();
    private BigInteger sessionId = null;
    private JanusCallback janusCallback;

    private volatile boolean isKeepAliveRunning;
    private Thread keepAliveThread;

    private String janusUrl;
    private WebSocketChannel webSocketChannel;

    public JanusClient(String janusUrl) {
        this.janusUrl = janusUrl;
        webSocketChannel = new WebSocketChannel();
        webSocketChannel.setWebSocketCallback(this);
    }

    public void setJanusCallback(JanusCallback janusCallback) {
        this.janusCallback = janusCallback;
    }

    public void connect() {
        webSocketChannel.connect(janusUrl);
    }

    public void disConnect() {
        stopKeepAliveTimer();
        if (webSocketChannel != null) {
            webSocketChannel.close();
            webSocketChannel = null;
        }
    }

    private void createSession() {
        String tid = randomString(12);
        transactions.put(tid, new Transaction(tid) {
            @Override
            public void onSuccess(JSONObject msg) throws Exception {
                JSONObject data = msg.getJSONObject("data");
                sessionId = new BigInteger(data.getString("id"));
                startKeepAliveTimer();
                if (janusCallback != null) {
                    janusCallback.onCreateSession(sessionId);
                }
            }
        });
        try {
            JSONObject obj = new JSONObject();
            obj.put("janus", "create");
            obj.put("transaction", tid);
            webSocketChannel.sendMessage(obj.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void destroySession() {
        String tid = randomString(12);
        transactions.put(tid, new Transaction(tid) {
            @Override
            public void onSuccess(JSONObject msg) throws Exception {
                stopKeepAliveTimer();
                if (janusCallback != null) {
                    janusCallback.onDestroySession(sessionId);
                }
            }
        });
        try {
            JSONObject obj = new JSONObject();
            obj.put("janus", "destroy");
            obj.put("transaction", tid);
            obj.put("session_id", sessionId);
            webSocketChannel.sendMessage(obj.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void attachPlugin(String pluginName) {
        String tid = randomString(12);
        transactions.put(tid, new Transaction(tid) {
            @Override
            public void onSuccess(JSONObject msg) throws Exception {
                JSONObject data = msg.getJSONObject("data");
                BigInteger handleId = new BigInteger(data.getString("id"));
                if (janusCallback != null) {
                    janusCallback.onAttached(handleId);
                }
                PluginHandle handle = new PluginHandle(handleId);
                attachedPlugins.put(handleId, handle);
            }
        });

        try {
            JSONObject obj = new JSONObject();
            obj.put("janus", "attach");
            obj.put("transaction", tid);
            obj.put("plugin", pluginName);
            obj.put("session_id", sessionId);
            webSocketChannel.sendMessage(obj.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void publisherCreateOffer(BigInteger handleId, SessionDescription sdp) {
        JSONObject message = new JSONObject();
        try {
            JSONObject publish = new JSONObject();
            publish.putOpt("audio", true);
            publish.putOpt("video", true);

            JSONObject jsep = new JSONObject();
            jsep.putOpt("type", sdp.type);
            jsep.putOpt("sdp", sdp.description);

            message.putOpt("janus", "message");
            message.putOpt("body", publish);
            message.putOpt("jsep", jsep);
            message.putOpt("transaction", randomString(12));
            message.putOpt("session_id", sessionId);
            message.putOpt("handle_id", handleId);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        webSocketChannel.sendMessage(message.toString());
    }

    public void trickleCandidate(BigInteger handleId, IceCandidate iceCandidate) {
        JSONObject candidate = new JSONObject();
        JSONObject message = new JSONObject();
        try {
            candidate.putOpt("candidate", iceCandidate.sdp);
            candidate.putOpt("sdpMid", iceCandidate.sdpMid);
            candidate.putOpt("sdpMLineIndex", iceCandidate.sdpMLineIndex);

            message.putOpt("janus", "trickle");
            message.putOpt("candidate", candidate);
            message.putOpt("transaction", randomString(12));
            message.putOpt("session_id", sessionId);
            message.putOpt("handle_id", handleId);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        webSocketChannel.sendMessage(message.toString());
    }

    public void trickleCandidateComplete(BigInteger handleId) {
        JSONObject candidate = new JSONObject();
        JSONObject message = new JSONObject();
        try {
            candidate.putOpt("completed", true);

            message.putOpt("janus", "trickle");
            message.putOpt("candidate", candidate);
            message.putOpt("transaction", randomString(12));
            message.putOpt("session_id", sessionId);
            message.putOpt("handle_id", handleId);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        webSocketChannel.sendMessage(message.toString());
    }

    @Override
    public void onOpen() {
        createSession();
    }

    @Override
    public void onMessage(String message) {
        Log.d(TAG, "收到消息》》》" + message);
        try {
            JSONObject obj = new JSONObject(message);
            JanusMessageType type = JanusMessageType.fromString(obj.getString("janus"));
            String transaction = null;
            BigInteger sender = null;
            if (obj.has("transaction")) {
                transaction = obj.getString("transaction");
            }
            if (obj.has("sender")) {
                sender = new BigInteger(obj.getString("sender"));
            }
            PluginHandle handle = null;
            if (sender != null) {
                handle = attachedPlugins.get(sender);
            }
            switch (type) {
                case keepalive:
                    break;
                case ack:
                    break;
                case success:
                    if (transaction != null) {
                        Transaction cb = transactions.get(transaction);
                        if (cb != null) {
                            try {
                                cb.onSuccess(obj);
                                transactions.remove(transaction);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    break;
                case error: {
                    if (transaction != null) {
                        Transaction cb = transactions.get(transaction);
                        if (cb != null) {
                            cb.onError();
                            transactions.remove(transaction);
                        }
                    }
                    break;
                }
                case hangup: {
                    break;
                }
                case detached: {
                    if (handle != null) {
                        if (janusCallback != null) {
                            janusCallback.onDetached(handle.getHandleId());
                        }
                    }
                    break;
                }
                case event: {
                    if (handle != null) {
                        JSONObject plugin_data = null;
                        if (obj.has("plugindata")) {
                            plugin_data = obj.getJSONObject("plugindata");
                        }
                        if (plugin_data != null) {
                            JSONObject data = null;
                            JSONObject jsep = null;
                            if (plugin_data.has("data")) {
                                data = plugin_data.getJSONObject("data");
                            }
                            if (obj.has("jsep")) {
                                jsep = obj.getJSONObject("jsep");
                            }
                            if (janusCallback != null) {
                                janusCallback.onMessage(handle.getHandleId(), data, jsep);
                            }
                        }
                    }
                }
                case trickle:
                    if (handle != null) {
                        if (obj.has("candidate")) {
                            JSONObject candidate = obj.getJSONObject("candidate");
                            if (janusCallback != null) {
                                janusCallback.onIceCandidate(handle.getHandleId(), candidate);
                            }
                        }
                    }
                    break;
                case destroy:
                    if (janusCallback != null) {
                        janusCallback.onDestroySession(sessionId);
                    }
                    break;
            }
        } catch (JSONException ex) {
            if (janusCallback != null) {
                janusCallback.onError(ex.getMessage());
            }
        }
    }

    private void startKeepAliveTimer() {
        isKeepAliveRunning = true;
        keepAliveThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (isKeepAliveRunning) {
                    try {
                        Thread.sleep(25000);
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                    if (webSocketChannel != null && webSocketChannel.isConnected()) {
                        JSONObject obj = new JSONObject();
                        try {
                            obj.put("janus", "keepalive");
                            obj.put("session_id", sessionId);
                            obj.put("transaction", randomString(12));
                            webSocketChannel.sendMessage(obj.toString());
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    } else {
                        Log.e(TAG, "keepAlive failed websocket is null or not connected");
                    }
                }
                Log.d(TAG, "keepAlive thread stopped");
            }
        }, "KeepAlive");
        keepAliveThread.start();
    }

    private void stopKeepAliveTimer() {
        isKeepAliveRunning = false;
        if (keepAliveThread != null) {
            keepAliveThread.interrupt();
        }
    }

    @Override
    public void onClosed() {
        stopKeepAliveTimer();
    }

    public String randomString(int length) {
        String str = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        Random random = new Random();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(str.charAt(random.nextInt(str.length())));
        }
        return sb.toString();
    }

    public interface JanusCallback {
        void onCreateSession(BigInteger sessionId);

        void onAttached(BigInteger handleId);

        void onDetached(BigInteger handleId);

        void onHangup(BigInteger handleId);

        void onMessage(BigInteger handleId, JSONObject msg, JSONObject jsep);

        void onIceCandidate(BigInteger handleId, JSONObject candidate);

        void onDestroySession(BigInteger sessionId);

        void onError(String error);
    }
}
