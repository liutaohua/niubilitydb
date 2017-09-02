package com.ldw.structure;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

public class BPlusNode<K> {
    private int M = 0;

    private int blockPointer;

    private List<Integer> children = new LinkedList<>();

    private List<K> keys = new LinkedList<>();

    private List<Long> data = new LinkedList<>();

    Comparator<K> keyComparator = null;

    private boolean isLeaf = false;
    private boolean isRoot = false;

    private BPlusTree container = null;

    private int nextBlockPointer;

    private K promotedKey;

    private int[] promotedChildPtrs;

    private SerDeserializer<K> keySerDeser;

    public BPlusNode(BPlusTree<K> tree) {
        container = tree;
        blockPointer = container.getNextBlockPointer();
        keySerDeser = tree.getSerDeserializer();
        keyComparator = tree.getKeyComparator();

        M = container.getNumKeysPerBlock();
    }


    public BPlusNode(BPlusTree<K> tree, byte[] b, int blockpointer) throws IOException {
        container = tree;
        blockPointer = blockpointer;
        keySerDeser = tree.getSerDeserializer();
        keyComparator = tree.getKeyComparator();

        M = container.getNumKeysPerBlock();
        if (blockPointer == 0) {
            isRoot = true;
        }
        ByteArrayInputStream bis = new ByteArrayInputStream(b);
        DataInputStream dis = new DataInputStream(bis);

        readNode(dis);
    }

    public BPlusNode insert(K key, long val) {
        if (!isLeaf()) {
            int ptr = -1;
            int ksize = keys.size();
            int i;
            for (i = 0; i < ksize; i++) {
                K k = keys.get(i);
                if (keyComparator.compare(key, k) < 0) {
                    ptr = (Integer) children.get(i);
                    break;
                }
            }

            if (ptr == -1) {
                ptr = (Integer) children.get(i);
            }

            BPlusNode<K> nextNode = readFromDisk(ptr);

            BPlusNode<K> newChild = nextNode.insert(key, val);
            nextNode.writeToDisk();

            if (newChild == null) {
                return null;
            }

            K skey = newChild.getPromotedKey();
            int[] pointers = newChild.getPromotedPointers();

            newChild = insert(skey, pointers);

            return newChild;
        }

        if (isLeaf()) {
            int size = keys.size();
            boolean foundpos = false;
            for (int i = 0; i < size; i++) {
                K k = keys.get(i);

                if (keyComparator.compare(key, k) < 0) {
                    keys.add(i, key);
                    data.add(i, val);

                    foundpos = true;
                    break;
                } else if (keyComparator.compare(key, k) == 0) {
                    data.set(i, val);
                    foundpos = true;
                    break;
                }
            }
            if (!foundpos) {
                keys.add(key);
                data.add(val);
            }

            size = keys.size();
            if (size <= M) {
                writeToDisk();
                return null;
            }
            BPlusNode<K> newNode = new BPlusNode<K>(container);
            newNode.setLeaf(true);

            int s_half_b = M / 2;
            int s_half_e = size - 1;

            for (int i = s_half_b; i <= s_half_e; i++) {
                K lkey = getKey(i);
                long ldata = getData(i);
                newNode.insert(lkey, ldata);
            }

            K promotedkey = getKey(s_half_b);

            for (int i = s_half_e; i >= s_half_b; i--) {
                removeKey(i);
                removeData(i);
            }

            nextBlockPointer = newNode.getPointer();
            newNode.setPromotedKey(promotedkey);

            int[] promotedpointers = new int[2];
            promotedpointers[0] = getPointer();
            promotedpointers[1] = newNode.getPointer();
            newNode.setPromotedPointers(promotedpointers);

            writeToDisk();
            newNode.writeToDisk();

            return newNode;
        }
        return null;
    }


    public BPlusNode<K> insert(K key, int[] pointers) {
        if (isLeaf()) {
            throw new RuntimeException("Method Applies only to Non Leaf nodes");
        }

        int size = keys.size();
        boolean foundpos = false;
        for (int i = 0; i < size; i++) {
            K k = keys.get(i);
            if (keyComparator.compare(key, k) < 0) {
                keys.add(i, key);
                children.add(i + 1, pointers[1]);
                foundpos = true;
                break;
            } else if (keyComparator.compare(key, k) == 0) {
                children.set(i + i, pointers[0]);
                foundpos = true;
                break;
            }
        }

        if (!foundpos) {
            keys.add(size, key);
            children.add(size + 1, pointers[1]);
        }

        size = keys.size();
        if (size <= M) {
            writeToDisk();
            return null;
        }

        BPlusNode newnode = new BPlusNode(container);
        newnode.setLeaf(false);
        int s_half_b = M / 2;
        int s_half_e = size - 1;

        for (int i = s_half_b + 1; i <= s_half_e; i++) {
            K lkey = getKey(i);
            newnode.appendKey(lkey);
        }

        for (int i = s_half_e; i >= s_half_b + 1; i--) {
            removeKey(i);
        }

        for (int i = s_half_b + 1; i <= s_half_e + 1; i++) {
            newnode.appendChildPtr(getChildPtr(i));
        }

        for (int i = s_half_e + 1; i >= s_half_b + 1; i--) {
            removeChildPtr(i);
        }

        int middle_idx = s_half_b;
        newnode.setPromotedKey(keys.get(middle_idx));

        int[] promotedpointers = new int[2];
        promotedpointers[0] = getChildPtr(middle_idx);
        promotedpointers[1] = newnode.getPointer();
        newnode.setPromotedPointers(promotedpointers);

        keys.remove(middle_idx);
        newnode.writeToDisk();
        return newnode;
    }

    public K find(K key) {
        int ptr = -1;

        if (!isLeaf()) {
            int ksize = keys.size();
            int i;
            for (i = 0; i < ksize; i++) {
                K k = keys.get(i);
                if (keyComparator.compare(key, k) < 0) {
                    ptr = (Integer) children.get(i);
                    break;
                }
            }

            if (ptr == -1) {
                ptr = (Integer) children.get(i);
            }
            BPlusNode<K> nextNode = readFromDisk(ptr);
            return nextNode.find(key);
        }

        int ksize = keys.size();
        int i;
        for (i = 0; i < ksize; i++) {
            K k = keys.get(i);
            if (keyComparator.compare(key, k) == 0) {
                return key;
            }
        }
        return null;
    }

    private void readNode(DataInputStream ds) throws IOException {
        byte type = ds.readByte();
        if (type == 1) {
            setLeaf(true);
        } else if (type == 0) {
            setLeaf(false);
        } else {
            throw new RuntimeException("first byte of the block is not 0 or 1. Invalid");
        }

        if (isLeaf) {
            readLeaf(ds);
        } else {
            readNonLeaf(ds);
        }
    }

    private void readLeaf(DataInputStream ds) throws IOException {
        int numkeys = ds.readInt();
        for (int i = 0; i < numkeys; i++) {
            K key = keySerDeser.read(ds);
            keys.add(key);
        }

        int numItems = ds.readInt();
        for (int i = 0; i < numItems; i++) {
            long dataitem = ds.readLong();
            data.add(dataitem);
        }
        this.nextBlockPointer = ds.readInt();
    }


    public void writeLeaf(DataOutputStream ds) throws IOException {
        ds.writeByte(1);
        ds.writeByte(1);

        int knum = keys.size();
        ds.writeInt(knum);

        for (int i = 0; i < knum; i++) {
            K val = keys.get(i);
            keySerDeser.write(val, ds);
        }

        int num = data.size();
        ds.writeInt(num);

        for (int i = 0; i < num; i++) {
            long val = data.get(i);
            ds.writeLong(val);
        }

        ds.writeInt(nextBlockPointer);
    }

    private void readNonLeaf(DataInputStream ds) throws IOException {
        int numKeys = ds.readInt();
        for (int i = 0; i < numKeys; i++) {
            K key = keySerDeser.read(ds);
            keys.add(key);
        }

        int numChildPtrs = ds.readInt();
        for (int i = 0; i < numChildPtrs; i++) {
            int ptr = ds.readInt();
            children.add(ptr);
        }
    }

    public void writeNonLeaf(DataOutputStream ds) throws IOException {
        ds.writeByte(1);
        ds.writeByte(0);

        int num_keys = keys.size();

        ds.writeInt(num_keys);
        for (int i = 0; i < num_keys; i++) {
            K val = keys.get(i);
            keySerDeser.write(val, ds);
        }

        int num_childPtrs = children.size();
        ds.writeInt(num_childPtrs);
        for (int i = 0; i < num_childPtrs; i++) {
            int ptr = children.get(i);
            ds.writeInt(ptr);
        }
    }

    public void appendKey(K key) {
        keys.add(key);
    }

    public void addChildren(BPlusNode oldroot, BPlusNode newchild) {
        if (!isRoot) {
            throw new RuntimeException("Method should be called for root only!");
        }

        K key = (K) newchild.getPromotedKey();
        appendKey(key);

        int[] promotedPointers = newchild.getPromotedPointers();

        appendChildPtr(oldroot.getPointer());
        appendChildPtr(promotedPointers[1]);
    }

    public List<Integer> getChildren() {
        return children;
    }

    public void appendChildPtr(int p) {
        children.add(p);
    }

    private void removeChildPtr(int index) {
        children.remove(index);
    }

    public int getChildPtr(int index) {
        return children.get(index);
    }

    public void writeToDisk() {
        try {
            container.writeToDisk(this);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public BPlusNode readFromDisk(int pointer) {
        try {
            return container.readFromDisk(pointer);
        } catch (Exception e) {
            System.out.println(e);
        }
        return null;
    }

    public void moveBlock() {
        blockPointer = container.getNextBlockPointer();
        writeToDisk();
    }

    public void setRoot(boolean root) {
        isRoot = root;
        if (isRoot == true) {
            blockPointer = 0;
            container.decNextBlockPointer();
        }
    }

    public void setLeaf(boolean leaf) {
        isLeaf = leaf;
    }

    public boolean isLeaf() {
        return isLeaf;
    }

    public boolean isRoot() {
        return isRoot;
    }

    private void removeKey(int index) {

        keys.remove(index);
    }

    public K getKey(int index) {
        return keys.get(index);
    }

    private void removeData(int index) {
        data.remove(index);
    }

    public long getData(int index) {
        return data.get(index);
    }


    public int getPointer() {
        return blockPointer;
    }

    public K getPromotedKey() {
        return promotedKey;
    }

    public void setPromotedPointers(int[] pointers) {
        promotedChildPtrs = pointers;
    }

    public void setPromotedKey(K key) {
        promotedKey = key;
    }

    public int[] getPromotedChildPtrs() {
        return promotedChildPtrs;
    }

    public int[] getPromotedPointers() {
        return promotedChildPtrs;
    }

    public void printNode() {
        System.out.println("---- Begin Node");
        System.out.println("BlockPointer :" + blockPointer);

        if (isRoot) {
            System.out.println("Root \n");
        }
        if (isLeaf) {
            System.out.println("Leaf ");
            System.out.println("Keys :" + keys.size());
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < keys.size(); i++) {
                sb.append(keys.get(i) + ",");
            }
            System.out.println(sb.toString());
            System.out.println("---- End Node ");
            return;
        } else {
            System.out.println("Non leaf \n");
            System.out.println("Keys:" + keys.size() + "Keys \n");

            StringBuilder kb = new StringBuilder();
            for (int i = 0; i < keys.size(); i++) {
                kb.append(keys.get(i));
                kb.append(",");
            }

            System.out.println(kb.toString());
            System.out.println("Child Pointers: \n");

            StringBuilder cb = new StringBuilder();
            for (int i = 0; i < children.size(); i++) {
                cb.append(children.get(i));
                cb.append(",");
            }

            System.out.println(cb.toString());
        }

        System.out.println("--- End Node ");
        return;
    }
}
