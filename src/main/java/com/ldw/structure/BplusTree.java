package com.ldw.structure;

import com.ldw.store.Store;

import java.io.FileNotFoundException;
import java.util.ArrayDeque;
import java.util.Deque;

public class BplusTree implements BTree {

    private Node root;

    private int M;

    private Node head;

    private int high = 0;

    private Store store;

    public Store getStore() {
        return store;
    }

    public Node getRoot() {
        return root;
    }

    public void setRoot(Node root) {
        this.root = root;
    }

    public int getM() {
        return M;
    }

    public void setM(int m) {
        M = m;
    }

    public Node getHead() {
        return head;
    }

    public void setHead(Node head) {
        this.head = head;
    }

    public int getHigh() {
        return high;
    }

    public void setHigh(int high) {
        this.high = high;
    }

    @Override
    public Object get(Comparable key) {
        return root.get(key);
    }

    @Override
    public void remove(Comparable key) {
        root.remove(key, this);
    }

    @Override
    public void insertOrUpdate(Comparable key, Object obj) {
        root.insertOrUpdate(key, obj, this);
    }

//    public BplusTree(int order) {
//        if (order < 3) {
//            throw new RuntimeException("order must be greater than 2");
//        }
//        M = order;
//        root = new Node(true, true, getStore().getBlockPoint());
//        head = root;
//    }

    public BplusTree(int order, Store store) throws FileNotFoundException {
        if (order < 3) {
            throw new RuntimeException("order must be greater than 2");
        }
        M = order;
        root = new Node(true, true, store.getBlockPoint());
        head = root;

        this.store = store;
    }

    public void printTree() {
        System.out.println(root.toString());
        Deque<Node> deque = new ArrayDeque();
        deque.addAll(root.getChildren());
        Node node;
        while ((node = deque.poll()) != null) {
            if (!node.isLeaf()) {
                deque.addAll(node.getChildren());
            }
            System.out.println(node.toString());
        }

    }
}
