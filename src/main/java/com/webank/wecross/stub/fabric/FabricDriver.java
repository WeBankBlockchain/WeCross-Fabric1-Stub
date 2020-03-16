package com.webank.wecross.stub.fabric;

import com.webank.wecross.stub.BlockHeader;
import com.webank.wecross.stub.Connection;
import com.webank.wecross.stub.Driver;
import com.webank.wecross.stub.TransactionRequest;
import com.webank.wecross.stub.TransactionResponse;
import com.webank.wecross.stub.WithAccount;

public class FabricDriver implements Driver {
    @Override
    public byte[] encodeTransactionRequest(WithAccount<TransactionRequest> request) {
        return new byte[0];
    }

    @Override
    public WithAccount<TransactionRequest> decodeTransactionRequest(byte[] data) {
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
    public TransactionResponse call(WithAccount<TransactionRequest> request, Connection connection) {


        return null;
    }

    @Override
    public TransactionResponse sendTransaction(WithAccount<TransactionRequest> request, Connection connection) {
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
