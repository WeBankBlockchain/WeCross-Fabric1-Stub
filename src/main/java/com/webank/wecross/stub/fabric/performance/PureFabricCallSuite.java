package com.webank.wecross.stub.fabric.performance;

import com.webank.wecross.stub.fabric.EndorsementPolicyAnalyzer;
import com.webank.wecross.stub.fabric.FabricConnection;
import com.webank.wecross.stub.fabric.FabricStubFactory;
import java.util.Collection;
import org.hyperledger.fabric.sdk.ChaincodeID;
import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.HFClient;
import org.hyperledger.fabric.sdk.Peer;
import org.hyperledger.fabric.sdk.ProposalResponse;
import org.hyperledger.fabric.sdk.QueryByChaincodeRequest;

public class PureFabricCallSuite implements PerformanceSuite {
    private Channel channel;
    private HFClient hfClient;
    private Collection<Peer> peers;

    public PureFabricCallSuite(String chainPath) throws Exception {
        FabricStubFactory fabricStubFactory = new FabricStubFactory();
        FabricConnection fabricConnection =
                (FabricConnection) fabricStubFactory.newConnection(chainPath);

        this.channel = fabricConnection.getChannel();

        if (!fabricConnection.getChaincodeMap().containsKey("chaincode/sacc")) {
            throw new Exception("Resource sacc has not been deployed!");
        }

        this.hfClient = fabricConnection.getHfClient();

        this.peers = fabricConnection.getChaincodeMap().get("chaincode/sacc").getEndorsers();

        queryOnce();
    }

    @Override
    public String getName() {
        return "Pure Fabric Call Suite";
    }

    @Override
    public void call(PerformanceSuiteCallback callback) {
        try {
            Collection<ProposalResponse> responses = queryOnce();
            EndorsementPolicyAnalyzer analyzer = new EndorsementPolicyAnalyzer(responses);

            if (analyzer.allSuccess()) {
                callback.onSuccess("Success");
            } else {
                callback.onFailed("Failed: " + analyzer.info());
            }

        } catch (Exception e) {
            callback.onFailed("sacc query failed: " + e);
        }
    }

    private Collection<ProposalResponse> queryOnce() throws Exception {
        QueryByChaincodeRequest request = hfClient.newQueryProposalRequest();
        String cc = "chaincode/sacc";
        ChaincodeID ccid = ChaincodeID.newBuilder().setName(cc).build();
        request.setChaincodeID(ccid);
        request.setFcn("query");
        request.setArgs("a");
        request.setProposalWaitTime(3000);

        Collection<ProposalResponse> responses = channel.queryByChaincode(request, peers);
        return responses;
    }
}
