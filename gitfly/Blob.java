package gitfly;

import java.io.Serializable;
import java.io.File;

import static gitfly.Utils.*;

public class Blob implements Serializable {
    private final byte[] dataBytes;
    private final String id;
    private final String stringData;
    private final String relativePath;

    public Blob(File file) {
        this.relativePath = file.getAbsolutePath().substring(Repository.CWD.getAbsolutePath().length() + 1);
        this.dataBytes = readContents(file);
        this.stringData = fileContentsToString(file);
        this.id = getSHA1(dataBytes);
    }

    public String getRelativePath() {
        return this.relativePath;
    }

    public String getID() {
        return id;
    }
    public String getStringData() {
        return stringData;
    }
}
