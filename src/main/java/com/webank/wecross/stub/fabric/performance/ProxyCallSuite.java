package com.webank.wecross.stub.fabric.performance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webank.wecross.stub.StubConstant;
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

public class ProxyCallSuite implements PerformanceSuite {
    private final String proxyName = StubConstant.PROXY_NAME;
    private static ObjectMapper objectMapper = new ObjectMapper();

    private Channel channel;
    private HFClient hfClient;
    private Collection<Peer> peers;

    public ProxyCallSuite(String chainPath) throws Exception {
        FabricStubFactory fabricStubFactory = new FabricStubFactory();
        FabricConnection fabricConnection =
                (FabricConnection) fabricStubFactory.newConnection(chainPath);

        this.channel = fabricConnection.getChannel();

        if (!fabricConnection.getChaincodeMap().containsKey(proxyName)) {
            throw new Exception("WeCross proxy chaincode has not been deployed");
        }

        if (!fabricConnection.getChaincodeMap().containsKey("chaincode/sacc")) {
            throw new Exception("Resource sacc has not been deployed");
        }

        this.hfClient = fabricConnection.getHfClient();

        this.peers = fabricConnection.getChaincodeMap().get("chaincode/sacc").getEndorsers();

        queryOnceByProxy();
    }

    @Override
    public String getName() {
        return "Proxy Call Performance Test Suite";
    }

    @Override
    public void call(PerformanceSuiteCallback callback) {
        try {
            Collection<ProposalResponse> responses = queryOnceByProxy();
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

    private Collection<ProposalResponse> queryOnceByProxy() throws Exception {
        QueryByChaincodeRequest request = hfClient.newQueryProposalRequest();
        String cc = proxyName;
        ChaincodeID ccid = ChaincodeID.newBuilder().setName(cc).build();
        request.setChaincodeID(ccid);
        request.setFcn("constantCall");
        request.setArgs(buildQueryProxyArgs());
        request.setProposalWaitTime(5000);

        Collection<ProposalResponse> responses = channel.queryByChaincode(request, peers);
        return responses;
    }

    private String[] buildQueryProxyArgs() throws Exception {
        class ChaincodeArgs {
            public String[] args;

            public ChaincodeArgs() {}

            public ChaincodeArgs(String[] args) {
                this.args = args;
            }
        }

        String[] chaincodeArgs = {"a"};
        String argsJsonString = objectMapper.writeValueAsString(new ChaincodeArgs(chaincodeArgs));
        String[] args = {"0", "payment.fabric.sacc", "query", argsJsonString};

        return args;
    }
}
