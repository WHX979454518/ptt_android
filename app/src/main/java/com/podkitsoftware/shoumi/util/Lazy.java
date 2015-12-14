package com.podkitsoftware.shoumi.util;

import rx.functions.Func0;

/**
 *
 * 延时线程安全的初始化模板
 *
 * Created by fanchao on 13/12/15.
 */
public class Lazy<T> {
    private final Func0<T> initializer;
    private T obj;

    public Lazy(final Func0<T> initializer) {
        this.initializer = initializer;
    }

    public Lazy() {
        this(null);
    }

    public T get() {
        synchronized (this) {
            if (obj == null) {
                obj = initialize();
            }
        }

        return obj;
    }

    protected T initialize() {
        if (initializer != null) {
            return initializer.call();
        }

        throw new IllegalStateException("You need to provide either initializer or override initialize()");
    }
}
