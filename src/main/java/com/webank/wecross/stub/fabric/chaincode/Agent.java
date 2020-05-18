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
    public int startTransaction(final Context ctx, String transactionID, List<String> paths) {
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
        return null;
    }

    @Transaction
    public TransactionInfo getLatestTransactionInfo(final Context ctx) {
        return null;
    }
}
