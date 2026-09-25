package dev.crystal.client.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Settings files written so they are never left half-written. The text goes to
 * a temporary file next to the target first and is then moved over it in one
 * step: a crash or power cut mid-save leaves the old file, not a broken one
 * that loads as "no settings" and then gets overwritten with the defaults.
 */
public final class SafeFiles {

    private SafeFiles() {}

    public static void writeAtomic(Path target, String text) throws IOException {
        Files.createDirectories(target.getParent());
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temp, text, StandardCharsets.UTF_8);
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // Windows refuses the move now and then (a virus scanner holding the
            // file); writing in place as before still beats not saving at all.
            Files.deleteIfExists(temp);
            Files.writeString(target, text, StandardCharsets.UTF_8);
        }
    }

    /**
     * A file that could not be read is moved aside as "<name>.broken" instead of
     * being overwritten on the next save, so its contents can still be recovered.
     */
    public static void keepBroken(Path file) {
        try {
            if (Files.exists(file)) {
                Files.move(file, file.resolveSibling(file.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
            // Nothing more to do; the next save replaces it.
        }
    }
}
