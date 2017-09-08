package com.ldw.structures.store;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.*;

public class FileBlockStore {

    public final int blockSize;

    private File file = null;

    private RandomAccessFile raf = null;

    private FileChannel fileChannel = null;

    private final HashMap<Integer, BufferReference> mmaps = new HashMap<>(128);

    public void sync() {
        syncAllMmaps();
        if (fileChannel != null) {
            try {
                fileChannel.force(true);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private Comparator<BufferReference<MappedByteBuffer>> comparatorByPointer = new Comparator<BufferReference<MappedByteBuffer>>() {
        @Override
        public int compare(BufferReference<MappedByteBuffer> o1, BufferReference<MappedByteBuffer> o2) {
            if (o1 == null) {
                if (o2 == null) {
                    return 0;
                }
                return 1;
            }
            if (o2 == null) {
                return -1;
            }
            final int thisVal = o1.pointer < 0 ? -o1.pointer : o1.pointer;
            final int anotherVal = o2.pointer < 0 ? -o2.pointer : o2.pointer;
            return thisVal < anotherVal ? -1 : (thisVal == anotherVal ? 0 : 1);
        }
    };

    private void syncAllMmaps() {

        Collection<BufferReference> values = mmaps.values();
        BufferReference<MappedByteBuffer>[] maps = new BufferReference[values.size()];
        values.toArray(maps);
        Arrays.sort(maps, comparatorByPointer);
        for (Reference<MappedByteBuffer> ref : maps) {
            if (ref == null) {
                break;
            }
            final MappedByteBuffer mbb = ref.get();
            if (mbb != null) {
                try {
                    mbb.force();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    static class BufferReference<T extends MappedByteBuffer> extends SoftReference<T> {
        final int pointer;

        public BufferReference(final int pointer, final T referent) {
            super(referent);
            this.pointer = pointer;
        }
    }

    public FileBlockStore(String file, int blockSize) {
        this(new File(file), blockSize);
    }

    public FileBlockStore(final File file, final int blockSize) {
        this.file = file;
        this.blockSize = blockSize;
    }

    //----------------------------  打开文件 -----------------------------//
    public boolean open() {
        if (isOpen()) {
            close();
        }
        try {
            raf = new RandomAccessFile(file, "rw");
            fileChannel = raf.getChannel();
        } catch (Exception e) {
            System.out.println("Exception in open()" + e);
            try {
                fileChannel.close();
                raf.close();
            } catch (Exception ign) {
                ign.printStackTrace();
            }
            raf = null;
            fileChannel = null;
        }
        return isOpen();
    }

    //-------------------------------------------------------------------//

    //----------------------------  关闭文件 -----------------------------//
    public void close() {
        mmaps.clear();
        try {
            fileChannel.close();
            raf.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        fileChannel = null;
        raf = null;
    }

    //-------------------------------------------------------------------//

    //----------------------------  删除文件 -----------------------------//
    public void delete() {
        close();
        try {
            file.delete();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void release(ByteBuffer buf) {

    }
    //-------------------------------------------------------------------//

    //----------------------------  写入文件块 -----------------------------//

    private boolean set(int index, ByteBuffer buf) {
        try {
            if (buf.limit() > blockSize) {
                System.out.println("ERROR: buffer.capacity=" + buf.limit() + " > blocksize=" + blockSize);
            }
            if (buf instanceof MappedByteBuffer) {
                ((MappedByteBuffer) buf).force();
                return true;
            }
            MappedByteBuffer mbb = getMmapForIndex(index);
            if (mbb != null) {
                mbb.put(buf);
                return true;
            }
        } catch (Exception e) {
            System.out.println("Exception in set(" + index + ")" + e);
        }
        return false;
    }

    //-------------------------------------------------------------------//

    //----------------------------  获取文件块 -----------------------------//
    public WriteBuffer set(int index) {
        final MappedByteBuffer buf = getMmapForIndex(index);
        if (buf != null) {
            return new WriteBuffer(this, index, buf);
        }
        return null;
    }

    private MappedByteBuffer getMmapForIndex(int index) {
        int mapPointer = index;
        int mapSize = blockSize;

        try {
            BufferReference<MappedByteBuffer> bref = mmaps.get(index);
            MappedByteBuffer mbb = null;
            if (bref != null) {
                mbb = bref.get();
            }
            if (mbb == null) {
                long mapOffset = (long) mapPointer * mapSize;
                mbb = fileChannel.map(FileChannel.MapMode.READ_WRITE, mapOffset, mapSize);
                mmaps.put(mapPointer, new BufferReference<MappedByteBuffer>(mapPointer, mbb));
            } else {
                mbb.clear();
            }
            return mbb;
        } catch (IOException e) {
            System.out.println("IOException in getMmapForIndex(" + index + ")" + e);
        }
        return null;
    }


    public ByteBuffer get(int index) {
        MappedByteBuffer mmapForIndex = getMmapForIndex(index);
        if (mmapForIndex != null) {
            return mmapForIndex;
        }
        try {
            return fileChannel.map(FileChannel.MapMode.READ_WRITE, index * blockSize, blockSize);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    //-------------------------------------------------------------------//

    //----------------------------  判断方法 -----------------------------//
    public boolean isOpen() {
        try {
            if (fileChannel != null) {
                return fileChannel.isOpen();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return false;
    }

    public int sizeInBlocks() {
        try {
            final long len = file.length();
            final long num_blocks = ((len / blockSize) + (((len % blockSize) == 0) ? 0 : 1));
            return (int) num_blocks;
        } catch (Exception e) {
            System.out.println("Exception in sizeInBlocks()" + e);
        }
        return -1;
    }
    //-------------------------------------------------------------------//

    public static class WriteBuffer {
        private final FileBlockStore storage;
        private final int index;
        private MappedByteBuffer buf;


        public MappedByteBuffer buf() {
            return buf;
        }

        private WriteBuffer(final FileBlockStore storage, final int index, final MappedByteBuffer buf) {
            this.storage = storage;
            this.index = index;
            this.buf = buf;
        }

        public boolean save() {
            boolean ret = storage.set(index, buf);
            storage.release(buf);
            buf = null;
            return ret;
        }
    }


}
