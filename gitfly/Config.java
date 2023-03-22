package gitfly;

import static gitfly.Utils.fileContentsToString;
import static gitfly.Utils.writeContents;

class Config {
    private static String author;
    private static String email;

    static void initConfig() {
        String sb = """
                author: John Doe
                email: john_doe@outlook.com
                """;
        writeContents(Repository.CONFIG, sb);
    }
    static String getAuthor() {
        String str = fileContentsToString(Repository.CONFIG);
        return str.split("\n")[0].split(": ")[1];
    }

    static String getEmail() {
        String str = fileContentsToString(Repository.CONFIG);
        return str.split("\n")[1].split(": ")[1];
    }
}
