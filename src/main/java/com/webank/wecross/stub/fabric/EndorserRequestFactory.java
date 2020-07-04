package com.webank.wecross.stub.fabric;

import com.google.protobuf.ByteString;
import com.webank.wecross.account.FabricAccount;
import com.webank.wecross.common.FabricType;
import com.webank.wecross.stub.Account;
import com.webank.wecross.stub.Request;
import com.webank.wecross.stub.ResourceInfo;
import com.webank.wecross.stub.TransactionContext;
import com.webank.wecross.stub.TransactionRequest;
import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.List;
import org.hyperledger.fabric.protos.common.Common;
import org.hyperledger.fabric.protos.msp.Identities;
import org.hyperledger.fabric.protos.peer.Chaincode;
import org.hyperledger.fabric.protos.peer.FabricProposal;
import org.hyperledger.fabric.sdk.ChaincodeID;
import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.HFClient;
import org.hyperledger.fabric.sdk.TransactionProposalRequest;
import org.hyperledger.fabric.sdk.security.CryptoSuite;
import org.hyperledger.fabric.sdk.transaction.InstallProposalBuilder;
import org.hyperledger.fabric.sdk.transaction.InstantiateProposalBuilder;
import org.hyperledger.fabric.sdk.transaction.ProposalBuilder;

public class EndorserRequestFactory {

    public static byte[] buildProposalRequestBytes(TransactionContext<TransactionRequest> request)
            throws Exception {
        if (!request.getAccount().getType().equals(FabricType.Account.FABRIC_ACCOUNT)) {
            throw new Exception(
                    "Illegal account type for fabric call: " + request.getAccount().getType());
        }

        FabricAccount account = (FabricAccount) request.getAccount();
        ResourceInfo resourceInfo = request.getResourceInfo();
        TransactionRequest transactionRequest = request.getData();

        // generate proposal
        FabricProposal.Proposal proposal = buildProposal(account, resourceInfo, transactionRequest);

        byte[] signedProposalBytes = signProposal(account, proposal);
        return signedProposalBytes;
    }

    public static Request buildInstallProposalRequest(
            TransactionContext<InstallChaincodeRequest> request) throws Exception {
        if (!request.getAccount().getType().equals(FabricType.Account.FABRIC_ACCOUNT)) {
            throw new Exception(
                    "Illegal account type for fabric call: " + request.getAccount().getType());
        }

        FabricAccount account = (FabricAccount) request.getAccount(); // Account
        InstallChaincodeRequest installChaincodeRequest = request.getData();

        // generate proposal
        FabricProposal.Proposal proposal = buildInstallProposal(account, installChaincodeRequest);
        byte[] signedProposalBytes = signProposal(account, proposal);
        Request endorserRequest = new Request();
        endorserRequest.setData(signedProposalBytes);
        return endorserRequest;
    }

    public static Request buildInstantiateProposalRequest(
            TransactionContext<InstantiateChaincodeRequest> request) throws Exception {
        if (!request.getAccount().getType().equals(FabricType.Account.FABRIC_ACCOUNT)) {
            throw new Exception(
                    "Illegal account type for fabric call: " + request.getAccount().getType());
        }

        FabricAccount account = (FabricAccount) request.getAccount(); // Account
        InstantiateChaincodeRequest instantiateChaincodeRequest = request.getData();

        // generate proposal
        FabricProposal.Proposal proposal =
                buildInstantiationProposal(account, instantiateChaincodeRequest);
        byte[] signedProposalBytes = signProposal(account, proposal);
        Request endorserRequest = new Request();
        endorserRequest.setData(signedProposalBytes);
        return endorserRequest;
    }

    public static byte[] signProposal(FabricAccount account, FabricProposal.Proposal proposal)
            throws Exception {
        // sign
        byte[] sign = account.sign(proposal.toByteArray());

        // generate signed proposal
        FabricProposal.SignedProposal signedProposal =
                FabricProposal.SignedProposal.newBuilder()
                        .setProposalBytes(proposal.toByteString())
                        .setSignature(ByteString.copyFrom(sign))
                        .build();

        return signedProposal.toByteArray();
    }

    private static FabricProposal.Proposal buildProposal(
            FabricAccount account, ResourceInfo resourceInfo, TransactionRequest transactionRequest)
            throws Exception {
        // Generate transactionProposalRequest
        TransactionProposalRequest transactionProposalRequest =
                TransactionProposalRequest.newInstance(account.getUser());

        ResourceInfoProperty properties =
                ResourceInfoProperty.parseFrom(resourceInfo.getProperties());
        ChaincodeID chaincodeID =
                ChaincodeID.newBuilder().setName(properties.getChaincodeName()).build();

        HFClient hfClient = HFClient.createNewInstance();
        hfClient.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());
        hfClient.setUserContext(account.getUser());

        Channel channel = hfClient.newChannel(properties.getChannelName());

        transactionProposalRequest.setChaincodeID(chaincodeID);
        // transactionProposalRequest.setChaincodeLanguage(properties.getChainCodeType()); // no
        // need language
        transactionProposalRequest.setFcn(transactionRequest.getMethod());
        String[] paramterList = getParamterList(transactionRequest);
        transactionProposalRequest.setArgs(paramterList);
        // transactionProposalRequest.setProposalWaitTime(properties.getProposalWaitTime());

        org.hyperledger.fabric.sdk.TransactionRequest proposalRequest = transactionProposalRequest;

        org.hyperledger.fabric.sdk.transaction.TransactionContext transactionContext =
                new org.hyperledger.fabric.sdk.transaction.TransactionContext(
                        channel, account.getUser(), CryptoSuite.Factory.getCryptoSuite());

        transactionContext.verify(proposalRequest.doVerify());
        // transactionContext.setProposalWaitTime(proposalRequest.getProposalWaitTime());

        ProposalBuilder proposalBuilder = ProposalBuilder.newBuilder();
        proposalBuilder.context(transactionContext);
        proposalBuilder.request(proposalRequest);

        FabricProposal.Proposal proposal = proposalBuilder.build();
        return proposal;
    }

    private static FabricProposal.Proposal buildInstallProposal(
            FabricAccount account, InstallChaincodeRequest installChaincodeRequest)
            throws Exception {
        installChaincodeRequest.check(); // check has all params

        HFClient hfClient = HFClient.createNewInstance();
        hfClient.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());
        hfClient.setUserContext(account.getUser());
        Channel channel =
                hfClient.newChannel(installChaincodeRequest.getChannelName()); // ChannelName

        org.hyperledger.fabric.sdk.transaction.TransactionContext transactionContext =
                new org.hyperledger.fabric.sdk.transaction.TransactionContext(
                        channel, account.getUser(), CryptoSuite.Factory.getCryptoSuite());

        ChaincodeID chaincodeID =
                ChaincodeID.newBuilder()
                        .setName(installChaincodeRequest.getName())
                        .setVersion(installChaincodeRequest.getVersion())
                        .setPath("chaincode")
                        .build(); // path default with generateTarGzInputStreamBytes function

        transactionContext.verify(
                false); // Install will have no signing cause it's not really targeted to a channel.
        // transactionContext.setProposalWaitTime(
        // FabricStubConfigParser.DEFAULT_DEPLOY_WAIT_TIME); // wait time
        InstallProposalBuilder installProposalbuilder = InstallProposalBuilder.newBuilder();
        installProposalbuilder.context(transactionContext);
        installProposalbuilder.setChaincodeLanguage(
                installChaincodeRequest.getChaincodeLanguageType()); // chaincode language
        installProposalbuilder.chaincodeName(chaincodeID.getName()); // name
        installProposalbuilder.chaincodePath(chaincodeID.getPath()); // path
        installProposalbuilder.chaincodeVersion(chaincodeID.getVersion()); // version
        // installProposalbuilder.setChaincodeSource(installProposalRequest.getChaincodeSourceLocation());
        installProposalbuilder.setChaincodeInputStream(
                new ByteArrayInputStream(installChaincodeRequest.getCode()));
        // installProposalbuilder.setChaincodeMetaInfLocation(installProposalRequest.getChaincodeMetaInfLocation());

        FabricProposal.Proposal deploymentProposal = installProposalbuilder.build();
        return deploymentProposal;
    }

    private static FabricProposal.Proposal buildInstantiationProposal(
            FabricAccount account, InstantiateChaincodeRequest instantiateChaincodeRequest)
            throws Exception {

        instantiateChaincodeRequest.check(); // check has all params

        HFClient hfClient = HFClient.createNewInstance();
        hfClient.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());
        hfClient.setUserContext(account.getUser());
        Channel channel =
                hfClient.newChannel(instantiateChaincodeRequest.getChannelName()); // ChannelName

        org.hyperledger.fabric.sdk.transaction.TransactionContext transactionContext =
                new org.hyperledger.fabric.sdk.transaction.TransactionContext(
                        channel, account.getUser(), CryptoSuite.Factory.getCryptoSuite());

        ChaincodeID chaincodeID =
                ChaincodeID.newBuilder()
                        .setName(instantiateChaincodeRequest.getName())
                        .setVersion(instantiateChaincodeRequest.getVersion())
                        .setPath("chaincode")
                        .build(); // path default with generateTarGzInputStreamBytes function

        // transactionContext.setProposalWaitTime(
        // FabricStubConfigParser.DEFAULT_DEPLOY_WAIT_TIME); // proposal wait time
        InstantiateProposalBuilder instantiateProposalbuilder =
                InstantiateProposalBuilder.newBuilder();
        instantiateProposalbuilder.context(transactionContext);
        instantiateProposalbuilder.argss(instantiateChaincodeRequest.getArgss()); // argss
        instantiateProposalbuilder.chaincodeName(chaincodeID.getName()); // name
        instantiateProposalbuilder.chaincodeType(
                instantiateChaincodeRequest.getChaincodeLanguageType()); // language
        instantiateProposalbuilder.chaincodePath(chaincodeID.getPath());
        instantiateProposalbuilder.chaincodeVersion(chaincodeID.getVersion()); // version
        instantiateProposalbuilder.chaincodEndorsementPolicy(
                instantiateChaincodeRequest.getEndorsementPolicyType()); // policy
        // instantiateProposalbuilder.chaincodeCollectionConfiguration(instantiateProposalRequest.getChaincodeCollectionConfiguration());
        instantiateProposalbuilder.setTransientMap(instantiateChaincodeRequest.getTransientMap());

        FabricProposal.Proposal instantiateProposal = instantiateProposalbuilder.build();
        return instantiateProposal;
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

    public static byte[] encode(TransactionContext<TransactionRequest> request) throws Exception {
        if (!request.getAccount().getType().equals(FabricType.Account.FABRIC_ACCOUNT)) {
            throw new Exception(
                    "Illegal account type for fabric call: " + request.getAccount().getType());
        }

        FabricAccount account = (FabricAccount) request.getAccount();
        ResourceInfo resourceInfo = request.getResourceInfo();
        TransactionRequest transactionRequest = request.getData();

        // generate proposal
        FabricProposal.Proposal proposal = buildProposal(account, resourceInfo, transactionRequest);
        return signProposal(account, proposal);
    }

    public static TransactionContext<TransactionRequest> decode(byte[] signedProposalBytes)
            throws Exception {
        String identity = getIdentityFromSignedProposal(signedProposalBytes);
        Account simpleAccount =
                new Account() {
                    @Override
                    public String getName() {
                        return "Unknown";
                    }

                    @Override
                    public String getType() {
                        return FabricType.Account.FABRIC_ACCOUNT;
                    }

                    @Override
                    public String getIdentity() {
                        return identity;
                    }
                };

        TransactionRequest transactionRequest =
                getTransactionRequestFromSignedProposalBytes(signedProposalBytes);

        TransactionContext<TransactionRequest> transactionContext =
                new TransactionContext<>(transactionRequest, simpleAccount, null, null, null);

        return transactionContext;
    }

    private static String getIdentityFromSignedProposal(byte[] signedProposalBytes)
            throws Exception {
        FabricProposal.SignedProposal signedProposal =
                FabricProposal.SignedProposal.parseFrom(signedProposalBytes);
        FabricProposal.Proposal proposal =
                FabricProposal.Proposal.parseFrom(signedProposal.getProposalBytes());
        Common.Header header = Common.Header.parseFrom(proposal.getHeader());
        Common.SignatureHeader signatureHeader =
                Common.SignatureHeader.parseFrom(header.getSignatureHeader());
        Identities.SerializedIdentity serializedIdentity =
                Identities.SerializedIdentity.parseFrom(signatureHeader.getCreator());

        return serializedIdentity.toByteString().toStringUtf8();
    }

    public static TransactionRequest getTransactionRequestFromSignedProposalBytes(
            byte[] signedProposalBytes) throws Exception {
        FabricProposal.SignedProposal signedProposal =
                FabricProposal.SignedProposal.parseFrom(signedProposalBytes);
        FabricProposal.Proposal proposal =
                FabricProposal.Proposal.parseFrom(signedProposal.getProposalBytes());
        FabricProposal.ChaincodeProposalPayload payload =
                FabricProposal.ChaincodeProposalPayload.parseFrom(proposal.getPayload());
        Chaincode.ChaincodeInvocationSpec chaincodeInvocationSpec =
                Chaincode.ChaincodeInvocationSpec.parseFrom(payload.getInput());
        Chaincode.ChaincodeSpec chaincodeSpec = chaincodeInvocationSpec.getChaincodeSpec();
        Chaincode.ChaincodeInput chaincodeInput = chaincodeSpec.getInput();
        List<ByteString> allArgs = chaincodeInput.getArgsList();

        boolean isMethod = true;
        String method = new String();
        List<String> args = new LinkedList<>();
        for (ByteString byteString : allArgs) {
            if (isMethod) {
                // First is method, other is args
                method = byteString.toStringUtf8();
                isMethod = false;
            } else {
                args.add(new String(byteString.toByteArray(), Charset.forName("UTF-8")));
            }
        }

        TransactionRequest request = new TransactionRequest();
        request.setMethod(method);

        request.setArgs(args.toArray(new String[] {}));

        return request;
    }

    public static String getTxIDFromEnvelopeBytes(byte[] envelopeBytes) throws Exception {

        Common.Envelope envelope = Common.Envelope.parseFrom(envelopeBytes);

        Common.Payload payload = Common.Payload.parseFrom(envelope.getPayload().toByteArray());

        Common.ChannelHeader channelHeader =
                Common.ChannelHeader.parseFrom(payload.getHeader().getChannelHeader());

        return channelHeader.getTxId();
    }
}
