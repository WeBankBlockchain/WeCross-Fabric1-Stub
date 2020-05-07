package com.webank.wecross.stub.fabric.performance;

import static com.webank.wecross.utils.FabricUtils.longToBytes;

import com.webank.wecross.stub.fabric.EndorsementPolicyAnalyzer;
import com.webank.wecross.stub.fabric.FabricConnection;
import com.webank.wecross.stub.fabric.FabricConnectionFactory;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.hyperledger.fabric.sdk.BlockEvent;
import org.hyperledger.fabric.sdk.ChaincodeID;
import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.HFClient;
import org.hyperledger.fabric.sdk.ProposalResponse;
import org.hyperledger.fabric.sdk.TransactionProposalRequest;

public class PureFabricSendTransactionSuite implements PerformanceSuite {

    private Channel channel;
    private HFClient hfClient;
    private ReadWriteLock lock = new ReentrantReadWriteLock();
    private SecureRandom randSeed = new SecureRandom();

    public PureFabricSendTransactionSuite(String chainPath) throws Exception {
        FabricConnection fabricConnection = FabricConnectionFactory.build(chainPath);
        fabricConnection.start();

        this.channel = fabricConnection.getChannel();

        if (!fabricConnection.getChaincodeMap().containsKey("abac")) {
            throw new Exception(
                    "Resource abac has not been config, please check chains/fabric/stub.toml");
        }

        this.hfClient = fabricConnection.getChaincodeMap().get("abac").getHfClient();

        sendTransactionOnce(null);
    }

    @Override
    public String getName() {
        return "Pure Fabric SendTransaction Suite";
    }

    @Override
    public void call(PerformanceSuiteCallback callback) {
        try {
            lock.writeLock().lock();
            sendTransactionOnce(callback);

        } catch (Exception e) {
            System.out.println("mycc query failed: " + e);
            callback.onFailed("mycc query failed: " + e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void sendTransactionOnce(PerformanceSuiteCallback callback) throws Exception {

        TransactionProposalRequest request = hfClient.newTransactionProposalRequest();
        String cc = "mycc";
        ChaincodeID ccid = ChaincodeID.newBuilder().setName(cc).build();
        request.setChaincodeID(ccid);
        request.setFcn("invoke");

        request.setArgs("a", "b", "1");
        request.setProposalWaitTime(3000);

        Collection<ProposalResponse> proposalResponse = channel.sendTransactionProposal(request);
        EndorsementPolicyAnalyzer analyzer = new EndorsementPolicyAnalyzer(proposalResponse);

        if (!analyzer.allSuccess()) {
            throw new Exception("Not all transactionProposal success");
        }

        if (callback != null) {
            BlockEvent.TransactionEvent event =
                    channel.sendTransaction(proposalResponse).get(20, TimeUnit.SECONDS);
            handleEvent(event, callback);
            /*.thenAcceptAsync(
            (transactionEvent) -> {
                handleEvent(transactionEvent, callback);
            });
            */
        } else {
            BlockEvent.TransactionEvent event =
                    channel.sendTransaction(proposalResponse).get(20, TimeUnit.SECONDS);
            if (!event.isValid()) {
                throw new Exception("Invoke failed");
            }
        }
    }

    public static void handleEvent(
            BlockEvent.TransactionEvent transactionEvent, PerformanceSuiteCallback callback) {
        if (transactionEvent.isValid()) {
            long blockNumber = transactionEvent.getBlockEvent().getBlockNumber();
            byte[] blockNumberBytes = longToBytes(blockNumber);
            callback.onSuccess(Arrays.toString(blockNumberBytes));
        } else {
            callback.onFailed("Failed");
        }
    }
}
