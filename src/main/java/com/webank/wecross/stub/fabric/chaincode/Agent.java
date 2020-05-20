package com.webank.wecross.stub.fabric.chaincode;

import java.util.List;

import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.contract.ContractInterface;
import org.hyperledger.fabric.contract.annotation.Contact;
import org.hyperledger.fabric.contract.annotation.Contract;
import org.hyperledger.fabric.contract.annotation.Info;
import org.hyperledger.fabric.contract.annotation.License;
import org.hyperledger.fabric.contract.annotation.Transaction;
import org.hyperledger.fabric.shim.Chaincode.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

@Contract(
        name = "Agent",
        info =
                @Info(
                        title = "WeCross agent contract",
                        description = "WeCross",
                        version = "0.0.1-SNAPSHOT",
                        license =
                                @License(
                                        name = "Apache 2.0 License",
                                        url = "http://www.apache.org/licenses/LICENSE-2.0.html"),
                        contact =
                                @Contact(
                                        email = "f.carr@example.com",
                                        name = "F Carr",
                                        url = "https://hyperledger.example.com")))
public class Agent implements ContractInterface {
	private ObjectMapper objectMapper = new ObjectMapper();
	private Logger logger = LoggerFactory.getLogger(Agent.class);
	
    @Transaction
    public void initLedger(final Context ctx) {}

    @Transaction
    public Response call(
            final Context ctx,
            final String transactionID,
            final int seq,
            final String path,
            final String contract,
            final String method,
            final List<byte[]> args) {
        return ctx.getStub().invokeChaincode(contract, args);
    }

    @Transaction
    public Response sendTransaction(
            final Context ctx,
            final String transactionID,
            final int seq,
            final String path,
            final String contract,
            final String method,
            final List<byte[]> args) {
        return ctx.getStub().invokeChaincode(contract, args);
    }

    @Transaction
    public int startTransaction(final Context ctx, String transactionID, List<Contract> contracts) {
    	try {
	    	TransactionInfo transactionInfo = new TransactionInfo();
	    	transactionInfo.setTransactionID(transactionID);
	    	transactionInfo.setStatus(0);
	    	
	    	String key = "tx_" + transactionID;
	    	
	    	byte[] data = ctx.getStub().getState(key);
	    	if(data != null && data.length > 0) {
	    		logger.error("Start transaction failed, transactionID: {} exists", transactionID);
	    		
	    		return -1;
	    	}
	    	
	    	ctx.getStub().putState(key, objectMapper.writeValueAsBytes(transactionInfo));
    	}
    	catch(Exception e) {
    		logger.error("Error", e);
    		return -1;
    	}
        return 0;
    }

    @Transaction
    public int commitTransaction(final Context ctx, String transactionID) {
        return 0;
    }

    @Transaction
    public int rollbackTransaction(final Context ctx, String trnasactionID) {
        return 0;
    }

    @Transaction
    public TransactionInfo getTransactionInfo(final Context ctx, String transactionID) {
    	try {
	    	String key = "tx_" + transactionID;
	    	
	    	byte[] data = ctx.getStub().getState(key);
	    	if(data == null || data.length == 0) {
	    		logger.error("Get transaction failed, transactionID: {} not found", transactionID);
	    		
	    		return null;
	    	}
        
			return objectMapper.readValue(data, TransactionInfo.class);
		} catch (Exception e) {
			logger.error("Error", e);
			return null;
		}
    }

    @Transaction
    public TransactionInfo getLatestTransactionInfo(final Context ctx) {
        return null;
    }
}
