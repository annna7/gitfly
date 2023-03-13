package gitfly;

import static gitfly.Utils.fileContentsToString;
import static gitfly.Utils.writeContents;

public class Config {
    private static String author;
    private static String email;

    public static void initConfig() {
        String sb = """
                author: ANONYMOUS_AUTHOR
                email = ANONYMOUS_EMAIL
                """;
        writeContents(Repository.CONFIG, sb);
    }
    public static String getAuthor() {
        String str = fileContentsToString(Repository.CONFIG);
        return str.split("\n")[0].split(": ")[1];
    }

    public static String getEmail() {
        String str = fileContentsToString(Repository.CONFIG);
        return str.split("\n")[1].split(" = ")[1];
    }
}
