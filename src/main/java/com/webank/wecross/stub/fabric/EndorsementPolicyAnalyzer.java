package com.webank.wecross.stub.fabric;

import static java.lang.String.format;

import com.google.protobuf.ByteString;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import org.hyperledger.fabric.sdk.ProposalResponse;
import org.hyperledger.fabric.sdk.SDKUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EndorsementPolicyAnalyzer {
    // TODO: Use service discovery
    private Logger logger = LoggerFactory.getLogger(EndorsementPolicyAnalyzer.class);

    private Collection<ProposalResponse> successResponse;
    private Collection<ProposalResponse> failedResponse;
    private Set<ByteString> payloadSet;

    public EndorsementPolicyAnalyzer(Collection<ProposalResponse> proposalResponses) {
        successResponse = new LinkedList<>();
        failedResponse = new LinkedList<>();
        payloadSet = new HashSet<>();

        for (ProposalResponse response : proposalResponses) {
            if (response.getStatus() == ProposalResponse.Status.SUCCESS) {
                ByteString payload = response.getProposalResponse().getResponse().getPayload();
                payloadSet.add(payload);
                successResponse.add(response);
            } else {
                String status = response.getStatus().toString();
                failedResponse.add(response);
            }
        }
    }

    public boolean allSuccess() {
        return failedResponse.isEmpty()
                && !successResponse.isEmpty()
                && samePayload()
                && sameSuccessResponse();
    }

    public boolean hasSuccess() {
        return !successResponse.isEmpty() && samePayload() && sameSuccessResponse();
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
        infoStr += "] payloads:" + dumpPayloadSet() + " " + dumpConsistencyInfo();
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

    private boolean sameSuccessResponse() {
        try {
            HashSet<ProposalResponse> invalid = new HashSet<>();
            int consistencyGroups =
                    SDKUtils.getProposalConsistencySets(successResponse, invalid).size();

            if (consistencyGroups != 1 || !invalid.isEmpty()) {
                throw new IllegalArgumentException(
                        format(
                                "The proposal responses have %d inconsistent groups with %d that are invalid."
                                        + " Expected all to be consistent and none to be invalid.",
                                consistencyGroups, invalid.size()));
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
            return false;
        }
        return true;
    }

    private String dumpPayloadSet() {
        String res = "size: " + payloadSet.size() + " [";

        boolean firstIn = true;
        for (ByteString bs : payloadSet) {
            if (firstIn) {
                firstIn = false;
            } else {
                res += ", ";
            }

            res += bs.toStringUtf8();
        }
        res += "]";

        return res;
    }

    private String dumpConsistencyInfo() {
        try {
            HashSet<ProposalResponse> invalid = new HashSet<>();
            int consistencyGroups =
                    SDKUtils.getProposalConsistencySets(successResponse, invalid).size();

            if (consistencyGroups != 1 || !invalid.isEmpty()) {
                throw new IllegalArgumentException(
                        format(
                                "The proposal responses have %d inconsistent groups with %d that are invalid."
                                        + " Expected all to be consistent and none to be invalid.",
                                consistencyGroups, invalid.size()));
            }
        } catch (Exception e) {
            return "proposal response consistency: " + e.getMessage();
        }
        return "proposal response consistency: ok";
    }

    public Collection<ProposalResponse> getSuccessResponse() {
        return successResponse;
    }

    public Collection<ProposalResponse> getFailedResponse() {
        return failedResponse;
    }
}
