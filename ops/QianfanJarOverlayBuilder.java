import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

/** Builds a production-baseline overlay containing only the Qianfan runtime classes. */
public final class QianfanJarOverlayBuilder {
    private static final String DOMAIN_OUTER =
            "BOOT-INF/lib/digital-doctor-domain-1.0.jar";
    private static final String INFRASTRUCTURE_OUTER =
            "BOOT-INF/lib/digital-doctor-infrastructure-1.0.jar";
    private static final String DOMAIN_INNER =
            "com/hzmwmt/domain/ai/service/chat/model/strategy/QianfanModelStrategyService.class";
    private static final String REGISTRY_INNER =
            "com/hzmwmt/infrastructure/gateway/ai/DatabaseBackedChatModelRegistry.class";

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            throw new IllegalArgumentException(
                    "usage: QianfanJarOverlayBuilder <baseline.jar> <domain.class> <registry.class> <candidate.jar>");
        }
        Path baseline = Path.of(args[0]).toAbsolutePath().normalize();
        Path domainClass = Path.of(args[1]).toAbsolutePath().normalize();
        Path registryClass = Path.of(args[2]).toAbsolutePath().normalize();
        Path candidate = Path.of(args[3]).toAbsolutePath().normalize();
        requireFile(baseline, "baseline");
        requireFile(domainClass, "domain class");
        requireFile(registryClass, "registry class");
        if (Files.exists(candidate)) {
            throw new IllegalStateException("candidate already exists: " + candidate);
        }

        Map<String, Map<String, byte[]>> replacements = new LinkedHashMap<>();
        replacements.put(DOMAIN_OUTER, Map.of(DOMAIN_INNER, Files.readAllBytes(domainClass)));
        replacements.put(INFRASTRUCTURE_OUTER, Map.of(REGISTRY_INNER, Files.readAllBytes(registryClass)));

        Path temporary = Files.createTempFile(candidate.getParent(), ".qianfan-candidate-", ".tmp");
        try {
            rewriteOuter(baseline, temporary, replacements);
            verifyCandidate(temporary, replacements);
            Files.move(temporary, candidate, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporary);
        }

        System.out.println("QIANFAN_CANDIDATE_OK");
        System.out.println("BASELINE_SHA256=" + sha256(baseline));
        System.out.println("CANDIDATE_SHA256=" + sha256(candidate));
        System.out.println("CANDIDATE_SIZE=" + Files.size(candidate));
        System.out.println("OUTER_REPLACEMENTS=2");
        System.out.println("CLASS_REPLACEMENTS=2");
    }

    private static void rewriteOuter(
            Path baseline,
            Path output,
            Map<String, Map<String, byte[]>> replacements) throws IOException {
        Set<String> seen = new HashSet<>();
        Set<String> replaced = new HashSet<>();
        try (ZipInputStream input = new ZipInputStream(Files.newInputStream(baseline));
             ZipOutputStream target = new ZipOutputStream(Files.newOutputStream(output))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (!seen.add(entry.getName())) {
                    throw new IOException("duplicate outer entry: " + entry.getName());
                }
                byte[] payload = input.readAllBytes();
                Map<String, byte[]> nested = replacements.get(entry.getName());
                if (nested != null) {
                    payload = rewriteNested(payload, nested, entry.getName());
                    replaced.add(entry.getName());
                }
                writeEntry(target, entry, payload);
                input.closeEntry();
            }
        }
        if (!replaced.equals(replacements.keySet())) {
            throw new IOException("missing outer replacement entries");
        }
    }

    private static byte[] rewriteNested(
            byte[] archive,
            Map<String, byte[]> replacements,
            String label) throws IOException {
        Set<String> seen = new HashSet<>();
        Set<String> replaced = new HashSet<>();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(archive.length + 4096);
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(archive));
             ZipOutputStream output = new ZipOutputStream(buffer)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (!seen.add(entry.getName())) {
                    throw new IOException("duplicate nested entry in " + label + ": " + entry.getName());
                }
                byte[] payload = input.readAllBytes();
                if (replacements.containsKey(entry.getName())) {
                    payload = replacements.get(entry.getName());
                    replaced.add(entry.getName());
                }
                writeEntry(output, entry, payload);
                input.closeEntry();
            }
            for (Map.Entry<String, byte[]> replacement : replacements.entrySet()) {
                if (replaced.contains(replacement.getKey())) {
                    continue;
                }
                ZipEntry added = new ZipEntry(replacement.getKey());
                added.setMethod(ZipEntry.DEFLATED);
                writeEntry(output, added, replacement.getValue());
                replaced.add(replacement.getKey());
            }
        }
        if (!replaced.equals(replacements.keySet())) {
            throw new IOException("incomplete nested replacement in " + label);
        }
        return buffer.toByteArray();
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

    private static void verifyCandidate(
            Path candidate,
            Map<String, Map<String, byte[]>> replacements) throws IOException {
        try (ZipFile outer = new ZipFile(candidate.toFile())) {
            Set<String> outerNames = new HashSet<>();
            outer.stream().forEach(entry -> {
                if (!outerNames.add(entry.getName())) {
                    throw new IllegalStateException("duplicate candidate entry: " + entry.getName());
                }
            });
            for (Map.Entry<String, Map<String, byte[]>> module : replacements.entrySet()) {
                ZipEntry outerEntry = outer.getEntry(module.getKey());
                if (outerEntry == null || outerEntry.getMethod() != ZipEntry.STORED) {
                    throw new IOException("nested module is missing or compressed: " + module.getKey());
                }
                byte[] nestedBytes;
                try (InputStream input = outer.getInputStream(outerEntry)) {
                    nestedBytes = input.readAllBytes();
                }
                verifyNested(nestedBytes, module.getValue(), module.getKey());
            }
        }
    }

    private static void verifyNested(
            byte[] archive,
            Map<String, byte[]> replacements,
            String label) throws IOException {
        Set<String> names = new HashSet<>();
        Set<String> verified = new HashSet<>();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (!names.add(entry.getName())) {
                    throw new IOException("duplicate candidate nested entry in " + label);
                }
                byte[] expected = replacements.get(entry.getName());
                byte[] actual = input.readAllBytes();
                if (expected != null) {
                    if (!Arrays.equals(expected, actual)) {
                        throw new IOException("candidate class payload mismatch: " + entry.getName());
                    }
                    verified.add(entry.getName());
                }
            }
        }
        if (!verified.equals(replacements.keySet())) {
            throw new IOException("candidate class missing from " + label);
        }
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
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) {
            result.append(String.format("%02x", value));
        }
        return result.toString();
    }
}
