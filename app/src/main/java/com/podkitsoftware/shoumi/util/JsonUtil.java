package com.podkitsoftware.shoumi.util;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.Iterator;

import rx.functions.Func1;

/**
 * Created by fanchao on 15/12/15.
 */
public class JsonUtil {

    public static <T, R> Iterable<R> fromArray(final JSONArray array, final Func1<T, R> transformer) {
        return () -> new Iterator<R>() {
            int currIndex = 0;

            @Override
            public boolean hasNext() {
                return currIndex < array.length();
            }

            @Override
            public R next() {
                try {
                    return transformer.call((T) array.get(currIndex++));
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }
}
