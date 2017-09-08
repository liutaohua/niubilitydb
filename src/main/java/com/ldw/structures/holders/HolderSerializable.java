package com.ldw.structures.holders;

import java.nio.ByteBuffer;

public interface HolderSerializable<T> {

    public void serialize(final ByteBuffer buf);

    public T deserialize(final ByteBuffer buf);
}
