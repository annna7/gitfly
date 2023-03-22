package gitfly;

import static gitfly.Utils.*;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.*;

public class Repository {
    /*
     * A repository is a directory that contains a .gitfly directory.
     *
     *     .gitfly
     *          HEAD
     *          index
     *          config
     *          to_add
     *          to_remove
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
    public static final String EMPTY_FILE_ID = getSHA1("");
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
    public static final File MERGE_HEAD = join(GITFLY_DIR, "MERGE_HEAD");
    public static final Character MODIFY = 'M';
    public static final Character ADD = 'A';
    public static final Character REMOVE = 'R';
    public static final Character CONFLICT = 'C';
    public static final Character SAME = 'S';
    public static String INITIAL_COMMIT_ID;

    public static final HashMap<Character, String> STATUS_CODE = new HashMap<Character, String>() {{
        put(MODIFY, "Modified: ");
        put(ADD, "Added: ");
        put(REMOVE, "Removed: ");
        put(CONFLICT, "Conflicted: ");
        put(SAME, "Same: ");
    }};

    public static class FileStatus {
        public Character status;
        public String receiver;
        public String giver;
        public String base;
        public FileStatus(String receiver, String giver, String base) {
            this.status = fileStatus(receiver, giver, base);
            this.receiver = receiver;
            this.giver = giver;
            this.base = base;
        }
        @Override
        public String toString() {
            return "FILE STATUS: " + status + " " + receiver + " " + giver + " " + base + "\n";
        }

        public Character getStatus() {
            return status;
        }

        public String getReceiver() {
            return receiver;
        }
        public String getBase() { return base; }
        public String getGiver() { return giver; }
    }

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

    public static void branch(String branch_name) {
        File branch_file = join(HEADS_DIR, branch_name);
        if (branch_file.exists()) {
            exit("A branch called %s already exists", branch_name);
        } else {
            try {
                if (branch_file.createNewFile()) {
                    writeContents(branch_file, getCurrentCommitID());
                    exit("Created an empty branch called %s", branch_name);
                }
            } catch (IOException e){
                e.printStackTrace();
            }
        }
    }

    public static void rm_branch(String branch_name) {
        File branch_file = join(HEADS_DIR, branch_name);
        if (!branch_file.exists()) {
            exit("There is no branch called %s", branch_name);
        } else {
            if (branch_file.delete()) {
                exit("Successfully deleted branch %s", branch_name);
            }
        }
    }

    public static void status() {
        System.out.println("BRANCHES:");
        for (File branch : HEADS_DIR.listFiles()) {
            if (branch.getName().equals(getCurrentBranchName())) {
                System.out.println("*" + branch.getName());
            } else {
                System.out.println(branch.getName());
            }
        }
        System.out.println("====================");
        System.out.println("UNTRACKED FILES: ");
        HashSet<String> untrackedFiles = getUntrackedFiles();
        for (String untrackedFile : untrackedFiles) {
            System.out.println(untrackedFile);
        }
        System.out.println("====================");
        System.out.println("Changes to be commited: ");
        HashMap<String, Character> changesToBeCommitted = getChangesToBeCommitted();
        for (String s : changesToBeCommitted.keySet()) {
            System.out.println(STATUS_CODE.get(changesToBeCommitted.get(s)) + " " + s);
        }
        System.out.println("====================");
        System.out.println("Changes not staged for commit:");
        HashMap<String, Character> changesNotStagedForCommit = getChangesNotStagedForCommit();
        for (String s : changesNotStagedForCommit.keySet()) {
            System.out.println(STATUS_CODE.get(changesNotStagedForCommit.get(s)) + " " + s);
        }
        System.out.println("====================");
        HashSet<String> filesInConflict = getFilesInConflict();
        if (filesInConflict.size() > 0) {
            System.out.println("Files in conflict: ");
            for (String fileInConflict : filesInConflict) {
                System.out.println(fileInConflict);
            }
        }
    }

    private static void modifyHEAD(String argument) {
        writeContents(HEAD, argument);
    }

    static String getCurrentBranchName() {
        String headContents = fileContentsToString(HEAD);
        if (headContents.startsWith("ref: refs/heads/")){
            return headContents.split(" ")[1].split("/")[2];
        } else {
            return null;
        }
    }

    public static HashSet<String> getUntrackedFiles() {
        HashMap<String, String> workingDirectoryContents = getWorkingDirectoryContents();
        HashMap<String, String> indexContents = Stage.getIndexContents();
        HashSet<String> untrackedFiles = new HashSet<>();
        for (String key : workingDirectoryContents.keySet()) {
            if (!indexContents.containsKey(key)) {
                untrackedFiles.add(key);
            }
        }
        return untrackedFiles;
    }

    public static HashMap<String, Character> getChangesToBeCommitted() {
        HashMap<String, FileStatus> changesFromHeadToIndex = diff(getCurrentCommitID(), "INDEX", null);
        HashMap<String, Character> changesToBeCommitted = new HashMap<>();
        return getStringCharacterHashMap(changesToBeCommitted, (HashMap<String, FileStatus>) changesFromHeadToIndex);
    }

    public static HashMap<String, Character> getChangesNotStagedForCommit() {
        // difference between working directory and index
        HashMap<String, Character> changesNotStagedForCommit = new HashMap<>();
        HashMap<String, FileStatus> changesFromIndexToWorkingCopy = diff(null, null, null);
        System.out.println("INDEX CONTENTS:\n");
        printHashMap(Stage.getIndexContents());
        System.out.println("WORKING DIRECTORY CONTENTS:\n");
        printHashMap(getWorkingDirectoryContents());
        return getStringCharacterHashMap(changesNotStagedForCommit, changesFromIndexToWorkingCopy);
    }

    private static HashMap<String, Character> getStringCharacterHashMap(HashMap<String, Character> changesNotStagedForCommit, HashMap<String, FileStatus> changesFromIndexToWorkingCopy) {
        for (String filename : changesFromIndexToWorkingCopy.keySet()) {
            FileStatus fileStatus = changesFromIndexToWorkingCopy.get(filename);
            if (fileStatus.getStatus() == ADD || fileStatus.getStatus() == MODIFY || fileStatus.getStatus() == REMOVE) {
                changesNotStagedForCommit.put(filename, fileStatus.getStatus());
            }
        }
        return changesNotStagedForCommit;
    }

    private static boolean isCommitID(String commitID) {
        File commitFile = join(OBJECTS_DIR, commitID);
        if (!commitFile.exists()) {
            return false;
        }
        return fileContentsToString(commitFile).split("\n")[1].split(" ")[0].equals("parent");
    }


    private static HashMap<Stage.NameAndStatus, String> getIndexContent(String commitID) {
        HashMap<Stage.NameAndStatus, String> indexContent = new HashMap<>();
        // status is only used when dealing with merge conflicts, so it is set to NOT_CONFLICT at the moment.
        HashMap<String, String> commitContent = getCommitContents(commitID);
        for (String key : commitContent.keySet()) {
            indexContent.put(new Stage.NameAndStatus(key, NOT_CONFLICT), commitContent.get(key));
        }
        return indexContent;
    }

    public static HashSet<String> getFilesCommitWouldOverwrite(String commitID) {
        String currentCommitID = getCurrentCommitID();
        HashMap<String, FileStatus> diff1 = diff(currentCommitID, null, null);
        HashMap<String, FileStatus> diff2 = diff(currentCommitID, commitID, null);
        HashSet<String> filesToOverwrite = new HashSet<>();
        // intersection of files that are different in both diffs
        for (String key : diff1.keySet()) {
            if (diff1.get(key).getStatus() != SAME && diff2.containsKey(key) && diff2.get(key).getStatus() != SAME) {
                filesToOverwrite.add(key);
            }
        }
        return filesToOverwrite;
    }
    public static HashMap<String, String> getFilesWithStatus(HashMap<String, FileStatus> diff, Character status) {
        HashMap<String, String> result = new HashMap<>();
        for (String key : diff.keySet()) {
            if (diff.get(key).getStatus() == status) {
                result.put(key, diff.get(key).getReceiver());
            }
        }
        return result;
    }
    public static void printHashMap(HashMap<String, String> h) {
        for (String key : h.keySet()) {
            System.out.println(key + " " + h.get(key));
        }
        System.out.println("========================");
    }

    public static HashMap<String, String> getCommitContents(String commitID) {
        String treeID = Commit.getSnapshotID(commitID);
        return getTreeContents("", treeID);
    }

    private static HashMap<String, String> getTreeContents(String currentDirectoryPrefix, String treeID) {
        HashMap<String, String> result = new HashMap<>();
        String treeContents = fileContentsToString(new File(OBJECTS_DIR, treeID));
        if (treeContents.equals("")) {
            return result;
        }
        String[] lines = treeContents.split("\n");
        for (String line : lines) {
            String[] tokens = line.split(" ");
            String prefix = currentDirectoryPrefix.equals("") ? "" : currentDirectoryPrefix + "/";
            if (tokens[0].equals("blob")) {
                result.put(prefix + tokens[2], tokens[1]);
            } else {
                result.putAll(getTreeContents(prefix + tokens[2], tokens[1]));
            }
        }
        return result;
    }

    public static HashMap<String, String> getWorkingDirectoryContents() {
        return getWorkingDirectoryContentsHelper(CWD, "");
    }

    private static HashMap<String, String> getWorkingDirectoryContentsHelper(File currDirectory, String currDirectoryPrefix) {
        File[] files = currDirectory.listFiles();
        HashMap<String, String> result = new HashMap<>();
        if (files == null) {
            return result;
        }
        for (File file : files) {
            if (file.getName().startsWith(".")) {
                continue;
            }
            String prefix = currDirectoryPrefix.equals("") ? "" : currDirectoryPrefix + "/";
            if (file.isFile()) {
                result.put(prefix + file.getName(), getSHA1(fileContentsToString(file)));
            } else {
                result.putAll(getWorkingDirectoryContentsHelper(file, prefix + file.getName()));
            }
        }
        return result;
    }

    private static String getCommitOfBranch(String branchName) {
        File branch_file = join(HEADS_DIR, branchName);
        return fileContentsToString(branch_file);
    }

    private static boolean isBranchName(String branchName) {
        File branch_file = join(HEADS_DIR, branchName);
        return branch_file.exists();
    }

    public static String getCurrentCommitID() {
        String head = fileContentsToString(HEAD);
        if (head.startsWith("ref")) {
            String[] head_split = head.split(" ");
            File branch_file = join(GITFLY_DIR, head_split[1]);
            return fileContentsToString(branch_file);
        } else {
            return head;
        }
    }

    private static String initCommit() {
        Commit initialCommit = new Commit("Initial commit", EMPTY_FILE_ID, null);
        Repository.INITIAL_COMMIT_ID = initialCommit.getCommitID();
        return initialCommit.getCommitID();
    }

    /**
     * Returns whether a given file is SAME, ADDED, REMOVED, MODIFIED OR IN CONFLICT.
     */
    public static Character fileStatus(String receiver, String giver, String base) {
        /*if (receiver == null && giver == null) {
            return SAME;
        }
        if (receiver != null && giver != null && !receiver.equals(giver)) {
            if (!receiver.equals(base) && !giver.equals(base)) {
                return CONFLICT;
            } else {
                return MODIFY;
            }
        }
        if (receiver != null && receiver.equals(giver)) {
            return SAME;
        }
        if (giver != null && base == null){
            return ADD;
        }
        return REMOVE;

         */

        if (receiver == null && giver == null) {
            if (base == null) {
                return SAME;
            } else {
                return REMOVE;
            }
        }
        if (receiver != null && giver != null) {
            if (receiver.equals(giver)) {
                return SAME;
            } else if (!receiver.equals(giver)) {
                if (!receiver.equals(base) && !giver.equals(base)) {
                    return CONFLICT;
                } else {
                    return MODIFY;
                }
            }
        }
        return ADD;

//        if (giver == null && receiver == null) {
//            return SAME;
//        }
//        if (giver == null) {
//            return ADD;
//        }
//        if (receiver == null) {
//            return REMOVE;
//        }
//        if (giver.equals(receiver)) {
//            return SAME;
//        }
//        return MODIFY;
    }

    // Computes diff between repository in two different states
    // state1 - the state of the repository before the change
    // if null, then the state represents the index, else a commit id
    // state2 - the state of the repository after the change
    // if null, then the state represents the working directory, else a commit id
    public static HashMap<String, FileStatus> diff(String giver, String receiver, String base) {
        HashMap<String, String> giverContents = giver == null ? Stage.getIndexContents() : getCommitContents(giver);
        HashMap<String, String> receiverContents;
        if (receiver == null) {
            receiverContents = getWorkingDirectoryContents();
        } else {
            receiverContents = receiver.equals("INDEX") ? Stage.getIndexContents() : getCommitContents(receiver);
        }
        HashMap<String, String> baseContents = base == null ? receiverContents : getCommitContents(base);
        HashMap<String, FileStatus> diffResult = new HashMap<>();
        Set<String> allKeys = new HashSet<>(receiverContents.keySet());
        allKeys.addAll(giverContents.keySet());
        allKeys.addAll(baseContents.keySet());

//        if (base == null) {
//            for (String key : receiverContents.keySet()) {
//                diffResult.put(key, new FileStatus(receiverContents.getOrDefault(key, null), giverContents.getOrDefault(key, null), null));
//            }
//            for (String key : giverContents.keySet()) {
//                if (!diffResult.containsKey(key)) {
//                    diffResult.put(key, new FileStatus(receiverContents.getOrDefault(key, null), giverContents.getOrDefault(key, null), null));
//                }
//            }
//        }
        for (String key : allKeys) {
            diffResult.put(key, new FileStatus(receiverContents.getOrDefault(key, null), giverContents.getOrDefault(key, null), baseContents.getOrDefault(key, null)));
        }
        return diffResult;
    }

    public static String getContentOfConflictedFile(String giverID, String receiverID) {
        return "<<<<<<< HEAD\n" + fileContentsToString(join(OBJECTS_DIR, receiverID)) +
                "=======\n" +
                fileContentsToString(join(OBJECTS_DIR, giverID)) +
                ">>>>>>> branch\n";
    }

    public static void writeToWorkingCopyForMerge(String giver, String receiver, String base) {
        HashMap<String, FileStatus> writeToWorkingCopy = diff(giver, receiver, base);
        for (String key : writeToWorkingCopy.keySet()) {
            if (writeToWorkingCopy.get(key).getStatus() == MODIFY) {
                writeContents(join(CWD, key), fileContentsToString(join(OBJECTS_DIR, writeToWorkingCopy.get(key).getReceiver())));
            } else if (writeToWorkingCopy.get(key).getStatus() == REMOVE) {
                join(CWD, key).delete();
            } else if (writeToWorkingCopy.get(key).getStatus() == ADD) {
                if (writeToWorkingCopy.get(key).getReceiver() != null) {
                    writeContents(join(CWD, key), fileContentsToString(join(OBJECTS_DIR, writeToWorkingCopy.get(key).getReceiver())));
                } else {
                    writeContents(join(CWD, key), fileContentsToString(join(OBJECTS_DIR, writeToWorkingCopy.get(key).getGiver())));
                }
            } else if (writeToWorkingCopy.get(key).getStatus() == CONFLICT) {
                writeContents(join(CWD, key), getContentOfConflictedFile(writeToWorkingCopy.get(key).getGiver(), writeToWorkingCopy.get(key).getReceiver()));
            }
        }
    }

    public static void checkoutToCommit(String commitID) {
        HashMap<String, FileStatus> diffResult = diff(null, commitID, null);
        // Check if there are files that are changed in the working directory and different in HEAD commit and commit to check out to
        // If there are, abort checkout because those changes would be lost
//        HashSet<String> filesThatWouldBeOverwritten = getFilesCommitWouldOverwrite(commitID);
//        if (!filesThatWouldBeOverwritten.isEmpty()) {
//            exit("There are files that would be overwritten by checkout.");
//        }
        // Modify the working directory to match the files in the given commit ID
        // writeToWorkingCopy(getCurrentCommitID(), commitID, null);
        HashMap<String, FileStatus> writeToWorkingCopy = diff(getCurrentCommitID(), commitID,null);
        for (String key : writeToWorkingCopy.keySet()) {
            if (writeToWorkingCopy.get(key).getStatus() == MODIFY) {
                writeContents(join(CWD, key), fileContentsToString(join(OBJECTS_DIR, writeToWorkingCopy.get(key).getReceiver())));
            } else if (writeToWorkingCopy.get(key).getStatus() == REMOVE) {
                join(CWD, key).delete();
            } else if (writeToWorkingCopy.get(key).getStatus() == ADD) {
                if (writeToWorkingCopy.get(key).getReceiver() != null) {
                    writeContents(join(CWD, key), fileContentsToString(join(OBJECTS_DIR, writeToWorkingCopy.get(key).getReceiver())));
                } else {
                    writeContents(join(CWD, key), fileContentsToString(join(OBJECTS_DIR, writeToWorkingCopy.get(key).getGiver())));
                }
            }
        }

        // update the index to match the files in the given commit ID
        Stage.updateIndex(getIndexContent(commitID));
    }

    /**
     * Changes the index, working copy and HEAD to reflect the content of {argument}
     * Two cases:
     * 1. Receives branch name
     * 2. Receives commit ID => DETACHED HEAD state
     * @param argument branch name or commit id
     */
    public static void checkout(String argument) {
        if (MERGE_HEAD.exists()) {
            exit("Cannot perform this command until the merge conflict has been resolved.");
        }
        if (isBranchName(argument)) {
            if (getCurrentBranchName() != null && argument.equals(getCurrentBranchName())) {
                exit("No need to checkout the current branch.");
            }
            checkoutToCommit(getCommitOfBranch(argument));
            modifyHEAD("ref: refs/heads/" + argument);
            exit("Switched out to %s\n", argument);
        } else if (isCommitID(argument)) {
            System.out.println("DETACHED HEAD STATE\n");
            if (argument.equals(fileContentsToString(HEAD))) {
                exit("[DETACHED HEAD STATE]\nNo need to checkout the current commit.");
            }
            checkoutToCommit(argument);
            modifyHEAD(argument);
            exit("Note: checking out to %s\nYou are in detached HEAD state", argument);
        } else {
            exit("Not a branch name or a commit id: %s", argument);
        }
    }

    public static void merge(String giver) {
        if (detachedHeadState()) {
            exit("Merges are unsupported in detached head state");
        }
        String receiverID = getCurrentCommitID();
        if (!isBranchName(giver)) {
            exit("Branch %s doesn't exist.", giver);
        }
        String giverID = getCommitIDOfBranch(giver);
        if (giverID.equals(receiverID)) {
            exit("Cannot merge a branch with itself.");
        }
        if (isAncestor(giverID, receiverID)) {
            exit("Already up-to-date.");
        }
        if (isAncestor(receiverID, giverID)) {
            checkoutToCommit(giverID);
            File receiver_branch = join(HEADS_DIR, getCurrentBranchName());
            writeContents(receiver_branch, getCommitIDOfBranch(giver));
            exit("Fast-forwarded.");
        }
        if (MERGE_HEAD.exists()) {
            exit("Merge already happening.");
        }
        try {
            MERGE_HEAD.createNewFile();
        } catch (IOException e) {
            exit("Error creating MERGE_HEAD file.");
        }
        writeContents(MERGE_HEAD, giverID);
        String baseID = getLCA(giverID, receiverID);
        HashMap<String, FileStatus> diffResult = diff(giverID, receiverID, baseID);

        writeToWorkingCopyForMerge(giverID, receiverID, baseID);

        Stage.updateIndexFromDiff(diffResult);

        HashSet<String> filesInConflict = getFilesInConflict(diffResult);

        if (filesInConflict.isEmpty()) {
            String commitMessage = "Merged " + giver + " into " + getCurrentBranchName() + ".";
            String newTreeID = buildUpdatedTree(CWD, EMPTY_FILE_ID, getWorkingDirectoryContents(), null);
            Commit newCommit = new Commit(commitMessage, newTreeID, giverID);
            MERGE_HEAD.delete();
            updateCurrentBranch(newCommit.getCommitID());
            // createCommit(commitMessage, receiverID);
            exit("Merged %s into %s.", giver, getCurrentBranchName());
        } else {
            exit("Encountered a merge conflict.\nThe following files are in conflict:\n%s", filesInConflict.toString().replace("[", "").replace("]", ""));
        }
    }
    public static HashSet<String> getFilesInConflict() {
        HashMap<Stage.NameAndStatus, String> indexFiles = Stage.getIndexFiles();
        HashSet<String> filesInConflict = new HashSet<>();
        for (Stage.NameAndStatus nameAndStatus : indexFiles.keySet()) {
            if (nameAndStatus.getStatus() != 0) {
                filesInConflict.add(nameAndStatus.getName());
            }
        }
        return filesInConflict;
    }

    public static HashSet<String> getFilesInConflict(HashMap<String, FileStatus> files) {
        HashSet<String> filesInConflict = new HashSet<>();
        for (String filename : files.keySet()) {
            if (files.get(filename).getStatus() == CONFLICT) {
                filesInConflict.add(filename);
            }
        }
        return filesInConflict;
    }

    // Find what commit is higher in the commit tree.
    // Traverse the two arrays simultaneously until a common commit is found.
    // Complexity of both reading ancestors and solving query is linear in terms of the commit tree height.
    public static String getLCA(String commit1, String commit2) {
        ArrayList<String> ancestors1 = getAncestorsOfCommit(commit1);
        ArrayList<String> ancestors2 = getAncestorsOfCommit(commit2);
        int minNodeIndex = Math.min(ancestors1.size(), ancestors2.size());
        for (int i = minNodeIndex - 1; i >= 0; --i) {
            if (ancestors1.get(i).equals(ancestors2.get(i))) {
                return ancestors1.get(i);
            }
        }
        return null;
//        for (String ancestor : ancestors1) {
//            if (ancestors2.contains(ancestor)) {
//                return ancestor;
//            }
//        }
//        return null;
    }

    public static boolean isAncestor(String childID, String parentID) {
        return getAncestorsOfCommit(parentID).contains(childID);
    }

    public static ArrayList<String> getAncestorsOfCommit(String commitID) {
        ArrayList<String> ancestors = new ArrayList<>();
        String currentCommitID = commitID;
        while (!currentCommitID.equals("null")) {
            ancestors.add(currentCommitID);
            currentCommitID = Commit.getParentID(currentCommitID);
        }
        return ancestors;
    }

    public static String getCommitIDOfBranch(String branch) {
        File branch_file = join(HEADS_DIR, branch);
        return fileContentsToString(branch_file);
    }

    private static boolean detachedHeadState() {
        return !fileContentsToString(HEAD).startsWith("ref");
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
            ArrayList<String> oldBlobHashes = new ArrayList<String>(Arrays.asList(Stage.getFromIndex(NOT_CONFLICT, filepath), Stage.getFromIndex(CONFLICT_BASE, filepath), Stage.getFromIndex(CONFLICT_GIVER, filepath), Stage.getFromIndex(CONFLICT_RECEIVER, filepath)));
            // OLD :String oldBlobHash = Stage.getFromIndex(NOT_CONFLICT, filepath);
            // daca este in index cu o alta valoare atunci o suprascriu
            // daca este deja in index cu valoarea corecta nu mai fac nimic

            // CHECK IF MERGE CONFLICT EXISTS
            if (oldBlobHashes.get(1) != null || oldBlobHashes.get(2) != null || oldBlobHashes.get(3) != null) {
                Stage.readIndex();
                Stage.removeFromIndex(filepath, CONFLICT_BASE);
                Stage.removeFromIndex(filepath, CONFLICT_GIVER);
                Stage.removeFromIndex(filepath, CONFLICT_RECEIVER);
                Stage.addToIndex(filepath, NOT_CONFLICT, newBlobHash);
                Stage.writeIndex();
            } else if (oldBlobHashes.get(0) == null || !oldBlobHashes.get(0).equals(newBlobHash)) {
                if (oldBlobHashes.get(0) != null) {
                    Stage.readIndex();
                    Stage.removeFromIndex(filepath, NOT_CONFLICT);
                    Stage.writeIndex();
                }
                Stage.addToIndex(filepath, NOT_CONFLICT, newBlobHash);
            }
            /*else {
                // MERGE CONFLICT DETECTED
                if (oldBlobHashes.get(1) != null || oldBlobHashes.get(2) != null || oldBlobHashes.get(3) != null) {
                    Stage.readIndex();
                    Stage.removeFromIndex(filepath, CONFLICT_BASE);
                    Stage.removeFromIndex(filepath, CONFLICT_GIVER);
                    Stage.removeFromIndex(filepath, CONFLICT_RECEIVER);
                    Stage.addToIndex(filepath, NOT_CONFLICT, newBlobHash);
                    Stage.writeIndex();
                }
            }
             */
//            if (oldBlobHash == null || !oldBlobHash.equals(blob.getID())) {
//                // Daca este in index cu o alta valoare, atunci o suprascriu
//                if (oldBlobHash != null) {
//                    Stage.readIndex();
//                    Stage.removeFromIndex(filepath, NOT_CONFLICT);
//                    Stage.writeIndex();
//                }
//                Stage.addToIndex(filepath, NOT_CONFLICT, newBlobHash);
//            }

            // Acum se afla in index cu valoarea corecta
            // Daca este in TO_ADD cu o alta valoare, atunci o suprascriu
            if (Stage.getFromToAdd(filepath) == null || !Stage.getFromToAdd(filepath).equals(newBlobHash)) {
                if (Stage.getFromToAdd(filepath) != null) {
                    Stage.readAll();
                    Stage.removeFromToAdd(filepath);
                    Stage.writeAll();
                }
                Stage.readAll();
                Stage.addToToAdd(filepath, newBlobHash);
                Stage.writeAll();
            }
            // Acum se afla in TO_ADD cu valoarea corecta
            // Daca se afla in TO_REMOVE, atunci o sterg
            if (Stage.isInToRemove(filepath)) {
                Stage.removeFromToRemove(filepath);
            }
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
            ArrayList<String> oldBlobHashes = new ArrayList<String>(Arrays.asList(Stage.getFromIndex(NOT_CONFLICT, path), Stage.getFromIndex(CONFLICT_BASE, path), Stage.getFromIndex(CONFLICT_GIVER, path), Stage.getFromIndex(CONFLICT_RECEIVER, path)));
            if (oldBlobHashes.get(0) != null || oldBlobHashes.get(1) != null || oldBlobHashes.get(2) != null || oldBlobHashes.get(3) != null) {
                Stage.readIndex();
                Stage.removeFromIndex(path, NOT_CONFLICT);
                Stage.removeFromIndex(path, CONFLICT_BASE);
                Stage.removeFromIndex(path, CONFLICT_GIVER);
                Stage.removeFromIndex(path, CONFLICT_RECEIVER);
                Stage.writeIndex();
                System.out.println(path + " got removed from index");
                Stage.addToToRemove(path);
                if (Stage.getFromToAdd(path) != null) {
                    Stage.removeFromToAdd(path);
                }
                File file = getFile(String.valueOf(CWD), path);
                if (file != null && file.delete()) {
                    System.out.println(path + " got deleted from disk");
                }
            }
//            if (Stage.getFromIndex(NOT_CONFLICT, path) != null) {
//                System.out.println(path + " got deleted");
//                Stage.removeFromIndex(path, NOT_CONFLICT);
//                Stage.addToToRemove(path);
//                if (Stage.getFromToAdd(path) != null) {
//                    Stage.removeFromToAdd(path);
//                }
//                File file = getFile(String.valueOf(CWD), path);
//                if (file != null) {
//                    file.delete();
//                }
//        }
             else {
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
        HashSet<String> filesInConflict = getFilesInConflict();
        if (filesInConflict.size() > 0) {
            exit("Cannot perform this command until the merge conflict has been resolved.");
        }
        createCommit(message, getCurrentCommitID());
//        Stage.readAll();
//        String buildTree = buildUpdatedTree(CWD, Commit.getSnapshotID(getCurrentCommitID()), Stage.TO_ADD_FILES, Stage.TO_REMOVE_FILES);
//        Commit commit = new Commit(message, buildTree, getCurrentCommitID());
//        updateCurrentBranch(commit.getCommitID());
//        Stage.clear();
//        Stage.writeAll();
    }

    public static void createCommit(String message, String commitID) {
        Stage.readAll();
        String buildTree = buildUpdatedTree(CWD, Commit.getSnapshotID(commitID), Stage.TO_ADD_FILES, Stage.TO_REMOVE_FILES);
        if (MERGE_HEAD.exists()) {
            Commit commit = new Commit(message + "\nResolved merge conflict.\n", buildTree, commitID);
            File merge_head = join(GITFLY_DIR, "MERGE_HEAD");
            merge_head.delete();
            updateCurrentBranch(commit.getCommitID());
        } else {
            Commit commit = new Commit(message, buildTree, commitID);
            updateCurrentBranch(commit.getCommitID());
        }
        Stage.clear();
        Stage.writeAll();
    }

    private static void updateCurrentBranch(String newCommitID) {
        String name = getCurrentBranchName();
        if (name == null) {
            writeContents(HEAD, newCommitID);
        } else {
            File branch_file = join(GITFLY_DIR, "refs/heads/" + name);
            writeContents(branch_file, newCommitID);
        }
    }

    static String buildUpdatedTree(File currentDirectory, String oldTreeHash, HashMap<String, String> toAdd, HashSet <String> toRemove) {
        File oldTree = join(OBJECTS_DIR, oldTreeHash);
        String oldTreeContent = fileContentsToString(oldTree);
        StringBuilder newTreeContent = new StringBuilder();
        if (!oldTreeContent.equals("")) {
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

    public static void log() {
        String currentCommitID = getCurrentCommitID();
        do {
            System.out.println(Commit.getCommitText(currentCommitID));
            currentCommitID = Commit.getParentID(currentCommitID);
            System.out.println("==================================");
        } while (!currentCommitID.equals("null"));
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
}
