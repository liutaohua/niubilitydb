package com.ldw.structures.btree;

import com.ldw.structures.holders.DataHolder;

import java.lang.reflect.Array;

public class LeafNode<K extends DataHolder<K>, V extends DataHolder<V>> extends Node<K, V> {
    public V[] values;

    public int leftPointer = 0;
    public int rightPointer = 0;

    protected LeafNode(BPlusTree<K, V> tree) {
        super(tree);
        this.values = (V[]) Array.newInstance(tree.factoryV().getClass(), getBOrder());
    }

    @Override
    public Node<K, V> split() {
        LeafNode<K, V> newHigh = tree.createLeafNode();
        newHigh.allocPointer();

        int mid = allocated / 2;
        int newSize = allocated - mid;

        System.arraycopy(keys, mid, newHigh.keys, 0, newSize);
        System.arraycopy(values, mid, newHigh.values, 0, newSize);

        for (int i = mid; i < allocated; i++) {
            keys[i] = null;
            values[i] = null;
        }
        newHigh.allocated = newSize;
        allocated -= newSize;

        if (rightPointer != 0) {
            LeafNode<K, V> oldHigh = (LeafNode<K, V>) tree.getNode(rightPointer);
            oldHigh.leftPointer = newHigh.pointer;
            tree.putNode(oldHigh);
        }

        newHigh.leftPointer = pointer;
        newHigh.rightPointer = rightPointer;
        rightPointer = newHigh.pointer;

        if (leftPointer == 0) {
            tree.high = newHigh.pointer;
        }

        tree.putNode(this);
        tree.putNode(newHigh);

        return newHigh;
    }

    @Override
    public K splitLeftShiftKeys() {
        return keys[0];
    }

    @Override
    public boolean remove(int index) {
        if (index < 0) {
            return false;
        }

        if (index < allocated) {
            moveElementsLeft(keys, index);
            moveElementsLeft(values, index);
        }

        if (allocated > 0) {
            allocated--;
        }

        keys[allocated] = null;
        values[allocated] = null;
        return true;
    }

    @Override
    public boolean isLeaf() {
        return true;
    }

    @Override
    public int getBOrder() {
        return tree.getM() / 2;
    }

    public boolean add(int index, K key, V val) {
        if (index < allocated) {
            moveElementsRight(keys, index);
            moveElementsRight(values, index);
        }

        allocated++;

        keys[index] = key;
        values[index] = val;

        return true;
    }


}
