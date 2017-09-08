package com.ldw.structures.holders;

import java.nio.ByteBuffer;

public abstract class DataHolder<T> implements Comparable<T>, HolderSerializable<T> {

    // ========= Basic Object methods =========

    @Override
    abstract public String toString();

    @Override
    abstract public int hashCode();

    // ========= Comparable =========

    @Override
    abstract public boolean equals(final Object obj);

    @Override
    abstract public int compareTo(final T another);

    // ========= Serialization =========

    @Override
    abstract public void serialize(final ByteBuffer buf);

    @Override
    abstract public T deserialize(final ByteBuffer buf);
}
