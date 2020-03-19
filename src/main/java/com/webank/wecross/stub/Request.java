package com.webank.wecross.stub;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class Request {
    private int type;
    @JsonIgnore private ResourceInfo resourceInfo;
    private byte[] data;

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public ResourceInfo getResourceInfo() {
        return resourceInfo;
    }

    public void setResourceInfo(ResourceInfo resourceInfo) {
        this.resourceInfo = resourceInfo;
    }
}
