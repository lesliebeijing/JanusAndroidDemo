package com.lesliefang.janusdemo2.janus;

import java.math.BigInteger;

public class PluginHandle {
    private BigInteger handleId;

    public PluginHandle(BigInteger handleId) {
        this.handleId = handleId;
    }

    public BigInteger getHandleId() {
        return handleId;
    }

    public void setHandleId(BigInteger handleId) {
        this.handleId = handleId;
    }
}