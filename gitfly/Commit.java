package gitfly;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static gitfly.Utils.getSHA1;

public class Commit {
    private String message;
    private String treeID;
    private String parentID;
    private String datetime;
    private String author;
    private String email;
    private String commitID;
    public Commit(String message, String id, String parentID) {
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
        System.out.println(commit);
        this.commitID = getSHA1(commit.getBytes());
        Repository.addObjectToObjectDirectory(commitID, commit);
    }

    public String getCommitID() {
        return this.commitID;
    }

    public String getSnapshotID() {
        return this.treeID;
    }
}
