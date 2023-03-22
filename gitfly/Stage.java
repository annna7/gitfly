package gitfly;

import java.io.*;
import java.util.HashMap;
import java.util.HashSet;
class Stage {
    static class NameAndStatus implements Serializable{
        String name;
        Integer status;
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
        public String getName() {
            return name;
        }
        public Integer getStatus() {
            return status;
        }
    }
    static HashMap<String, String> TO_ADD_FILES = new HashMap<>();
    static HashSet<String> TO_REMOVE_FILES = new HashSet<>();
    private static HashMap<NameAndStatus, String> INDEX_FILES = new HashMap<>();

    static void addToIndex(String filename, Integer status, String contents) {
        NameAndStatus nameAndStatus = new NameAndStatus(filename, status);
        INDEX_FILES.put(nameAndStatus, contents);
    }

    static void removeFromIndex(String filename, Integer status) {
        NameAndStatus nameAndStatus = new NameAndStatus(filename, status);
        INDEX_FILES.remove(nameAndStatus);
    }

    static String getFromIndex(Integer status, String filename) {
        readIndex();
        NameAndStatus nameAndStatus = new NameAndStatus(filename, status);
        return INDEX_FILES.getOrDefault(nameAndStatus, null);
    }

    static String getFromToAdd(String filename) {
        return TO_ADD_FILES.getOrDefault(filename, null);
    }

    static void removeFromToAdd(String filename) {
        TO_ADD_FILES.remove(filename);
    }
    static boolean isInToRemove(String filename) {
        return TO_REMOVE_FILES.contains(filename);
    }

    static void removeFromToRemove(String filename) {
        TO_REMOVE_FILES.remove(filename);
    }

    static void addToToAdd(String filename, String contents) {
        TO_ADD_FILES.put(filename, contents);
    }

    static void addToToRemove(String filepath) {
        TO_REMOVE_FILES.add(filepath);
    }
    static void readIndex() {
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
    static void readFilesToBeAdded() {
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

    static void readFilesToBeRemoved() {
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

    static void writeIndex() {
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
    static void writeToAdd() {
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

    static void writeToRemove() {
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

    static void readAll() {
        readIndex();
        readFilesToBeAdded();
        readFilesToBeRemoved();
    }
    static void writeAll() {
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

    public static void updateIndexFromDiff(HashMap<String, Repository.FileStatus> files) {
        INDEX_FILES = new HashMap<>();
        for (String filename : files.keySet()) {
            Repository.FileStatus status = files.get(filename);
            if (status.getStatus().equals(Repository.CONFLICT)) {
                readIndex();
                addToIndex(filename, 1, status.getBase());
                addToIndex(filename, 2, status.getGiver());
                addToIndex(filename, 3, status.getReceiver());
                writeIndex();
            } else if (status.getStatus().equals(Repository.MODIFY)) {
                readIndex();
                addToIndex(filename, 0, status.getGiver());
                writeIndex();
            } else if (status.getStatus().equals(Repository.ADD) ||
                    status.getStatus().equals(Repository.SAME)) {
                readIndex();
                addToIndex(filename, 0, status.getGiver() == null ? status.getReceiver() : status.getGiver());
                writeIndex();
            }
        }
    }

    public static HashMap<NameAndStatus, String> getIndexFiles() {
        Stage.readIndex();
        return INDEX_FILES;
    }

    public static void printIndex() {
        Stage.readIndex();
        for (NameAndStatus key : INDEX_FILES.keySet()) {
            System.out.println(key.name + " " + key.status + " " + INDEX_FILES.get(key));
        }
    }

    public static void updateIndex(HashMap<NameAndStatus, String> newContents) {
        INDEX_FILES = newContents;
        writeIndex();
    }
}

