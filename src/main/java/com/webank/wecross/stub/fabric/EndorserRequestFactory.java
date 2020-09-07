package com.webank.wecross.stub.fabric;

import com.google.protobuf.ByteString;
import com.webank.wecross.account.FabricAccount;
import com.webank.wecross.common.FabricType;
import com.webank.wecross.stub.Request;
import com.webank.wecross.stub.ResourceInfo;
import com.webank.wecross.stub.TransactionContext;
import com.webank.wecross.stub.TransactionRequest;
import com.webank.wecross.stub.fabric.FabricCustomCommand.InstallChaincodeRequest;
import com.webank.wecross.stub.fabric.FabricCustomCommand.InstantiateChaincodeRequest;
import com.webank.wecross.stub.fabric.FabricCustomCommand.UpgradeChaincodeRequest;
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
import org.hyperledger.fabric.sdk.transaction.UpgradeProposalBuilder;

public class EndorserRequestFactory {

    public static byte[] buildProposalRequestBytes(
            TransactionContext transactionContext, TransactionRequest transactionRequest)
            throws Exception {
        if (!transactionContext.getAccount().getType().equals(FabricType.Account.FABRIC_ACCOUNT)) {
            throw new Exception(
                    "Illegal account type for fabric call: "
                            + transactionContext.getAccount().getType());
        }

        FabricAccount account = (FabricAccount) transactionContext.getAccount();
        ResourceInfo resourceInfo = transactionContext.getResourceInfo();

        // generate proposal
        FabricProposal.Proposal proposal = buildProposal(account, resourceInfo, transactionRequest);

        byte[] signedProposalBytes = signProposal(account, proposal);
        return signedProposalBytes;
    }

    public static Request buildInstallProposalRequest(
            TransactionContext transactionContext, InstallChaincodeRequest installChaincodeRequest)
            throws Exception {
        if (!transactionContext.getAccount().getType().equals(FabricType.Account.FABRIC_ACCOUNT)) {
            throw new Exception(
                    "Illegal account type for fabric call: "
                            + transactionContext.getAccount().getType());
        }

        FabricAccount account = (FabricAccount) transactionContext.getAccount(); // Account

        // generate proposal
        FabricProposal.Proposal proposal = buildInstallProposal(account, installChaincodeRequest);
        byte[] signedProposalBytes = signProposal(account, proposal);

        TransactionParams transactionParams =
                new TransactionParams(new TransactionRequest(), signedProposalBytes, false);
        transactionParams.setOrgNames(
                new String[] {
                    installChaincodeRequest.getOrgName()
                }); // only 1 in each install request

        Request endorserRequest = new Request();
        endorserRequest.setData(transactionParams.toBytes());
        return endorserRequest;
    }

    public static Request buildInstantiateProposalRequest(
            TransactionContext transactionContext,
            InstantiateChaincodeRequest instantiateChaincodeRequest)
            throws Exception {
        if (!transactionContext.getAccount().getType().equals(FabricType.Account.FABRIC_ACCOUNT)) {
            throw new Exception(
                    "Illegal account type for fabric call: "
                            + transactionContext.getAccount().getType());
        }

        FabricAccount account = (FabricAccount) transactionContext.getAccount(); // Account

        // generate proposal
        FabricProposal.Proposal proposal =
                buildInstantiationProposal(account, instantiateChaincodeRequest);
        byte[] signedProposalBytes = signProposal(account, proposal);

        TransactionParams transactionParams =
                new TransactionParams(new TransactionRequest(), signedProposalBytes, false);
        transactionParams.setOrgNames(instantiateChaincodeRequest.getOrgNames());

        Request endorserRequest = new Request();
        endorserRequest.setData(transactionParams.toBytes());

        return endorserRequest;
    }

    public static Request buildUpgradeProposalRequest(
            TransactionContext transactionContext, UpgradeChaincodeRequest upgradeChaincodeRequest)
            throws Exception {
        if (!transactionContext.getAccount().getType().equals(FabricType.Account.FABRIC_ACCOUNT)) {
            throw new Exception(
                    "Illegal account type for fabric call: "
                            + transactionContext.getAccount().getType());
        }

        FabricAccount account = (FabricAccount) transactionContext.getAccount(); // Account

        // generate proposal
        FabricProposal.Proposal proposal = buildUpgradeProposal(account, upgradeChaincodeRequest);
        byte[] signedProposalBytes = signProposal(account, proposal);

        TransactionParams transactionParams =
                new TransactionParams(new TransactionRequest(), signedProposalBytes, false);
        transactionParams.setOrgNames(upgradeChaincodeRequest.getOrgNames());

        Request endorserRequest = new Request();
        endorserRequest.setData(transactionParams.toBytes());

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

        if (installChaincodeRequest
                .getChaincodeLanguageType()
                .equals(org.hyperledger.fabric.sdk.TransactionRequest.Type.GO_LANG)) {
            installProposalbuilder.chaincodePath(chaincodeID.getPath()); // path
        }

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
        if (instantiateChaincodeRequest
                .getChaincodeLanguageType()
                .equals(org.hyperledger.fabric.sdk.TransactionRequest.Type.GO_LANG)) {
            instantiateProposalbuilder.chaincodePath(chaincodeID.getPath());
        }
        instantiateProposalbuilder.chaincodeVersion(chaincodeID.getVersion()); // version
        instantiateProposalbuilder.chaincodEndorsementPolicy(
                instantiateChaincodeRequest.getEndorsementPolicyType()); // policy
        // instantiateProposalbuilder.chaincodeCollectionConfiguration(instantiateProposalRequest.getChaincodeCollectionConfiguration());
        instantiateProposalbuilder.setTransientMap(instantiateChaincodeRequest.getTransientMap());

        FabricProposal.Proposal instantiateProposal = instantiateProposalbuilder.build();
        return instantiateProposal;
    }

    private static FabricProposal.Proposal buildUpgradeProposal(
            FabricAccount account, UpgradeChaincodeRequest upgradeChaincodeRequest)
            throws Exception {

        upgradeChaincodeRequest.check(); // check has all params

        HFClient hfClient = HFClient.createNewInstance();
        hfClient.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());
        hfClient.setUserContext(account.getUser());
        Channel channel =
                hfClient.newChannel(upgradeChaincodeRequest.getChannelName()); // ChannelName

        org.hyperledger.fabric.sdk.transaction.TransactionContext transactionContext =
                new org.hyperledger.fabric.sdk.transaction.TransactionContext(
                        channel, account.getUser(), CryptoSuite.Factory.getCryptoSuite());

        ChaincodeID chaincodeID =
                ChaincodeID.newBuilder()
                        .setName(upgradeChaincodeRequest.getName())
                        .setVersion(upgradeChaincodeRequest.getVersion())
                        .setPath("chaincode")
                        .build(); // path default with generateTarGzInputStreamBytes function

        // transactionContext.setProposalWaitTime(upgradeProposalRequest.getProposalWaitTime());
        UpgradeProposalBuilder upgradeProposalBuilder = UpgradeProposalBuilder.newBuilder();
        upgradeProposalBuilder.context(transactionContext);
        upgradeProposalBuilder.argss(upgradeChaincodeRequest.getArgss());
        upgradeProposalBuilder.chaincodeName(chaincodeID.getName());

        upgradeProposalBuilder.chaincodeType(
                upgradeChaincodeRequest.getChaincodeLanguageType()); // language
        if (upgradeChaincodeRequest
                .getChaincodeLanguageType()
                .equals(org.hyperledger.fabric.sdk.TransactionRequest.Type.GO_LANG)) {
            upgradeProposalBuilder.chaincodePath(chaincodeID.getPath());
        }
        upgradeProposalBuilder.chaincodeVersion(chaincodeID.getVersion());
        upgradeProposalBuilder.chaincodEndorsementPolicy(
                upgradeChaincodeRequest.getEndorsementPolicyType());
        // upgradeProposalBuilder.chaincodeCollectionConfiguration(upgradeProposalRequest.getChaincodeCollectionConfiguration());

        FabricProposal.Proposal upgradeProposal = upgradeProposalBuilder.build();
        return upgradeProposal;
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

    public static byte[] encode(
            TransactionContext transactionContext, TransactionRequest transactionRequest)
            throws Exception {
        if (!transactionContext.getAccount().getType().equals(FabricType.Account.FABRIC_ACCOUNT)) {
            throw new Exception(
                    "Illegal account type for fabric call: "
                            + transactionContext.getAccount().getType());
        }

        FabricAccount account = (FabricAccount) transactionContext.getAccount();
        ResourceInfo resourceInfo = transactionContext.getResourceInfo();

        // generate proposal
        FabricProposal.Proposal proposal = buildProposal(account, resourceInfo, transactionRequest);
        return signProposal(account, proposal);
    }

    public static TransactionRequest decode(byte[] signedProposalBytes) throws Exception {
        return getTransactionRequestFromSignedProposalBytes(signedProposalBytes);
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
