package com.webank.wecross.account;

import com.google.protobuf.ByteString;
import org.hyperledger.fabric.protos.common.Common;
import org.hyperledger.fabric.protos.msp.Identities;
import org.hyperledger.fabric.protos.peer.FabricProposal;
import org.hyperledger.fabric.sdk.User;
import org.hyperledger.fabric.sdk.helper.Utils;
import org.hyperledger.fabric.sdk.identity.IdentityFactory;
import org.hyperledger.fabric.sdk.identity.SigningIdentity;
import org.hyperledger.fabric.sdk.security.CryptoSuite;

public class FabricAccount implements Account {

    public static final String CRYPTO_SUITE_FABRIC_BC_SECP256R1 = "FABRIC_BC_SECP256R1";

    public static final String PROPOSAL_TYPE_PEER_PAYLODAD = "QUERY_PEER_PAYLODAD";
    public static final String PROPOSAL_TYPE_ENDORSER_PAYLODAD = "ENDORSER_PAYLODAD";
    public static final String PROPOSAL_TYPE_ORDERER_PAYLOAD = "ORDERER_PAYLOAD"; // for fabric

    private User user;
    private SigningIdentity signer;

    public FabricAccount(User user) throws Exception {
        this.setUser(user);

        // ECDSA secp256r1
        this.signer =
                IdentityFactory.getSigningIdentity(CryptoSuite.Factory.getCryptoSuite(), user);
    }

    @Override
    public String getIdentity() {
        return null;
    }

    public byte[] reassembleProposal(byte[] proposalBytes, String proposalType) throws Exception {
        if (proposalType == null) {
            return proposalBytes;
        }

        switch (proposalType) {
            case PROPOSAL_TYPE_PEER_PAYLODAD:
            case PROPOSAL_TYPE_ENDORSER_PAYLODAD:
                // Fabric needs to set account's identity in the proposal before signing
                return refactFabricProposalIdentity(proposalBytes);
            default:
                return proposalBytes;
        }
    }

    public Boolean isProposalReady(byte[] proposalBytes, String proposalType) {
        switch (proposalType) {
            case PROPOSAL_TYPE_ENDORSER_PAYLODAD:
                // endorser payload need propose again
                return false;
            default:
                return true;
        }
    }

    public byte[] sign(byte[] message) throws Exception {
        return signer.sign(message);
    }

    public String getName() {
        return user.getName();
    }

    public String getAddress() {
        return user.getAccount();
    }

    public String getSignCryptoSuite() {
        return CRYPTO_SUITE_FABRIC_BC_SECP256R1;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public SigningIdentity getSigner() {
        return signer;
    }

    private byte[] refactFabricProposalIdentity(byte[] proposalBytes) throws Exception {
        org.hyperledger.fabric.protos.peer.FabricProposal.Proposal innerFabricProposal;
        innerFabricProposal =
                org.hyperledger.fabric.protos.peer.FabricProposal.Proposal.parseFrom(proposalBytes);
        Common.Header header = Common.Header.parseFrom(innerFabricProposal.getHeader());

        Common.ChannelHeader channelHeader =
                Common.ChannelHeader.parseFrom(header.getChannelHeader());

        Identities.SerializedIdentity refactedSerializedIdentity =
                signer.createSerializedIdentity();

        Common.SignatureHeader refactedSignatureHeader =
                Common.SignatureHeader.newBuilder()
                        .setCreator(refactedSerializedIdentity.toByteString())
                        .setNonce(ByteString.copyFrom(Utils.generateNonce()))
                        .build();

        Common.ChannelHeader refactedChannelHeader =
                Common.ChannelHeader.newBuilder()
                        .setType(channelHeader.getType())
                        .setVersion(channelHeader.getVersion())
                        .setTxId(
                                calcRefactedTxID(
                                        refactedSignatureHeader, refactedSerializedIdentity))
                        .setChannelId(channelHeader.getChannelId())
                        .setTimestamp(channelHeader.getTimestamp())
                        .setEpoch(channelHeader.getEpoch())
                        .setExtension(channelHeader.getExtension())
                        .build();

        Common.Header refactedHeader =
                Common.Header.newBuilder()
                        .setSignatureHeader(refactedSignatureHeader.toByteString())
                        .setChannelHeader(refactedChannelHeader.toByteString())
                        .build();

        FabricProposal.Proposal refactedProposal =
                FabricProposal.Proposal.newBuilder()
                        .setHeader(refactedHeader.toByteString())
                        .setPayload(innerFabricProposal.getPayload())
                        .build();
        byte[] refactedProposalBytes = refactedProposal.toByteArray();

        // System.out.println(Arrays.toString(proposalBytes));
        // System.out.println(Arrays.toString(refactedProposalBytes));

        return refactedProposalBytes;
    }

    private String calcRefactedTxID(
            Common.SignatureHeader refactedSignatureHeader,
            Identities.SerializedIdentity refactedSerializedIdentity)
            throws Exception {
        ByteString no = refactedSignatureHeader.getNonce();

        ByteString comp = no.concat(refactedSerializedIdentity.toByteString());

        byte[] txh = CryptoSuite.Factory.getCryptoSuite().hash(comp.toByteArray());

        //    txID = Hex.encodeHexString(txh);
        String txID = new String(Utils.toHexString(txh));
        return txID;
    }
}
