package com.webank.wecross.stub.fabric;

import com.google.protobuf.ByteString;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import org.hyperledger.fabric.protos.common.Common;
import org.hyperledger.fabric.protos.peer.Chaincode;
import org.hyperledger.fabric.protos.peer.FabricProposal;
import org.hyperledger.fabric.protos.peer.FabricProposalResponse;

public class FabricTransaction {
    private Common.Header header;
    private org.hyperledger.fabric.protos.peer.FabricTransaction.Transaction transaction;

    private List<TransactionAction> transactionActionList = new ArrayList<>();
    private String txID;

    FabricTransaction(byte[] payloadBytes) throws Exception {

        Common.Payload transactionPayload = Common.Payload.parseFrom(payloadBytes);
        this.header = transactionPayload.getHeader();
        this.txID = Common.ChannelHeader.parseFrom(header.getChannelHeader()).getTxId();
        this.transaction =
                org.hyperledger.fabric.protos.peer.FabricTransaction.Transaction.parseFrom(
                        transactionPayload.getData());
        for (org.hyperledger.fabric.protos.peer.FabricTransaction.TransactionAction action :
                transaction.getActionsList()) {
            transactionActionList.add(new TransactionAction(action));
        }
    }

    public static FabricTransaction buildFromEnvelopeBytes(byte[] envelopeBytes) throws Exception {
        Common.Envelope envelope = Common.Envelope.parseFrom(envelopeBytes);

        byte[] payloadBytes = envelope.getPayload().toByteArray();
        return buildFromPayloadBytes(payloadBytes);
    }

    public static FabricTransaction buildFromPayloadBytes(byte[] payloadBytes) throws Exception {
        return new FabricTransaction(payloadBytes);
    }

    public String getTxID() {
        return txID;
    }

    public String getChaincodeName() {
        return transactionActionList
                .get(0)
                .getChaincodeAction()
                .getProposalPayload()
                .getChaincodeName();
    }

    public String getMethod() {
        return transactionActionList.get(0).getChaincodeAction().getProposalPayload().getMethod();
    }

    public List<String> getArgs() {
        return transactionActionList.get(0).getChaincodeAction().getProposalPayload().getArgs();
    }

    public String getOutput() {
        return transactionActionList.get(0).getChaincodeAction().getEndorsedAction().getOutput();
    }

    public byte[] getOutputBytes() {
        return transactionActionList
                .get(0)
                .getChaincodeAction()
                .getEndorsedAction()
                .getOutputBytes();
    }

    public static class TransactionAction {
        private org.hyperledger.fabric.protos.peer.FabricTransaction.TransactionAction
                transactionAction;
        private ChaincodeActionPayload chaincodeAction;

        public TransactionAction(
                org.hyperledger.fabric.protos.peer.FabricTransaction.TransactionAction
                        transactionAction)
                throws Exception {
            this.transactionAction = transactionAction;
            chaincodeAction =
                    new ChaincodeActionPayload(this.transactionAction.getPayload().toByteArray());
        }

        public ChaincodeActionPayload getChaincodeAction() {
            return chaincodeAction;
        }

        public static class ChaincodeActionPayload {
            private org.hyperledger.fabric.protos.peer.FabricTransaction.ChaincodeActionPayload
                    chaincodeActionPayload;
            private ProposalPayload proposalPayload;
            private EndorsedAction endorsedAction;

            public ChaincodeActionPayload(byte[] bytes) throws Exception {
                this.chaincodeActionPayload =
                        org.hyperledger.fabric.protos.peer.FabricTransaction.ChaincodeActionPayload
                                .parseFrom(bytes);
                this.proposalPayload =
                        new ProposalPayload(
                                chaincodeActionPayload.getChaincodeProposalPayload().toByteArray());
                this.endorsedAction = new EndorsedAction(chaincodeActionPayload.getAction());
            }

            public ProposalPayload getProposalPayload() {
                return proposalPayload;
            }

            public EndorsedAction getEndorsedAction() {
                return endorsedAction;
            }

            public static class ProposalPayload {
                private FabricProposal.ChaincodeProposalPayload chaincodeProposalPayload;
                private org.hyperledger.fabric.protos.peer.Chaincode.ChaincodeID chaincodeID;
                private String method;
                private List<String> args;

                public ProposalPayload(byte[] bytes) throws Exception {
                    this.chaincodeProposalPayload =
                            FabricProposal.ChaincodeProposalPayload.parseFrom(bytes);

                    Chaincode.ChaincodeInvocationSpec chaincodeInvocationSpec =
                            Chaincode.ChaincodeInvocationSpec.parseFrom(
                                    chaincodeProposalPayload.getInput());

                    Chaincode.ChaincodeSpec chaincodeSpec =
                            chaincodeInvocationSpec.getChaincodeSpec();

                    this.chaincodeID = chaincodeSpec.getChaincodeId();

                    Chaincode.ChaincodeInput chaincodeInput = chaincodeSpec.getInput();
                    List<ByteString> allArgs = chaincodeInput.getArgsList();

                    boolean isMethod = true;
                    this.method = new String();
                    this.args = new LinkedList<>();
                    for (ByteString byteString : allArgs) {
                        if (isMethod) {
                            // First is method, other is args
                            method = byteString.toStringUtf8();
                            isMethod = false;
                        } else {
                            args.add(
                                    new String(byteString.toByteArray(), Charset.forName("UTF-8")));
                        }
                    }
                }

                public String getChaincodeName() {
                    return chaincodeID.getName();
                }

                public String getMethod() {
                    return method;
                }

                public List<String> getArgs() {
                    return args;
                }
            }

            public static class EndorsedAction {
                private org.hyperledger.fabric.protos.peer.FabricTransaction.ChaincodeEndorsedAction
                        chaincodeEndorsedAction;
                private ByteString output;

                public EndorsedAction(
                        org.hyperledger.fabric.protos.peer.FabricTransaction.ChaincodeEndorsedAction
                                chaincodeEndorsedAction)
                        throws Exception {
                    this.chaincodeEndorsedAction = chaincodeEndorsedAction;

                    FabricProposalResponse.ProposalResponsePayload proposalResponsePayload =
                            FabricProposalResponse.ProposalResponsePayload.parseFrom(
                                    this.chaincodeEndorsedAction.getProposalResponsePayload());
                    org.hyperledger.fabric.protos.peer.FabricProposal.ChaincodeAction
                            chaincodeAction =
                                    org.hyperledger.fabric.protos.peer.FabricProposal
                                            .ChaincodeAction.parseFrom(
                                            proposalResponsePayload.getExtension());
                    this.output = chaincodeAction.getResponse().getPayload();
                }

                public String getOutput() {
                    return output.toStringUtf8();
                }

                public byte[] getOutputBytes() {
                    return output.toByteArray();
                }
            }
        }
    }
}
