package com.liskovsoft.mediaserviceinterfaces.data;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import io.reactivex.rxjava3.core.Observable;

/** Coalesces active work. Detaching one screen does not cancel a shared provider request. */
public final class SharedRequest<T> {
    private final Map<String, Observable<T>> active = new HashMap<>();
    public Observable<T> running(String prefix) {
        synchronized (active) {
            for (Map.Entry<String, Observable<T>> entry : active.entrySet())
                if (entry.getKey().startsWith(prefix)) return entry.getValue();
            return null;
        }
    }
    public Observable<T> get(String key, Supplier<Observable<T>> source) {
        return Observable.defer(() -> {
            synchronized (active) {
                Observable<T> running = active.get(key);
                if (running != null) return running;
                Observable<T> request = Observable.defer(source::get)
                        .doFinally(() -> { synchronized (active) { active.remove(key); } }).cache();
                active.put(key, request);
                return request;
            }
        });
    }
}
