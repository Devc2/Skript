package ch.njol.util;

import java.util.AbstractMap;
import java.util.Map;

public class NonNullPair<A, B> extends AbstractMap.SimpleEntry<A, B> {

    public NonNullPair(A key, B value) {
        super(key, value);
    }

    public A getFirst() {
        return getKey();
    }

    public B getSecond() {
        return getValue();
    }
}
