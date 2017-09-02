package com.ldw.structure;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public interface SerDeserializer<T> {
    public void write(T value, DataOutputStream dos) throws IOException;

    public T read(DataInputStream ds) throws IOException;
}
