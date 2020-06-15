package com.webank.wecross.stub.fabric;

import static com.webank.wecross.utils.FabricUtils.longToBytes;
import static java.lang.String.format;

import com.google.protobuf.ByteString;
import com.webank.wecross.common.FabricType;
import com.webank.wecross.stub.Request;
import com.webank.wecross.stub.ResourceInfo;
import com.webank.wecross.stub.Response;
import com.webank.wecross.utils.core.HashUtils;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import io.netty.util.TimerTask;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.hyperledger.fabric.protos.common.Common;
import org.hyperledger.fabric.protos.msp.Identities;
import org.hyperledger.fabric.protos.orderer.Ab;
import org.hyperledger.fabric.protos.peer.FabricProposal;
import org.hyperledger.fabric.sdk.BlockEvent;
import org.hyperledger.fabric.sdk.ChaincodeID;
import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.EventHub;
import org.hyperledger.fabric.sdk.HFClient;
import org.hyperledger.fabric.sdk.Orderer;
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

    public void asyncSendTransactionOrderer(
            Request request, SendTransactionOrdererCallback callback) {
        if (request.getType() != FabricType.ConnectionMessage.FABRIC_SENDTRANSACTION_ORDERER) {
            callback.onResponseInternal(
                    FabricConnectionResponse.build()
                            .errorCode(FabricType.TransactionResponseStatus.ILLEGAL_REQUEST_TYPE)
                            .errorMessage("Illegal request type: " + request.getType()));
        }

        try {
            Common.Envelope envelope = Common.Envelope.parseFrom(request.getData());
            byte[] payload = envelope.getPayload().toByteArray();
            final String proposalTransactionID = getTxIDFromProposalBytes(payload);

            sendOrdererPayload(envelope, proposalTransactionID)
                    .thenApply(
                            new Function<
                                    BlockEvent.TransactionEvent, BlockEvent.TransactionEvent>() {
                                @Override
                                public BlockEvent.TransactionEvent apply(
                                        BlockEvent.TransactionEvent transactionEvent) {
                                    FabricConnectionResponse response;
                                    if (transactionEvent.isValid()) {
                                        long blockNumber =
                                                transactionEvent.getBlockEvent().getBlockNumber();
                                        byte[] blockNumberBytes = longToBytes(blockNumber);
                                        response =
                                                FabricConnectionResponse.build()
                                                        .errorCode(
                                                                FabricType.TransactionResponseStatus
                                                                        .SUCCESS)
                                                        .data(blockNumberBytes);
                                        // success is blockNumber

                                        logger.info(
                                                "Wait event success: "
                                                        + transactionEvent.getChannelId()
                                                        + " "
                                                        + transactionEvent.getTransactionID()
                                                        + " "
                                                        + transactionEvent.getType()
                                                        + " "
                                                        + transactionEvent.getValidationCode());
                                    } else {
                                        response =
                                                FabricConnectionResponse.build()
                                                        .errorCode(
                                                                FabricType.TransactionResponseStatus
                                                                        .FABRIC_EXECUTE_CHAINCODE_FAILED)
                                                        .data(
                                                                new byte[] {
                                                                    transactionEvent
                                                                            .getValidationCode()
                                                                });
                                        // error is TxValidationCode of fabric define in
                                        // Transaction.proto

                                        logger.info(
                                                "Wait event failed: "
                                                        + transactionEvent.getChannelId()
                                                        + " "
                                                        + transactionEvent.getTransactionID()
                                                        + " "
                                                        + transactionEvent.getType()
                                                        + " "
                                                        + transactionEvent.getValidationCode());
                                    }
                                    callback.onResponseInternal(response);

                                    return transactionEvent;
                                }
                            });

            callback.setTimeout(
                    timeoutHandler.newTimeout(
                            new TimerTask() {
                                @Override
                                public void run(Timeout timeout) throws Exception {
                                    callback.onTimeout();
                                }
                            },
                            5000,
                            TimeUnit.MILLISECONDS));

        } catch (Exception e) {
            FabricConnectionResponse response =
                    FabricConnectionResponse.build()
                            .errorCode(
                                    FabricType.TransactionResponseStatus
                                            .FABRIC_COMMIT_CHAINCODE_FAILED)
                            .errorMessage("Invoke orderer exception: " + e);
            callback.onResponseInternal(response);
        }
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

    private CompletableFuture<BlockEvent.TransactionEvent> sendOrdererPayload(
            Common.Envelope transactionEnvelope, String proposalTransactionID) throws Exception {
        // make certain we have our own copy

        final List<Orderer> shuffeledOrderers = new ArrayList<>(channel.getOrderers());
        final String name = channel.getName();
        Channel.TransactionOptions transactionOptions =
                Channel.TransactionOptions.createTransactionOptions()
                        .orderers(channel.getOrderers())
                        .userContext(hfClient.getUserContext());
        try {
            Collections.shuffle(shuffeledOrderers);

            logger.debug(
                    format(
                            "Channel %s sending transaction to orderer(s) with TxID %s ",
                            name, proposalTransactionID));
            boolean success = false;
            Exception lException =
                    null; // Save last exception to report to user .. others are just logged.

            CompletableFuture<BlockEvent.TransactionEvent> sret =
                    createTransactionEvent(proposalTransactionID);

            Ab.BroadcastResponse resp = null;
            Orderer failed = null;

            for (Orderer orderer : shuffeledOrderers) {
                if (failed != null) {
                    logger.warn(
                            format("Channel %s  %s failed. Now trying %s.", name, failed, orderer));
                }
                failed = orderer;
                try {

                    resp =
                            fabricInnerFunction.sendTransactionToOrderer(
                                    orderer, transactionEnvelope);

                    lException = null; // no longer last exception .. maybe just failed.
                    if (resp.getStatus() == Common.Status.SUCCESS) {
                        success = true;
                        break;
                    } else {
                        logger.warn(
                                format(
                                        "Channel %s %s failed. Status returned %s",
                                        name, orderer, dumpRespData(resp)));
                    }
                } catch (Exception e) {
                    String emsg =
                            format(
                                    "Channel %s unsuccessful sendTransaction to orderer %s (%s)",
                                    name, orderer.getName(), orderer.getUrl());
                    if (resp != null) {

                        emsg =
                                format(
                                        "Channel %s unsuccessful sendTransaction to orderer %s (%s).  %s",
                                        name,
                                        orderer.getName(),
                                        orderer.getUrl(),
                                        dumpRespData(resp));
                    }

                    logger.error(emsg);
                    lException = new Exception(emsg, e);
                }
            }

            if (success) {
                logger.debug(
                        format(
                                "Channel %s successful sent to Orderer transaction id: %s",
                                name, proposalTransactionID));

                // sret.complete(null); // just say we're done.

                return sret;
            } else {

                String emsg =
                        format(
                                "Channel %s failed to place transaction %s on Orderer. Cause: UNSUCCESSFUL. %s",
                                name, proposalTransactionID, dumpRespData(resp));

                CompletableFuture<BlockEvent.TransactionEvent> ret = new CompletableFuture<>();
                ret.completeExceptionally(
                        lException != null ? new Exception(emsg, lException) : new Exception(emsg));
                return ret;
            }
        } catch (Exception e) {

            CompletableFuture<BlockEvent.TransactionEvent> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    private CompletableFuture<BlockEvent.TransactionEvent> createTransactionEvent(
            String proposalTransactionID) {
        try {
            String name = channel.getName();
            Channel.NOfEvents nOfEvents = Channel.NOfEvents.createNofEvents();
            Collection<Peer> eventingPeers =
                    channel.getPeers(EnumSet.of(Peer.PeerRole.EVENT_SOURCE));
            boolean anyAdded = false;
            if (!eventingPeers.isEmpty()) {
                anyAdded = true;
                nOfEvents.addPeers(eventingPeers);
            }
            Collection<EventHub> eventHubs = channel.getEventHubs();
            if (!eventHubs.isEmpty()) {
                anyAdded = true;
                nOfEvents.addEventHubs(channel.getEventHubs());
            }

            if (!anyAdded) {
                nOfEvents = Channel.NOfEvents.createNoEvents();
            }

            final boolean replyonly =
                    nOfEvents == Channel.NOfEvents.nofNoEvents
                            || (channel.getEventHubs().isEmpty()
                                    && channel.getPeers(EnumSet.of(Peer.PeerRole.EVENT_SOURCE))
                                            .isEmpty());

            CompletableFuture<BlockEvent.TransactionEvent> sret;

            if (replyonly) { // If there are no eventhubs to complete the future, complete it
                // immediately but give no transaction event
                logger.debug(
                        format(
                                "Completing transaction id %s immediately no event hubs or peer eventing services found in channel %s.",
                                proposalTransactionID, name));
                sret = new CompletableFuture<>();
            } else {
                sret =
                        fabricInnerFunction.registerTxListener(
                                proposalTransactionID, nOfEvents, true);
            }

            return sret;
        } catch (Exception e) {

            CompletableFuture<BlockEvent.TransactionEvent> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    private String dumpRespData(Ab.BroadcastResponse resp) {

        StringBuilder respdata = new StringBuilder(400);
        if (resp != null) {
            Common.Status status = resp.getStatus();
            if (null != status) {
                respdata.append(status.name());
                respdata.append("-");
                respdata.append(status.getNumber());
            }

            String info = resp.getInfo();
            if (null != info && !info.isEmpty()) {
                if (respdata.length() > 0) {
                    respdata.append(", ");
                }
                respdata.append("Additional information: ").append(info);
            }
        }

        return respdata.toString();
    }

    private String getTxIDFromProposalBytes(byte[] proposalBytes) throws Exception {
        Common.Payload payload = Common.Payload.parseFrom(proposalBytes);

        Common.ChannelHeader channelHeader =
                Common.ChannelHeader.parseFrom(payload.getHeader().getChannelHeader());
        return channelHeader.getTxId();
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
