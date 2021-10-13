package org.trustnet.protocol.link.fabric1;

import java.util.AbstractMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class TnFabricTxAdapter {
    /**
     * Because we couldn't get block number by txHash in fabric api. So we need to add blockNumber
     * in txHash and return to upper level. We define: TnFabricTxHash = <original txHash> + '-' +
     * <blockNumber> Eg: 0x6507f293020abd974247302189fc84251523e57fea3b844ef79feb439c37f714-16 which
     * means that: original txHash is 0x65..714 and belongs to block 16
     *
     * @param txHash
     * @param blockNumber
     * @return TnFabricTxHash
     */
    public static String toTnFabricTxHash(String txHash, Long blockNumber) {
        return txHash + '-' + blockNumber;
    }

    /**
     * @param luyuFabricTxHash
     * @return Entry(original txHash, blockNumber)
     */
    public static Map.Entry<String, Long> parseFromTnFabricTxHash(String luyuFabricTxHash)
            throws Exception {
        String[] sp = luyuFabricTxHash.split("-");
        if (sp.length != 2) {
            throw new Exception("Invalid luyuFabricTxHash format: " + luyuFabricTxHash);
        }

        String txHash = sp[0];
        Long blockNumber = new Long(sp[1]);

        return new AbstractMap.SimpleEntry<String, Long>(txHash, blockNumber);
    }

    public static List<String> toTnFabricTxHashes(List<String> txHashs, Long blockNumber) {
        List<String> res = new LinkedList<>();
        for (String txHash : txHashs) {
            res.add(toTnFabricTxHash(txHash, blockNumber));
        }
        return res;
    }
}
