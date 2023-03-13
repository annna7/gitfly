package gitfly;

import static gitfly.Utils.*;

import java.io.File;
import java.io.IOException;
import java.util.*;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Repository {
    /*
     * A repository is a directory that contains a .gitfly directory.
     *
     *     .gitfly
     *          HEAD
     *          index
     *          config
     *          objects
     *              commits
     *              trees
     *              blobs
     *              tags
     *          refs
     *              heads
     *              tags
    */
    public static final Integer NOT_CONFLICT = 0;
    public static final Integer CONFLICT_BASE = 1;
    public static final Integer CONFLICT_RECEIVER = 2;
    public static final Integer CONFLICT_GIVER = 3;
    public static final File CWD = new File(System.getProperty("user.dir"));
    public static final File GITFLY_DIR = join(CWD, ".gitfly");
    public static final File HEAD = join(GITFLY_DIR, "HEAD");
    public static final File INDEX = join(GITFLY_DIR, "index");
    public static final File TO_ADD = join(GITFLY_DIR, "to_add");
    public static final File TO_REMOVE = join(GITFLY_DIR, "to_remove");
    public static final File CONFIG = join(GITFLY_DIR, "config");
    public static final File OBJECTS_DIR = join(GITFLY_DIR, "objects");
    public static final File REFS_DIR = join(GITFLY_DIR, "refs");
    public static final File HEADS_DIR = join(REFS_DIR, "heads");
    public static final File TAGS_DIR = join(REFS_DIR, "tags");

    public static void init() throws IOException {
        if (GITFLY_DIR.exists()) {
            exit("A gitfly repository already exists in the current directory.");
        }
        mkdir(GITFLY_DIR);
        mkdir(OBJECTS_DIR);
        mkdir(REFS_DIR);
        mkdir(HEADS_DIR);
        mkdir(TAGS_DIR);

        initHEAD();
        TO_ADD.createNewFile();
        TO_REMOVE.createNewFile();
        INDEX.createNewFile();
        CONFIG.createNewFile();
        Config.initConfig();
        // Create empty snapshot of the working directory
        String emptyTreeID = getSHA1("");
        addObjectToObjectDirectory(emptyTreeID, "");
        String initialCommitID = initCommit();
        initBranch("master", initialCommitID);
        System.out.println("SNAPSHOT: " + getCurrentSnapshotID() + "g");

        exit("Initialized empty gitfly repository in %s", CWD.getPath());
    }

    private static void initBranch(String branch, String commitID) {
        File branch_file = join(HEADS_DIR, branch);
        try {
            if (branch_file.createNewFile()) {
                System.out.println("Created file: " + branch_file.getName() + " " + branch_file.getPath());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("BRANCH FILE: " + branch_file.getPath());
        writeContents(branch_file, commitID);
    }

    private static String getCurrentCommitID() {
        String head = fileContentsToString(HEAD);
        String[] head_split = head.split(" ");
        File branch_file = join(GITFLY_DIR, head_split[1]);
        System.out.println("BRANCH FILE: " + branch_file.getPath());
        return fileContentsToString(branch_file);
    }

    private static String initCommit() {
        Commit initialCommit = new Commit("Initial commit", getSHA1(""), null);
        return initialCommit.getCommitID();
    }

    /**
     * Accepts a variable number of paths.
     * Computes an ArrayList of all the files which satisfy the paths (the files that exist in the working directory).
     * For each such file, builds a Blob object.
     * Adds to OBJ_DIR a Blob object (name: SHA1 of content, content: binary of original content)
     * Adds {status} {SHA1} {filename} to index and replaces old SHA1 with new SHA1 if necessary.
     */
    public static void add(String... paths) {
        // create array lists with paths that exist in cwd
        List<File> files = new ArrayList<File>();
        for (String path : paths) {
            File file = getFile(String.valueOf(CWD), path);
            if (file == null) {
                System.out.printf("No files matching %s", path);
                System.out.println();
            } else {
                System.out.println("File added: " + file.getPath());
                // append file to files array
                files.add(file);
            }
        }
        Stage.readAll();
        System.out.println("TO ADD FILES: " + Stage.TO_ADD_FILES);
        for (File filename : files) {
            Blob blob = new Blob(filename);
            addObjectToObjectDirectory(blob.getID(), blob.getStringData());
            // Daca nu exista in index cu valoarea corecta, adauga-l in index
            //         Adauga-l in TO_ADD cu valoarea corecta.
            //         Daca este in TO_REMOVE, sterge-l din TO_REMOVE.
            // Daca exista in index cu valoarea corecta, atunci scoate-l din TO_ADD daca exista.
            String filepath = blob.getRelativePath();
            String newBlobHash = blob.getID();
            String oldBlobHash = Stage.getFromIndex(NOT_CONFLICT, filepath);
            // daca este in index cu o alta valoare atunci o suprascriu
            // daca este deja in index cu valoarea corecta nu mai fac nimic
            if (oldBlobHash == null || !oldBlobHash.equals(blob.getID())) {
                // Daca este in index cu o alta valoare, atunci o suprascriu
                if (oldBlobHash != null) {
                    Stage.removeFromIndex(filepath, NOT_CONFLICT);
                }
                Stage.addToIndex(filepath, NOT_CONFLICT, newBlobHash);
            }
            // Acum se afla in index cu valoarea corecta
            // Daca este in TO_ADD cu o alta valoare, atunci o suprascriu
            if (Stage.getFromToAdd(filepath) == null || !Stage.getFromToAdd(filepath).equals(newBlobHash)) {
                if (Stage.getFromToAdd(filepath) != null) {
                    Stage.removeFromToAdd(filepath);
                }
                Stage.addToToAdd(filepath, newBlobHash);
            }
            // Acum se afla in TO_ADD cu valoarea corecta
            // Daca se afla in TO_REMOVE, atunci o sterg
            if (Stage.isInToRemove(filepath)) {
                Stage.removeFromToRemove(filepath);
            }
//            if (!oldBlobHash.equals(blob.getID())) {
//                Stage.removeFromIndex(filepath, NOT_CONFLICT);
//            }
//            Stage.addToIndex(filepath, NOT_CONFLICT, newBlobHash);
//
//            if (oldBlobHash == null || !oldBlobHash.equals(blob.getID())) {
//                Stage.addToIndex(filepath, NOT_CONFLICT, newBlobHash);
//                if (Stage.getFromToAdd(filepath) == null || !Stage.getFromToAdd(filepath).equals(newBlobHash)) {
//                    Stage.addToToAdd(filepath, newBlobHash);
//                }
//                if (Stage.isInToRemove(filepath)) {
//                    Stage.removeFromToRemove(filepath);
//                }
//            } else {
//                if (Stage.getFromToAdd(filepath) != null) {
//                    Stage.removeFromToAdd(filepath);
//                }
//            }
        }
        Stage.writeAll();
    }

    /**
     * Accepts a variable number of paths.
     * If path in index:
     *     Delete path from index.
     *     If path in TO_ADD, remove it from TO_ADD.
     *     Add path to TO_REMOVE.
     *     Delete path from disk if it exists.
     *     OBS: Should also check if path is in current commit. Insert it into TO_REMOVE only if it is.
     * @param paths
     */
    public static void rm(String ...paths) {
        Stage.readAll();
        for (String path : paths) {
            if (Stage.getFromIndex(NOT_CONFLICT, path) != null) {
                System.out.println(path + " got deleted");
                Stage.removeFromIndex(path, NOT_CONFLICT);
                Stage.addToToRemove(path);
                if (Stage.getFromToAdd(path) != null) {
                    Stage.removeFromToAdd(path);
                }
                File file = getFile(String.valueOf(CWD), path);
                if (file != null) {
                    file.delete();
                }
            } else {
                System.out.println("No reason to remove the file.");
            }
        }
        Stage.writeAll();
    }

    public static void addObjectToObjectDirectory(String name, String content) {
        File object = join(OBJECTS_DIR, name);
        writeContents(object, content);
    }

    public static void commit(String message) {
        Stage.readAll();
        String buildTree = buildUpdatedTree(CWD, getCurrentSnapshotID(), Stage.TO_ADD_FILES, Stage.TO_REMOVE_FILES);
        Commit commit = new Commit(message, buildTree, getCurrentCommitID());
        updateCurrentBranch(commit.getCommitID());
        Stage.clear();
        Stage.writeAll();
    }

    public static String getCurrentSnapshotID() {
        File currentCommit = join(OBJECTS_DIR, getCurrentCommitID());
        String commitContent = fileContentsToString(currentCommit);
        return commitContent.split(" ")[1].split("\n")[0];
    }
    private static void updateCurrentBranch(String newCommitID) {
        String head = fileContentsToString(HEAD);
        String[] head_split = head.split(" ");
        File branch_file = join(GITFLY_DIR, head_split[1]);
        writeContents(branch_file, newCommitID);
    }

    private static void initHEAD() {
        String head = "ref: refs/heads/master";
        writeContents(HEAD, head);
    }

    public static void checkIfGitflyInitialized() {
        if (!GITFLY_DIR.exists()) {
            exit("Not in an initialized gitfly repository.");
        }
    }

    static String buildUpdatedTree(File currentDirectory, String oldTreeHash, HashMap<String, String> toAdd, HashSet <String> toRemove) {
        File oldTree = join(OBJECTS_DIR, oldTreeHash);
        System.out.println(oldTreeHash);
        System.out.println("oldTree: " + oldTree.getPath());
        String oldTreeContent = fileContentsToString(oldTree);
        StringBuilder newTreeContent = new StringBuilder();
        System.out.println(oldTree.getPath());
        System.out.println("toAdd: " + toAdd);
        if (!oldTreeContent.equals("")) {
            System.out.println("oldTreeContent: " + oldTreeContent);
            for (String line : oldTreeContent.split("\n")) {
                String type, hash, filename;
                type = line.split(" ")[0];
                hash = line.split(" ")[1];
                filename = line.split(" ")[2];
                System.out.println("type: " + type + " hash: " + hash + " filename: " + filename);
                if (type.equals("blob")) {
                    if (toAdd.containsKey(filename)) {
                        String newHash = toAdd.get(filename);
                        newTreeContent.append("blob").append(" ").append(newHash).append(" ").append(filename).append("\n");
                        toAdd.remove(filename);
                    }
                    else if (!toRemove.contains(filename)) {
                        newTreeContent.append(line).append("\n");
                    } else {
                        toRemove.remove(filename);
                    }
                } else if (type.equals("tree")) {
                    // Keys must start with "${filename}/"
                    HashMap<String, String> newToAdd = fromDirectory(toAdd, filename);
                    HashSet<String> newToRemove = fromDirectory(toRemove, filename);
                    removeFilesFromDirectory(toAdd, filename);
                    removeFilesFromDirectory(toRemove, filename);
                    if (!newToAdd.isEmpty()|| !newToRemove.isEmpty()) {
                        File goDown = join(currentDirectory, filename);
                        String newHash = buildUpdatedTree(goDown, hash, newToAdd, newToRemove);
                        newTreeContent.append("tree").append(" ").append(newHash).append(" ").append(filename).append("\n");
                    } else {
                        newTreeContent.append(line).append("\n");
                    }
                }
            }
        }
        Set <String> visitedChildDirectories = new HashSet<>();
        for (String filename : toAdd.keySet()) {
            String hash = toAdd.get(filename);
            System.out.println("filename: " + filename + " hash: " + hash);
            if (isNormalFile(filename)) {
                newTreeContent.append("blob").append(" ").append(hash).append(" ").append(filename).append("\n");
            } else {
                String firstChildDirectory = getFirstDirectory(filename);
                if (visitedChildDirectories.contains(firstChildDirectory)) {
                    continue;
                }
                visitedChildDirectories.add(firstChildDirectory);
                File goDown = join(currentDirectory, firstChildDirectory);
                if (goDown.exists()) {
                    System.out.println("goDown: " + goDown);
                }
                String newHash = buildUpdatedTree(goDown, getSHA1(""), fromDirectory(toAdd, firstChildDirectory), fromDirectory(toRemove, firstChildDirectory));
                newTreeContent.append("tree").append(" ").append(newHash).append(" ").append(firstChildDirectory).append("\n");
            }
        }
        String newTreeHash = getSHA1(newTreeContent.toString());
        String newTreeContentString = newTreeContent.toString();
        addObjectToObjectDirectory(newTreeHash, newTreeContentString);
        return newTreeHash;
    }

    private static HashMap<String, String> fromDirectory(HashMap<String, String> toAdd, String filename) {
        HashMap<String, String> newToAdd = new HashMap<>();
        for (String key : toAdd.keySet()) {
            if (key.startsWith(filename + "/")) {
                newToAdd.put(key.substring(filename.length() + 1), toAdd.get(key));
            }
        }
        return newToAdd;
    }

    private static HashSet<String> fromDirectory(HashSet<String> toRemove, String filename) {
        HashSet<String> newToRemove = new HashSet<>();
        for (String element : toRemove) {
            if (element.startsWith(filename + "/")) {
                newToRemove.add(element.substring(filename.length() + 1));
            }
        }
        return newToRemove;
    }

    private static void removeFilesFromDirectory(HashMap<String, String> mp, String filename) {
        for (String key : mp.keySet()) {
            if (key.startsWith(filename + "/")) {
                mp.remove(key);
            }
        }
    }

    private static void removeFilesFromDirectory(HashSet<String> st, String filename) {
        st.removeIf(element -> element.startsWith(filename + "/"));
    }
}
