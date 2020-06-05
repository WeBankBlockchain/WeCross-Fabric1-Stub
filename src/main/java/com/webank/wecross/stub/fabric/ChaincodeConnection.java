package com.webank.wecross.stub.fabric;

import com.google.protobuf.ByteString;
import com.webank.wecross.common.FabricType;
import com.webank.wecross.stub.Request;
import com.webank.wecross.stub.ResourceInfo;
import com.webank.wecross.stub.Response;
import com.webank.wecross.utils.core.HashUtils;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timer;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import org.hyperledger.fabric.protos.common.Common;
import org.hyperledger.fabric.protos.msp.Identities;
import org.hyperledger.fabric.protos.peer.FabricProposal;
import org.hyperledger.fabric.sdk.ChaincodeID;
import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.HFClient;
import org.hyperledger.fabric.sdk.Peer;
import org.hyperledger.fabric.sdk.ProposalResponse;
import org.hyperledger.fabric.sdk.User;
import org.hyperledger.fabric.sdk.exception.CryptoException;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric.sdk.helper.Utils;
import org.hyperledger.fabric.sdk.security.CryptoSuite;
import org.hyperledger.fabric.sdk.transaction.TransactionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChaincodeConnection {
    private Logger logger = LoggerFactory.getLogger(ChaincodeConnection.class);

    private String name;
    private String type;
    private String chainCodeName;
    private String chainLanguage;
    private ChaincodeID chaincodeID;
    private long proposalWaitTime;
    private org.hyperledger.fabric.sdk.TransactionRequest.Type chainCodeType;

    private HFClient hfClient;
    private Channel channel;
    private Collection<Peer> endorsers;

    private FabricInnerFunction fabricInnerFunction;

    private Timer timeoutHandler;

    public ChaincodeConnection(
            HFClient hfClient,
            Map<String, Peer> peersMap,
            Channel channel,
            FabricStubConfigParser.Resources.Resource resourceConfig)
            throws Exception {
        this.name = resourceConfig.getName();
        this.type = resourceConfig.getType();
        this.chainCodeName = resourceConfig.getChainCodeName();
        this.chainLanguage = resourceConfig.getChainLanguage();
        this.chaincodeID = ChaincodeID.newBuilder().setName(this.chainCodeName).build();
        this.proposalWaitTime = resourceConfig.getProposalWaitTime();

        if (resourceConfig.getChainLanguage().toLowerCase().equals("go")) {
            this.chainCodeType = org.hyperledger.fabric.sdk.TransactionRequest.Type.GO_LANG;
        } else if (resourceConfig.getChainLanguage().toLowerCase().equals("java")) {
            this.chainCodeType = org.hyperledger.fabric.sdk.TransactionRequest.Type.JAVA;
        } else if (resourceConfig.getChainLanguage().toLowerCase().equals("node")) {
            this.chainCodeType = org.hyperledger.fabric.sdk.TransactionRequest.Type.NODE;
        } else {
            String errorMessage =
                    "\"chainLanguage\" in [[resource]] not support chaincode language "
                            + resourceConfig.getChainLanguage();
            throw new Exception(errorMessage);
        }

        this.hfClient = hfClient;
        this.channel = channel;

        this.endorsers = new LinkedHashSet<>();
        for (String endorserName : resourceConfig.getPeers()) {
            Peer endorser = peersMap.get(endorserName);
            if (endorser == null) {
                logger.warn("Could not found endorser " + endorserName + " in peersMap");
                continue;
            }

            endorsers.add(endorser);
        }

        this.fabricInnerFunction = new FabricInnerFunction(channel);

        this.timeoutHandler = new HashedWheelTimer();
    }

    public ResourceInfo getResourceInfo() {
        ResourceInfo resourceInfo = new ResourceInfo();
        resourceInfo.setName(name);
        resourceInfo.setStubType(FabricType.STUB_NAME);

        resourceInfo.setProperties(
                ResourceInfoProperty.build()
                        .channelName(channel.getName())
                        .chainCodeName(chainCodeName)
                        .chainCodeType(chainCodeType)
                        .proposalWaitTime(proposalWaitTime)
                        .toMap());

        resourceInfo.setChecksum(HashUtils.sha256String(chainCodeName));

        return resourceInfo;
    }

    public Response call(Request request) {
        if (request.getType() != FabricType.ConnectionMessage.FABRIC_CALL) {
            return FabricConnectionResponse.build()
                    .errorCode(FabricType.TransactionResponseStatus.ILLEGAL_REQUEST_TYPE)
                    .errorMessage("Illegal request type: " + request.getType());
        }

        FabricConnectionResponse response;
        try {
            Collection<ProposalResponse> proposalResponses = queryEndorser(request);
            EndorsementPolicyAnalyzer analyzer = new EndorsementPolicyAnalyzer(proposalResponses);

            if (analyzer.hasSuccess()) {
                response =
                        FabricConnectionResponse.build()
                                .errorCode(FabricType.TransactionResponseStatus.SUCCESS)
                                .errorMessage(analyzer.info())
                                .data(analyzer.getPayload());
            } else {
                response =
                        FabricConnectionResponse.build()
                                .errorCode(
                                        FabricType.TransactionResponseStatus
                                                .FABRIC_INVOKE_CHAINCODE_FAILED)
                                .errorMessage("Query endorser failed: " + analyzer.info());
            }
        } catch (Exception e) {
            response =
                    FabricConnectionResponse.build()
                            .errorCode(
                                    FabricType.TransactionResponseStatus
                                            .FABRIC_INVOKE_CHAINCODE_FAILED)
                            .errorMessage("Query endorser exception: " + e);
        }
        return response;
    }

    public Response sendTransactionEndorser(Request request) {
        if (request.getType() != FabricType.ConnectionMessage.FABRIC_SENDTRANSACTION_ENDORSER) {
            return FabricConnectionResponse.build()
                    .errorCode(FabricType.TransactionResponseStatus.ILLEGAL_REQUEST_TYPE)
                    .errorMessage("Illegal request type: " + request.getType());
        }

        FabricConnectionResponse response;
        try {
            Collection<ProposalResponse> proposalResponses = queryEndorser(request);
            EndorsementPolicyAnalyzer analyzer = new EndorsementPolicyAnalyzer(proposalResponses);

            if (analyzer.allSuccess()) { // All success endorsement policy, TODO: pull policy
                byte[] ordererPayloadToSign =
                        FabricInnerProposalResponsesEncoder.encode(proposalResponses);
                response =
                        FabricConnectionResponse.build()
                                .errorCode(FabricType.TransactionResponseStatus.SUCCESS)
                                .errorMessage(analyzer.info())
                                .data(ordererPayloadToSign);
            } else {
                response =
                        FabricConnectionResponse.build()
                                .errorCode(
                                        FabricType.TransactionResponseStatus
                                                .FABRIC_INVOKE_CHAINCODE_FAILED)
                                .errorMessage("Query endorser failed: " + analyzer.info());
            }
        } catch (Exception e) {
            response =
                    FabricConnectionResponse.build()
                            .errorCode(
                                    FabricType.TransactionResponseStatus
                                            .FABRIC_INVOKE_CHAINCODE_FAILED)
                            .errorMessage("Query endorser exception: " + e);
        }
        return response;
    }

    public Collection<ProposalResponse> queryEndorser(Request request) throws Exception {
        FabricProposal.SignedProposal sp =
                FabricProposal.SignedProposal.parseFrom(request.getData());
        TransactionContext transactionContext = getTransactionContext(sp);
        Collection<ProposalResponse> endorserResponses =
                fabricInnerFunction.sendProposalToPeers(endorsers, sp, transactionContext);
        return endorserResponses;
    }

    private TransactionContext getTransactionContext(FabricProposal.SignedProposal signedProposal)
            throws Exception {
        User userContext = hfClient.getUserContext();
        User.userContextCheck(userContext);

        String txID = recoverTxID(signedProposal);

        TransactionContext transactionContext =
                new TransactionContextMask(txID, channel, userContext, hfClient.getCryptoSuite());

        return transactionContext;
    }

    private String recoverTxID(FabricProposal.SignedProposal signedProposal) throws Exception {
        FabricProposal.Proposal proposal =
                FabricProposal.Proposal.parseFrom(signedProposal.getProposalBytes());

        Common.Header header = Common.Header.parseFrom(proposal.getHeader());

        Common.SignatureHeader signatureHeader =
                Common.SignatureHeader.parseFrom(header.getSignatureHeader());

        Identities.SerializedIdentity serializedIdentity =
                Identities.SerializedIdentity.parseFrom(signatureHeader.getCreator());

        ByteString no = signatureHeader.getNonce();

        ByteString comp = no.concat(serializedIdentity.toByteString());

        byte[] txh = CryptoSuite.Factory.getCryptoSuite().hash(comp.toByteArray());

        //    txID = Hex.encodeHexString(txh);
        String txID = new String(Utils.toHexString(txh));

        return txID;
    }

    // Only for mask the txID
    public static class TransactionContextMask extends TransactionContext {
        private String txIDMask;
        private byte[] signMask = null;

        public TransactionContextMask(
                String txID, Channel channel, User user, CryptoSuite cryptoPrimitives) {
            super(channel, user, cryptoPrimitives);
            this.txIDMask = txID;
        }

        @Override
        public byte[] sign(byte[] b) throws CryptoException, InvalidArgumentException {
            if (signMask != null) {
                return signMask;
            }
            return super.sign(b);
        }

        @Override
        public String getTxID() {
            return txIDMask;
        }

        public void setSignMask(byte[] signMask) {
            this.signMask = signMask;
        }
    }

    public Channel getChannel() {
        return channel;
    }

    public HFClient getHfClient() {
        return hfClient;
    }

    public Collection<Peer> getEndorsers() {
        return endorsers;
    }
}
