package com.ldw.store;

import com.ldw.structure.Node;


public interface Store {

    void save(Node node);

    Node load(int pointer);

    int getBlockPoint();

    void release(int point);
}
