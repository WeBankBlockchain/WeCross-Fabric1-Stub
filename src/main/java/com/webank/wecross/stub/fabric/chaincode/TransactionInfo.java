package com.webank.wecross.stub.fabric.chaincode;

import java.util.List;

public class TransactionInfo {
    private String transactionID;
    int status; // 0-Start 1-Commit 2-Rollback
    private List<TransactionStep> steps;

    public String getTransactionID() {
        return transactionID;
    }

    public void setTransactionID(String transactionID) {
        this.transactionID = transactionID;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public List<TransactionStep> getSteps() {
        return steps;
    }

    public void setSteps(List<TransactionStep> steps) {
        this.steps = steps;
    }
}
