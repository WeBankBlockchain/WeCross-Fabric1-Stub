package com.webank.wecross.stub.fabric;

import com.google.protobuf.ByteString;
import com.webank.wecross.stub.BlockHeader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.codec.binary.Hex;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSequenceGenerator;
import org.hyperledger.fabric.protos.common.Common;
import org.hyperledger.fabric.protos.msp.Identities;
import org.hyperledger.fabric.protos.peer.FabricProposalResponse;
import org.hyperledger.fabric.protos.peer.FabricTransaction;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric.sdk.security.CryptoSuite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FabricBlock {
    private Logger logger = LoggerFactory.getLogger(FabricBlock.class);

    private Common.Block block;

    private Header header;
    private BlockData blockData;
    private MetaData metaData;

    private String hash;
    private Set<String> validTxs;

    public FabricBlock(byte[] blockBytes) throws Exception {
        this.block = Common.Block.parseFrom(blockBytes);
        this.header = new Header(block.getHeader());
        this.blockData = new BlockData(block.getData());
        this.metaData = new MetaData(block.getMetadata());
    }

    public static FabricBlock encode(byte[] bytes) throws Exception {
        return new FabricBlock(bytes);
    }

    public static byte[] decode(FabricBlock fabricBlock) throws Exception {
        return fabricBlock.block.toByteArray();
    }

    public BlockHeader dumpWeCrossHeader() {
        BlockHeader blockHeader = new BlockHeader();
        blockHeader.setNumber(header.getNumber());
        blockHeader.setHash(this.getHash());
        blockHeader.setPrevHash(header.getPrevHash());
        blockHeader.setTransactionRoot(header.getDataHash());
        return blockHeader;
    }

    public String getHash() {
        if (hash == null || hash.isEmpty()) {
            hash = calculateBlockHash(this.block);
        }
        return hash;
    }

    public boolean hasTransaction(String txID) {
        return getValidTxs().contains(txID);
    }

    public Set<String> getValidTxs() {
        if (validTxs == null) {
            validTxs = parseValidTxIDListFromDataAndFilter();
        }
        return validTxs;
    }

    public Set<String> parseValidTxIDListFromDataAndFilter() {
        Set<String> validList = null;
        try {
            ArrayList<String> txIDList = blockData.getTxIDList();
            byte[] txFilter = metaData.getTransactionFilter();

            if (txIDList.size() != txFilter.length) {
                throw new Exception(
                        "Illegal block format. tx number: "
                                + txIDList.size()
                                + " tx filter size: "
                                + txFilter.length);
            }
            validList = new HashSet<>();
            for (int i = 0; i < txIDList.size(); i++) {
                if (txFilter[i] == FabricTransaction.TxValidationCode.VALID_VALUE) {
                    validList.add(txIDList.get(i));
                }
            }
            logger.debug(
                    "Block valid txID list(" + validList.size() + "): " + validList.toString());

        } catch (Exception e) {
            logger.error("getValidTxIDList exception: " + e);
        }
        return validList;
    }

    public Header getHeader() {
        return header;
    }

    public static class Header {
        private Common.BlockHeader header;

        public Header(Common.BlockHeader header) {
            this.header = header;
        }

        public long getNumber() {
            return header.getNumber();
        }

        public String getPrevHash() {
            return Hex.encodeHexString(header.getPreviousHash().toByteArray());
        }

        public String getDataHash() {
            return Hex.encodeHexString(header.getDataHash().toByteArray());
        }
    }

    public static class BlockData {
        private Logger logger = LoggerFactory.getLogger(BlockData.class);
        private Common.BlockData blockData;
        private ArrayList<String> txIDList;

        public BlockData(Common.BlockData blockData) {
            this.blockData = blockData;
        }

        public ArrayList<String> getTxIDList() {
            if (txIDList == null || txIDList.isEmpty()) {
                txIDList = getTxIDListFromBlockData();
            }

            return txIDList;
        }

        private ArrayList<String> getTxIDListFromBlockData() {
            ArrayList<String> list = null;
            try {

                list = new ArrayList<>();
                for (ByteString envelopeBytes : blockData.getDataList()) {
                    Common.Envelope envelope = Common.Envelope.parseFrom(envelopeBytes);
                    Common.Payload payload = Common.Payload.parseFrom(envelope.getPayload());
                    Common.ChannelHeader channelHeader =
                            Common.ChannelHeader.parseFrom(payload.getHeader().getChannelHeader());
                    String txID = channelHeader.getTxId();
                    list.add(txID);
                }
                logger.debug("Block txID list(" + list.size() + "): " + list.toString());

            } catch (Exception e) {
                logger.error("getTxIDListFromBlockData exception: " + e);
            }

            return list;
        }
    }

    public static class MetaData {
        private Common.BlockMetadata metadata;

        public MetaData(Common.BlockMetadata metadata) {
            this.metadata = metadata;
        }

        public byte[] getTransactionFilter() {
            return metadata.getMetadata(Common.BlockMetadataIndex.TRANSACTIONS_FILTER_VALUE)
                    .toByteArray();
        }

        public Common.Metadata getBlockSignatures() throws Exception {
            return  Common.Metadata.parseFrom(metadata.getMetadata(Common.BlockMetadataIndex.SIGNATURES_VALUE));
        }
    }

    public static String calculateBlockHash(Common.Block block) {
        try {
            CryptoSuite cryptoSuite = CryptoSuite.Factory.getCryptoSuite();
            if (null == cryptoSuite) {
                throw new InvalidArgumentException("Client crypto suite has not  been set.");
            }

            ByteArrayOutputStream s = new ByteArrayOutputStream();
            DERSequenceGenerator seq = new DERSequenceGenerator(s);
            seq.addObject(new ASN1Integer(block.getHeader().getNumber()));
            seq.addObject(new DEROctetString(block.getHeader().getPreviousHash().toByteArray()));
            seq.addObject(new DEROctetString(block.getHeader().getDataHash().toByteArray()));
            seq.close();
            return Hex.encodeHexString(cryptoSuite.hash(s.toByteArray()));
        } catch (Exception e) {
            Logger logger = LoggerFactory.getLogger(MetaData.class);
            logger.error("Could not calculate block hash: " + e);
            return null;
        }
    }

    @Override
    public String toString() {
        return block.toString();
    }

    public boolean verify(Collection<String> endorsers, Collection<String> orderers) {
        if (!verifyBlockCreator(orderers)) {
            logger.warn("Verify creator in block {} failed.", header.getNumber());
            return false;
        }

        if (!verifyTransactions(endorsers)) {
            logger.warn("Verify transaction in block {} failed.", header.getNumber());
            return false;
        }

        return true;
    }

    public boolean verifyBlockCreator(Collection<String> orderers) {
        try {
            Common.Metadata metadata = metaData.getBlockSignatures();
            ByteString signData = metadata.getValue();

            // It seems that this metadata has no relation with block number or hash

            for(Common.MetadataSignature metadataSignature : metadata.getSignaturesList()) {
                byte[] signBytes = metadataSignature.getSignature().toByteArray();
                ByteString ordererCertifcate = Common.SignatureHeader.parseFrom(  metadataSignature.getSignatureHeader()).getCreator();

                // TODO: verify orderers
                /*
                if (orderers.contains(ordererCertifcate)) {

                }
                 */

                if( verifySignature(ordererCertifcate, signBytes, signData.toByteArray())) {
                    return false;
                }

            }
            return true;

        } catch (Exception e) {
            logger.warn("Verify block creator exception: ", e );
            return false;
        }
    }

    // Verify every transaction's endorsement
    public boolean verifyTransactions(Collection<String> endorsers) {
        try {
            byte[] txFilter = metaData.getTransactionFilter();

            if (block.getData().getDataList().size() != txFilter.length) {
                throw new Exception(
                        "Illegal block format. block data(tx) number: "
                                + block.getData().getDataList().size()
                                + " tx filter size: "
                                + txFilter.length);
            }

            for (int i = 0; i < block.getData().getDataCount(); i++) {
                // a tx
                ByteString envelopeBytes = block.getData().getData(i);

                if (txFilter[i] != FabricTransaction.TxValidationCode.VALID_VALUE) {
                    // jump illegal tx
                    continue;
                }

                Common.Envelope envelope = Common.Envelope.parseFrom(envelopeBytes);
                Common.Payload payload = Common.Payload.parseFrom(envelope.getPayload());

                Common.ChannelHeader channelHeader =
                        Common.ChannelHeader.parseFrom(payload.getHeader().getChannelHeader());
                String txID = channelHeader.getTxId();
                if (txID == null || txID.length() == 0) {
                    // ignore system tx
                    continue;
                }

                FabricTransaction.Transaction transaction =
                        FabricTransaction.Transaction.parseFrom(payload.getData());
                for (FabricTransaction.TransactionAction action : transaction.getActionsList()) {
                    // an action of tx
                    FabricTransaction.ChaincodeActionPayload chaincodeActionPayload =
                            FabricTransaction.ChaincodeActionPayload.parseFrom(action.getPayload());
                    FabricTransaction.ChaincodeEndorsedAction chaincodeEndorsedAction =
                            chaincodeActionPayload.getAction();

                    for (FabricProposalResponse.Endorsement endorsement :
                            chaincodeEndorsedAction.getEndorsementsList()) {
                        // a endorsement of tx
                        Identities.SerializedIdentity endorser =
                                Identities.SerializedIdentity.parseFrom(endorsement.getEndorser());
                        ByteString plainText =
                                chaincodeEndorsedAction
                                        .getProposalResponsePayload()
                                        .concat(endorsement.getEndorser());

                        ByteString endorserCertifcate = endorser.getIdBytes();
                        byte[] signBytes = endorsement.getSignature().toByteArray();
                        byte[] data = plainText.toByteArray();

                        // TODO: verify endorser
                        /*
                        if (endorsers.contains(endorserCertifcate)) {

                        }
                         */

                        // verify endorser signature
                        if (!verifySignature(endorserCertifcate, signBytes, data)) {
                            return false;
                        }

                    }
                }
            }
            return true;
        } catch (Exception e) {
            logger.warn("verify block txs failed: ", e);
            return false;
        }
    }



    private boolean verifySignature(ByteString identity, byte[] signBytes, byte[] data) {
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(identity.toByteArray());
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate certificate = (X509Certificate) cf.generateCertificate(bis);

            Signature signer = Signature.getInstance(certificate.getSigAlgName());
            signer.initVerify(certificate);
            signer.update(data);
            boolean ok = signer.verify(signBytes);

            logger.debug(
                    "verifySignature: {}, identity: {}, signBytes:{}, data: {} ",
                    ok,
                    identity.toStringUtf8(),
                    Arrays.toString(signBytes),
                    data);

            return ok;
        } catch (Exception e) {
            logger.error("verifySignature exception: ", e);
            return false;
        }
    }
}
