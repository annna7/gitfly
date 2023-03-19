package gitfly;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import static gitfly.Utils.join;
import static gitfly.Utils.writeContents;

public class Stage {
    static class NameAndStatus implements Serializable{
        public String name;
        public Integer status;
        public NameAndStatus(String name, Integer status) {
            this.name = name;
            this.status = status;
        }
        @Override
        public String toString() {
            return status + name;
        }
        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (!(o instanceof NameAndStatus n)) {
                return false;
            }
            return n.name.equals(this.name) && n.status.equals(this.status);
        }
        @Override
        public int hashCode() {
            return this.toString().hashCode();
        }
    }
    public static HashMap<String, String> TO_ADD_FILES = new HashMap<>();
    public static HashSet<String> TO_REMOVE_FILES = new HashSet<>();
    public static HashMap<NameAndStatus, String> INDEX_FILES = new HashMap<>();

    public static void addToIndex(String filename, Integer status, String contents) {
        NameAndStatus nameAndStatus = new NameAndStatus(filename, status);
        INDEX_FILES.put(nameAndStatus, contents);
    }

    public static void removeFromIndex(String filename, Integer status) {
        NameAndStatus nameAndStatus = new NameAndStatus(filename, status);
        INDEX_FILES.remove(nameAndStatus);
    }

    public static String getFromIndex(Integer status, String filename) {
        NameAndStatus nameAndStatus = new NameAndStatus(filename, status);
        return INDEX_FILES.getOrDefault(nameAndStatus, null);
    }

    public static String getFromToAdd(String filename) {
        return TO_ADD_FILES.getOrDefault(filename, null);
    }

    public static void removeFromToAdd(String filename) {
        TO_ADD_FILES.remove(filename);
    }
    public static boolean isInToRemove(String filename) {
        return TO_REMOVE_FILES.contains(filename);
    }

    public static void removeFromToRemove(String filename) {
        TO_REMOVE_FILES.remove(filename);
    }

    public static void addToToAdd(String filename, String contents) {
        TO_ADD_FILES.put(filename, contents);
    }

    public static void addToToRemove(String filepath) {
        TO_REMOVE_FILES.add(filepath);
    }
    public static void readIndex() {
        try {
            File index = Repository.INDEX;
            FileInputStream fis = new FileInputStream(index);
            ObjectInputStream ois = new ObjectInputStream(fis);
            Object o = ois.readObject();
            INDEX_FILES = o == null ? new HashMap<>() : (HashMap<NameAndStatus, String>) o;
            ois.close();
            fis.close();
        } catch (EOFException ignored) {
        } catch (Exception e) {
            System.out.println("Error read index: " + e);
        }
    }
    public static void readFilesToBeAdded() {
        try {
            File toAdd = Repository.TO_ADD;
            FileInputStream fis = new FileInputStream(toAdd);
            ObjectInputStream ois = new ObjectInputStream(fis);
            Object o = ois.readObject();
            TO_ADD_FILES = o == null ? new HashMap<>() : (HashMap<String, String>) o;
            ois.close();
            fis.close();
        } catch (EOFException ignored) {
        } catch (Exception e) {
            System.out.println("Error read index: " + e);
        }
    }

    public static void readFilesToBeRemoved() {
        try {
            File toRemove = Repository.TO_REMOVE;
            FileInputStream fis = new FileInputStream(toRemove);
            ObjectInputStream ois = new ObjectInputStream(fis);
            Object o = ois.readObject();
            TO_REMOVE_FILES = o == null ? new HashSet<>() : (HashSet<String>) o;
            ois.close();
            fis.close();
        } catch (EOFException ignored) {
        } catch (Exception e) {
            System.out.println("Error read index: " + e);
        }
    }

    public static void writeIndex() {
        try {
            File index = Repository.INDEX;
            FileOutputStream fos = new FileOutputStream(index);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(INDEX_FILES);
            oos.flush();
            oos.close();
            fos.close();
        } catch (Exception e) {
            System.out.println("Error: " + e);
        }
    }
    public static void writeToAdd() {
        try {
            File toAdd = Repository.TO_ADD;
            FileOutputStream fos = new FileOutputStream(toAdd);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(TO_ADD_FILES);
            oos.flush();
            oos.close();
            fos.close();
        } catch (Exception e) {
            System.out.println("Error: " + e);
        }
    }

    public static void writeToRemove() {
        try {
            File toRemove = Repository.TO_REMOVE;
            FileOutputStream fos = new FileOutputStream(toRemove);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(TO_REMOVE_FILES);
            oos.flush();
            oos.close();
            fos.close();
        } catch (Exception e) {
            System.out.println("Error: " + e);
        }
    }

    public static void readAll() {
        readIndex();
        readFilesToBeAdded();
        readFilesToBeRemoved();
    }
    public static void writeAll() {
        writeIndex();
        writeToAdd();
        writeToRemove();
    }

    public static void clear() {
        clearToAdd();
        clearToRemove();
    }

    public static void clearToAdd() {
        TO_ADD_FILES.clear();
    }

    public static void clearToRemove() {
        TO_REMOVE_FILES.clear();
    }

    public static HashMap<String, String> getIndexContents() {
        readIndex();
        HashMap<String, String> contents = new HashMap<>();
        for (NameAndStatus key : INDEX_FILES.keySet()) {
            contents.put(key.name, INDEX_FILES.get(key));
        }
        return contents;
    }

    public static void updateIndex(HashMap<NameAndStatus, String> newContents) {
        INDEX_FILES = newContents;
        writeIndex();
    }
}

