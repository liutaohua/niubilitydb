package com.ldw.structures.btree;

import com.ldw.structures.holders.DataHolder;
import com.ldw.structures.store.FileBlockStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.BitSet;
import java.util.RandomAccess;

public class BPlusTreeFile<K extends DataHolder<K>, V extends DataHolder<V>> extends BPlusTree<K, V> {

    private File fileStorage;

    private FileBlockStore storage;
    private File fileFreeBlocks;
    private BitSet freeBlocks;

    private int storageBlock = 0;

    private transient String fileName;

    private int maxLeafNodes = 0;
    private int maxInternalNodes = 0;

    private static final int MAGIC_1 = 0x42D6AECB;
    private static final int MAGIC_2 = 0x6B708B42;

    public BPlusTreeFile(String fileName, Class<K> kType, Class<V> vType) throws InstantiationException, IllegalAccessException {
        super(kType, vType);
        this.fileName = fileName;

        fileStorage = new File(fileName + ".data");
        fileFreeBlocks = new File(fileName + ".free");

        freeBlocks = new BitSet();
        storage = new FileBlockStore(fileStorage.getAbsolutePath(), BLOCK_SIZE);
    }

    //----------------------------  获取节点 -----------------------------//

    @Override
    protected Node<K, V> getNode(int pointer) {
        if (pointer == 0) {
            System.out.println(this.getClass().getName() + "::getNode(" + pointer + ") ERROR");
            return null;
        }
        return getNodeFromStore(pointer);
    }

    private Node<K, V> getNodeFromStore(final int nodeid) {
        final int index = nodeid < 0 ? -nodeid : nodeid;
        ByteBuffer buf = storage.get(index);
        Node<K, V> node = Node.deserialize(buf, this);
        if (rootPointer == node.pointer) {
            System.out.println(this.getClass().getName() + "::getNodeFromStore(" + nodeid + ") WARN LOADED ROOT NODE");
        }

        storage.release(buf);
        return node;
    }
    //-------------------------------------------------------------------//


    //----------------------------  申请节点 -----------------------------//
    public boolean open() {
        boolean allRight = false;
        if (storage.isOpen()) {
            throw new RuntimeException("file is open");
        }

        storage.open();
        try {
            if (storage.sizeInBlocks() == 0) {
                clearStates();
                return true;
            }
            try {
                boolean isClean = readMetaData();
                if (isClean) {
                    if (writeMetaData(false)) {
                        allRight = true;
                    }
                } else {
                    throw new RuntimeException("need recovery");
                }
            } catch (RuntimeException e) {
                storage.close();
                throw e;
            }
        } finally {
            releaseNodes();
        }
        return allRight;
    }

    private boolean readMetaData() {
        final ByteBuffer buf = storage.get(0);
        int magic1, magic2, t_b_order_leaf, t_b_order_internal, t_blockSize; // sanity
        boolean isClean = false;
        magic1 = buf.getInt();
        if (magic1 != MAGIC_1) {
            throw new RuntimeException("Invalid metadata (MAGIC1)");
        }
        t_blockSize = buf.getInt();
        if (t_blockSize != BLOCK_SIZE) {
            throw new RuntimeException("Invalid metadata (blockSize) " + t_blockSize + " != " + BLOCK_SIZE);
        }
        t_b_order_leaf = buf.getInt();
        t_b_order_internal = buf.getInt();
        if (t_b_order_leaf != M / 2) {
            throw new RuntimeException("Invalid metadata (b-order leaf) " + t_b_order_leaf + " != "
                    + M / 2);
        }
        if (t_b_order_internal != M / 2) {
            throw new RuntimeException("Invalid metadata (b-order internal) " + t_b_order_internal
                    + " != " + M / 2);
        }
        storageBlock = buf.getInt();
        rootPointer = buf.getInt();
        headPointer = buf.getInt();
        lastPointer = buf.getInt();
        elements = buf.getInt();
        high = buf.getInt();
        maxInternalNodes = buf.getInt();
        maxLeafNodes = buf.getInt();
        isClean = ((buf.get() == ((byte) 0xEA)) ? true : false);
        magic2 = buf.getInt();
        if (magic2 != MAGIC_2) {
            throw new RuntimeException("Invalid metadata (MAGIC2)");
        }
        storage.release(buf);
        if (isClean && fileFreeBlocks.exists()) {
            try {
                RandomAccessFile fos = new RandomAccessFile(fileFreeBlocks, "rw");
                FileChannel channel = fos.getChannel();
                ByteBuffer lenght = ByteBuffer.allocate(4);
                channel.read(lenght);
                int anInt = lenght.getInt();
                ByteBuffer allocate = ByteBuffer.allocate(anInt);
                channel.read(allocate);
                BitSet newFreeBlocks = BitSet.valueOf(allocate.array());
                freeBlocks = newFreeBlocks;
            } catch (IOException e) {
                System.out.println("IOException in readMetaData()" + e);
            }
        }
        return isClean;
    }

    @Override
    public int allocNode(boolean isLeaf) {
        int pointer = freeBlocks.nextSetBit(0);
        if (pointer < 0) {
            if (isLeaf) {
                maxLeafNodes++;
            } else {
                maxInternalNodes++;
            }
            pointer = ++storageBlock;
        } else {
            freeBlocks.clear(pointer);
        }
        return (isLeaf ? pointer : -pointer);
    }

    //-------------------------------------------------------------------//

    //----------------------------  存储节点 -----------------------------//
    @Override
    protected void putNode(Node<K, V> node) {
        putNodeToStore(node);
    }

    private void putNodeToStore(Node<K, V> node) {
        int nodePointer = node.pointer;
        int index = nodePointer < 0 ? -nodePointer : nodePointer;
        FileBlockStore.WriteBuffer wbuf = storage.set(index);
        MappedByteBuffer buf = wbuf.buf();

        if (node.isDeleted()) {
            node.clean(buf);
            freeBlocks.set(index);
        } else {
            node.serialize(buf);
        }
        wbuf.save();
    }

    private boolean writeMetaData(boolean isClean) {
        final FileBlockStore.WriteBuffer wbuf = storage.set(0);
        final MappedByteBuffer buf = wbuf.buf();
        boolean isOK = false;
        buf.putInt(MAGIC_1)
                .putInt(BLOCK_SIZE)
                .putInt(M / 2)
                .putInt(M / 2)
                .putInt(storageBlock)
                .putInt(rootPointer)
                .putInt(headPointer)
                .putInt(lastPointer)
                .putInt(elements)
                .putInt(high)
                .putInt(maxInternalNodes)
                .putInt(maxLeafNodes)
                .put((byte) (isClean ? 0xEA : 0x00))
                .putInt(MAGIC_2)
                .flip();
        isOK = wbuf.save();
        if (isClean) {
            storage.sync();
        }
        try {
            if (isClean) {
                FileOutputStream fos = null;
                FileChannel fosChannel = null;
                try {
                    fos = new FileOutputStream(fileFreeBlocks, false);
                    fosChannel = fos.getChannel();
                    byte[] bytes = freeBlocks.toByteArray();
                    ByteBuffer wrap = ByteBuffer.wrap(bytes);

                    fosChannel.write(ByteBuffer.allocate(4).putInt(bytes.length));
                    fosChannel.write(wrap);
                } finally {
                    try {
                        fosChannel.force(true);
                        fos.close();
                    } catch (Exception e) {
                    }
                }
//                BitSet.serializeToFile(fileFreeBlocks, freeBlocks);
            } else {
                fileFreeBlocks.delete();
            }
        } catch (IOException e) {
            System.out.println("IOException in writeMetaData(" + isClean + ")" + e);
        }
        return isOK;
    }

    //-------------------------------------------------------------------//

    //----------------------------  释放节点 -----------------------------//
    @Override
    protected void freeNode(Node<K, V> node) {
        int pointer = node.pointer;
        if (pointer == 0) {
            return;
        }
        node.delete();
        putNode(node);
    }

    @Override
    protected void releaseNodes() {

    }

    @Override
    protected boolean clearStorage() {
        storage.delete();
        return storage.open();
    }

    @Override
    protected void clearStates() {
        maxInternalNodes = 0;
        maxLeafNodes = 0;
        storageBlock = 0;

        freeBlocks = new BitSet();

        super.clearStates();

        writeMetaData(false);
        sync();
    }

    public void sync() {
        try {
            privateSync(true, true);
        } finally {
            releaseNodes();
        }
    }

    private void privateSync(boolean syncInternal, boolean forceSyncStore) {
        storage.sync();
    }
    //-------------------------------------------------------------------//

    @Override
    protected int getHighestNodePointer() {
        return storageBlock;
    }

    public void create() {
        clear();
    }
}
