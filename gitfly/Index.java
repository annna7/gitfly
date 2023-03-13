package gitfly;

import java.io.File;
import java.util.HashMap;
import gitfly.Utils.*;

import static gitfly.Utils.writeContents;

public class Index {
    /**
     * Index maps files to hashes of their content.
     */
    private static class FileStagePair {
        public String filename;
        public Integer stage;
        public FileStagePair (String filename, Integer stage) {
            this.filename = filename;
            this.stage = stage;
        }
        @Override
        public String toString() {
            return stage + filename;
        }
    }
    private File file;
    private HashMap <FileStagePair, String> fileToContent;
    public Index (File indexFile) {
        this.file = indexFile;
        this.fileToContent = new HashMap<>();
    }
    public boolean hasFileAtStage (String filename, Integer status) {
        return this.fileToContent.containsKey(new FileStagePair(filename, status));
    }

    public void addFileToIndex(String filename, Integer status, String contents) {
        this.fileToContent.put(new FileStagePair(filename, status), contents);
        writeContents(this.file, this.fileToContent.toString());
    }
}
