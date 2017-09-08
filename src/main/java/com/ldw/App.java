package com.ldw;

import com.ldw.structures.btree.BPlusTreeFile;
import com.ldw.structures.holders.StringHolder;

/**
 * Hello world!
 */
public class App {
    public static void main(String[] args) throws Exception {

        BPlusTreeFile<StringHolder, StringHolder> treeFile = new BPlusTreeFile<StringHolder, StringHolder>("E:\\aaa", StringHolder.class, StringHolder.class);

        treeFile.open();

        treeFile.put(StringHolder.valueOf("1"), StringHolder.valueOf("1"));
    }
}
