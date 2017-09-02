package com.ldw.structure;

import java.io.*;
import java.util.ArrayDeque;
import java.util.Comparator;

public class BPlusTree<K> {
    private final int BLOCK_SIZE = 1024;

    private final int VALUE_SIZE = 8;

    private int keysize;

    private RandomAccessFile treeStore;

    private BPlusNode<K> root = null;

    private int nextBlockPointer = 0;

    private boolean newTree = false;

    private SerDeserializer<K> keySerDeser;

    private Comparator<K> keyComparator;

    private int M = 0;

    public BPlusTree(File store, int keysize, int recordsize,
                     SerDeserializer<K> sd, Comparator<K> com) throws IOException {
        if (store.exists()) {
            newTree = false;
        } else {
            newTree = true;
        }

        keySerDeser = sd;
        keyComparator = com;

        this.keysize = keysize;
        M = (BLOCK_SIZE - 14) / (keysize + VALUE_SIZE);

        treeStore = new RandomAccessFile(store, "rw");

        load();

        return;
    }

    public void decNextBlockPointer() {
        if (nextBlockPointer > 1)
            nextBlockPointer--;
    }

    public int getNextBlockPointer() {
        int next = nextBlockPointer;
        nextBlockPointer++;
        return next;
    }

    protected int getNumKeysPerBlock() {
        return M;
    }

    public SerDeserializer<K> getSerDeserializer() {
        return keySerDeser;
    }

    public Comparator<K> getKeyComparator() {
        return keyComparator;
    }


    public K find(K key) {
        return root.find(key);
    }

    public void insert(K key, long val) {
        BPlusNode<K> node = null;

        if (root == null) {
            node = new BPlusNode<>(this);
            root = node;
            node.setRoot(true);
            node.setLeaf(true);
        }

        BPlusNode<K> newChild = root.insert(key, val);

        if (newChild != null) {
            BPlusNode<K> newnode = new BPlusNode<K>(this);
            newnode.setRoot(true);
            newnode.setLeaf(false);
            root.setRoot(false);
            root.moveBlock();
            newnode.addChildren(root, newChild);

            newnode.writeToDisk();
            root = newnode;
        }
    }


    private void load() throws IOException {
        try {
            long fsize = treeStore.length();
            int numblocks = (int) fsize / this.BLOCK_SIZE;

            nextBlockPointer = numblocks;
            root = readFromDisk(0);

        } catch (EOFException e) {
            root = null;
        }
    }


    public void writeToDisk(BPlusNode<K> node) throws IOException {
        int pointer = node.getPointer();
        treeStore.seek(pointer * BLOCK_SIZE);

        ByteArrayOutputStream bos = new ByteArrayOutputStream(BLOCK_SIZE);
        DataOutputStream ds = new DataOutputStream(bos);

        if (node.isLeaf()) {
            node.writeLeaf(ds);
        } else {
            node.writeNonLeaf(ds);
        }

        int recsize = bos.size();
        int fill = BLOCK_SIZE - recsize;
        for (int i = 1; i <= fill; i++) {
            bos.write(0);
        }

        byte[] recode = bos.toByteArray();
        if (recode.length != BLOCK_SIZE) {
            node.printNode();
            throw new RuntimeException("rec size=" + recode.length + " block size=" + BLOCK_SIZE);
        }

        treeStore.write(recode);
    }

    public BPlusNode<K> readFromDisk(int blockPointer) throws IOException {
        treeStore.seek(blockPointer * BLOCK_SIZE);

        int freeOrNot = treeStore.readByte();

        if (freeOrNot == 1) {
            byte[] b = new byte[BLOCK_SIZE - 1];
            treeStore.readFully(b, 0, BLOCK_SIZE - 1);
            return new BPlusNode<K>(this, b, blockPointer);
        }
        return null;
    }


    public void printTree() throws IOException {
        if (root == null) {
            System.out.println("Root is null. Tree is empty");
            return;
        }

        ArrayDeque<Integer> queue = new ArrayDeque<>();
        root.printNode();

        queue.addAll(root.getChildren());

        Integer current = null;
        while (!queue.isEmpty() && (current = queue.poll()) != null) {
            BPlusNode cNode = readFromDisk(current);
            cNode.printNode();
            queue.addAll(cNode.getChildren());
        }
    }
}
