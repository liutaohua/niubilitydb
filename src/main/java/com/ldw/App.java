package com.ldw;

import com.ldw.structure.BPlusTree;
import com.ldw.structure.StringComparator;
import com.ldw.structure.StringSerDeser;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Hello world!
 */
public class App {
    public static void main(String[] args) throws IOException {
        BPlusTree<String> tree = new BPlusTree<>(new File("E:\\test.bp"),
                8, 4,
                new StringSerDeser(), new StringComparator());

        for (int i = 0; i < 20000; i++) {
            tree.insert("a" + i, i);
        }
        tree.printTree();
    }
}
