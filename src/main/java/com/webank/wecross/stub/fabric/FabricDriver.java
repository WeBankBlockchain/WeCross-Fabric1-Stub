package com.webank.wecross.stub.fabric;

import com.webank.wecross.stub.BlockHeader;
import com.webank.wecross.stub.Connection;
import com.webank.wecross.stub.Driver;
import com.webank.wecross.stub.TransactionContext;
import com.webank.wecross.stub.TransactionRequest;
import com.webank.wecross.stub.TransactionResponse;

public class FabricDriver implements Driver {

    @Override
    public byte[] encodeTransactionRequest(TransactionContext<TransactionRequest> request) {
        return new byte[0];
    }

    @Override
    public TransactionContext<TransactionRequest> decodeTransactionRequest(byte[] data) {
        return null;
    }

    @Override
    public byte[] encodeTransactionResponse(TransactionResponse response) {
        return new byte[0];
    }

    @Override
    public TransactionResponse decodeTransactionResponse(byte[] data) {
        return null;
    }

    @Override
    public BlockHeader decodeBlockHeader(byte[] data) {
        return null;
    }

    @Override
    public TransactionResponse call(
            TransactionContext<TransactionRequest> request, Connection connection) {
        return null;
    }

    @Override
    public TransactionResponse sendTransaction(
            TransactionContext<TransactionRequest> request, Connection connection) {
        return null;
    }

    @Override
    public long getBlockNumber(Connection connection) {
        return 0;
    }

    @Override
    public byte[] getBlockHeader(long number, Connection connection) {
        return new byte[0];
    }
}
