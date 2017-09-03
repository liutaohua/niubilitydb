package com.ldw.store;

import com.ldw.structure.Node;

import java.io.*;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

public class FileStore implements Store {
    private int BLOCK_SIZE = 1024;
    private RandomAccessFile fd;
    private int blockPoint;
    private Stack<Integer> relPoint = new Stack<>();


    public FileStore(File store) throws FileNotFoundException {
        if (store.exists()) {
            store.delete();
        }
        this.fd = new RandomAccessFile(store, "rw");
        blockPoint = 0;
    }

    @Override
    public void release(int point) {
        relPoint.push(point);
    }

    @Override
    public int getBlockPoint() {
        if (relPoint.empty()) {
            return blockPoint++;
        }
        return relPoint.pop();
    }

//    public int getBlockPoint() {
//        return blockPoint++;
//    }

    @Override
    public void save(Node node) {
        try {
            int pointer = node.getPointer();
            fd.seek(pointer * BLOCK_SIZE);
            ByteArrayOutputStream bos = new ByteArrayOutputStream(BLOCK_SIZE);
            DataOutputStream ds = new DataOutputStream(bos);

            //是否为叶子节点
            ds.writeBoolean(node.isLeaf());
            ds.writeBoolean(node.isRoot());
            //一共有多少key
            List<Map.Entry<String, Integer>> entries = node.getEntries();
            int ksize = entries.size();
            ds.writeInt(ksize);
            //写入所有key
            for (int i = 0; i < ksize; i++) {
                ds.writeUTF(entries.get(i).getKey().toString());
            }
            //写入数据文件
            if (node.isLeaf()) {
                ds.writeInt(ksize);
                for (int i = 0; i < ksize; i++) {
                    ds.writeInt(entries.get(i).getValue());
                }
            } else {
                List<Node> children = node.getChildren();
                int childSize = children.size();
                ds.writeInt(childSize);
                for (int i = 0; i < childSize; i++) {
                    ds.writeInt(children.get(i).getPointer());
                }

            }
            if (!node.isRoot()) {
                //写入关系指针
                ds.writeBoolean(node.getNext() != null);
                if (node.getNext() != null) {
                    ds.writeInt(node.getNext().getPointer());
                }
                ds.writeBoolean(node.getPrevious() != null);
                if (node.getPrevious() != null) {
                    ds.writeInt(node.getPrevious().getPointer());
                }
                ds.writeInt(node.getParent().getPointer());
            }

            int fill = BLOCK_SIZE - bos.size();
            for (int i = 0; i < fill; i++) {
                bos.write(0);
            }

            byte[] record = bos.toByteArray();
            if (record.length != BLOCK_SIZE) {
                System.out.println(node.toString());
                throw new RuntimeException("rec size= " + record.length + " block size=" + BLOCK_SIZE);
            }

            fd.write(record);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public Node load(int pointer) {
        try {
            fd.seek(pointer * BLOCK_SIZE);
            boolean isLeaf = fd.readBoolean();
            boolean isRoot = fd.readBoolean();
            Node node = new Node(isLeaf, isRoot, pointer);

            //读取key
            int keyNums = fd.readInt();
            for (int i = 0; i < keyNums; i++) {
                node.getEntries().add(new AbstractMap.SimpleEntry<String, Integer>(fd.readUTF(), null));
            }
            if (isLeaf) {
                //读取val
                int valNums = fd.readInt();
                for (int i = 0; i < valNums; i++) {
                    List<Map.Entry> entries = node.getEntries();
                    entries.get(i).setValue(fd.readInt());
                }
            } else {
                int children = fd.readInt();
                for (int i = 0; i < children; i++) {
                    node.getChildren().add(fd.readInt());
                }
            }
            if (!isRoot) {
                //组成关系
                if (fd.readBoolean()) {
                    node.setNext(new Node(isLeaf, fd.readInt()));
                }
                if (fd.readBoolean()) {
                    node.setPrevious(new Node(isLeaf, fd.readInt()));
                }
                node.setParent(new Node(false, fd.readInt()));
            }
            return node;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
