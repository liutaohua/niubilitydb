package com.ldw.structure;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Node<K extends Comparable, V> {
    private boolean isLeaf, isRoot;

    private Node parent;
    private Node previous;
    private Node next;

    private List<Map.Entry<K, V>> entries;

    private List<Node> children;

    private int pointer = -1;


    public void setParent(Node parent) {
        this.parent = parent;
    }

    public void setPrevious(Node previous) {
        this.previous = previous;
    }

    public void setNext(Node next) {
        this.next = next;
    }

    public Node getParent() {
        return parent;
    }

    public Node getPrevious() {
        return previous;
    }

    public Node getNext() {
        return next;
    }

    public int getPointer() {
        return pointer;
    }

    public List<Node> getChildren() {
        return children;
    }

    public List<Map.Entry<K, V>> getEntries() {
        return entries;
    }

    public boolean isLeaf() {
        return isLeaf;
    }

    public boolean isRoot() {
        return isRoot;
    }

    private Node(boolean isLeaf) {
        this.isLeaf = isLeaf;
        entries = new ArrayList();
        if (!isLeaf) {
            children = new ArrayList<>();
        }
    }

    public Node(boolean isLeaf, int pointer) {
        this(isLeaf);
        this.pointer = pointer;
    }

    private Node(boolean isLeaf, boolean isRoot) {
        this(isLeaf);
        this.isRoot = isRoot;
    }

    public Node(boolean isLeaf, boolean isRoot, int pointer) {
        this(isLeaf, isRoot);
        this.pointer = pointer;
    }

    public V get(K key) {
        return null;
    }

    public void remove(K key, BplusTree bplusTree) {
    }

    public void insertOrUpdate(K key, V obj, BplusTree tree) {
        if (isLeaf) {
            //不需要分裂
            if (contains(key) != -1 || entries.size() < tree.getM()) {
                insertOrUpdate(key, obj);
                if (tree.getHigh() == 0) {
                    tree.setHigh(1);
                }
                tree.getStore().save(this);
                return;
            }
            //需要分裂
            Node<K, V> left = new Node<K, V>(true, tree.getStore().getBlockPoint());
            Node<K, V> right = new Node<K, V>(true, tree.getStore().getBlockPoint());
            /**
             * 因为自己要被分裂成两部分了，所以需要设置两个新的节点的关联关系
             * 1.自己的前驱结点成了左边节点的前驱结点
             * 2.自己的后驱调整为右边的后驱
             * 3.左边节点是右边节点的前驱
             *
             * 还有一种情况，就是本来就只有一层的时候开始分裂的，那么左边的节点成了链表的头
             *
             */
            if (previous != null) {
                previous.next = left;
                left.previous = previous;
                tree.getStore().save(previous);
            }
            if (next != null) {
                next.previous = right;
                right.next = next;
                tree.getStore().save(next);
            }
            if (previous == null) {
                tree.setHead(left);
            }

            left.next = right;
            right.previous = left;
            previous = null;
            next = null;
            //插入到新节点里面去
            copy2Nodes(key, obj, left, right, tree);

            if (parent != null) {
                //调整父子关系
                int index = parent.children.indexOf(this);
                parent.children.remove(index);

                left.parent = parent;
                right.parent = parent;

                parent.children.add(index, left);
                parent.children.add(index + 1, right);
                parent.entries.add(index, right.entries.get(0));

                entries = null;
                children = null;

                tree.getStore().release(pointer);
                tree.getStore().save(parent);
                parent.updateInsert(tree);

                parent = null;
            } else {
                /**
                 * 原本为节点的时候，自己马上要消亡了，裂变成了两个叶子节点
                 * 这时候就需要从新创建一个非叶子节点的根节点
                 */
                isRoot = false;
                Node<K, V> node = new Node<K, V>(false, true, 0);

                tree.setRoot(node);
                left.parent = node;
                right.parent = node;
                node.children.add(left);
                node.children.add(right);

                node.entries.add(right.entries.get(0));
                entries = null;
                children = null;

                tree.getStore().save(node);
            }
            tree.getStore().save(left);
            tree.getStore().save(right);
            return;
        }
        //非叶子节点
        //比第一个小就往左，比最后一个大就往右
        if (key.compareTo(entries.get(0).getKey()) <= 0) {
            children.get(0).insertOrUpdate(key, obj, tree);
        } else if (key.compareTo(entries.get(entries.size() - 1).getKey()) >= 0) {
            children.get(children.size() - 1).insertOrUpdate(key, obj, tree);
        } else {
            //二分遍历剩下的所有
            int low = 0, high = entries.size() - 1, mid = 0;
            int comp;

            while (low <= high) {
                mid = (low + high) / 2;
                comp = entries.get(mid).getKey().compareTo(key);
                if (comp == 0) {
                    children.get(mid + 1).insertOrUpdate(key, obj, tree);
                } else if (comp < 0) {
                    low = mid + 1;
                } else {
                    high = mid - 1;
                }
            }

            if (low > high) {
                children.get(low).insertOrUpdate(key, obj, tree);
            }
        }
    }

    private void updateInsert(BplusTree tree) {
        //分裂的时候可能会产生父节点同样需要分裂的情况

        if (children.size() > tree.getM()) {
            //再次把自己分裂成两个
            Node<K, V> left = new Node<K, V>(false, tree.getStore().getBlockPoint());
            Node<K, V> right = new Node<K, V>(false, tree.getStore().getBlockPoint());
            //计算左右分别放多少个key进去
            int leftSize = (tree.getM() + 1) / 2 + (tree.getM() + 1) % 2;
            int rightSize = (tree.getM() + 1) / 2;
            //将父节点原有的值分裂到两个里面
            for (int i = 0; i < leftSize; i++) {
                left.children.add(children.get(i));
                children.get(i).parent = left;
            }
            for (int i = 0; i < rightSize; i++) {
                right.children.add(children.get(leftSize + i));
                children.get(leftSize + i).parent = right;
            }

            for (int i = 0; i < leftSize - 1; i++) {
                left.entries.add(entries.get(i));
            }
            for (int i = 0; i < rightSize - 1; i++) {
                right.entries.add(entries.get(leftSize + i));
            }

            //如果不是根结点
            if (parent != null) {
                int index = parent.children.indexOf(this);
                parent.children.remove(index);

                left.parent = parent;
                right.parent = parent;
                parent.children.add(index, left);
                parent.children.add(index + 1, right);
                parent.entries.add(index, entries.get(leftSize - 1));
                entries = null;
                children = null;

                tree.getStore().release(pointer);
                tree.getStore().save(parent);
                parent.updateInsert(tree);
                parent = null;
            } else {
                isRoot = false;
                Node<K, V> parent = new Node<K, V>(false, true, 0);
                tree.setRoot(parent);
                tree.setHigh(tree.getHigh() + 1);

                left.parent = parent;
                right.parent = parent;
                parent.children.add(left);
                parent.children.add(right);
                parent.entries.add(entries.get(leftSize - 1));
                entries = null;
                children = null;

                tree.getStore().save(parent);
            }

            tree.getStore().save(left);
            tree.getStore().save(right);
        }
    }

    private void copy2Nodes(K key, V obj, Node<K, V> left, Node<K, V> right, BplusTree tree) {
        //因为原本的被分裂成了两个，所以计算一下左右分别装多少个，如果为奇数多余的一个放在左边
        int leftSize = (tree.getM() + 1) / 2 + (tree.getM() + 1) % 2;
        boolean b = false;
        //遍历当前节点的所有值，先往左边装，再往右边
        for (int i = 0; i < entries.size(); i++) {
            if (leftSize != 0) {
                leftSize--;
                if (!b && entries.get(i).getKey().compareTo(key) > 0) {
                    left.entries.add(new AbstractMap.SimpleEntry<K, V>(key, obj));
                    b = true;
                    i--;
                } else {
                    left.entries.add(entries.get(i));
                }
            } else {
                if (!b && entries.get(i).getKey().compareTo(key) > 0) {
                    right.entries.add(new AbstractMap.SimpleEntry<K, V>(key, obj));
                    b = true;
                    i--;
                } else {
                    right.entries.add(entries.get(i));
                }
            }
        }
        //判断新的需要插入的key是否在左右插入过了，如果没有说明这个key是最大的，放在最后
        if (!b) {
            right.entries.add(new AbstractMap.SimpleEntry<K, V>(key, obj));
        }
    }

    private void insertOrUpdate(K key, V obj) {
        int low = 0, high = entries.size() - 1, mid;
        int comp;
        while (low <= high) {
            mid = (low + high) / 2;
            comp = entries.get(mid).getKey().compareTo(key);
            if (comp == 0) {
                entries.get(mid).setValue(obj);
            } else if (comp < 0) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }

        }

        if (low > high) {
            entries.add(low, new AbstractMap.SimpleEntry<K, V>(key, obj));
        }
    }

    private int contains(K key) {
        //二分查找，判断Key是否存在
        int low = 0, high = entries.size() - 1, mid;
        int comp;
        while (low <= high) {
            mid = (low + high) / 2;
            comp = entries.get(mid).getKey().compareTo(key);
            if (comp == 0) {
                return mid;
            } else if (comp < 0) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return -1;
    }


    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("isRoot: ");
        sb.append(isRoot);
        sb.append(", ");
        sb.append("isLeaf: ");
        sb.append(isLeaf);
        sb.append(", ");
        sb.append("name is:" + this.hashCode());
        sb.append(",");
        if (!isRoot) {
            sb.append("parent is: " + parent.hashCode());
            sb.append(",");
        }
        sb.append("keys: ");
        for (Map.Entry<K, V> entry : entries) {
            sb.append(entry.getKey());
            sb.append(", ");
        }
        return sb.toString();

    }
}
