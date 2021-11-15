package org.trustnet.protocol.link.fabric1;

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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.trustnet.protocol.algorithm.ecdsa.secp256r1.EcdsaSecp256r1WithSHA256;
import org.trustnet.protocol.common.STATUS;
import org.trustnet.protocol.link.Driver;
import org.trustnet.protocol.network.Account;
import org.trustnet.protocol.network.CallRequest;
import org.trustnet.protocol.network.CallResponse;
import org.trustnet.protocol.network.Events;
import org.trustnet.protocol.network.Receipt;
import org.trustnet.protocol.network.Resource;
import org.trustnet.protocol.network.Transaction;

public class TnDriverAdapter implements Driver {
    private static Logger logger = LoggerFactory.getLogger(TnDriverAdapter.class);
    private static ObjectMapper objectMapper = ObjectMapperFactory.getObjectMapper();
    private static final int QUERY_SUCCESS = 0;
    private static final int QUERY_FAILED = 99100;

    private String type;
    private String chainPath;
    private com.webank.wecross.stub.Driver wecrossDriver;
    private TnWeCrossConnection tnWeCrossConnection;
    private TnMemoryBlockManager blockManager;
    private Map<String, ResourceInfo> name2ResourceInfo = new HashMap<>();
    private Events routerEventsHandler;

    public TnDriverAdapter(
            String type,
            String chainPath,
            com.webank.wecross.stub.Driver wecrossDriver,
            TnWeCrossConnection tnWeCrossConnection,
            TnMemoryBlockManager blockManager) {
        this.type = type;
        this.chainPath = chainPath;
        this.wecrossDriver = wecrossDriver;
        this.tnWeCrossConnection = tnWeCrossConnection;
        this.blockManager = blockManager;

        tnWeCrossConnection.setConnectionEventHandler(
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

                                if (tnWeCrossConnection.getLuyuConnection()
                                        instanceof TnConnectionAdapter) {
                                    // is local connection
                                    TnConnectionAdapter connectionAdapter =
                                            (TnConnectionAdapter)
                                                    tnWeCrossConnection.getLuyuConnection();

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
            String chaincodeName, TnConnectionAdapter connectionAdapter) {
        connectionAdapter.subscribe(
                TnConnectionAdapter.ON_CHAINCODEVENT,
                chaincodeName.getBytes(StandardCharsets.UTF_8),
                new org.trustnet.protocol.link.Connection.Callback() {
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

                            if (packet.operation.equals(ChaincodeEventManager.SENDTX_EVENT_NAME)) {
                                Transaction request = new Transaction();
                                request.setPath(packet.path);
                                request.setMethod(packet.method);
                                request.setArgs(packet.args);
                                request.setSender(packet.identity);
                                request.setNonce(System.currentTimeMillis()); // TODO:Add seq

                                routerEventsHandler.submit(
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
                                                                try {
                                                                    Path path =
                                                                            Path.decode(chainPath);
                                                                    path.setResource(packet.name);
                                                                    Transaction response =
                                                                            new Transaction();
                                                                    response.setMethod(
                                                                            packet.callbackMethod);
                                                                    response.setArgs(
                                                                            receipt.getResult());
                                                                    response.setNonce(
                                                                            request.getNonce());
                                                                    response.setSender(
                                                                            packet.identity);
                                                                    submit(
                                                                            account,
                                                                            response,
                                                                            new ReceiptCallback() {
                                                                                @Override
                                                                                public void
                                                                                        onResponse(
                                                                                                int
                                                                                                        i,
                                                                                                String
                                                                                                        s,
                                                                                                Receipt
                                                                                                        receipt) {
                                                                                    if (i
                                                                                            != STATUS.OK) {
                                                                                        logger
                                                                                                .error(
                                                                                                        "Response to chain {} {} {}",
                                                                                                        i,
                                                                                                        s,
                                                                                                        receipt);
                                                                                    } else {
                                                                                        logger
                                                                                                .debug(
                                                                                                        "Response to chain {} {} {}",
                                                                                                        i,
                                                                                                        s,
                                                                                                        receipt);
                                                                                    }
                                                                                }
                                                                            });
                                                                } catch (Exception e) {
                                                                    logger.error(
                                                                            "Response to chain e:",
                                                                            e);
                                                                }
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
                                request.setNonce(System.currentTimeMillis()); // TODO:Add seq
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
                                                                try {
                                                                    Path path =
                                                                            Path.decode(chainPath);
                                                                    path.setResource(packet.name);
                                                                    Transaction response =
                                                                            new Transaction();
                                                                    response.setMethod(
                                                                            packet.callbackMethod);
                                                                    response.setArgs(
                                                                            callResponse
                                                                                    .getResult());
                                                                    response.setNonce(
                                                                            request.getNonce());
                                                                    response.setSender(
                                                                            packet.identity);
                                                                    submit(
                                                                            account,
                                                                            response,
                                                                            new ReceiptCallback() {
                                                                                @Override
                                                                                public void
                                                                                        onResponse(
                                                                                                int
                                                                                                        i,
                                                                                                String
                                                                                                        s,
                                                                                                Receipt
                                                                                                        receipt) {
                                                                                    if (i
                                                                                            != STATUS.OK) {
                                                                                        logger
                                                                                                .error(
                                                                                                        "Response to chain {} {} {}",
                                                                                                        i,
                                                                                                        s,
                                                                                                        receipt);
                                                                                    } else {
                                                                                        logger
                                                                                                .debug(
                                                                                                        "Response to chain {} {} {}",
                                                                                                        i,
                                                                                                        s,
                                                                                                        receipt);
                                                                                    }
                                                                                }
                                                                            });
                                                                } catch (Exception e) {
                                                                    logger.error(
                                                                            "Response to chain e:",
                                                                            e);
                                                                }
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
    public void submit(Account account, Transaction request, ReceiptCallback callback) {
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
                    tnWeCrossConnection,
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
                                        TnFabricTxAdapter.toTnFabricTxHash(
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
                    tnWeCrossConnection,
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
            Map.Entry<String, Long> entry = TnFabricTxAdapter.parseFromTnFabricTxHash(txHash);
            String orignalTxHash = entry.getKey();
            Long blockNumber = entry.getValue();

            wecrossDriver.asyncGetTransaction(
                    orignalTxHash,
                    blockNumber,
                    blockManager,
                    true,
                    tnWeCrossConnection,
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
                                org.trustnet.protocol.network.Block luyuBlock = toTnBlock(block);
                                callback.onResponse(QUERY_SUCCESS, "success", luyuBlock);
                            }
                        }
                    });
        } else {
            wecrossDriver.asyncGetBlock(
                    blockNumber,
                    false,
                    tnWeCrossConnection,
                    new com.webank.wecross.stub.Driver.GetBlockCallback() {
                        @Override
                        public void onResponse(Exception e, Block block) {
                            if (e != null) {
                                callback.onResponse(QUERY_FAILED, e.getMessage(), null);
                            } else {
                                org.trustnet.protocol.network.Block luyuBlock = toTnBlock(block);
                                callback.onResponse(QUERY_SUCCESS, "success", luyuBlock);
                            }
                        }
                    });
        }
    }

    private org.trustnet.protocol.network.Block toTnBlock(Block block) {
        org.trustnet.protocol.network.Block luyuBlock = new org.trustnet.protocol.network.Block();
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
                TnFabricTxAdapter.toTnFabricTxHashes(
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
            blockNumber = future.get(TnDefault.ADAPTER_QUERY_EXPIRES, TimeUnit.SECONDS);
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
            Collection<Resource> resources = new HashSet<>();

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
            callback.onResponse(
                    QUERY_SUCCESS, "success", resources.toArray(new Resource[resources.size()]));
        } catch (Exception e) {
            callback.onResponse(QUERY_FAILED, e.getMessage(), null);
        }
    }

    @Override
    public void registerEvents(Events events) {
        this.routerEventsHandler = events;
    }

    private com.webank.wecross.stub.Account toWeCrossAccount(String chainPath, Account account)
            throws Exception {
        return TnWeCrossAccount.build(chainPath, account);
    }
}
