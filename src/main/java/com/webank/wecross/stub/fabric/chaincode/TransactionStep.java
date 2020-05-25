package com.webank.wecross.stub.fabric.chaincode;

import java.util.List;

public class TransactionStep {
    private int seq;
    private ChaincodeAddress contract;
    private List<byte[]> args;

    public int getSeq() {
        return seq;
    }

    public void setSeq(int seq) {
        this.seq = seq;
    }

    public ChaincodeAddress getContract() {
		return contract;
	}

	public void setContract(ChaincodeAddress contract) {
		this.contract = contract;
	}

    public List<byte[]> getArgs() {
        return args;
    }

    public void setArgs(List<byte[]> args) {
        this.args = args;
    }
}
