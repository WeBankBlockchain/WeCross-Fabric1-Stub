package org.luyu.protocol.link.fabric1;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webank.wecross.account.FabricAccountFactory;
import com.webank.wecross.stub.Account;
import com.webank.wecross.stub.AccountFactory;
import com.webank.wecross.stub.Block;
import com.webank.wecross.stub.BlockManager;
import com.webank.wecross.stub.Connection;
import com.webank.wecross.stub.ObjectMapperFactory;
import com.webank.wecross.stub.Path;
import com.webank.wecross.stub.Request;
import com.webank.wecross.stub.ResourceInfo;
import com.webank.wecross.stub.Response;
import com.webank.wecross.stub.TransactionContext;
import com.webank.wecross.stub.TransactionException;
import com.webank.wecross.stub.TransactionRequest;
import com.webank.wecross.stub.TransactionResponse;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.luyu.protocol.link.Driver;
import org.luyu.protocol.network.CallRequest;
import org.luyu.protocol.network.CallResponse;
import org.luyu.protocol.network.Events;
import org.luyu.protocol.network.Receipt;
import org.luyu.protocol.network.Resource;
import org.luyu.protocol.network.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LuyuDriverAdapter implements Driver {
    private static Logger logger = LoggerFactory.getLogger(LuyuDriverAdapter.class);
    private static final int QUERY_SUCCESS = 0;
    private static final int QUERY_FAILED = 99100;

    private String type;
    private String chainPath;
    private com.webank.wecross.stub.Driver wecrossDriver;
    private LuyuWeCrossConnection luyuWeCrossConnection;
    private LuyuMemoryBlockManager blockManager;
    private AccountFactory accountFactory;
    private ObjectMapper objectMapper = ObjectMapperFactory.getObjectMapper();
    private Map<String, ResourceInfo> name2ResourceInfo = new HashMap<>();

    public LuyuDriverAdapter(
            String type,
            String chainPath,
            com.webank.wecross.stub.Driver wecrossDriver,
            LuyuWeCrossConnection luyuWeCrossConnection,
            LuyuMemoryBlockManager blockManager,
            AccountFactory accountFactory) {
        this.type = type;
        this.chainPath = chainPath;
        this.wecrossDriver = wecrossDriver;
        this.luyuWeCrossConnection = luyuWeCrossConnection;
        this.blockManager = blockManager;
        this.accountFactory = accountFactory;

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
                            }

                            name2ResourceInfo = newName2ResourceInfo;
                        }
                    }
                });
    }

    @Override
    public void sendTransaction(Transaction request, ReceiptCallback callback) {
        try {
            Account account = toAccount(request.getKey());
            Path path = Path.decode(request.getPath());
            ResourceInfo resourceInfo = name2ResourceInfo.get(path.getResource());
            if (resourceInfo == null) {
                throw new Exception("Resource " + request.getPath() + " not found.");
            }

            TransactionContext context =
                    new TransactionContext(account, path, resourceInfo, blockManager);
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
                                receipt.setTransactionHash(transactionResponse.getHash());
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
    public void call(CallRequest request, CallResponseCallback callback) {
        try {
            Account account = toAccount(request.getKey());
            Path path = Path.decode(request.getPath());
            ResourceInfo resourceInfo = name2ResourceInfo.get(path.getResource());
            if (resourceInfo == null) {
                throw new Exception("Resource " + request.getPath() + " not found.");
            }

            TransactionContext context =
                    new TransactionContext(account, path, resourceInfo, blockManager);
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
        // blockNumber is not used by BCOS Driver, TODO: Fabric stub support this
        wecrossDriver.asyncGetTransaction(
                txHash,
                0,
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
                                receipt.setResult(transaction.getTransactionResponse().getResult());
                                receipt.setCode(
                                        transaction.getTransactionResponse().getErrorCode());
                                receipt.setMessage(
                                        transaction.getTransactionResponse().getMessage());
                                receipt.setPath(path.toString());
                                receipt.setMethod(transaction.getTransactionRequest().getMethod());
                                receipt.setArgs(transaction.getTransactionRequest().getArgs());
                                receipt.setTransactionHash(txHash);
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
                                org.luyu.protocol.network.Block luyuBlock = toLuyuBlock(block);
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
                                org.luyu.protocol.network.Block luyuBlock = toLuyuBlock(block);
                                callback.onResponse(QUERY_SUCCESS, "success", luyuBlock);
                            }
                        }
                    });
        }
    }

    private org.luyu.protocol.network.Block toLuyuBlock(Block block) {
        org.luyu.protocol.network.Block luyuBlock = new org.luyu.protocol.network.Block();
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
    public byte[] accountSign(byte[] key, byte[] message) {
        Account account = toAccount(key);
        return wecrossDriver.accountSign(account, message);
    }

    @Override
    public boolean accountVerify(byte[] identity, byte[] signBytes, byte[] message) {
        return wecrossDriver.accountVerify(new String(identity), signBytes, message);
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public void listResources(ResourcesCallback callback) {
        Request request = new Request();
        request.setPath(chainPath);
        request.setType(LuyuDefault.LIST_RESOURCES);
        luyuWeCrossConnection.asyncSend(
                request,
                new Connection.Callback() {
                    @Override
                    public void onResponse(Response response) {
                        try {
                            if (response.getErrorCode() != 0) {
                                callback.onResponse(
                                        response.getErrorCode(), response.getErrorMessage(), null);
                                return;
                            } else {
                                Collection<Resource> resources = new HashSet<>();
                                resources =
                                        objectMapper.readValue(
                                                response.getData(),
                                                new TypeReference<Collection<Resource>>() {});

                                callback.onResponse(
                                        QUERY_SUCCESS,
                                        "Success",
                                        resources.toArray(new Resource[resources.size()]));
                            }

                        } catch (Exception e) {
                            callback.onResponse(QUERY_FAILED, e.getMessage(), null);
                        }
                    }
                });
    }

    @Override
    public void registerEvents(Events events) {
        // TODO: implement this
    }

    private Account toAccount(byte[] key) {
        try {
            // TODO: compat with account manager
            Map<String, Object> properties =
                    objectMapper.readValue(key, new TypeReference<Map<String, Object>>() {});
            properties.put("keyID", new Integer(0)); // not used at all
            properties.put("type", type);
            properties.put("isDefault", false); // not used at all
            FabricAccountFactory accountFactory = new FabricAccountFactory();

            return accountFactory.build(properties);
        } catch (Exception e) {
            logger.error("toAccount exception: ", e);
            return null;
        }
    }
}
