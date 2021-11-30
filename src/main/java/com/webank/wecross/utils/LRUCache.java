package com.webank.wecross.utils;

import java.util.LinkedHashMap;

public class LRUCache<K, V> {

    private LinkedHashMap<K, V> lruCacheMap;
    private final int capacity;
    private final boolean SORT_BY_ACCESS = true;
    private final float LOAD_FACTOR = 0.75F;

    public LRUCache(int capacity) {
        synchronized (this) {
            this.capacity = capacity;
            this.lruCacheMap = new LinkedHashMap<>(capacity, LOAD_FACTOR, SORT_BY_ACCESS);
        }
    }

    public V get(K k) {
        synchronized (this) {
            return lruCacheMap.get(k);
        }
    }

    public void put(K k, V v) {
        synchronized (this) {
            if (lruCacheMap.containsKey(k)) {
                lruCacheMap.remove(k);
            } else if (lruCacheMap.size() >= capacity) {
                lruCacheMap.remove(lruCacheMap.keySet().iterator().next());
            }
            lruCacheMap.put(k, v);
        }
    }

    public void clear() {
        synchronized (this) {
            lruCacheMap.clear();
        }
    }
}
