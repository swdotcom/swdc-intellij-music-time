package com.softwareco.intellij.plugin;

import com.google.gson.JsonObject;

public class SoftwareResponse {

    private boolean ok = false;
    private boolean deactivated = false;
    private int code;
    private String dataMessage;
    private String errorMessage;
    private String jsonStr;
    private JsonObject jsonObj;

    public boolean isOk() {
        return ok;
    }

    public void setIsOk(boolean ok) {
        this.ok = ok;
    }

    public boolean isDeactivated() {
        return deactivated;
    }

    public void setDeactivated(boolean deactivated) {
        this.deactivated = deactivated;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getDataMessage() {
        return dataMessage;
    }

    public void setDataMessage(String dataMessage) {
        this.dataMessage = dataMessage;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getJsonStr() {
        return jsonStr;
    }

    public void setJsonStr(String jsonStr) {
        this.jsonStr = jsonStr;
    }

    public JsonObject getJsonObj() {
        return jsonObj;
    }

    public void setJsonObj(JsonObject jsonObj) {
        this.jsonObj = jsonObj;
    }
}
