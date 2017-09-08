package com.ldw.structures.btree;

import com.ldw.structures.holders.DataHolder;

import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.util.Arrays;

public abstract class Node<K extends DataHolder<K>, V extends DataHolder<V>> {
    public BPlusTree<K, V> tree;

    public K[] keys;

    public int pointer = 0;
    public int allocated = 0;

    protected Node(BPlusTree<K, V> tree) {
        this.tree = tree;
        this.keys = (K[]) Array.newInstance(tree.factoryK().getClass(), tree.getM());
    }

    public int allocPointer() {
        pointer = tree.allocNode(isLeaf());
        return pointer;
    }


    public int binarySearch(K key) {
        int low = 0, high = allocated - 1;

        while (low <= high) {
            int mid = (low + high) / 2;
            K midVal = keys[mid];
            int cmp = midVal.compareTo(key);

            if (cmp == 0) {
                return mid;
            } else if (cmp < 0) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return -(low + 1);
    }

    //----------------------------  分裂节点 -----------------------------//

    public abstract Node<K, V> split();

    public abstract K splitLeftShiftKeys();

    //delete element
    protected void moveElementsLeft(Object[] elements, int srcPos) {
        System.arraycopy(elements, srcPos + 1, elements, srcPos, allocated - srcPos - 1);
    }

    // insert element
    protected void moveElementsRight(final Object[] elements, final int srcPos) {
        System.arraycopy(elements, srcPos, elements, srcPos + 1, allocated - srcPos);
    }
    //-------------------------------------------------------------------//

    //----------------------------  删除内容 -----------------------------//

    public abstract boolean remove(int index);

    public final void clean(ByteBuffer buf) {
        buf.clear();
        buf.putLong(0);
        buf.flip();
    }

    protected void delete() {
        clear();
        allocated = Integer.MAX_VALUE;
    }


    private void clear() {
        Arrays.fill(keys, null);
        allocated = 0;
    }
    //-------------------------------------------------------------------//

    //----------------------------  判断方法 -----------------------------//
    abstract public boolean isLeaf();

    public static boolean isLeaf(int pointer) {
        return pointer > 0;
    }

    public boolean isFull() {
        return allocated >= keys.length;
    }

    public boolean isEmpty() {
        return allocated <= 0;
    }

    protected boolean isDeleted() {
        return (allocated == Integer.MIN_VALUE);
    }
    //-------------------------------------------------------------------//

    public abstract int getBOrder();

    public void serialize(ByteBuffer buf) {
        buf.clear();
        buf.putInt(pointer);
        buf.putInt(allocated);

        for (int i = 0; i < allocated; i++) {
            keys[i].serialize(buf);
        }
    }

    public static <K extends DataHolder<K>, V extends DataHolder<V>> Node<K, V> deserialize(
            final ByteBuffer buf, final BPlusTree<K, V> tree) {
        final int pointer = buf.getInt();
        if (pointer == 0) {
            throw new RuntimeException("Invalid Node pointer=0");
        }
        boolean isLeaf = isLeaf(pointer);
        Node<K, V> node = (isLeaf ? tree.createLeafNode() : tree.createInternalNode());
        node.pointer = pointer;
        return node.deserializeNode(buf);
    }

    protected Node<K, V> deserializeNode(final ByteBuffer buf) {
        K k = tree.factoryK();
        allocated = buf.getInt();
        for (int i = 0; i < allocated; i++) {
            keys[i] = k.deserialize(buf);
        }
        return this;
    }

}
