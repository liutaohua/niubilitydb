package com.ldw.structures.btree;

import com.ldw.structures.holders.DataHolder;

public class InternalNode<K extends DataHolder<K>, V extends DataHolder<V>> extends Node<K, V> {
    public int[] childs;
    public K[] keys;
    public int allocated;

    protected InternalNode(BPlusTree<K, V> tree) {
        super(tree);
        this.childs = new int[getBOrder()];
    }


    @Override
    public Node<K, V> split() {
        InternalNode<K, V> newHigh = tree.createInternalNode();
        newHigh.allocPointer();

        int mid = allocated / 2;
        int newSize = allocated - mid;

        System.arraycopy(keys, mid, newHigh.keys, 0, newSize);
        System.arraycopy(childs, mid + 1, newHigh.childs, 0, newSize);

        for (int i = mid; i < allocated; i++) {
            keys[i] = null;
            childs[i + 1] = 0;
        }
        newHigh.allocated = newSize;
        allocated -= newSize;

        tree.putNode(this);
        tree.putNode(newHigh);

        return newHigh;
    }

    @Override
    public K splitLeftShiftKeys() {
        K removed = keys[0];
        moveElementsLeft(keys, 0);
        allocated--;

        keys[allocated] = null;
        childs[allocated + 1] = 0;
        return removed;
    }

    @Override
    public boolean remove(int index) {
        if (index < 0) {
            return false;
        }

        if (index < allocated) {
            moveElementsLeft(keys, index);
            moveChildsLeft(index);
        }

        if (allocated > 0) {
            allocated--;
        }

        keys[allocated] = null;
        childs[allocated + 1] = 0;

        return true;
    }

    // remove child
    protected void moveChildsLeft(final int srcPos) {
        System.arraycopy(childs, srcPos + 1, childs, srcPos, allocated - srcPos);
    }

    @Override
    public boolean isLeaf() {
        return false;
    }

    @Override
    public int getBOrder() {
        return tree.getM() / 2;
    }

}
