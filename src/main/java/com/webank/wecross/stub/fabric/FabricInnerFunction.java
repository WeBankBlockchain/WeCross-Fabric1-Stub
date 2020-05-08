package com.webank.wecross.stub.fabric;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import org.hyperledger.fabric.protos.common.Common;
import org.hyperledger.fabric.protos.orderer.Ab;
import org.hyperledger.fabric.protos.peer.FabricProposal;
import org.hyperledger.fabric.sdk.BlockEvent;
import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.Orderer;
import org.hyperledger.fabric.sdk.Peer;
import org.hyperledger.fabric.sdk.ProposalResponse;
import org.hyperledger.fabric.sdk.transaction.TransactionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FabricInnerFunction {
    private Logger logger = LoggerFactory.getLogger(FabricInnerFunction.class);

    // Fabric inner functions
    private Method methodSendProposalToPeers;
    private Method methodSendTransactionToOrderer;
    private Method methodRegisterTxListener;

    private Channel channel;

    public FabricInnerFunction(Channel channel) {
        this.channel = channel;
        try {
            // enable channel.sendProposalToPeers()
            methodSendProposalToPeers =
                    channel.getClass()
                            .getDeclaredMethod(
                                    "sendProposalToPeers",
                                    new Class[] {
                                        Collection.class,
                                        org.hyperledger.fabric.protos.peer.FabricProposal
                                                .SignedProposal.class,
                                        TransactionContext.class
                                    });
            methodSendProposalToPeers.setAccessible(true);

            // sendTransaction(Common.Envelope transaction)
            methodSendTransactionToOrderer =
                    Orderer.class.getDeclaredMethod(
                            "sendTransaction", new Class[] {Common.Envelope.class});
            methodSendTransactionToOrderer.setAccessible(true);

            // Channel.CompletableFuture<TransactionEvent> registerTxListener(String txid, NOfEvents
            // nOfEvents, boolean failFast)
            methodRegisterTxListener =
                    channel.getClass()
                            .getDeclaredMethod(
                                    "registerTxListener",
                                    new Class[] {
                                        String.class, Channel.NOfEvents.class, boolean.class
                                    });
            methodRegisterTxListener.setAccessible(true);
        } catch (Exception e) {
            logger.error("enableFabricInnerFunctions exception: " + e);
        }
    }

    public Collection<ProposalResponse> sendProposalToPeers(
            Collection<Peer> peers,
            FabricProposal.SignedProposal signedProposal,
            TransactionContext transactionContext)
            throws Exception {
        try {
            return (Collection<ProposalResponse>)
                    methodSendProposalToPeers.invoke(
                            (Object) channel, peers, signedProposal, transactionContext);
        } catch (InvocationTargetException e) {
            throw new Exception(e.getTargetException().getMessage());
        }
    }

    public Ab.BroadcastResponse sendTransactionToOrderer(
            Orderer orderer, Common.Envelope transactionEnvelope) throws Exception {
        try {
            return (Ab.BroadcastResponse)
                    methodSendTransactionToOrderer.invoke((Object) orderer, transactionEnvelope);
        } catch (InvocationTargetException e) {
            throw new Exception(e.getTargetException().getMessage());
        }
    }

    public CompletableFuture<BlockEvent.TransactionEvent> registerTxListener(
            String txid, Channel.NOfEvents nOfEvents, boolean failFast) throws Exception {
        try {
            return (CompletableFuture<BlockEvent.TransactionEvent>)
                    methodRegisterTxListener.invoke((Object) channel, txid, nOfEvents, true);
        } catch (InvocationTargetException e) {
            throw new Exception(e.getTargetException().getMessage());
        }
    }
}
