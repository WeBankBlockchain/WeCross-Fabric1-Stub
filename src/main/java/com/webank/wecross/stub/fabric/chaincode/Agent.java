package com.webank.wecross.stub.fabric.chaincode;

import java.io.IOException;
import java.util.List;

import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.contract.ContractInterface;
import org.hyperledger.fabric.contract.annotation.Contact;
import org.hyperledger.fabric.contract.annotation.Contract;
import org.hyperledger.fabric.contract.annotation.Info;
import org.hyperledger.fabric.contract.annotation.License;
import org.hyperledger.fabric.contract.annotation.Transaction;
import org.hyperledger.fabric.shim.Chaincode.Response;
import org.hyperledger.fabric.shim.ChaincodeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
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
            final ChaincodeAddress address,
            final List<byte[]> args) {
    	try {
	    	ChaincodeState chaincodeState = readChaincodeInfo(ctx, address.getAddress());
	    	
	    	if(chaincodeState != null && !chaincodeState.getTransactionID().isEmpty()) {
	    		if(!chaincodeState.getTransactionID().equals(transactionID)) {
	    			// transaction ID not equal, failed and exists
	    			logger.warn("Path: {}, chaincode: {} in transaction: {}", address.getPath(), address.getAddress(), transactionID);
	    		}
	    		
	    		TransactionState transactionInfo = readTransactionInfo(ctx, transactionID);
	    		if(transactionInfo.getStatus() != 0) {
	    			logger.warn("Transaction {} not in progress: {}", transactionID, transactionInfo.getStatus());
	    		}
	    	}
	    	
	        return ctx.getStub().invokeChaincode(address.getAddress(), args);
    	}
    	catch(Exception e) {
    		logger.error("Call error", e);
    		
    		return new Response(-1, "Call error", null);
    	}
    }

    @Transaction
    public Response sendTransaction(
    		 final Context ctx,
             final String transactionID,
             final int seq,
             final ChaincodeAddress address,
             final List<byte[]> args) {
    	try {
	    	ChaincodeState chaincodeState = readChaincodeInfo(ctx, address.getAddress());
	    	
	    	if(chaincodeState != null && !chaincodeState.getTransactionID().isEmpty()) {
	    		if(!chaincodeState.getTransactionID().equals(transactionID)) {
	    			// transaction ID not equal, failed and exit
	    			logger.error("Path: {}, chaincode: {} in transaction: {}", address.getPath(), address.getAddress(), transactionID);
	    			throw new ChaincodeException("Chaincode in transaction");
	    			// return new Response(-1, "Chaincode in transaction", null);
	    		}
	    		
	    		TransactionState transactionState = readTransactionInfo(ctx, transactionID);
	    		if(transactionState == null) {
	    			logger.error("Transaction {} not exists", transactionID);
	    			throw new ChaincodeException("Transaction not exists");
	    			// return new Response(-1, "Transaction not exists", null);
	    		}
	    		
	    		if(transactionState.getStatus() != 0) {
	    			logger.error("Transaction {} not in progress: {}", transactionID, transactionState.getStatus());
	    			throw new ChaincodeException("Transaction not in progress");
	    			// return new Response(-1, "Transaction not in progress", null);
	    		}
	    		
	    		//record the operation
	    		TransactionStep transactionStep = new TransactionStep();
	    		transactionStep.setContract(address);
	    		transactionStep.setSeq(seq);
	    		transactionStep.setArgs(args);
	    		
	    		transactionState.getSteps().add(transactionStep);
	    	}
	    	
	        return ctx.getStub().invokeChaincode(address.getAddress(), args);
    	}
    	catch(ChaincodeException e) {
    		throw e;
    	}
    	catch(Exception e) {
    		logger.error("Call error", e);
    		
    		return new Response(-1, "Call error", null);
    	}
    }

    @Transaction
    public int startTransaction(final Context ctx, String transactionID, List<Contract> contracts) {
    	try {
    		TransactionState current = readTransactionInfo(ctx, transactionID);
    		if(current != null) {
    			logger.error("Transaction: {} exists", transactionID);
    			return -1;
    		}
    		
	    	TransactionState transactionInfo = new TransactionState();
	    	transactionInfo.setTransactionID(transactionID);
	    	transactionInfo.setStatus(0);
	    	
	    	writeTransactionInfo(ctx, transactionID, transactionInfo);
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
    public TransactionState getTransactionInfo(final Context ctx, String transactionID) {
    	try {
			return readTransactionInfo(ctx, transactionID);
		} catch (Exception e) {
			logger.error("getTransactionInfo error", e);
			
			throw new ChaincodeException("getTransactionInfo error");
		}
    }

    @Transaction
    public TransactionState getLatestTransactionInfo(final Context ctx) {
        return null;
    }
    
    private String stateKey(String transactionID) {
    	return "tx_" + transactionID;
    }
    
    private TransactionState readTransactionInfo(final Context ctx, String transactionID) throws JsonParseException, JsonMappingException, IOException {
    	String key = stateKey(transactionID);
    	
    	byte[] data = ctx.getStub().getState(key);
    	if(data == null || data.length == 0) {
    		logger.error("Get transaction failed, transactionID: {} not found", transactionID);
    		
    		return null;
    	}
    
		return objectMapper.readValue(data, TransactionState.class);
    }
    
    private void writeTransactionInfo(final Context ctx, String transactionID, TransactionState transactionInfo) throws JsonProcessingException {
    	String key = stateKey(transactionID);
    	
    	ctx.getStub().putState(key, objectMapper.writeValueAsBytes(transactionInfo));
    }
    
    private String chaincodeKey(String chaincode) {
    	return "chaincode_" + chaincode;
    }
    
    private ChaincodeState readChaincodeInfo(final Context ctx, String chaincode) throws JsonParseException, JsonMappingException, IOException {
    	String key = chaincodeKey(chaincode);
    	
    	byte[] data = ctx.getStub().getState(key);
    	
    	if(data == null || data.length == 0) {
    		logger.error("Get chaincode info failed, chaincode: {} not found", chaincode);
    		
    		return null;
    	}
    
		return objectMapper.readValue(data, ChaincodeState.class);
    }
    
    private void writeChaincodeInfo(final Context ctx, String chaincode, ChaincodeState chaincodeInfo) throws JsonProcessingException {
    	String key = chaincodeKey(chaincode);
    	
    	ctx.getStub().putState(key, objectMapper.writeValueAsBytes(chaincodeInfo));
    }
}
