package com.webank.wecross.stub.fabric;

import static com.webank.wecross.utils.FabricUtils.bytesToLong;
import static com.webank.wecross.utils.FabricUtils.longToBytes;
import static java.lang.String.format;

import com.google.protobuf.ByteString;
import com.webank.wecross.common.FabricType;
import com.webank.wecross.stub.Connection;
import com.webank.wecross.stub.Request;
import com.webank.wecross.stub.ResourceInfo;
import com.webank.wecross.stub.Response;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import io.netty.util.TimerTask;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.hyperledger.fabric.protos.common.Common;
import org.hyperledger.fabric.protos.msp.Identities;
import org.hyperledger.fabric.protos.orderer.Ab;
import org.hyperledger.fabric.protos.peer.FabricProposal;
import org.hyperledger.fabric.sdk.BlockEvent;
import org.hyperledger.fabric.sdk.BlockInfo;
import org.hyperledger.fabric.sdk.ChaincodeEndorsementPolicy;
import org.hyperledger.fabric.sdk.ChaincodeID;
import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.EventHub;
import org.hyperledger.fabric.sdk.HFClient;
import org.hyperledger.fabric.sdk.InstantiateProposalRequest;
import org.hyperledger.fabric.sdk.Orderer;
import org.hyperledger.fabric.sdk.Peer;
import org.hyperledger.fabric.sdk.ProposalResponse;
import org.hyperledger.fabric.sdk.TransactionInfo;
import org.hyperledger.fabric.sdk.User;
import org.hyperledger.fabric.sdk.exception.CryptoException;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric.sdk.helper.Utils;
import org.hyperledger.fabric.sdk.security.CryptoSuite;
import org.hyperledger.fabric.sdk.transaction.TransactionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public class FabricConnection implements Connection {
    private Logger logger = LoggerFactory.getLogger(FabricConnection.class);
    private HFClient hfClient;
    private Channel channel;
    private String proxyChaincodeName;
    private Map<String, Peer> peersMap;
    private ChaincodeResourceManager chaincodeResourceManager;
    private FabricInnerFunction fabricInnerFunction;
    private Timer timeoutHandler;
    private long latestBlockNumber = 0;
    private ThreadPoolTaskExecutor threadPool;
    private String blockListenerHandler = new String();

    public FabricConnection(
            HFClient hfClient,
            Channel channel,
            Map<String, ChaincodeResource> chaincodeMap,
            Map<String, Peer> peersMap,
            String proxyChaincodeName) {
        this.hfClient = hfClient;
        this.channel = channel;
        this.chaincodeResourceManager =
                new ChaincodeResourceManager(
                        hfClient, channel, peersMap, chaincodeMap, proxyChaincodeName);
        this.peersMap = peersMap;
        this.proxyChaincodeName = proxyChaincodeName;

        this.fabricInnerFunction = new FabricInnerFunction(channel);

        this.timeoutHandler = new HashedWheelTimer();

        this.threadPool = new ThreadPoolTaskExecutor();
        this.threadPool.setCorePoolSize(16);
        this.threadPool.setMaxPoolSize(16);
        this.threadPool.setQueueCapacity(10000);
    }

    public void start() throws Exception {

        this.blockListenerHandler =
                channel.registerBlockListener(
                        blockEvent -> {
                            long currentBlockNumber = blockEvent.getBlockNumber();
                            if (this.latestBlockNumber < currentBlockNumber) {
                                this.latestBlockNumber = currentBlockNumber;
                            }
                        });

        channel.initialize();

        threadPool.initialize();

        chaincodeResourceManager.start();
    }

    @Override
    public Response send(Request request) {
        switch (request.getType()) {
            case FabricType.ConnectionMessage.FABRIC_CALL:
                return handleCall(request);

            case FabricType.ConnectionMessage.FABRIC_SENDTRANSACTION_ENDORSER:
                return handleSendTransactionEndorser(request);

            case FabricType.ConnectionMessage.FABRIC_SENDTRANSACTION_ORDERER:
                return handleSendTransactionOrderer(request);

            case FabricType.ConnectionMessage.FABRIC_GET_BLOCK_NUMBER:
                return handleGetBlockNumber(request);

            case FabricType.ConnectionMessage.FABRIC_GET_BLOCK_HEADER:
                return handleGetBlockHeader(request);

            case FabricType.ConnectionMessage.FABRIC_GET_TRANSACTION:
                return handleGetTransaction(request);

            case FabricType.ConnectionMessage.FABRIC_SENDTRANSACTION_ORG_ENDORSER:
                return handleInstallChaincodeProposal(request);

            default:
                return FabricConnectionResponse.build()
                        .errorCode(FabricType.TransactionResponseStatus.ILLEGAL_REQUEST_TYPE)
                        .errorMessage("Illegal request type: " + request.getType());
        }
    }

    @Override
    public void asyncSend(Request request, Connection.Callback callback) {
        switch (request.getType()) {
            case FabricType.ConnectionMessage.FABRIC_CALL:
                handleAsyncCall(request, callback);
                break;

            case FabricType.ConnectionMessage.FABRIC_SENDTRANSACTION_ENDORSER:
                handleAsyncSendTransactionEndorser(request, callback);
                break;

            case FabricType.ConnectionMessage.FABRIC_SENDTRANSACTION_ORDERER:
                handleAsyncSendTransactionOrderer(request, callback);
                break;

            case FabricType.ConnectionMessage.FABRIC_SENDTRANSACTION_ORG_ENDORSER:
                handleAsyncInstallChaincodeProposal(request, callback);
                break;
            default:
                callback.onResponse(send(request));
        }
    }

    @Override
    public List<ResourceInfo> getResources() {
        return chaincodeResourceManager.getResourceInfoList();
    }

    public static class Properties {
        private String channelName;

        Properties() {}

        public static Properties builder() {
            return new Properties();
        }

        public Properties channelName(String channelName) {
            this.channelName = channelName;
            return this;
        }

        public Map<String, String> toMap() {
            Map<String, String> res = new HashMap<>();
            res.put("ChannelName", channelName);
            return res;
        }

        public static Properties parseFromMap(Map<String, String> map) {
            return builder().channelName(map.get("ChannelName"));
        }

        public String getChannelName() {
            return channelName;
        }
    }

    @Override
    public Map<String, String> getProperties() {
        return Properties.builder().channelName(this.channel.getName()).toMap();
    }

    @Override
    public void setConnectionEventHandler(ConnectionEventHandler eventHandler) {
        chaincodeResourceManager.setEventHandler(
                new ChaincodeResourceManager.EventHandler() {
                    @Override
                    public void onChange(List<ResourceInfo> resourceInfos) {
                        eventHandler.onResourcesChange(resourceInfos);
                    }
                });
    }

    private Response handleCall(Request request) {
        ChaincodeResource chaincodeResource =
                chaincodeResourceManager.getChaincodeResource(request.getResourceInfo().getName());
        if (chaincodeResource != null) {
            return call(request, chaincodeResource.getEndorsers());
        } else {
            return FabricConnectionResponse.build()
                    .errorCode(FabricType.TransactionResponseStatus.RESOURCE_NOT_FOUND)
                    .errorMessage(
                            "Resource not found, getResourceInfo: "
                                    + request.getResourceInfo().toString());
        }
    }

    private void handleAsyncCall(Request request, Connection.Callback callback) {
        // chaincode call is sync, use thread pool to simulate async for better performance
        threadPool.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        callback.onResponse(handleCall(request));
                    }
                });
    }

    private Response handleSendTransactionEndorser(Request request) {
        ChaincodeResource chaincodeResource =
                chaincodeResourceManager.getChaincodeResource(request.getResourceInfo().getName());
        if (chaincodeResource != null) {
            return sendTransactionEndorser(request, chaincodeResource.getEndorsers());
        } else {
            return FabricConnectionResponse.build()
                    .errorCode(FabricType.TransactionResponseStatus.RESOURCE_NOT_FOUND)
                    .errorMessage(
                            "Resource not found, name: " + request.getResourceInfo().getName());
        }
    }

    private void handleAsyncSendTransactionEndorser(Request request, Connection.Callback callback) {
        // chaincode call is sync, use thread pool to simulate async for better performance
        threadPool.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        callback.onResponse(handleSendTransactionEndorser(request));
                    }
                });
    }

    private Response handleSendTransactionOrderer(Request request) {
        CompletableFuture<Response> responseFuture = new CompletableFuture<>();

        handleAsyncSendTransactionOrderer(
                request,
                new Callback() {
                    @Override
                    public void onResponse(Response response) {
                        responseFuture.complete(response);
                    }
                });

        try {
            return responseFuture.get(10000, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            return FabricConnectionResponse.build()
                    .errorCode(FabricType.TransactionResponseStatus.INTERNAL_ERROR)
                    .errorMessage("handleSendTransactionOrderer exception: " + e.getMessage());
        }
    }

    private void handleAsyncSendTransactionOrderer(Request request, Connection.Callback callback) {

        asyncSendTransactionOrderer(
                request,
                new SendTransactionOrdererCallback() {
                    @Override
                    public void onResponse(Response response) {
                        callback.onResponse(response);
                    }
                });
    }

    private Response handleGetBlockNumber(Request request) {
        byte[] numberBytes = longToBytes(latestBlockNumber);

        return FabricConnectionResponse.build()
                .errorCode(FabricType.TransactionResponseStatus.SUCCESS)
                .errorMessage("Success")
                .data(numberBytes);
    }

    private Response handleGetBlockHeader(Request request) {

        Response response;
        try {
            long blockNumber = bytesToLong(request.getData());

            // Fabric Just return block
            BlockInfo blockInfo = channel.queryBlockByNumber(blockNumber);
            byte[] blockBytes = blockInfo.getBlock().toByteArray();

            response =
                    FabricConnectionResponse.build()
                            .errorCode(FabricType.TransactionResponseStatus.SUCCESS)
                            .errorMessage("Success")
                            .data(blockBytes);

        } catch (Exception e) {
            response =
                    FabricConnectionResponse.build()
                            .errorCode(FabricType.TransactionResponseStatus.INTERNAL_ERROR)
                            .errorMessage("Get block exception: " + e);
        }
        return response;
    }

    public Response handleGetTransaction(Request request) {
        Response response;
        try {
            String txID = new String(request.getData());
            TransactionInfo transactionInfo = channel.queryTransactionByID(txID);
            response =
                    FabricConnectionResponse.build()
                            .errorCode(FabricType.TransactionResponseStatus.SUCCESS)
                            .errorMessage("Success")
                            .data(transactionInfo.getEnvelope().toByteArray());

        } catch (Exception e) {
            response =
                    FabricConnectionResponse.build()
                            .errorCode(FabricType.TransactionResponseStatus.INTERNAL_ERROR)
                            .errorMessage("Get transaction exception: " + e);
        }
        return response;
    }

    private Response handleInstallChaincodeProposal(Request request) {
        FabricConnectionResponse response;
        try {
            String[] orgNames =
                    (String[])
                            request.getResourceInfo().getProperties().get(FabricType.ORG_NAME_DEF);

            Collection<String> orgSet = new HashSet<>(Arrays.asList(orgNames));

            Collection<Peer> orgPeers = new HashSet<>();
            for (Map.Entry<String, Peer> peerEntry : peersMap.entrySet()) {
                Peer peer = peerEntry.getValue();
                if (orgSet.contains(
                        (String) peer.getProperties().getProperty(FabricType.ORG_NAME_DEF))) {
                    logger.debug(
                            "Peer:{} of will install chaincode {}",
                            peerEntry.getKey(),
                            request.getResourceInfo().getName());
                    orgPeers.add(peer);
                }
            }

            Collection<ProposalResponse> proposalResponses = queryEndorser(request, orgPeers);
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
                                .errorMessage(
                                        "Install chaincode query to endorser failed: "
                                                + analyzer.info());
            }

        } catch (Exception e) {
            response =
                    FabricConnectionResponse.build()
                            .errorCode(
                                    FabricType.TransactionResponseStatus
                                            .FABRIC_INVOKE_CHAINCODE_FAILED)
                            .errorMessage("Install chaincode query to endorser exception: " + e);
        }
        return response;
    }

    private void handleAsyncInstallChaincodeProposal(
            Request request, Connection.Callback callback) {
        // send proposal is sync, use thread pool to simulate async for better performance
        threadPool.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        callback.onResponse(handleInstallChaincodeProposal(request));
                    }
                });
    }

    private Response call(Request request, Collection<Peer> endorsers) {
        if (request.getType() != FabricType.ConnectionMessage.FABRIC_CALL) {
            return FabricConnectionResponse.build()
                    .errorCode(FabricType.TransactionResponseStatus.ILLEGAL_REQUEST_TYPE)
                    .errorMessage("Illegal request type: " + request.getType());
        }

        FabricConnectionResponse response;
        try {
            Collection<ProposalResponse> proposalResponses = queryEndorser(request, endorsers);
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

    private Response sendTransactionEndorser(Request request, Collection<Peer> endorsers) {
        if (request.getType() != FabricType.ConnectionMessage.FABRIC_SENDTRANSACTION_ENDORSER) {
            return FabricConnectionResponse.build()
                    .errorCode(FabricType.TransactionResponseStatus.ILLEGAL_REQUEST_TYPE)
                    .errorMessage("Illegal request type: " + request.getType());
        }

        FabricConnectionResponse response;
        try {
            Collection<ProposalResponse> proposalResponses = queryEndorser(request, endorsers);
            EndorsementPolicyAnalyzer analyzer = new EndorsementPolicyAnalyzer(proposalResponses);

            if (analyzer.hasSuccess()) { // All success endorsement policy, TODO: pull policy
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

    private void asyncSendTransactionOrderer(
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

    public Collection<ProposalResponse> queryEndorser(Request request, Collection<Peer> endorsers)
            throws Exception {
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
        transactionContext.setProposalWaitTime(FabricStubConfigParser.DEFAULT_PROPOSAL_WAIT_TIME);

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

    private Response handleInstantiateChaincodeProposal(Request request) {
        FabricConnectionResponse response;
        try {
            InstantiateProposalRequest instantiateProposalRequest =
                    hfClient.newInstantiationProposalRequest();

            InstantiateChaincodeRequest instantiateChaincodeRequest =
                    InstantiateChaincodeRequest.parseFrom(request.getData());
            ChaincodeID chaincodeID =
                    ChaincodeID.newBuilder()
                            .setName(instantiateChaincodeRequest.getName())
                            .setVersion(instantiateChaincodeRequest.getVersion())
                            .build();

            instantiateProposalRequest.setChaincodeID(chaincodeID);
            instantiateProposalRequest.setChaincodeLanguage(
                    instantiateChaincodeRequest.getChaincodeLanguageType()); // language
            instantiateProposalRequest.setArgs(instantiateChaincodeRequest.getArgs()); // args
            instantiateProposalRequest.setFcn("init");

            instantiateProposalRequest.setTransientMap(
                    instantiateChaincodeRequest.getTransientMap()); // transientMap

            String policyString = instantiateChaincodeRequest.getEndorsementPolicy();
            ChaincodeEndorsementPolicy chaincodeEndorsementPolicy =
                    new ChaincodeEndorsementPolicy();
            chaincodeEndorsementPolicy.fromStream(
                    new ByteArrayInputStream(policyString.getBytes()));
            instantiateProposalRequest.setChaincodeEndorsementPolicy(
                    chaincodeEndorsementPolicy); // policy
            instantiateProposalRequest.setProposalWaitTime(
                    FabricStubConfigParser.DEFAULT_PROPOSAL_WAIT_TIME);

            Collection<ProposalResponse> proposalResponses =
                    this.channel.sendInstantiationProposal(
                            instantiateProposalRequest, getChannel().getPeers());
            EndorsementPolicyAnalyzer analyzer = new EndorsementPolicyAnalyzer(proposalResponses);

            if (analyzer.allSuccess()) {
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
                                .errorMessage(
                                        "Instantiate chaincode query to endorser failed: "
                                                + analyzer.info());
            }
        } catch (Exception e) {
            response =
                    FabricConnectionResponse.build()
                            .errorCode(
                                    FabricType.TransactionResponseStatus
                                            .FABRIC_INVOKE_CHAINCODE_FAILED)
                            .errorMessage(
                                    "Instantiate chaincode query to endorser exception: " + e);
        }
        return response;
    }

    private void handleAsyncInstantiateChaincodeProposal(
            Request request, Connection.Callback callback) {
        // send proposal is sync, use thread pool to simulate async for better performance
        threadPool.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        callback.onResponse(handleInstantiateChaincodeProposal(request));
                    }
                });
    }

    public Channel getChannel() {
        return this.channel;
    }

    public Map<String, ChaincodeResource> getChaincodeMap() {
        return chaincodeResourceManager.getChaincodeMap();
    }

    public void updateChaincodeMap() {
        chaincodeResourceManager.updateChaincodeMap();
        chaincodeResourceManager.dumpChaincodeMap();
    }

    public HFClient getHfClient() {
        return hfClient;
    }

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

    public Set<String> getAllPeerOrgNames() {
        Set<String> peerOrgNames = new HashSet<>();
        for (Peer peer : peersMap.values()) {
            peerOrgNames.add((String) peer.getProperties().getProperty(FabricType.ORG_NAME_DEF));
        }
        return peerOrgNames;
    }

    public Set<String> getProxyOrgNames(boolean updateBeforeGet) {
        if (updateBeforeGet) {
            updateChaincodeMap();
        }

        Set<String> resourceOrgNames = new HashSet<>();
        List<ResourceInfo> resourceInfos = getResources();
        for (ResourceInfo resourceInfo : resourceInfos) {

            if (!resourceInfo.getName().equals(this.proxyChaincodeName)) {
                continue; // Ignore other chaincode info
            }

            ArrayList<String> orgNames =
                    ResourceInfoProperty.parseFrom(resourceInfo.getProperties()).getOrgNames();
            for (String orgName : orgNames) {
                resourceOrgNames.add(orgName);
            }
        }
        return resourceOrgNames;
    }

    public boolean hasProxyDeployed2AllPeers() {
        Set<String> peerOrgNames = getAllPeerOrgNames();
        Set<String> resourceOrgNames = getProxyOrgNames(true);

        logger.info("peerOrgNames: " + peerOrgNames.toString());
        logger.info("resourceOrgNames: " + resourceOrgNames.toString());

        peerOrgNames.removeAll(resourceOrgNames);
        if (!peerOrgNames.isEmpty()) {
            String errorMsg = "WeCrossProxy has not been deployed to: " + peerOrgNames.toString();
            System.out.println(errorMsg);
            logger.info(errorMsg);
            return false;
        }
        return true;
    }
}
