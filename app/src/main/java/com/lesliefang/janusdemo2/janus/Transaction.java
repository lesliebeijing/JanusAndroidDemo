package com.lesliefang.janusdemo2.janus;

import org.json.JSONObject;

public class Transaction {
    private String tid;

    public Transaction(String tid) {
        this.tid = tid;
    }

    public void onError() {
    }

    public void onSuccess(JSONObject data) throws Exception {
    }

    public String getTid() {
        return tid;
    }
}