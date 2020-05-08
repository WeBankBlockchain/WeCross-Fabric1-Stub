package com.webank.wecross.stub.fabric;

import com.google.protobuf.ByteString;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import org.hyperledger.fabric.sdk.ProposalResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EndorsementPolicyAnalyzer {
    private Logger logger = LoggerFactory.getLogger(EndorsementPolicyAnalyzer.class);

    private List<ProposalResponse> successResponse;
    private List<ProposalResponse> failedResponse;
    private Set<ByteString> payloadSet;

    public EndorsementPolicyAnalyzer(Collection<ProposalResponse> proposalResponses) {
        successResponse = new LinkedList<>();
        failedResponse = new LinkedList<>();
        payloadSet = new HashSet<>();

        for (ProposalResponse response : proposalResponses) {
            if (response.getStatus() == ProposalResponse.Status.SUCCESS) {
                ByteString payload = response.getProposalResponse().getResponse().getPayload();

                payloadSet.add(payload);

                logger.info(
                        "[√] Got success response from peer:{} , payload:{}",
                        response.getPeer().getName(),
                        payload.toStringUtf8());
                successResponse.add(response);
            } else {
                String status = response.getStatus().toString();
                logger.error(
                        "[×] Got failed response from peer:{}, status:{}, error message:{}",
                        response.getPeer().getName(),
                        status,
                        response.getMessage());
                failedResponse.add(response);
            }
        }
    }

    public boolean allSuccess() {
        return failedResponse.isEmpty() && !successResponse.isEmpty() && samePayload();
    }

    public boolean hasSuccess() {
        return !successResponse.isEmpty() && samePayload();
    }

    public byte[] getPayload() {
        if (hasSuccess()) {
            for (ByteString payload : payloadSet) {
                return payload.toByteArray(); // return first element
            }
        } else {
            return null;
        }
        return null;
    }

    public String info() {
        String infoStr =
                "Success endorser: "
                        + successResponse.size()
                        + " Failed endorser: "
                        + failedResponse.size()
                        + "[";

        for (ProposalResponse failedResponse : failedResponse) {
            infoStr += dumpResponse(failedResponse) + ", ";
        }
        infoStr += "]";
        return infoStr;
    }

    private String dumpResponse(ProposalResponse response) {
        return "\"peer: "
                + response.getPeer().getName()
                + " status: "
                + response.getStatus()
                + " message: "
                + response.getMessage()
                + "\"";
    }

    private boolean samePayload() {
        return payloadSet.size() == 1;
    }
}
