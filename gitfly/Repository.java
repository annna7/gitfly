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

    private static final HashMap<Character, String> STATUS_CODE = new HashMap<Character, String>() {{
        put(MODIFY, "Modified: ");
        put(ADD, "Added: ");
        put(REMOVE, "Removed: ");
        put(CONFLICT, "Conflicted: ");
        put(SAME, "Same: ");
    }};


    /**
     * A class that represents a file's status.
     * status - the file's status
     *               (M: modified, A: added, R: removed, C: conflicted, S: same)
     * receiver - the file's SHA1 ID in the receiver commit
     *                 (the commit that is being merged into)
     * giver - the file's SHA1 ID in the giver commit
     *              (the commit that is being merged from)
     * base - the file's SHA1 ID in the base commit
     *             (the LCA commit of the two commits being merged)
     */
    static class FileStatus {
        private final Character status;
        private final String receiver;
        private final String giver;
        private final String base;
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

    /**
     * Initializes gitfly repository in the current directory.
     * Exits if a gitfly repository already exists in the current directory.
     * Creates the necessary files and directories.
     * Creates the initial commit.
     * Creates the master branch.
     * Sets the HEAD to point to the master branch.
     * Sets the initial commit as the master branch's commit.
     * Sets the initial commit as the current commit.
     */
    static void init(){
        if (GITFLY_DIR.exists()) {
            exit("A gitfly repository already exists in the current directory.");
        }
        mkdir(GITFLY_DIR);
        mkdir(OBJECTS_DIR);
        mkdir(REFS_DIR);
        mkdir(HEADS_DIR);
        mkdir(TAGS_DIR);

        initHEAD();
        try {
            TO_ADD.createNewFile();
            TO_REMOVE.createNewFile();
            INDEX.createNewFile();
            CONFIG.createNewFile();
        } catch (IOException e) {
            System.out.println("Could not create necessary gitfly files.");
            e.printStackTrace();
        }
        Config.initConfig();
        // Create empty snapshot of the working directory
        String emptyTreeID = getSHA1("");
        addObjectToObjectDirectory(emptyTreeID, "");
        String initialCommitID = initCommit();
        initBranch(initialCommitID);

        exit("Initialized empty gitfly repository in %s", CWD.getPath());
    }

    /**
     * Initializes the master branch with the initial commit.
     */
    private static void initBranch(String commitID) {
        File branch_file = join(HEADS_DIR, "master");
        try {
            if (branch_file.createNewFile()) {
                System.out.println("Created file: " + branch_file.getName() + " " + branch_file.getPath());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        writeContents(branch_file, commitID);
    }

    /**
     * Creates a new empty branch, which initially points at the current commit node (found in HEAD).
     * @param branch_name the name of the new branch
     */
    static void branch(String branch_name) {
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

    /**
     * Deletes the branch with the given name.
     * @param branch_name the name of the branch to be deleted
     */
    static void rm_branch(String branch_name) {
        File branch_file = join(HEADS_DIR, branch_name);
        if (!branch_file.exists()) {
            exit("There is no branch called %s", branch_name);
        } else {
            if (branch_file.delete()) {
                exit("Successfully deleted branch %s", branch_name);
            }
        }
    }

    /**
     * Prints the current branch name, all branches, untracked files, changes to be committed, changes not staged for commit and, if there any, the files that are in conflict.
     */
    static void status() {
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

    /**
     * Modifies the HEAD file to point to the given argument.
     * @param argument the argument to which HEAD should point, either a commit ID or a branch ref
     */
    private static void modifyHEAD(String argument) {
        writeContents(HEAD, argument);
    }

    /**
     * Returns the current branch name by reading the HEAD file.
     * @return the current branch name
     */
    static String getCurrentBranchName() {
        String headContents = fileContentsToString(HEAD);
        if (headContents.startsWith("ref: refs/heads/")){
            return headContents.split(" ")[1].split("/")[2];
        } else {
            return null;
        }
    }

    /**
     * Returns untracked files by comparing the working directory contents to the index contents.
     * @return a HashSet of untracked files
     */
    private static HashSet<String> getUntrackedFiles() {
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


    /**
     * Returns the files that have been modified in index since the last commit by performing a diff between the current commit and the index.
     * @return a HashMap of files with changes to be committed and their status codes (whether they have been added, modified or removed)
     */
    private static HashMap<String, Character> getChangesToBeCommitted() {
        HashMap<String, FileStatus> changesFromHeadToIndex = diff("INDEX", getCurrentCommitID(), null);
        return getStringCharacterHashMap(changesFromHeadToIndex);
    }

    /**
     * Returns the files that have been modified in the working directory since the last commit by performing a diff between the current commit and the working directory.
     * @return a HashMap of files with changes not staged for commit and their status codes (whether they have been added, modified or removed)
     */
    private static HashMap<String, Character> getChangesNotStagedForCommit() {
        // difference between working directory and index
        HashMap<String, FileStatus> changesFromIndexToWorkingCopy = diff(null, null, null);
        return getStringCharacterHashMap(changesFromIndexToWorkingCopy);
    }

    /**
     * Transforms a HashMap of files with changes from the index to the working copy and their File Status objects (status, giver, receiver, base contents)
     * into a HashMap which maps filenames to status codes (only if they have been added, modified or removed).
     * @param changesFromIndexToWorkingCopy a HashMap of files with changes from the index to the working copy {@link #diff(String, String, String)}
     * @return a HashMap of files with changes not staged for commit and their status codes (whether they have been added, modified or removed)
     */
    private static HashMap<String, Character> getStringCharacterHashMap(HashMap<String, FileStatus> changesFromIndexToWorkingCopy) {
        HashMap<String, Character> changesNotStagedForCommit = new HashMap<>();
        for (String filename : changesFromIndexToWorkingCopy.keySet()) {
            FileStatus fileStatus = changesFromIndexToWorkingCopy.get(filename);
            if (fileStatus.getStatus() == ADD || fileStatus.getStatus() == MODIFY || fileStatus.getStatus() == REMOVE) {
                changesNotStagedForCommit.put(filename, fileStatus.getStatus());
            }
        }
        return changesNotStagedForCommit;
    }

    /**
     * Returns whether a given hash represents a commit ID by analyzing the contents of the file in the objects directory.
     * @param commitID the hash to be checked
     * @return true if the hash represents a commit ID, false otherwise
     */
    private static boolean isCommitID(String commitID) {
        File commitFile = join(OBJECTS_DIR, commitID);
        if (!commitFile.exists()) {
            return false;
        }
        return fileContentsToString(commitFile).split("\n")[1].split(" ")[0].equals("parent");
    }

    /*
     * Returns the contents of the commit with the given commit ID.
     * Used in checkoutToCommit function.
     * The contents are returned as a HashMap mapping filenames to their contents.
     * Every file is given by default the status NOT_CONFLICT, since this function is only used when checking out to a commit, which can be done only if there are no merge conflicts.
     * @param commitID the commit ID of the commit whose contents are to be returned
     */
    private static HashMap<Stage.NameAndStatus, String> getIndexContent(String commitID) {
        HashMap<Stage.NameAndStatus, String> indexContent = new HashMap<>();
        // status is only used when dealing with merge conflicts, so it is set to NOT_CONFLICT at the moment.
        HashMap<String, String> commitContent = getCommitContents(commitID);
        for (String key : commitContent.keySet()) {
            indexContent.put(new Stage.NameAndStatus(key, NOT_CONFLICT), commitContent.get(key));
        }
        return indexContent;
    }

    /**
     * Return a HashSet of files that would be overwritten by checking out to the given commit ID.
     * This is done by performing two diffs:
     * 1. diff between the current commit and the working directory
     * 2. diff between the current commit and the given commit ID
     * and then finding the intersection of files that are different in both diffs.
     */
    private static HashSet<String> getFilesCommitWouldOverwrite(String commitID) {
        String currentCommitID = getCurrentCommitID();
        HashMap<String, FileStatus> diff1 = diff(null, getCurrentCommitID(), null);
        HashMap<String, FileStatus> diff2 = diff(commitID, getCurrentCommitID(), null);
        HashSet<String> filesToOverwrite = new HashSet<>();
        // intersection of files that are different in both diffs
        for (String key : diff1.keySet()) {
            if (diff1.get(key).getStatus() != SAME && diff2.containsKey(key) && diff2.get(key).getStatus() != SAME) {
                filesToOverwrite.add(key);
            }
        }
        return filesToOverwrite;
    }

    /**
     * Returns the contents of the commit with the given commit ID.
     * The contents are returned as a HashMap mapping filenames to their contents (SHA1s).
     * @param commitID the commit ID of the commit whose contents are to be returned
     */
    private static HashMap<String, String> getCommitContents(String commitID) {
        String treeID = Commit.getSnapshotID(commitID);
        return getTreeContents("", treeID);
    }

    /**
     * Returns the contents of the tree with the given tree ID as a HashMap mapping filenames to their contents (SHA1s).
     * The filenames are given relative to the current directory.
     * The function is called recursively on each "tree" entry in the tree.
     * @param currentDirectoryPrefix the prefix of the current directory, used to construct the full path of the files
     * @param treeID the tree ID of the tree whose contents are to be returned, object whose contents can be found in OBJECTS_DIR
     * @return a HashMap mapping filenames to their contents (SHA1s)
     */
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

    /**
     * Returns the contents of the working directory (the root in which .gitfly is found) as a HashMap mapping filenames to their contents (SHA1s).
     */
    private static HashMap<String, String> getWorkingDirectoryContents() {
        return getWorkingDirectoryContentsHelper(CWD, "");
    }

    /**
     * Helper function for getWorkingDirectoryContents.
     * Returns the contents of the working directory (the root in which .gitfly is found) as a HashMap mapping filenames to their contents (SHA1s).
     * Gets called recursively on each subdirectory.
     * Ignores hidden files and directories, such as .gitfly.
     */
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

    /**
     * Gets most recent commit on a given branch.
     * @param branchName the name of the branch
     * @return SHA1 of the most recent commit on the respective branch
     */
    private static String getCommitOfBranch(String branchName) {
        File branch_file = join(HEADS_DIR, branchName);
        return fileContentsToString(branch_file);
    }

    /**
     * Returns whether a given branch name is valid by checking the existence of the respective branch file in .gitfly/refs/heads.
     * @param branchName the name of the branch
     * @return true if the branch name is valid, false otherwise
     */
    private static boolean isBranchName(String branchName) {
        File branch_file = join(HEADS_DIR, branchName);
        return branch_file.exists();
    }

    /**
     * Returns the commit ID of the current commit by checking the contents of HEAD.
     * If HEAD is a branch, then the commit ID is the contents of the respective branch file.
     */
    private static String getCurrentCommitID() {
        String head = fileContentsToString(HEAD);
        if (head.startsWith("ref")) {
            String[] head_split = head.split(" ");
            File branch_file = join(GITFLY_DIR, head_split[1]);
            return fileContentsToString(branch_file);
        } else {
            return head;
        }
    }

    /**
     * Creates the initial commit, whose parent is null and whose snapshot is an empty tree.
     * The commit ID is stored in INITIAL_COMMIT_ID.
     * Parent commit for all the future commits, irrespective of branch.
     * @return the commit ID of the initial commit
     */
    private static String initCommit() {
        Commit initialCommit = new Commit("Initial commit", EMPTY_FILE_ID, null);
        Repository.INITIAL_COMMIT_ID = initialCommit.getCommitID();
        return initialCommit.getCommitID();
    }

    /**
     * Analyzes SHA1s of a file in receiver, giver and base commits and returns the status of the file.
     * @param receiver SHA1 of the file in the receiver commit
     * @param giver SHA1 of the file in the giver commit
     * @param base SHA1 of the file in the base commit
     * @return the status of the file (ADD, REMOVE, MODIFY, CONFLICT, SAME)
     */
    private static Character fileStatus(String receiver, String giver, String base) {
        if (receiver != null && giver != null && !receiver.equals(giver)) {
            if (!receiver.equals(base) && !giver.equals(base)) {
                return CONFLICT;
            } else {
                return MODIFY;
            }
        } else if ((giver != null && base == null && receiver == null) ||
                (receiver != null && base == null && giver == null)) {
            return ADD;
        } else if ((receiver != null && base != null && giver == null) ||
                (giver != null && base != null && receiver == null)) {
            return REMOVE;
        } else {
            return SAME;
        }
    }

    /**
     * Computes the diff between the repository in two different states.
     * @param giver the state of the repository before the change; if null, then the state represents the index, else a commit id
     * @param receiver the state of the repository after the change; if null, then the state represents the working directory, else a commit id
     * @param base the base state of the repository; if null, then the base state is the same as the receiver state; used in merging
     * @return
     */
    private static HashMap<String, FileStatus> diff(String giver, String receiver, String base) {
        HashMap<String, String> receiverContents = receiver == null ? Stage.getIndexContents() : getCommitContents(receiver);
        HashMap<String, String> giverContents;
        if (giver == null) {
            giverContents = getWorkingDirectoryContents();
        } else {
            giverContents = giver.equals("INDEX") ? Stage.getIndexContents() : getCommitContents(giver);
        }
        // HashMap<String, String> giverContents = giver == null ? getWorkingDirectoryContents() : getCommitContents(giver);
//        HashMap<String, String> receiverContents;
//        if (receiver == null) {
//            receiverContents = getWorkingDirectoryContents();
//        } else {
//            receiverContents = receiver.equals("INDEX") ? Stage.getIndexContents() : getCommitContents(receiver);
//        }
        HashMap<String, String> baseContents = base == null ? receiverContents : getCommitContents(base);
        HashMap<String, FileStatus> diffResult = new HashMap<>();
        Set<String> allKeys = new HashSet<>(receiverContents.keySet());
        allKeys.addAll(giverContents.keySet());
        allKeys.addAll(baseContents.keySet());
        String defaultValue = null;
        for (String key : allKeys) {
            diffResult.put(key, new FileStatus(receiverContents.getOrDefault(key, null), giverContents.getOrDefault(key, null), baseContents.getOrDefault(key, defaultValue)));
        }
        return diffResult;
    }

    /**
     * Returns the contents of a file found in conflict by concatenating the contents of the giver and receiver files.
     * The contents of the two file's versions are separated by the conflict markers.
     * Will be used to write the contents of the conflicted file to the working directory at the 5th step of merging.
     * @param giverID SHA1 of the giver file
     * @param receiverID SHA1 of the receiver file
     * @return the contents of the file found in conflict
     */
    private static String getContentOfConflictedFile(String giverID, String receiverID) {
        return "<<<<<<< HEAD\n" + fileContentsToString(join(OBJECTS_DIR, receiverID)) +
                "=======\n" +
                fileContentsToString(join(OBJECTS_DIR, giverID)) +
                ">>>>>>> branch\n";
    }

    /**
     * Writes the contents of the files in the working directory based on the diff between the giver, receiver and base commits.
     * @param giver commit ID of the giver commit
     * @param receiver commit ID of the receiver commit
     * @param base commit ID of the base commit
     */
    private static void writeToWorkingCopyForMerge(String giver, String receiver, String base) {
        HashMap<String, FileStatus> writeToWorkingCopy = diff(giver, receiver, base);
        for (String key : writeToWorkingCopy.keySet()) {
            if (writeToWorkingCopy.get(key).getStatus() == MODIFY) {
                writeContents(join(CWD, key), fileContentsToString(join(OBJECTS_DIR, writeToWorkingCopy.get(key).getGiver())));
            } else if (writeToWorkingCopy.get(key).getStatus() == REMOVE) {
                join(CWD, key).delete();
            } else if (writeToWorkingCopy.get(key).getStatus() == ADD) {
                if (writeToWorkingCopy.get(key).getGiver() != null) {
                    writeContents(join(CWD, key), fileContentsToString(join(OBJECTS_DIR, writeToWorkingCopy.get(key).getGiver())));
                } else {
                    writeContents(join(CWD, key), fileContentsToString(join(OBJECTS_DIR, writeToWorkingCopy.get(key).getReceiver())));
                }
            } else if (writeToWorkingCopy.get(key).getStatus() == CONFLICT) {
                writeContents(join(CWD, key), getContentOfConflictedFile(writeToWorkingCopy.get(key).getGiver(), writeToWorkingCopy.get(key).getReceiver()));
            }
        }
    }

    /**
     * Modifies the working directory and the index to match the files in the given commit ID.
     * Aborts if there are files that would be overwritten by checkout.
     * @param commitID commit ID of the commit to check out to
     */
    private static void checkoutToCommit(String commitID) {
//        HashSet<String> filesThatWouldBeOverwritten = getFilesCommitWouldOverwrite(commitID);
//        if (!filesThatWouldBeOverwritten.isEmpty()) {
//            exit("There are files that would be overwritten by checkout.");
//        }
        // Modify the working directory to match the files in the given commit ID
        // writeToWorkingCopyForMerge(getCurrentCommitID(), commitID, null);
        HashMap<String, FileStatus> writeToWorkingCopy = diff(commitID, getCurrentCommitID(), null);
        for (String key : writeToWorkingCopy.keySet()) {
            if (writeToWorkingCopy.get(key).getStatus() == MODIFY) {
                writeContents(join(CWD, key), fileContentsToString(join(OBJECTS_DIR, writeToWorkingCopy.get(key).getGiver())));
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
    static void checkout(String argument) {
        if (MERGE_HEAD.exists()) {
            exit("Cannot perform this command until the merge conflict has been resolved.");
        }
        if (isBranchName(argument)) {
            if (getCurrentBranchName() != null && argument.equals(getCurrentBranchName())) {
                exit("No need to checkout the current branch.");
            }
            checkoutToCommit(getCommitOfBranch(argument));
            modifyHEAD("ref: refs/heads/" + argument);
            exit("Switched out to %s", argument);
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

    /**
     * Merges the giver branch into the current branch.
     * Aborts if the giver branch is the current branch/doesn't exist.
     * There are three main cases:
     * 1. The giver branch is an ancestor of the current branch => Already up-to-date, no merge is needed.
     * 2. The receiver branch is an ancestor of the giver branch => Fast-forwarded, the commit history isn't changed, only the current branch is moved to the giver branch.
     * 3. The receiver branch and the giver branch are not related. Merge conflicts can be encountered. Perform eight steps:
     *      3.1. Write hash of the giver's branch latest commit to MERGE_HEAD to indicate that a merge is in progress.
     *      3.2. Find the LCA of the giver and the receiver branches. This will be the base commit.
     *      3.3. Get contents of the receiver, giver and base.
     *      3.4. Generate a diff that specifies the status of each file analyzing the contents of the receiver, giver and base.
     *          3.4.1. If the file is different in all three commits, then it is a merge conflict.
     *      3.5. Apply changes to the working directory.
     *          3.5.1. If there are any files found in conflict, write both versions (receiver and giver) to the working directory (invoke getContentOfConflictedFile function).
     *      3.6. Write the contents of the working directory to the index.
     *          3.6.1. If there are any files found in conflict, write three versions in the index: 1 - SHA1 of base contents, 2 - SHA1 of receiver contents, 3 - SHA1 of giver contents
     *
     *      The following step differs fundamentally on whether merge conflicts were found.
     *
     *      No conflict: 3.7. Create a commit with the given index.
     *
     *      Conflict: When a user adds a conflicted file, the other index entries for the respective filename (which indicate conflict) get removed. Wait until all merge conflicts are resolved.
     *                   3.7. User makes a new commit. Gitfly sees that a merge is ongoing (MERGE_HEAD exists) and checks that there are no more conflicted files. A new simple commit is created.
     *
     *          3.8. Delete MERGE_HEAD and update current branch.
     *
     * @param giver branch name of the giver branch
     */
    static void merge(String giver) {
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
            exit("Merged %s into %s.", giver, getCurrentBranchName());
        } else {
            exit("Encountered a merge conflict.\nThe following files are in conflict:\n%s", filesInConflict.toString().replace("[", "").replace("]", ""));
        }
    }

    /**
     * Returns a HashSet of the files found in conflict by extracting entries with status different to 0 in index.
     */
    private static HashSet<String> getFilesInConflict() {
        HashMap<Stage.NameAndStatus, String> indexFiles = Stage.getIndexFiles();
        HashSet<String> filesInConflict = new HashSet<>();
        for (Stage.NameAndStatus nameAndStatus : indexFiles.keySet()) {
            if (nameAndStatus.getStatus() != 0) {
                filesInConflict.add(nameAndStatus.getName());
            }
        }
        return filesInConflict;
    }

    /**
     * Returns a HashSet of files found in conflict by analyzing the contents of a diff object.
     * @param files diff object
     * @return  file entries with status CONFLICT in the files HashMap
     */
    private static HashSet<String> getFilesInConflict(HashMap<String, FileStatus> files) {
        HashSet<String> filesInConflict = new HashSet<>();
        for (String filename : files.keySet()) {
            if (files.get(filename).getStatus() == CONFLICT) {
                filesInConflict.add(filename);
            }
        }
        return filesInConflict;
    }

    /**
     * Identify the higher commit in the commit tree.
     * Traverse the two arrays simultaneously until a common commit is found.
     * Complexity of reading operation: O(commit tree height)
     * Complexity of LCA identification: O(commit tree height)
     * Thus, the complexity of the query is linear in terms of the commit tree height.
     * @param commit1 SHA1 of the first commit object
     * @param commit2 SHA1 of the second commit object
     * @return SHA1 of the lowest common ancestor of the two commits
     */
    private static String getLCA(String commit1, String commit2) {
        ArrayList<String> ancestors1 = getAncestorsOfCommit(commit1);
        ArrayList<String> ancestors2 = getAncestorsOfCommit(commit2);
        int minNodeIndex = Math.min(ancestors1.size(), ancestors2.size());
        for (int i = minNodeIndex - 1; i >= 0; --i) {
            if (ancestors1.get(i).equals(ancestors2.get(i))) {
                return ancestors1.get(i);
            }
        }
        return null;
    }

    /**
     * Returns whether the commit object identified by childID is a direct descendant of the commit object identified by parentID.
     * @param childID SHA1 of first commit object
     * @param parentID SHA1 of second commit object
     * @return true if childID is a descendant of parentID
     */
    private static boolean isAncestor(String childID, String parentID) {
        return getAncestorsOfCommit(parentID).contains(childID);
    }

    /**
     * Returns an ArrayList of the SHA1 of the commit objects in the commit tree directly above the commit object identified by commitID.
     * @param commitID SHA1 of the commit object
     * @return ArrayList of SHA1 of the commit object ancestors of commitID in the commit tree
     */
    private static ArrayList<String> getAncestorsOfCommit(String commitID) {
        ArrayList<String> ancestors = new ArrayList<>();
        String currentCommitID = commitID;
        while (!currentCommitID.equals("null")) {
            ancestors.add(currentCommitID);
            currentCommitID = Commit.getParentID(currentCommitID);
        }
        return ancestors;
    }

    /**
     * Returns the SHA1 of the commit object that is the head of the branch identified by branchName.
     * @param branch of the branch
     * @return SHA1 of branch's latest commit
     */
    private static String getCommitIDOfBranch(String branch) {
        File branch_file = join(HEADS_DIR, branch);
        return fileContentsToString(branch_file);
    }

    /**
     * Returns whether we are in a detached head state by checking if HEAD contains a branch ref or a commit SHA1.
     * @return true if HEAD contains a commit SHA1, false otherwise
     */
    private static boolean detachedHeadState() {
        return !fileContentsToString(HEAD).startsWith("ref");
    }

    /**
     * Accepts a variable number of paths.
     * Computes an ArrayList of all the files which satisfy the paths (the files that exist in the working directory).
     * For each such file, builds a Blob object.
     * Adds to OBJ_DIR a Blob object (name: SHA1 of content, content: binary of original content)
     * Adds {status} {SHA1} {filename} to index and replaces old SHA1 with new SHA1 if necessary.
     * If the index contains conflicting entries, the conflicting entries are removed.
     * @param paths variable number of paths to be added to the index and to the toAdd list
     */
    static void add(String... paths) {
        // create array lists with paths that exist in CWD
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
        for (File filename : files) {
            Blob blob = new Blob(filename);
            addObjectToObjectDirectory(blob.getID(), blob.getStringData());

            String filepath = blob.getRelativePath();
            String newBlobHash = blob.getID();
            ArrayList<String> oldBlobHashes = new ArrayList<>(Arrays.asList(Stage.getFromIndex(NOT_CONFLICT, filepath), Stage.getFromIndex(CONFLICT_BASE, filepath), Stage.getFromIndex(CONFLICT_GIVER, filepath), Stage.getFromIndex(CONFLICT_RECEIVER, filepath)));
            // OLD :String oldBlobHash = Stage.getFromIndex(NOT_CONFLICT, filepath);

            // Check if merge conflicts exist
            // If they do, remove them from the index
            // Add the file to the index with the NOT_CONFLICT status
            if (oldBlobHashes.get(1) != null || oldBlobHashes.get(2) != null || oldBlobHashes.get(3) != null) {
                Stage.readIndex();
                Stage.removeFromIndex(filepath, CONFLICT_BASE);
                Stage.removeFromIndex(filepath, CONFLICT_GIVER);
                Stage.removeFromIndex(filepath, CONFLICT_RECEIVER);
                Stage.addToIndex(filepath, NOT_CONFLICT, newBlobHash);
                Stage.writeIndex();
            }
            // Check if the file is already in the index
            // If it is, check if the hash is the same
            // If it is not, replace the hash in the index with the new hash
            else if (oldBlobHashes.get(0) == null || !oldBlobHashes.get(0).equals(newBlobHash)) {
                if (oldBlobHashes.get(0) != null) {
                    Stage.readIndex();
                    Stage.removeFromIndex(filepath, NOT_CONFLICT);
                    Stage.writeIndex();
                }
                Stage.addToIndex(filepath, NOT_CONFLICT, newBlobHash);
                Stage.writeAll();
            }
            // Now the file is in the index with the correct value
            // If it is in TO_ADD with another value, replace it with the correct value
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
            // Now the file is in TO_ADD with the correct value
            // If the file is found in TO_REMOVE, remove it from TO_REMOVE
            if (Stage.isInToRemove(filepath)) {
                Stage.removeFromToRemove(filepath);
            }
        }
        Stage.writeAll();
    }

    /**
     * Accepts a variable number of filepaths.
     * If path in index:
     *     Delete path from index.
     *     If path in TO_ADD, remove it from TO_ADD.
     *     Add path to TO_REMOVE.
     *     Delete path from disk if it exists.
     * @param paths paths to be removed from index and working directory.
     */
    static void rm(String ...paths) {
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
             else {
                System.out.println("No reason to remove the file.");
            }
        }
        Stage.writeAll();
    }

    /**
     * Add a file to the object directory OBJECTS_DIR.
     * @param name name of file
     * @param content content of file
     */
    static void addObjectToObjectDirectory(String name, String content) {
        File object = join(OBJECTS_DIR, name);
        writeContents(object, content);
    }

    /**
     * Creates a new commit with the given message.
     * If there still exists a merge conflict, abort.
     * Otherwise, call helper function createCommit.
     * Parent of new commit is the current commit.
     * @param message message of the commit
     */
    static void commit(String message) {
        HashSet<String> filesInConflict = getFilesInConflict();
        if (filesInConflict.size() > 0) {
            exit("Cannot perform this command until the merge conflict has been resolved.");
        }
        createCommit(message, getCurrentCommitID());
    }

    /**
     * Creates a new commit with the given message and parent.
     * If there was an ongoing merge, resolve the merge conflict and delete the MERGE_HEAD file.
     * Build the tree of the new commit using the current commit as base.
     * @param message message of the commit
     * @param commitID parent of the new commit
     */
    private static void createCommit(String message, String commitID) {
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

    /**
     * Updates the current branch to point to the given commit ID.
     * @param newCommitID commit ID to be pointed to
     */
    private static void updateCurrentBranch(String newCommitID) {
        String name = getCurrentBranchName();
        if (name == null) {
            writeContents(HEAD, newCommitID);
        } else {
            File branch_file = join(GITFLY_DIR, "refs/heads/" + name);
            writeContents(branch_file, newCommitID);
        }
    }

    /**
     * Builds a new tree based on the old tree and the files in TO_ADD and TO_REMOVE.
     * Used for creating snapshot files of new commits.
     * Recursive helper function for buildUpdatedTree.
     */
    private static String buildUpdatedTree(File currentDirectory, String oldTreeHash, HashMap<String, String> toAdd, HashSet <String> toRemove) {
        File oldTree = join(OBJECTS_DIR, oldTreeHash);
        String oldTreeContent = fileContentsToString(oldTree);
        StringBuilder newTreeContent = new StringBuilder();
        if (!oldTreeContent.equals("")) {
            for (String line : oldTreeContent.split("\n")) {
                String type, hash, filename;
                type = line.split(" ")[0];
                hash = line.split(" ")[1];
                filename = line.split(" ")[2];
                // System.out.println("type: " + type + " hash: " + hash + " filename: " + filename);
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
            // System.out.println("filename: " + filename + " hash: " + hash);
            if (isNormalFile(filename)) {
                newTreeContent.append("blob").append(" ").append(hash).append(" ").append(filename).append("\n");
            } else {
                String firstChildDirectory = getFirstDirectory(filename);
                if (visitedChildDirectories.contains(firstChildDirectory)) {
                    continue;
                }
                visitedChildDirectories.add(firstChildDirectory);
                File goDown = join(currentDirectory, firstChildDirectory);
//                if (goDown.exists()) {
//                    System.out.println("goDown: " + goDown);
//                }
                String newHash = buildUpdatedTree(goDown, getSHA1(""), fromDirectory(toAdd, firstChildDirectory), fromDirectory(toRemove, firstChildDirectory));
                newTreeContent.append("tree").append(" ").append(newHash).append(" ").append(firstChildDirectory).append("\n");
            }
        }
        String newTreeHash = getSHA1(newTreeContent.toString());
        String newTreeContentString = newTreeContent.toString();
        addObjectToObjectDirectory(newTreeHash, newTreeContentString);
        return newTreeHash;
    }

    /**
     * Returns a new HashMap with all the keys in toAdd that start with "${filename}/".
     */
    private static HashMap<String, String> fromDirectory(HashMap<String, String> toAdd, String filename) {
        HashMap<String, String> newToAdd = new HashMap<>();
        for (String key : toAdd.keySet()) {
            if (key.startsWith(filename + "/")) {
                newToAdd.put(key.substring(filename.length() + 1), toAdd.get(key));
            }
        }
        return newToAdd;
    }

    /**
     * Returns a new HashMap with all the keys in toRemove that start with "${filename}/".
     */
    private static HashSet<String> fromDirectory(HashSet<String> toRemove, String filename) {
        HashSet<String> newToRemove = new HashSet<>();
        for (String element : toRemove) {
            if (element.startsWith(filename + "/")) {
                newToRemove.add(element.substring(filename.length() + 1));
            }
        }
        return newToRemove;
    }

    /**
     * Removes all the keys in mp that start with "${filename}/".
     */
    private static void removeFilesFromDirectory(HashMap<String, String> mp, String filename) {
        for (String key : mp.keySet()) {
            if (key.startsWith(filename + "/")) {
                mp.remove(key);
            }
        }
    }

    /**
     * Removes all the elements in st that start with "${filename}/".
     */
    private static void removeFilesFromDirectory(HashSet<String> st, String filename) {
        st.removeIf(element -> element.startsWith(filename + "/"));
    }

    /**
     * Prints the commit log.
     * Traverses the commit tree from the current commit to the initial commit using the parent pointers.
     */
    static void log() {
        String currentCommitID = getCurrentCommitID();
        do {
            System.out.println("COMMIT: " + currentCommitID);
            System.out.println(Commit.getCommitText(currentCommitID));
            currentCommitID = Commit.getParentID(currentCommitID);
            System.out.println("==================================");
        } while (!currentCommitID.equals("null"));
    }

    /**
     * Initializes the HEAD file to point at the master branch.
     */
    private static void initHEAD() {
        String head = "ref: refs/heads/master";
        writeContents(HEAD, head);
    }

    /**
     * Checks if the current directory is a gitfly repository.
     */
    static void checkIfGitflyInitialized() {
        if (!GITFLY_DIR.exists()) {
            exit("Not in an initialized gitfly repository.");
        }
    }
}
