package gitfly;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class Utils {
    /**
     * Outputs a relevant message to the user, composed of a format string and
     * relevant command parameters.
     * @param message format of message to output
     * @param args relevant command parameters
     */
    static void outputMessage(String message, Object... args) {
        System.out.printf(message, args);
        System.out.println();
    }

    /**
     * Exits the program with a relevant message to the user
     * @param message format of message to output
     * @param args relevant command parameters
     */
    public static void exit(String message, Object... args) {
        outputMessage(message, args);
        System.exit(0);
    }

    /**
     * Checks if the number of arguments is correct
     * @param args arguments to check
     * @param expected number of arguments expected
     */
    public static void checkNumberOfArguments(String[] args, int expected) {
        if (args.length != expected) {
            exit("Expected %d arguments, but got %d.", expected, args.length);
        }
    }

    /**
     * Creates a directory if it does not already exist
     * Throws an error if the directory could not be created
     * @param dir directory to create
     */
    public static void mkdir(File dir) {
        if (!dir.mkdir()) {
            exit("Could not create directory %s.", dir.getPath());
        }
    }

    /**
     * Joins a parent path with a list of children paths
     * @param parent parent path
     * @param children children paths
     * @return joined path
     */
    public static File join(String parent, String... children) {
        return Paths.get(parent, children).toFile();
    }

    /**
     * Joins a parent file with a list of children paths
     * @param parent parent file
     * @param children children paths
     * @return joined path
     */
    public static File join(File parent, String... children) {
        return join(parent.getPath(), children);
    }

    public static byte[] readContents(File file) {
        if (!file.isFile()) {
            System.out.println("NOT A NORMAL FILE: " + file.getPath() + "ggg");
            throw new IllegalArgumentException("Not a normal file");
        }
        try {
            return Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read contents of file");
        }
    }

    public static String fileContentsToString(File file) {
        return new String(readContents(file), StandardCharsets.UTF_8);
    }

    /**
     * Writes a variable number of objects to a file.
     * Each object can be either a String or a byte array.
     * @param file file descriptor to write to
     * @param contents objects to be written to file
     */
    public static void writeContents(File file, Object... contents) {
        try {
            BufferedOutputStream out = new BufferedOutputStream(
                    new FileOutputStream(file));
            for (Object obj : contents) {
                out.write(obj.toString().getBytes());
            }
            out.close();
        }
        catch (FileNotFoundException e) {
            exit("Not a valid file: %s", file.getPath());
        }
        catch (IOException e) {
            exit("Could not write to file: %s", file.getPath());
        }
    }

    /**
     * Checks if a certain file exists in given directory.
     * Returns File object if it does and null if it does not.
     * LIMITATION:
     * Only checks for the given expression, not for REGEX like original Git.
     * @param dirPath
     * @param filePath
     */
    public static File getFile(String dirPath, String filePath) {
        String newPath = dirPath + "/" + filePath;
        File file = new File(newPath);
        if (file.exists()) {
            return file;
        } else {
            return null;
        }
    }

    /**
     * Returns SHA1 hash of a byte array of indeterminate length.
     * Can be used to get SHA1 of gitfly files.
     * The name of each blob will be equal to the SHA1 of its contents (in raw binary form).
     * @param data contents of a file
     * @return SHA1 hash of given file contents
     */
    public static String getSHA1(byte[] data) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] hash = sha1.digest(data);

            StringBuilder stringBuilderHex = new StringBuilder();

            for (byte b : hash) {
                stringBuilderHex.append(String.format("%02x", b));
            }

            return stringBuilderHex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("SHA-1 algorithm not found. Fatal error.");
        }
    }

    public static String getSHA1(String data) {
        return getSHA1(data.getBytes());
    }

    static <T extends Serializable> T readObject(File file, Class <T> expectedClass) {
        try {
            ObjectInputStream in = new ObjectInputStream(new FileInputStream(file));
            T obj = expectedClass.cast(in.readObject());
            in.close();
            return obj;
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read object from file");
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Failed to cast object to expected class");
        }
    }

    public static boolean isNormalFile(String path) {
        return !path.contains("/");
    }

    public static String getFirstDirectory(String path) {
        return path.split("/")[0];
    }
}
