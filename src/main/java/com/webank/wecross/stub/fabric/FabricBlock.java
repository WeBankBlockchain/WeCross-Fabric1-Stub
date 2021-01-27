package com.webank.wecross.stub.fabric;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import com.webank.wecross.exception.WeCrossException;
import com.webank.wecross.stub.BlockHeader;
import com.webank.wecross.stub.ObjectMapperFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
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
    private static final Logger logger = LoggerFactory.getLogger(FabricBlock.class);

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
            hash = calculateBlockHashString(this.block);
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
            return Common.Metadata.parseFrom(
                    metadata.getMetadata(Common.BlockMetadataIndex.SIGNATURES_VALUE));
        }
    }

    public static String calculateBlockHashString(Common.Block block) {
        try {
            CryptoSuite cryptoSuite = CryptoSuite.Factory.getCryptoSuite();
            if (null == cryptoSuite) {
                throw new InvalidArgumentException("Client crypto suite has not  been set.");
            }
            return Hex.encodeHexString(cryptoSuite.hash(calculateBlockHeader(block)));
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

    public boolean verify(String blockVerifierString) {
        try {
            String chainType = getStringInVerifierString(blockVerifierString, "chainType");
            if (!"Fabric1.4".equals(chainType)) {
                logger.error(
                        "Verify error: Fabric Block Verifier chainType error, chainType in verifier is {}.",
                        chainType);
                return false;
            }
            Map<String, String> ordererCAMap =
                    getMapperInVerifierString(blockVerifierString, "ordererCA");
            Map<String, String> endorserCAMap =
                    getMapperInVerifierString(blockVerifierString, "endorserCA");
            if (ordererCAMap == null && endorserCAMap == null) {
                logger.error(
                        "Did not full config Fabric Block Verifier, will skip block verification on what field didn't config.");
                return false;
            }
            if (ordererCAMap != null && !verifyBlockCreator(ordererCAMap)) {
                logger.warn("Verify creator in block {} failed.", header.getNumber());
                return false;
            }
            if (endorserCAMap != null && !verifyTransactions(endorserCAMap)) {
                logger.warn("Verify transaction in block {} failed.", header.getNumber());
                return false;
            }
        } catch (WeCrossException e) {
            logger.error(
                    "Verify block error, errorCode:{}, error: {}, caused by: {}",
                    e.getErrorCode(),
                    e.getMessage(),
                    e.getCause());
            return false;
        }
        return true;
    }

    public boolean verifyBlockCreator(Map<String, String> ordererCAs) {
        try {
            Common.Metadata metadata = metaData.getBlockSignatures();
            if (logger.isTraceEnabled()) {
                logger.trace(
                        "Verifying Fabric block, ordererCAs is {}, SignatureList is {}.",
                        ordererCAs,
                        metadata.getSignaturesList());
            }

            for (Common.MetadataSignature metadataSignature : metadata.getSignaturesList()) {
                ByteString signature = metadataSignature.getSignature();
                byte[] signBytes = signature.toByteArray();
                Common.SignatureHeader header =
                        Common.SignatureHeader.parseFrom(metadataSignature.getSignatureHeader());
                Identities.SerializedIdentity serializedIdentity =
                        Identities.SerializedIdentity.parseFrom(header.getCreator());

                ByteString blockHeaderBytes = ByteString.copyFrom(calculateBlockHeader(block));
                ByteString plainText =
                        metadata.getValue()
                                .concat(metadataSignature.getSignatureHeader())
                                .concat(blockHeaderBytes);

                String mspId = serializedIdentity.getMspid();
                if (ordererCAs.containsKey(mspId)
                        && checkCert(
                                ordererCAs.get(mspId).getBytes(),
                                serializedIdentity.getIdBytes().toByteArray())) {
                    if (!verifySignature(
                            serializedIdentity.getIdBytes(), signBytes, plainText.toByteArray())) {
                        return false;
                    }
                } else {
                    logger.error(
                            "VerifyBlockCreator error, ordererCAMap didn't have a key of {} or checkCert error, ordererCAMap: {}",
                            serializedIdentity.getMspid(),
                            ordererCAs);
                    return false;
                }
            }
            return true;

        } catch (Exception e) {
            logger.warn("Verify block creator exception: ", e);
            return false;
        }
    }

    private static byte[] calculateBlockHeader(Common.Block block) throws IOException {
        ByteArrayOutputStream s = new ByteArrayOutputStream();
        DERSequenceGenerator seq = new DERSequenceGenerator(s);
        try {
            seq.addObject(new ASN1Integer(block.getHeader().getNumber()));
            seq.addObject(new DEROctetString(block.getHeader().getPreviousHash().toByteArray()));
            seq.addObject(new DEROctetString(block.getHeader().getDataHash().toByteArray()));
        } catch (Exception e) {
            logger.error("calculateBlockHeader error, e: ", e);
        } finally {
            seq.close();
        }
        return s.toByteArray();
    }

    // Verify every transaction's endorsement
    public boolean verifyTransactions(Map<String, String> endorserCAs) {
        if (logger.isTraceEnabled()) {
            logger.trace("Verifying Fabric transactions, endorserCAs: {}", endorserCAs);
        }
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

                        ByteString endorserCertificate = endorser.getIdBytes();
                        byte[] signBytes = endorsement.getSignature().toByteArray();
                        String mspId = endorser.getMspid();

                        // verify endorser certificate
                        if (endorserCAs.containsKey(mspId)
                                && checkCert(
                                        endorserCAs.get(mspId).getBytes(),
                                        endorserCertificate.toByteArray())) {
                            if (!verifySignature(
                                    endorserCertificate, signBytes, plainText.toByteArray())) {
                                return false;
                            }
                        } else {
                            logger.error(
                                    "Error occurs in verifyTransactions: endorserCAMap may not contains {} or cert is wrong. cert: {}",
                                    endorser.getMspid(),
                                    endorserCertificate.toByteArray());
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

            if (logger.isTraceEnabled()) {
                logger.trace(
                        "verifySignature: {}, identity: {}, signBytes:{}, data: {} ",
                        ok,
                        identity.toStringUtf8(),
                        Hex.encodeHexString(signBytes),
                        Hex.encodeHexString(data));
            }

            return ok;
        } catch (Exception e) {
            logger.error("verifySignature in block exception: ", e);
            return false;
        }
    }

    private boolean checkCert(byte[] caCert, byte[] cert) {
        ByteArrayInputStream CAByteStream = new ByteArrayInputStream(caCert);
        ByteArrayInputStream certByteStrean = new ByteArrayInputStream(cert);
        CertificateFactory cf = null;
        try {
            cf = CertificateFactory.getInstance("X.509");
            X509Certificate caCertificate = (X509Certificate) cf.generateCertificate(CAByteStream);
            X509Certificate certificate = (X509Certificate) cf.generateCertificate(certByteStrean);
            PublicKey caKey = caCertificate.getPublicKey();
            certificate.verify(caKey);
        } catch (CertificateException
                | NoSuchAlgorithmException
                | InvalidKeyException
                | NoSuchProviderException
                | SignatureException e) {
            logger.error("Check Cert fail, caCert: {}, cert: {}", caCert, cert);
            return false;
        }
        return true;
    }

    private Map<String, String> getMapperInVerifierString(String blockVerifierString, String key)
            throws WeCrossException {
        ObjectMapper objectMapper = ObjectMapperFactory.getObjectMapper();
        if (blockVerifierString == null || key == null) return null;
        try {
            Objects.requireNonNull(
                    blockVerifierString,
                    "'blockVerifierString' in getPubKeyInBCOSVerifier is null.");
            Map<String, Object> fabricVerifier =
                    objectMapper.readValue(
                            blockVerifierString, new TypeReference<Map<String, Object>>() {});
            if (!Objects.isNull(fabricVerifier.get(key))) {
                return (Map<String, String>) fabricVerifier.get(key);
            } else {
                return null;
            }
        } catch (JsonProcessingException e) {
            throw new WeCrossException(
                    WeCrossException.ErrorCode.UNEXPECTED_CONFIG,
                    "Parse Json to BCOSVerifier Error, " + e.getMessage(),
                    e.getCause());
        } catch (Exception e) {
            throw new WeCrossException(
                    WeCrossException.ErrorCode.UNEXPECTED_CONFIG,
                    "Read BCOSVerifier Json Error, " + e.getMessage(),
                    e.getCause());
        }
    }

    private String getStringInVerifierString(String blockVerifierString, String key)
            throws WeCrossException {
        ObjectMapper objectMapper = ObjectMapperFactory.getObjectMapper();
        if (blockVerifierString == null || key == null) return null;
        try {
            Objects.requireNonNull(
                    blockVerifierString,
                    "'blockVerifierString' in getPubKeyInBCOSVerifier is null.");
            Map<String, Object> fabricVerifier =
                    objectMapper.readValue(
                            blockVerifierString, new TypeReference<Map<String, Object>>() {});
            if (!Objects.isNull(fabricVerifier.get(key))) {
                return (String) fabricVerifier.get(key);
            } else {
                return null;
            }
        } catch (JsonProcessingException e) {
            throw new WeCrossException(
                    WeCrossException.ErrorCode.UNEXPECTED_CONFIG,
                    "Parse Json to BCOSVerifier Error, " + e.getMessage(),
                    e.getCause());
        } catch (Exception e) {
            throw new WeCrossException(
                    WeCrossException.ErrorCode.UNEXPECTED_CONFIG,
                    "Read BCOSVerifier Json Error, " + e.getMessage(),
                    e.getCause());
        }
    }
}
