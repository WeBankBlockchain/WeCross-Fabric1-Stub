package com.webank.wecross.stub.fabric;

import com.google.protobuf.ByteString;
import com.webank.wecross.account.FabricAccount;
import com.webank.wecross.common.FabricType;
import com.webank.wecross.stub.Request;
import com.webank.wecross.stub.ResourceInfo;
import com.webank.wecross.stub.TransactionContext;
import com.webank.wecross.stub.TransactionRequest;
import org.hyperledger.fabric.sdk.ChaincodeID;
import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.TransactionProposalRequest;
import org.hyperledger.fabric.sdk.security.CryptoSuite;
import org.hyperledger.fabric.sdk.transaction.ProposalBuilder;

public class EndorserRequestFactory {
    public static Request build(TransactionContext<TransactionRequest> request) throws Exception {
        if (!request.getAccount().getType().equals(FabricType.Account.FABRIC_ACCOUNT)) {
            throw new Exception(
                    "Illegal account type for fabric call: " + request.getAccount().getType());
        }

        FabricAccount account = (FabricAccount) request.getAccount();
        ResourceInfo resourceInfo = request.getResourceInfo();
        TransactionRequest transactionRequest = request.getData();

        // generate proposal
        org.hyperledger.fabric.protos.peer.FabricProposal.Proposal proposal =
                buildProposal(account, resourceInfo, transactionRequest);

        // sign
        byte[] sign = account.sign(proposal.toByteArray());

        // generate signed proposal
        org.hyperledger.fabric.protos.peer.FabricProposal.SignedProposal sp =
                org.hyperledger.fabric.protos.peer.FabricProposal.SignedProposal.newBuilder()
                        .setProposalBytes(proposal.toByteString())
                        .setSignature(ByteString.copyFrom(sign))
                        .build();

        Request endorserRequest = new Request();
        endorserRequest.setData(sp.toByteArray());
        return endorserRequest;
    }

    private static org.hyperledger.fabric.protos.peer.FabricProposal.Proposal buildProposal(
            FabricAccount account, ResourceInfo resourceInfo, TransactionRequest transactionRequest)
            throws Exception {
        // Generate transactionProposalRequest
        TransactionProposalRequest transactionProposalRequest =
                TransactionProposalRequest.newInstance(account.getUser());

        ResourceInfoProperty properties =
                ResourceInfoProperty.parseFrom(resourceInfo.getProperties());
        ChaincodeID chaincodeID =
                ChaincodeID.newBuilder().setName(properties.getChainCodeName()).build();
        Channel channel = properties.getChannel();

        transactionProposalRequest.setChaincodeID(chaincodeID);
        transactionProposalRequest.setChaincodeLanguage(properties.getChainCodeType());
        transactionProposalRequest.setFcn(transactionRequest.getMethod());
        String[] paramterList = getParamterList(transactionRequest);
        transactionProposalRequest.setArgs(paramterList);
        transactionProposalRequest.setProposalWaitTime(properties.getProposalWaitTime());

        org.hyperledger.fabric.sdk.TransactionRequest proposalRequest = transactionProposalRequest;

        org.hyperledger.fabric.sdk.transaction.TransactionContext transactionContext =
                new org.hyperledger.fabric.sdk.transaction.TransactionContext(
                        channel, account.getUser(), CryptoSuite.Factory.getCryptoSuite());
        ;
        transactionContext.verify(proposalRequest.doVerify());
        transactionContext.setProposalWaitTime(proposalRequest.getProposalWaitTime());

        ProposalBuilder proposalBuilder = ProposalBuilder.newBuilder();
        proposalBuilder.context(transactionContext);
        proposalBuilder.request(proposalRequest);

        org.hyperledger.fabric.protos.peer.FabricProposal.Proposal proposal =
                proposalBuilder.build();
        return proposal;
    }

    public static String[] getParamterList(Object[] args) {
        String[] paramterList = null;
        if (args.length == 0) {
            paramterList = new String[] {};
        } else {
            paramterList = new String[args.length];
            for (int i = 0; i < args.length; i++) {
                paramterList[i] = String.valueOf(args[i]);
            }
        }

        return paramterList;
    }

    public static String[] getParamterList(TransactionRequest request) {
        return getParamterList(request.getArgs());
    }
}
