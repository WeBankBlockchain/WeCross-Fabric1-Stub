package com.webank.wecross.stub.fabric;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webank.wecross.stub.ObjectMapperFactory;
import com.webank.wecross.utils.LRUCache;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.hyperledger.fabric.sdk.BlockEvent;
import org.hyperledger.fabric.sdk.ChaincodeEventListener;
import org.hyperledger.fabric.sdk.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChaincodeEventManager {
    public static final String SENDTX_EVENT_NAME = "_event_sendTransaction";
    public static final String CALL_EVENT_NAME = "_event_call";

    private static Logger logger = LoggerFactory.getLogger(ChaincodeEventManager.class);
    private static ObjectMapper objectMapper = ObjectMapperFactory.getObjectMapper();
    private Channel channel;
    private Set<String> eventNames = new HashSet<>();
    private LRUCache<Long, EventPacket> eventsCache = new LRUCache<>(128); // for idempotence

    private Map<String, List<ChaincodeEvent>> chaincodeName2Events = new HashMap<>();

    public ChaincodeEventManager(Channel channel) {
        this.channel = channel;
    }

    public interface ChaincodeEvent {
        void onEvent(String name, byte[] data);
    }

    public static class EventPacket {
        public String operation; // empty from chain: tnSendTransaction/tnCall
        public String path;
        public String method;
        public String[] args;
        public String identity; // first-class identity
        public String callbackMethod;
        public long nonce;
        public String sender;
        public String name; // empty from chain, resourceName

        @Override
        public String toString() {
            return "EventPacket{"
                    + "operation='"
                    + operation
                    + '\''
                    + ", path='"
                    + path
                    + '\''
                    + ", method='"
                    + method
                    + '\''
                    + ", args="
                    + Arrays.toString(args)
                    + ", identity='"
                    + identity
                    + '\''
                    + ", callbackMethod='"
                    + callbackMethod
                    + '\''
                    + ", nonce="
                    + nonce
                    + ", sender='"
                    + sender
                    + '\''
                    + ", name='"
                    + name
                    + '\''
                    + '}';
        }
    }

    public void registerEvent(String chaincodeName, ChaincodeEvent event) throws Exception {

        if (!chaincodeName2Events.containsKey(chaincodeName)) {
            chaincodeName2Events.put(chaincodeName, new LinkedList<ChaincodeEvent>());
        }

        List<ChaincodeEvent> events = chaincodeName2Events.get(chaincodeName);

        String eventName1 =
                channel.registerChaincodeEventListener(
                        Pattern.compile("^" + chaincodeName + "$"),
                        Pattern.compile("^" + SENDTX_EVENT_NAME + "$"),
                        new ChaincodeEventListener() {
                            @Override
                            public void received(
                                    String handle,
                                    BlockEvent blockEvent,
                                    org.hyperledger.fabric.sdk.ChaincodeEvent chaincodeEvent) {
                                logger.debug(
                                        "On chaincode {} event: {} {} {} {} {}",
                                        SENDTX_EVENT_NAME,
                                        handle,
                                        chaincodeEvent.getChaincodeId(),
                                        chaincodeEvent.getEventName(),
                                        new String(chaincodeEvent.getPayload()));

                                try {
                                    // TODO: Verify payload
                                    EventPacket packet =
                                            objectMapper.readValue(
                                                    chaincodeEvent.getPayload(),
                                                    new TypeReference<EventPacket>() {});

                                    // get creator
                                    blockEvent
                                            .getTransactionEvents()
                                            .forEach(
                                                    new Consumer<BlockEvent.TransactionEvent>() {
                                                        @Override
                                                        public void accept(
                                                                BlockEvent.TransactionEvent
                                                                        transactionEvent) {
                                                            if (transactionEvent
                                                                    .getTransactionID()
                                                                    .equals(
                                                                            chaincodeEvent
                                                                                    .getTxId())) {
                                                                String creator =
                                                                        transactionEvent
                                                                                .getCreator()
                                                                                .getId();
                                                                packet.sender = creator;
                                                            }
                                                        }
                                                    });

                                    if (packet.sender == null) {
                                        logger.debug(
                                                "Could not get creator from event: {}", handle);
                                        return;
                                    } else {
                                        logger.debug(
                                                "Get creator {} of event {}",
                                                packet.sender,
                                                handle);
                                    }

                                    synchronized (eventsCache) {
                                        EventPacket has = eventsCache.get(packet.nonce);
                                        if (has != null) {
                                            return; // for ignoring same callback
                                        } else {
                                            eventsCache.put(packet.nonce, packet);
                                        }
                                    }

                                    logger.debug(
                                            "Handle chaincode event: {} {} {} {} {}",
                                            handle,
                                            chaincodeEvent.getChaincodeId(),
                                            chaincodeEvent.getEventName(),
                                            new String(chaincodeEvent.getPayload()));

                                    packet.operation = SENDTX_EVENT_NAME;
                                    packet.name = chaincodeName;

                                    event.onEvent(
                                            chaincodeName, objectMapper.writeValueAsBytes(packet));
                                } catch (Exception e) {
                                    logger.warn("On {} event exception: ", SENDTX_EVENT_NAME, e);
                                }
                            }
                        });
        logger.info("Register chaincode event {} on {}", SENDTX_EVENT_NAME, chaincodeName);
        eventNames.add(eventName1);

        String eventName2 =
                channel.registerChaincodeEventListener(
                        Pattern.compile("^" + chaincodeName + "$"),
                        Pattern.compile("^" + CALL_EVENT_NAME + "$"),
                        new ChaincodeEventListener() {
                            @Override
                            public void received(
                                    String handle,
                                    BlockEvent blockEvent,
                                    org.hyperledger.fabric.sdk.ChaincodeEvent chaincodeEvent) {
                                logger.debug(
                                        "On chaincode {} event: {} {} {} {} {}",
                                        CALL_EVENT_NAME,
                                        handle,
                                        chaincodeEvent.getChaincodeId(),
                                        chaincodeEvent.getEventName(),
                                        new String(chaincodeEvent.getPayload()));

                                try {
                                    // TODO: Verify payload
                                    EventPacket packet =
                                            objectMapper.readValue(
                                                    chaincodeEvent.getPayload(),
                                                    new TypeReference<EventPacket>() {});

                                    // get creator
                                    blockEvent
                                            .getTransactionEvents()
                                            .forEach(
                                                    new Consumer<BlockEvent.TransactionEvent>() {
                                                        @Override
                                                        public void accept(
                                                                BlockEvent.TransactionEvent
                                                                        transactionEvent) {
                                                            if (transactionEvent
                                                                    .getTransactionID()
                                                                    .equals(
                                                                            chaincodeEvent
                                                                                    .getTxId())) {
                                                                String creator =
                                                                        transactionEvent
                                                                                .getCreator()
                                                                                .getId();
                                                                packet.sender = creator;
                                                            }
                                                        }
                                                    });

                                    if (packet.sender == null) {
                                        logger.debug(
                                                "Could not get creator from event: {}", handle);
                                        return;
                                    } else {
                                        logger.debug(
                                                "Get creator {} of event {}",
                                                packet.sender,
                                                handle);
                                    }

                                    synchronized (eventsCache) {
                                        EventPacket has = eventsCache.get(packet.nonce);
                                        if (has != null) {
                                            return; // for ignoring same callback
                                        } else {
                                            eventsCache.put(packet.nonce, packet);
                                        }
                                    }

                                    logger.debug(
                                            "Handle chaincode event: {} {} {} {} {}",
                                            handle,
                                            chaincodeEvent.getChaincodeId(),
                                            chaincodeEvent.getEventName(),
                                            new String(chaincodeEvent.getPayload()));

                                    packet.operation = CALL_EVENT_NAME;
                                    packet.name = chaincodeName;
                                    event.onEvent(
                                            chaincodeName, objectMapper.writeValueAsBytes(packet));
                                } catch (Exception e) {
                                    logger.warn("On {} event exception: ", CALL_EVENT_NAME, e);
                                }
                            }
                        });
        logger.info("Register chaincode event {} on {}", CALL_EVENT_NAME, chaincodeName);
        eventNames.add(eventName2);

        events.add(event);
    }
}
