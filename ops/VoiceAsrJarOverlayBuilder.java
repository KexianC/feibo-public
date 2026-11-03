import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Builds a production-baseline overlay containing only the ASR WebSocket handler classes. */
public final class VoiceAsrJarOverlayBuilder {
    private static final String INFRASTRUCTURE_OUTER =
            "BOOT-INF/lib/digital-doctor-infrastructure-1.0.jar";
    private static final String HANDLER_INNER =
            "com/hzmwmt/infrastructure/gateway/netty/server/NettyServerHandler.class";
    private static final String CALLBACK_INNER =
            "com/hzmwmt/infrastructure/gateway/netty/server/NettyServerHandler$1.class";

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            throw new IllegalArgumentException(
                    "usage: VoiceAsrJarOverlayBuilder <baseline.jar> <handler.class> <callback.class> <candidate.jar>");
        }
        Path baseline = Path.of(args[0]).toAbsolutePath().normalize();
        Path handlerClass = Path.of(args[1]).toAbsolutePath().normalize();
        Path callbackClass = Path.of(args[2]).toAbsolutePath().normalize();
        Path candidate = Path.of(args[3]).toAbsolutePath().normalize();
        requireFile(baseline, "baseline");
        requireFile(handlerClass, "handler class");
        requireFile(callbackClass, "callback class");
        if (Files.exists(candidate)) {
            throw new IllegalStateException("candidate already exists: " + candidate);
        }

        Map<String, byte[]> replacements = new LinkedHashMap<>();
        replacements.put(HANDLER_INNER, Files.readAllBytes(handlerClass));
        replacements.put(CALLBACK_INNER, Files.readAllBytes(callbackClass));

        Path temporary = Files.createTempFile(candidate.getParent(), ".voice-asr-candidate-", ".tmp");
        try {
            rewriteOuter(baseline, temporary, replacements);
            verifyBoundary(baseline, temporary, replacements);
            Files.move(temporary, candidate, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporary);
        }

        System.out.println("VOICE_ASR_CANDIDATE_OK");
        System.out.println("BASELINE_SHA256=" + sha256(baseline));
        System.out.println("CANDIDATE_SHA256=" + sha256(candidate));
        System.out.println("CANDIDATE_SIZE=" + Files.size(candidate));
        System.out.println("OUTER_CONTENT_REPLACEMENTS=1");
        System.out.println("CLASS_CONTENT_REPLACEMENTS=2");
    }

    private static void rewriteOuter(
            Path baseline,
            Path output,
            Map<String, byte[]> replacements) throws IOException {
        Set<String> names = new HashSet<>();
        boolean replaced = false;
        try (ZipInputStream input = new ZipInputStream(Files.newInputStream(baseline));
             ZipOutputStream target = new ZipOutputStream(Files.newOutputStream(output))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (!names.add(entry.getName())) {
                    throw new IOException("duplicate outer entry: " + entry.getName());
                }
                byte[] payload = input.readAllBytes();
                if (INFRASTRUCTURE_OUTER.equals(entry.getName())) {
                    payload = rewriteNested(payload, replacements);
                    replaced = true;
                }
                writeEntry(target, entry, payload);
                input.closeEntry();
            }
        }
        if (!replaced) {
            throw new IOException("missing infrastructure module: " + INFRASTRUCTURE_OUTER);
        }
    }

    private static byte[] rewriteNested(
            byte[] archive,
            Map<String, byte[]> replacements) throws IOException {
        Set<String> names = new HashSet<>();
        Set<String> replaced = new HashSet<>();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(archive.length + 4096);
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(archive));
             ZipOutputStream output = new ZipOutputStream(buffer)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (!names.add(entry.getName())) {
                    throw new IOException("duplicate infrastructure entry: " + entry.getName());
                }
                byte[] payload = input.readAllBytes();
                byte[] replacement = replacements.get(entry.getName());
                if (replacement != null) {
                    payload = replacement;
                    replaced.add(entry.getName());
                }
                writeEntry(output, entry, payload);
                input.closeEntry();
            }
        }
        if (!replaced.equals(replacements.keySet())) {
            throw new IOException("baseline is missing one or more handler classes");
        }
        return buffer.toByteArray();
    }

    private static void verifyBoundary(
            Path baseline,
            Path candidate,
            Map<String, byte[]> replacements) throws Exception {
        try (ZipFile before = new ZipFile(baseline.toFile());
             ZipFile after = new ZipFile(candidate.toFile())) {
            Map<String, ZipEntry> beforeEntries = uniqueEntries(before, "baseline");
            Map<String, ZipEntry> afterEntries = uniqueEntries(after, "candidate");
            if (!beforeEntries.keySet().equals(afterEntries.keySet())) {
                throw new IOException("outer entry set changed");
            }
            for (String name : beforeEntries.keySet()) {
                ZipEntry beforeEntry = beforeEntries.get(name);
                ZipEntry afterEntry = afterEntries.get(name);
                if (INFRASTRUCTURE_OUTER.equals(name)) {
                    if (afterEntry.getMethod() != ZipEntry.STORED) {
                        throw new IOException("infrastructure module must remain STORED");
                    }
                    byte[] beforeNested = readEntry(before, beforeEntry);
                    byte[] afterNested = readEntry(after, afterEntry);
                    verifyNestedBoundary(beforeNested, afterNested, replacements);
                } else if (!entrySha256(before, beforeEntry).equals(entrySha256(after, afterEntry))) {
                    throw new IOException("unexpected outer content change: " + name);
                }
            }
        }
    }

    private static void verifyNestedBoundary(
            byte[] beforeArchive,
            byte[] afterArchive,
            Map<String, byte[]> replacements) throws IOException {
        Map<String, byte[]> before = nestedEntries(beforeArchive, "baseline infrastructure");
        Map<String, byte[]> after = nestedEntries(afterArchive, "candidate infrastructure");
        if (!before.keySet().equals(after.keySet())) {
            throw new IOException("infrastructure entry set changed");
        }
        for (String name : before.keySet()) {
            byte[] expected = replacements.get(name);
            byte[] actual = after.get(name);
            if (expected != null) {
                if (!Arrays.equals(expected, actual)) {
                    throw new IOException("replacement payload mismatch: " + name);
                }
            } else if (!Arrays.equals(before.get(name), actual)) {
                throw new IOException("unexpected infrastructure content change: " + name);
            }
        }
    }

    private static Map<String, ZipEntry> uniqueEntries(ZipFile zip, String label) throws IOException {
        Map<String, ZipEntry> entries = new LinkedHashMap<>();
        var enumeration = zip.entries();
        while (enumeration.hasMoreElements()) {
            ZipEntry entry = enumeration.nextElement();
            if (entries.put(entry.getName(), entry) != null) {
                throw new IOException("duplicate " + label + " entry: " + entry.getName());
            }
        }
        return entries;
    }

    private static Map<String, byte[]> nestedEntries(byte[] archive, String label) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (entries.put(entry.getName(), input.readAllBytes()) != null) {
                    throw new IOException("duplicate " + label + " entry: " + entry.getName());
                }
                input.closeEntry();
            }
        }
        return entries;
    }

    private static byte[] readEntry(ZipFile zip, ZipEntry entry) throws IOException {
        try (InputStream input = zip.getInputStream(entry)) {
            return input.readAllBytes();
        }
    }

    private static String entrySha256(ZipFile zip, ZipEntry entry) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = zip.getInputStream(entry)) {
            byte[] block = new byte[1024 * 1024];
            int count;
            while ((count = input.read(block)) >= 0) {
                digest.update(block, 0, count);
            }
        }
        return hex(digest.digest());
    }

    private static void writeEntry(ZipOutputStream output, ZipEntry source, byte[] payload)
            throws IOException {
        ZipEntry target = new ZipEntry(source.getName());
        target.setMethod(source.getMethod() == -1 ? ZipEntry.DEFLATED : source.getMethod());
        if (source.getComment() != null) {
            target.setComment(source.getComment());
        }
        if (source.getTime() >= 0) {
            target.setTime(source.getTime());
        }
        if (target.getMethod() == ZipEntry.STORED) {
            CRC32 crc = new CRC32();
            crc.update(payload);
            target.setSize(payload.length);
            target.setCompressedSize(payload.length);
            target.setCrc(crc.getValue());
        }
        output.putNextEntry(target);
        output.write(payload);
        output.closeEntry();
    }

    private static void requireFile(Path path, String label) {
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("missing " + label + ": " + path);
        }
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] block = new byte[1024 * 1024];
            int count;
            while ((count = input.read(block)) >= 0) {
                digest.update(block, 0, count);
            }
        }
        return hex(digest.digest());
    }

    private static String hex(byte[] values) {
        StringBuilder result = new StringBuilder();
        for (byte value : values) {
            result.append(String.format("%02x", value));
        }
        return result.toString();
    }
}
