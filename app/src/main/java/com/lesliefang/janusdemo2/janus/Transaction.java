package com.lesliefang.janusdemo2.janus;

import org.json.JSONObject;

import java.math.BigInteger;

public class Transaction {
    private String tid;

    /**
     * 如果有 feed 说明这是一个订阅的 Transaction
     */
    private BigInteger feedId;

    public Transaction(String tid) {
        this.tid = tid;
    }

    public Transaction(String tid, BigInteger feedId) {
        this.tid = tid;
        this.feedId = feedId;
    }

    public void onError() {
    }

    public void onSuccess(JSONObject data) throws Exception {
    }

    public void onSuccess(JSONObject data, BigInteger feed) throws Exception {
    }

    public String getTid() {
        return tid;
    }

    public BigInteger getFeedId() {
        return feedId;
    }

    public void setFeedId(BigInteger feedId) {
        this.feedId = feedId;
    }
}