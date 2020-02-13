package com.fleetpin.graphql.aws.lambda;

import java.lang.ref.WeakReference;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;
import java.util.function.Supplier;

public class LambdaCache<K, V> {

	private final static ConcurrentLinkedQueue<WeakReference<LambdaCache<?, ?>>> entries = new ConcurrentLinkedQueue<>();

	private final Duration writeTTL;
	private final Map<K, CacheWrapper<V>> map;
	private final Function<K, V> producuer;

	public LambdaCache(Duration writeTTL, Function<K, V> builder) {
		this.writeTTL = writeTTL;
		this.producuer = builder;
		this.map = new ConcurrentHashMap<>();
		entries.add(new WeakReference<>(this));
	}
	
	private CacheWrapper<V> build(K key) {
		V value = producuer.apply(key);
		return new CacheWrapper<V>(Instant.now(), value);
	}
	
	public V get(K key) {
		return map.computeIfAbsent(key, this::build).value;
	}
	public V get(K key, Supplier<V> consumer) {
		return map.computeIfAbsent(key, __ -> new CacheWrapper<V>(Instant.now(), consumer.get())).value;
	}	
	
	public static void evict() {
		var now = Instant.now();
		var it = entries.iterator();
		while(it.hasNext()) {
			var v = it.next().get();
			if(v == null) {
				it.remove();
			}else {
				var vIt = v.map.values().iterator();
				while(vIt.hasNext()) {
					var value = vIt.next();
					if(value.added.plus(v.writeTTL).isBefore(now)) {
						vIt.remove();
					}
				}
			}
		}
	}

	private static class CacheWrapper<V> {
		private final Instant added;
		private final V value;
		public CacheWrapper(Instant added, V value) {
			this.added = added;
			this.value = value;
		}
	}

}
