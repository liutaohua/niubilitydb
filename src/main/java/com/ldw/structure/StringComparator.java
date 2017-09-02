package com.ldw.structure;

import java.util.Comparator;

public class StringComparator implements Comparator<String> {
    public int compare(String o1, String o2) {
        // TODO Auto-generated method stub
        if (o1 == null && o2 == null)
            throw new NullPointerException();

        if (o1 != null && o2 == null)
            return -1;

        if (o1 == null && o2 != null)
            return -1;

        return o1.compareToIgnoreCase(o2);

    }
}
