package com.ldw;

import com.ldw.store.FileStore;
import com.ldw.structure.BplusTree;
import com.ldw.structure.Node;

import java.io.File;
import java.io.IOException;

/**
 * Hello world!
 */
public class App {
    public static void main(String[] args) throws IOException {

        FileStore f = new FileStore(new File("E:\\test.pd"));

        BplusTree tree = new BplusTree(3, f);
        for (int i = 0; i < 14; i++) {
            tree.insertOrUpdate("" + i, i);
        }
//        BPlusTree<String> tree = new BPlusTree<>(new File("E:\\test.bp"),
//                8, 4,
//                new StringSerDeser(), new StringComparator());
//
//        for (int i = 0; i < 200; i++) {
//            if(i==182){
//                System.out.println();
//            }
//            tree.insert("" + i, i);
//        }
//        tree.printTree();
////        tree.delete("a37");
        tree.printTree();
        tree.printTree();

    }
}
