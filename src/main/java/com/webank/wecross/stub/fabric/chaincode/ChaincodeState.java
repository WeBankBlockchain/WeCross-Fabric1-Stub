package com.webank.wecross.stub.fabric.chaincode;

public class ChaincodeState {
	private String chaincode;
	private String transactionID;
	
	public String getChaincode() {
		return chaincode;
	}
	public void setChaincode(String chaincode) {
		this.chaincode = chaincode;
	}
	public String getTransactionID() {
		return transactionID;
	}
	public void setTransactionID(String transactionID) {
		this.transactionID = transactionID;
	}
}
