package link.luyu.protocol.link.fabric1;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webank.wecross.stub.Block;
import com.webank.wecross.stub.BlockManager;
import com.webank.wecross.stub.Connection;
import com.webank.wecross.stub.ObjectMapperFactory;
import com.webank.wecross.stub.Path;
import com.webank.wecross.stub.ResourceInfo;
import com.webank.wecross.stub.TransactionContext;
import com.webank.wecross.stub.TransactionException;
import com.webank.wecross.stub.TransactionRequest;
import com.webank.wecross.stub.TransactionResponse;
import com.webank.wecross.stub.fabric.ChaincodeEventManager;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import link.luyu.protocol.algorithm.ecdsa.secp256r1.EcdsaSecp256r1WithSHA256;
import link.luyu.protocol.common.STATUS;
import link.luyu.protocol.link.Driver;
import link.luyu.protocol.network.Account;
import link.luyu.protocol.network.CallRequest;
import link.luyu.protocol.network.CallResponse;
import link.luyu.protocol.network.Events;
import link.luyu.protocol.network.Receipt;
import link.luyu.protocol.network.Resource;
import link.luyu.protocol.network.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LuyuDriverAdapter implements Driver {
    private static Logger logger = LoggerFactory.getLogger(LuyuDriverAdapter.class);
    private static ObjectMapper objectMapper = ObjectMapperFactory.getObjectMapper();
    private static final int QUERY_SUCCESS = 0;
    private static final int QUERY_FAILED = 99100;

    private String type;
    private String chainPath;
    private com.webank.wecross.stub.Driver wecrossDriver;
    private LuyuWeCrossConnection luyuWeCrossConnection;
    private LuyuMemoryBlockManager blockManager;
    private Map<String, ResourceInfo> name2ResourceInfo = new HashMap<>();
    private Events routerEventsHandler;

    public LuyuDriverAdapter(
            String type,
            String chainPath,
            com.webank.wecross.stub.Driver wecrossDriver,
            LuyuWeCrossConnection luyuWeCrossConnection,
            LuyuMemoryBlockManager blockManager) {
        this.type = type;
        this.chainPath = chainPath;
        this.wecrossDriver = wecrossDriver;
        this.luyuWeCrossConnection = luyuWeCrossConnection;
        this.blockManager = blockManager;

        luyuWeCrossConnection.setConnectionEventHandler(
                new Connection.ConnectionEventHandler() {
                    @Override
                    public void onResourcesChange(List<ResourceInfo> resourceInfos) {
                        if (resourceInfos != null) {
                            System.out.println(
                                    "onResourcesChange: "
                                            + chainPath
                                            + " "
                                            + Arrays.toString(resourceInfos.toArray()));
                            logger.info(
                                    "onResourcesChange: {} {}",
                                    chainPath,
                                    Arrays.toString(resourceInfos.toArray()));

                            Map<String, ResourceInfo> newName2ResourceInfo = new HashMap<>();
                            for (ResourceInfo info : resourceInfos) {
                                newName2ResourceInfo.put(info.getName(), info);

                                if (luyuWeCrossConnection.getLuyuConnection()
                                        instanceof LuyuConnectionAdapter) {
                                    // is local connection
                                    LuyuConnectionAdapter connectionAdapter =
                                            (LuyuConnectionAdapter)
                                                    luyuWeCrossConnection.getLuyuConnection();

                                    logger.info("Register chaincode event for {}", info.getName());
                                    registerChainCodeEvent(info.getName(), connectionAdapter);
                                }
                            }

                            name2ResourceInfo = newName2ResourceInfo;
                        }
                    }
                });
    }

    private void registerChainCodeEvent(
            String chaincodeName, LuyuConnectionAdapter connectionAdapter) {
        connectionAdapter.subscribe(
                LuyuConnectionAdapter.ON_CHAINCODEVENT,
                chaincodeName.getBytes(StandardCharsets.UTF_8),
                new link.luyu.protocol.link.Connection.Callback() {
                    @Override
                    public void onResponse(int code, String message, byte[] data) {
                        if (code != STATUS.OK) {
                            logger.warn(
                                    "On chaincode event return error, code:{} message:{}",
                                    code,
                                    message);
                            return;
                        }

                        try {
                            ChaincodeEventManager.EventPacket packet =
                                    objectMapper.readValue(
                                            data,
                                            new TypeReference<
                                                    ChaincodeEventManager.EventPacket>() {});

                            logger.debug("On chain event: {}", packet);

                            // verify identity and chain identity
                            routerEventsHandler.getAccountByIdentity(
                                    packet.identity,
                                    new Events.KeyCallback() {
                                        @Override
                                        public void onResponse(Account account) {
                                            try {
                                                com.webank.wecross.stub.Account weCrossAccount =
                                                        toWeCrossAccount(chainPath, account);

                                                X509Certificate accountManagerCert =
                                                        convertStringToX509Cert(
                                                                weCrossAccount.getIdentity());
                                                X509Certificate paketCert =
                                                        convertStringToX509Cert(packet.sender);

                                                if (!accountManagerCert.equals(paketCert)) {
                                                    logger.warn(
                                                            "Permission denied of chain account:{} using luyu account:{} to query. real account is {}",
                                                            packet.sender,
                                                            packet.identity,
                                                            weCrossAccount.getIdentity());
                                                    return;
                                                } else {
                                                    logger.debug(
                                                            "Chain identity verify success. {}",
                                                            packet.identity);
                                                }
                                            } catch (Exception e) {
                                                logger.warn(
                                                        "On chaincode event verify account exception: ",
                                                        e);
                                                return;
                                            }
                                        }
                                    });

                            if (packet.operation.equals(ChaincodeEventManager.SENDTX_EVENT_NAME)) {
                                Transaction request = new Transaction();
                                request.setPath(packet.path);
                                request.setMethod(packet.method);
                                request.setArgs(packet.args);
                                request.setSender(packet.identity);
                                request.setNonce(packet.nonce);

                                routerEventsHandler.sendTransaction(
                                        request,
                                        new ReceiptCallback() {
                                            @Override
                                            public void onResponse(
                                                    int i, String s, Receipt receipt) {
                                                if (i != STATUS.OK) {
                                                    logger.warn(
                                                            "Chain {} to {} {} error: {}",
                                                            chainPath,
                                                            packet.path,
                                                            packet.operation,
                                                            s);
                                                    return;
                                                }

                                                if (receipt.getCode() != STATUS.OK) {
                                                    logger.warn(
                                                            "Chain {} to {} {} return error receipt: {}",
                                                            chainPath,
                                                            packet.path,
                                                            packet.operation,
                                                            receipt.toString());
                                                    return;
                                                }

                                                routerEventsHandler.getAccountByIdentity(
                                                        packet.identity,
                                                        new Events.KeyCallback() {
                                                            @Override
                                                            public void onResponse(
                                                                    Account account) {
                                                                sendCallbackTransaction(
                                                                        account,
                                                                        packet.name,
                                                                        packet.callbackMethod,
                                                                        receipt.getResult(),
                                                                        packet.nonce);
                                                            }
                                                        });
                                            }
                                        });

                            } else if (packet.operation.equals(
                                    ChaincodeEventManager.CALL_EVENT_NAME)) {
                                CallRequest request = new CallRequest();
                                request.setPath(packet.path);
                                request.setMethod(packet.method);
                                request.setArgs(packet.args);
                                request.setSender(packet.identity);
                                request.setNonce(packet.nonce);
                                routerEventsHandler.call(
                                        request,
                                        new CallResponseCallback() {
                                            @Override
                                            public void onResponse(
                                                    int i, String s, CallResponse callResponse) {
                                                if (i != STATUS.OK) {
                                                    logger.warn(
                                                            "Chain {} to {} {} error: {}",
                                                            chainPath,
                                                            packet.path,
                                                            packet.operation,
                                                            s);
                                                    return;
                                                }

                                                if (callResponse.getCode() != STATUS.OK) {
                                                    logger.warn(
                                                            "Chain {} to {} {} return error receipt: {}",
                                                            chainPath,
                                                            packet.path,
                                                            packet.operation,
                                                            callResponse.toString());
                                                    return;
                                                }

                                                routerEventsHandler.getAccountByIdentity(
                                                        packet.identity,
                                                        new Events.KeyCallback() {
                                                            @Override
                                                            public void onResponse(
                                                                    Account account) {
                                                                sendCallbackTransaction(
                                                                        account,
                                                                        packet.name,
                                                                        packet.callbackMethod,
                                                                        callResponse.getResult(),
                                                                        packet.nonce);
                                                            }
                                                        });
                                            }
                                        });
                            } else {
                                throw new Exception(
                                        "Unsupported operation:"
                                                + packet.operation
                                                + " packet:"
                                                + new String(data));
                            }

                        } catch (Exception e) {
                            logger.warn("On chaincode event exception, ", e);
                            return;
                        }
                    }
                });
    }

    @Override
    public void start() throws RuntimeException {}

    @Override
    public void stop() throws RuntimeException {
        blockManager.stop();
    }

    @Override
    public void sendTransaction(Account account, Transaction request, ReceiptCallback callback) {
        try {
            com.webank.wecross.stub.Account wecrossAccount =
                    toWeCrossAccount(request.getPath(), account);
            // Account account = adminUser;
            Path path = Path.decode(request.getPath());
            ResourceInfo resourceInfo = name2ResourceInfo.get(path.getResource());
            if (resourceInfo == null) {
                throw new Exception("Resource " + request.getPath() + " not found.");
            }

            TransactionContext context =
                    new TransactionContext(
                            wecrossAccount,
                            path,
                            resourceInfo,
                            blockManager); // TODO: use user account instead
            TransactionRequest transactionRequest = new TransactionRequest();
            transactionRequest.setMethod(request.getMethod());
            transactionRequest.setArgs(request.getArgs());
            wecrossDriver.asyncSendTransaction(
                    context,
                    transactionRequest,
                    false,
                    luyuWeCrossConnection,
                    new com.webank.wecross.stub.Driver.Callback() {
                        @Override
                        public void onTransactionResponse(
                                TransactionException transactionException,
                                TransactionResponse transactionResponse) {
                            int status = QUERY_SUCCESS;
                            String message = "Success";
                            Receipt receipt = null;
                            if (transactionException != null) {
                                status = transactionException.getErrorCode();
                                message = transactionException.getMessage();
                            }

                            if (transactionResponse != null) {
                                receipt = new Receipt();
                                receipt.setResult(transactionResponse.getResult());
                                receipt.setCode(transactionResponse.getErrorCode());
                                receipt.setMessage(transactionResponse.getMessage());
                                receipt.setPath(request.getPath());
                                receipt.setMethod(request.getMethod());
                                receipt.setArgs(request.getArgs());
                                String luyuFabricTxHash =
                                        LuyuFabricTxAdapter.toLuyuFabricTxHash(
                                                transactionResponse.getHash(),
                                                transactionResponse.getBlockNumber());
                                receipt.setTransactionHash(luyuFabricTxHash);
                                receipt.setTransactionBytes(new byte[] {}); // TODO: Add bytes
                                receipt.setBlockNumber(transactionResponse.getBlockNumber());
                            }

                            callback.onResponse(status, message, receipt);
                        }
                    });
        } catch (Exception e) {
            callback.onResponse(QUERY_FAILED, e.getMessage(), null);
        }
    }

    @Override
    public void call(Account account, CallRequest request, CallResponseCallback callback) {
        try {

            com.webank.wecross.stub.Account wecrossAccount =
                    toWeCrossAccount(request.getPath(), account);

            Path path = Path.decode(request.getPath());
            ResourceInfo resourceInfo = name2ResourceInfo.get(path.getResource());
            if (resourceInfo == null) {
                throw new Exception("Resource " + request.getPath() + " not found.");
            }

            TransactionContext context =
                    new TransactionContext(
                            wecrossAccount,
                            path,
                            resourceInfo,
                            blockManager); // TODO: use user account instead
            TransactionRequest transactionRequest = new TransactionRequest();
            transactionRequest.setMethod(request.getMethod());
            transactionRequest.setArgs(request.getArgs());
            wecrossDriver.asyncCall(
                    context,
                    transactionRequest,
                    false,
                    luyuWeCrossConnection,
                    new com.webank.wecross.stub.Driver.Callback() {
                        @Override
                        public void onTransactionResponse(
                                TransactionException transactionException,
                                TransactionResponse transactionResponse) {
                            int status = QUERY_SUCCESS;
                            String message = "Success";
                            CallResponse callResponse = null;
                            if (transactionException != null) {
                                status = transactionException.getErrorCode();
                                message = transactionException.getMessage();
                            }

                            if (transactionResponse != null) {
                                callResponse = new CallResponse();
                                callResponse.setResult(transactionResponse.getResult());
                                callResponse.setCode(transactionResponse.getErrorCode());
                                callResponse.setMessage(transactionResponse.getMessage());
                                callResponse.setPath(request.getPath());
                                callResponse.setMethod(request.getMethod());
                                callResponse.setArgs(request.getArgs());
                            }

                            callback.onResponse(status, message, callResponse);
                        }
                    });
        } catch (Exception e) {
            callback.onResponse(QUERY_FAILED, e.getMessage(), null);
        }
    }

    @Override
    public void getTransactionReceipt(String txHash, ReceiptCallback callback) {
        try {
            Map.Entry<String, Long> entry = LuyuFabricTxAdapter.parseFromLuyuFabricTxHash(txHash);
            String orignalTxHash = entry.getKey();
            Long blockNumber = entry.getValue();

            wecrossDriver.asyncGetTransaction(
                    orignalTxHash,
                    blockNumber,
                    blockManager,
                    true,
                    luyuWeCrossConnection,
                    new com.webank.wecross.stub.Driver.GetTransactionCallback() {
                        @Override
                        public void onResponse(
                                Exception e, com.webank.wecross.stub.Transaction transaction) {
                            if (e != null) {
                                callback.onResponse(QUERY_FAILED, e.getMessage(), null);
                            } else {
                                try {
                                    Path path = Path.decode(chainPath);
                                    path.setResource(transaction.getResource());

                                    Receipt receipt = new Receipt();
                                    receipt.setResult(
                                            transaction.getTransactionResponse().getResult());
                                    receipt.setCode(
                                            transaction.getTransactionResponse().getErrorCode());
                                    receipt.setMessage(
                                            transaction.getTransactionResponse().getMessage());
                                    receipt.setPath(path.toString());
                                    receipt.setMethod(
                                            transaction.getTransactionRequest().getMethod());
                                    receipt.setArgs(transaction.getTransactionRequest().getArgs());
                                    receipt.setTransactionHash(orignalTxHash);
                                    receipt.setTransactionBytes(transaction.getTxBytes());
                                    receipt.setBlockNumber(
                                            transaction.getTransactionResponse().getBlockNumber());

                                    callback.onResponse(QUERY_SUCCESS, "Success", receipt);

                                } catch (Exception e1) {
                                    callback.onResponse(QUERY_FAILED, e1.getMessage(), null);
                                }
                            }
                        }
                    });
        } catch (Exception e) {
            callback.onResponse(QUERY_FAILED, e.getMessage(), null);
        }
    }

    @Override
    public void getBlockByHash(String blockHash, BlockCallback callback) {
        // TODO: implement this
    }

    @Override
    public void getBlockByNumber(long blockNumber, BlockCallback callback) {
        if (blockManager.hasBlock(blockNumber)) {
            blockManager.asyncGetBlock(
                    blockNumber,
                    new BlockManager.GetBlockCallback() {
                        @Override
                        public void onResponse(Exception e, Block block) {
                            if (e != null) {
                                callback.onResponse(QUERY_FAILED, e.getMessage(), null);
                            } else {
                                link.luyu.protocol.network.Block luyuBlock = toLuyuBlock(block);
                                callback.onResponse(QUERY_SUCCESS, "success", luyuBlock);
                            }
                        }
                    });
        } else {
            wecrossDriver.asyncGetBlock(
                    blockNumber,
                    false,
                    luyuWeCrossConnection,
                    new com.webank.wecross.stub.Driver.GetBlockCallback() {
                        @Override
                        public void onResponse(Exception e, Block block) {
                            if (e != null) {
                                callback.onResponse(QUERY_FAILED, e.getMessage(), null);
                            } else {
                                link.luyu.protocol.network.Block luyuBlock = toLuyuBlock(block);
                                callback.onResponse(QUERY_SUCCESS, "success", luyuBlock);
                            }
                        }
                    });
        }
    }

    private link.luyu.protocol.network.Block toLuyuBlock(Block block) {
        link.luyu.protocol.network.Block luyuBlock = new link.luyu.protocol.network.Block();
        luyuBlock.setNumber(block.blockHeader.getNumber());
        luyuBlock.setChainPath(chainPath);
        luyuBlock.setHash(block.blockHeader.getHash());
        luyuBlock.setRoots(
                new String[] {
                    block.blockHeader.getStateRoot(),
                    block.blockHeader.getTransactionRoot(),
                    block.blockHeader.getReceiptRoot()
                });
        luyuBlock.setBytes(block.rawBytes);
        luyuBlock.setParentHash(new String[] {block.blockHeader.getPrevHash()});
        luyuBlock.setTimestamp(0); // TODO: add timestamp
        List<String> hashes =
                LuyuFabricTxAdapter.toLuyuFabricTxHashes(
                        block.getTransactionsHashes(), block.blockHeader.getNumber());
        luyuBlock.setTransactionHashs(hashes.toArray(new String[0]));
        return luyuBlock;
    }

    @Override
    public long getBlockNumber() {
        long blockNumber = -1;
        try {
            CompletableFuture<Long> future = new CompletableFuture<>();
            blockManager.asyncGetBlockNumber(
                    new BlockManager.GetBlockNumberCallback() {
                        @Override
                        public void onResponse(Exception e, long blockNumber) {
                            if (e != null) {
                                future.complete(new Long(-1));
                            } else {
                                future.complete(blockNumber);
                            }
                        }
                    });
            blockNumber = future.get(LuyuDefault.ADAPTER_QUERY_EXPIRES, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.warn("getBlockNumber exception: ", e);
        }
        return blockNumber;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public String getSignatureType() {
        return EcdsaSecp256r1WithSHA256.TYPE;
    }

    @Override
    public void listResources(ResourcesCallback callback) {

        try {
            List<Resource> resources = new ArrayList<>();

            for (ResourceInfo resourceInfo : name2ResourceInfo.values()) {
                Resource resource = new Resource();

                // path
                Path path = Path.decode(chainPath);
                path.setResource(resourceInfo.getName());
                resource.setPath(path.toString());

                // type
                resource.setType(getType());

                // method
                if (resourceInfo.getProperties().containsKey("methods")) {
                    ArrayList<String> methods =
                            (ArrayList<String>) resourceInfo.getProperties().get("methods");
                    resource.setMethods(methods.toArray(new String[] {}));
                }

                // properties
                Map<String, Object> properties = new HashMap<>();
                for (Map.Entry entry : resourceInfo.getProperties().entrySet()) {
                    if (entry.getKey().getClass().equals(String.class)) {
                        properties.put((String) entry.getKey(), entry.getValue());
                    }
                }
                resource.setProperties(properties);
                resources.add(resource);
            }

            callback.onResponse(QUERY_SUCCESS, "success", resources.toArray(new Resource[0]));
        } catch (Exception e) {
            logger.error("listResources exception: ", e);
            callback.onResponse(QUERY_FAILED, e.getMessage(), null);
        }
    }

    @Override
    public void registerEvents(Events events) {
        this.routerEventsHandler = events;
    }

    private com.webank.wecross.stub.Account toWeCrossAccount(String chainPath, Account account)
            throws Exception {
        return LuyuWeCrossAccount.build(chainPath, account);
    }

    private void sendCallbackTransaction(
            Account account,
            String resourceName,
            String callbackMethod,
            String[] args,
            long nonce) {
        Path callbackPath = null;
        try {
            callbackPath = Path.decode(chainPath);
            callbackPath.setResource(resourceName);
        } catch (Exception e) {
            logger.error("Chain path decode error, e:", e);
        }

        ArrayList<String> callbackArgs = new ArrayList<>();
        callbackArgs.add(new Long(nonce).toString()); // set
        // nonce
        for (String arg : args) {
            callbackArgs.add(arg);
        }
        Transaction callbackTx = new Transaction();
        callbackTx.setPath(callbackPath.toString());
        callbackTx.setMethod(callbackMethod);
        callbackTx.setArgs(callbackArgs.toArray(new String[] {}));
        // callbackTx.setSender(tnIdentity);
        callbackTx.setNonce(nonce);

        sendTransaction(
                account,
                callbackTx,
                new ReceiptCallback() {
                    @Override
                    public void onResponse(int status, String message, Receipt receipt) {
                        logger.debug("CallbackTx sended. {} {} {}", status, message, receipt);
                    }
                });
    }

    private X509Certificate convertStringToX509Cert(String certificate) throws Exception {
        InputStream targetStream = new ByteArrayInputStream(certificate.getBytes());
        return (X509Certificate)
                CertificateFactory.getInstance("X509").generateCertificate(targetStream);
    }
}
