package com.ldw.structures.btree;

import com.ldw.structures.holders.DataHolder;

public abstract class BPlusTree<K extends DataHolder<K>, V extends DataHolder<V>> {

    public static final int M = 3;

    protected int BLOCK_SIZE = 512;
    //root pointer
    protected int rootPointer;

    protected int headPointer;

    protected int lastPointer;

    protected int elements = 0;

    protected int high = 0;

    private final K factoryK;

    private final V factoryV;

    public BPlusTree(Class<K> kType, Class<V> vType) throws IllegalAccessException, InstantiationException {
        this.factoryK = kType.newInstance();
        this.factoryV = vType.newInstance();
    }


    //----------------------------  查找内容 -----------------------------//
    abstract protected Node<K, V> getNode(int pointer);


    public V getKey(K key) {
        if (isEmpty() || key == null) {
            return null;
        }

        try {
            LeafNode<K, V> node = findLeafNode(key);
            if (node == null) {
                return null;
            }

            int index = node.binarySearch(key);
            if (index >= 0) {
                return node.values[index];
            }
            return null;
        } finally {
            releaseNodes();
        }
    }

    private final LeafNode<K, V> findLeafNode(K key) {
        Node<K, V> node = getNode(rootPointer);
        while (!node.isLeaf()) {
            InternalNode<K, V> internalNode = (InternalNode<K, V>) node;
            int index = node.binarySearch(key);
            index = index < 0 ? (-index) - 1 : index + 1;
            node = getNode(internalNode.childs[index]);
            if (node == null) {
                return null;
            }
        }
        return node.isLeaf() ? (LeafNode<K, V>) node : null;
    }

    //-------------------------------------------------------------------//


    //----------------------------  插入内容 -----------------------------//
    public boolean put(K key, V val) {
        if (key == null || val == null) {
            return false;
        }
        try {
            Node<K, V> splitNode;
            splitNode = putIterative(key, val);

            if (splitNode != null) {
                InternalNode<K, V> newRootNode = createInternalNode();
                newRootNode.allocPointer();

                K newKey = splitNode.splitLeftShiftKeys();
                putNode(splitNode);

                newRootNode.childs[0] = rootPointer;
                newRootNode.keys[0] = newKey;
                newRootNode.childs[1] = splitNode.pointer;
                newRootNode.allocated++;
                putNode(newRootNode);

                high++;
            }

            elements++;
            return true;
        } finally {
            releaseNodes();
        }
    }

    protected Node<K, V> putIterative(K key, V val) {
        LeafNode<K, V> leafNode = findLeafNode(key);
        if (leafNode == null) {
            throw new RuntimeException("find leaf node is null : " + key);
        }
        //找到了重复的Key
        int index = leafNode.binarySearch(key);
        if (index >= 0) {
            throw new RuntimeException("duplicate key: " + key);
        }
        //没有重复key直接插入
        index = (-index) - 1;
        Node<K, V> splitNode = null;
        leafNode.add(index, key, val);
        putNode(leafNode);
        //判断是不是要分裂
        splitNode = leafNode.isFull() ? leafNode.split() : null;

        return splitNode;
    }

    //-------------------------------------------------------------------//


    //----------------------------  删除内容 -----------------------------//
    public boolean remove(K key) {
        if (key == null) {
            return false;
        }

        try {
            if (removeIterative(key)) {
                elements--;

                Node<K, V> rootNode = getNode(rootPointer);
                //根节点只有一个children
                if (rootNode.isEmpty() && elements > 0) {
                    rootPointer = ((InternalNode<K, V>) rootNode).childs[0];
                    freeNode(rootNode);
                    high--;
                } else if (rootNode.isEmpty() && rootNode.isLeaf() && elements == 0 && getHighestNodePointer() > 4096) {
                    clear();
                } else if (elements == 0 && (!rootNode.isLeaf() || !rootNode.isEmpty())) {
                    throw new RuntimeException("root is not empty but elements is zero");
                }
            }
            return false;
        } finally {
            releaseNodes();
        }
    }

    protected boolean removeIterative(K key) {
        LeafNode<K, V> leafNode = findLeafNode(key);

        int index = leafNode.binarySearch(key);
        if (index >= 0) {
            leafNode.remove(index);
            putNode(leafNode);

            return true;
        }
        return false;
    }

    //-------------------------------------------------------------------//


    //----------------------------  创建节点 -----------------------------//
    public abstract int allocNode(boolean isLeaf);

    protected InternalNode<K, V> createInternalNode() {
        return new InternalNode<K, V>(this);
    }

    protected abstract void putNode(Node<K, V> node);

    private void createRootNode() {
        Node<K, V> node = createLeafNode();
        node.allocPointer();
        rootPointer = node.pointer;
        high = 1;
        elements = 0;
        putNode(node);
    }

    protected LeafNode<K, V> createLeafNode() {
        return new LeafNode<K, V>(this);
    }

    //-------------------------------------------------------------------//

    //----------------------------  释放节点 -----------------------------//

    protected abstract void freeNode(Node<K, V> rootNode);

    protected abstract void releaseNodes();

    protected abstract boolean clearStorage();

    public void clear() {
        if (clearStorage()) {
            clearStates();
        }
    }

    protected void clearStates() {
        createRootNode();
    }

    //-------------------------------------------------------------------//


    protected abstract int getHighestNodePointer();

    public boolean isEmpty() {
        return elements == 0;
    }


    public int getM() {
        return M;
    }

    protected K factoryK() {
        return factoryK;
    }

    protected V factoryV() {
        return factoryV;
    }

}
