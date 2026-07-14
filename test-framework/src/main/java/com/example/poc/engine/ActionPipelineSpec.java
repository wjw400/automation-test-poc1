package com.example.poc.engine;

import java.util.ArrayList;
import java.util.List;

public class ActionPipelineSpec {
    private List<CaseActionSpec> beforeSend = new ArrayList<>();
    private List<CaseActionSpec> afterReceive = new ArrayList<>();

    public List<CaseActionSpec> getBeforeSend() {
        return beforeSend;
    }

    public void setBeforeSend(List<CaseActionSpec> beforeSend) {
        this.beforeSend = beforeSend == null ? new ArrayList<>() : beforeSend;
    }

    public List<CaseActionSpec> getAfterReceive() {
        return afterReceive;
    }

    public void setAfterReceive(List<CaseActionSpec> afterReceive) {
        this.afterReceive = afterReceive == null ? new ArrayList<>() : afterReceive;
    }
}
