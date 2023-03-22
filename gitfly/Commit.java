package gitfly;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static gitfly.Utils.*;

public class Commit {
    private String message;
    private String treeID;
    private String parentID;
    private String datetime;
    private String author;
    private String email;
    private String commitID;
    Commit(String message, String id, String parentID) {
        this.message = message;
        this.treeID = id;
        this.parentID = parentID;
        LocalDateTime current = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        this.datetime = current.format(formatter);
        this.author = Config.getAuthor();
        this.email = Config.getEmail();

        StringBuilder sb = new StringBuilder();
        sb.append("tree ").append(treeID).append("\n").append("parent ").append(parentID).append("\n").append("author ").append(author).append(" ").append(email).append(" ").append(datetime).append("\n").append("committer ").append(author).append(" ").append(email).append(" ").append(datetime).append("\n").append("\n").append(message);
        String commit = sb.toString();
        this.commitID = getSHA1(commit.getBytes());
        System.out.println("COMMIT: " + this.commitID);
        System.out.println(commit);
        System.out.println("==================================================");
        Repository.addObjectToObjectDirectory(commitID, commit);
    }

    static String getSnapshotID (String id) {
        return getCommitText(id).split(" ")[1].split("\n")[0];
    }

    static String getCommitText(String id) {
        File currentCommit = join(Repository.OBJECTS_DIR, id);
        return fileContentsToString(currentCommit);
    }

    static String getParentID(String id) {
        return getCommitText(id).split("\n")[1].split(" ")[1];
    }

    String getCommitID() {
        return this.commitID;
    }

    String getSnapshotID() {
        return this.treeID;
    }
}
