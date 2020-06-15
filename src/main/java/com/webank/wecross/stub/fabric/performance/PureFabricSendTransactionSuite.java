package com.webank.wecross.stub.fabric.performance;

import static com.webank.wecross.utils.FabricUtils.longToBytes;

import com.webank.wecross.stub.fabric.EndorsementPolicyAnalyzer;
import com.webank.wecross.stub.fabric.FabricConnection;
import com.webank.wecross.stub.fabric.FabricConnectionFactory;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.hyperledger.fabric.sdk.BlockEvent;
import org.hyperledger.fabric.sdk.ChaincodeID;
import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.HFClient;
import org.hyperledger.fabric.sdk.Peer;
import org.hyperledger.fabric.sdk.ProposalResponse;
import org.hyperledger.fabric.sdk.TransactionProposalRequest;

public class PureFabricSendTransactionSuite implements PerformanceSuite {

    private Channel channel;
    private HFClient hfClient;
    static final int BOUND = Integer.MAX_VALUE - 1;
    SecureRandom rand = new SecureRandom();
    Collection<Peer> endorsers;

    public PureFabricSendTransactionSuite(String chainPath) throws Exception {
        FabricConnection fabricConnection = FabricConnectionFactory.build(chainPath);
        fabricConnection.start();

        this.channel = fabricConnection.getChannel();

        if (!fabricConnection.getChaincodeMap().containsKey("sacc")) {
            throw new Exception(
                    "Resource sacc has not been config, please check chains/fabric/stub.toml\n"
                            + "And add this config:\n"
                            + "[[resources]]\n"
                            + "    # name cannot be repeated\n"
                            + "    name = 'sacc'\n"
                            + "    type = 'FABRIC_CONTRACT'\n"
                            + "    chainCodeName = 'sacc'\n"
                            + "    chainLanguage = 'go'\n"
                            + "    peers=['org1']\n");
        }

        this.hfClient = fabricConnection.getChaincodeMap().get("sacc").getHfClient();

        this.endorsers = fabricConnection.getChaincodeMap().get("sacc").getEndorsers();

        sendTransactionOnce(null);
    }

    @Override
    public String getName() {
        return "Pure Fabric SendTransaction Suite";
    }

    @Override
    public void call(PerformanceSuiteCallback callback) {
        try {
            sendTransactionOnce(callback);

        } catch (Exception e) {
            System.out.println("sacc query failed: " + e);
            callback.onFailed("sacc query failed: " + e);
        }
    }

    private void sendTransactionOnce(PerformanceSuiteCallback callback) throws Exception {

        TransactionProposalRequest request = hfClient.newTransactionProposalRequest();
        String cc = "sacc";
        ChaincodeID ccid = ChaincodeID.newBuilder().setName(cc).build();
        request.setChaincodeID(ccid);
        request.setFcn("set");

        String key = String.valueOf(rand.nextInt(BOUND));
        String value = String.valueOf(rand.nextInt(BOUND));

        request.setArgs(key, value);
        request.setProposalWaitTime(30000);

        Collection<ProposalResponse> proposalResponse =
                channel.sendTransactionProposal(request, endorsers);
        EndorsementPolicyAnalyzer analyzer = new EndorsementPolicyAnalyzer(proposalResponse);

        if (!analyzer.allSuccess()) {
            throw new Exception("Not all transactionProposal success");
        }

        if (callback != null) {
            channel.sendTransaction(proposalResponse)
                    .thenApply(
                            new Function<
                                    BlockEvent.TransactionEvent, BlockEvent.TransactionEvent>() {
                                @Override
                                public BlockEvent.TransactionEvent apply(
                                        BlockEvent.TransactionEvent transactionEvent) {
                                    handleEvent(transactionEvent, callback);
                                    return transactionEvent;
                                }
                            });

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
