import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Builds a narrowly-scoped Spring Boot candidate JAR from the pinned production
 * JAR and the isolated Maven target/classes tree. This is deliberately a
 * standalone Java 17 source file so the PowerShell wrapper also works under
 * Windows ConstrainedLanguage mode.
 */
public final class CopywritingWorkflowJarBuilder {
    private static final String EXPECTED_BASELINE_SHA =
            "9f98d5bb0dbbf5061a1f731bdee5eaacb135ec2081a90816c7e993b9dbb13503";
    private static final String EXPECTED_ALLOWLIST_SHA =
            "710dd6e578e0593b6f6977ca8fb3556bbd0176c1f7706cf86ec97e08155a8f79";
    private static final String CANDIDATE_NAME =
            "digital-doctor-app.copywriting-workflow.candidate.jar";

    private static final Map<String, String> NESTED = Map.of(
            "digital-doctor-api", "BOOT-INF/lib/digital-doctor-api-1.0.jar",
            "digital-doctor-domain", "BOOT-INF/lib/digital-doctor-domain-1.0.jar",
            "digital-doctor-infrastructure", "BOOT-INF/lib/digital-doctor-infrastructure-1.0.jar",
            "digital-doctor-trigger", "BOOT-INF/lib/digital-doctor-trigger-1.0.jar"
    );

    private static final Map<String, Integer> EXPECTED_CLASSES = Map.of(
            "digital-doctor-api", 9,
            "digital-doctor-domain", 45,
            "digital-doctor-infrastructure", 11,
            "digital-doctor-trigger", 2,
            "digital-doctor-app", 1
    );
    private static final Map<String, Integer> EXPECTED_RESOURCES = Map.of(
            "digital-doctor-domain", 2,
            "digital-doctor-app", 7
    );

    private static final List<ProtectedClass> PROTECTED = List.of(
            pc("digital-doctor-domain", "com/hzmwmt/domain/ai/service/chat/model/strategy/SzDoctorModelStrategyService.class"),
            pc("digital-doctor-domain", "com/hzmwmt/domain/controller/user/AdminDigitalWorkController.class"),
            pc("digital-doctor-domain", "com/hzmwmt/domain/digital/adapter/port/IDigitalPort.class"),
            pc("digital-doctor-domain", "com/hzmwmt/domain/digital/model/entity/DigitalWorkEntity.class"),
            pc("digital-doctor-domain", "com/hzmwmt/domain/digital/model/entity/DigitalWorkEntity$DigitalWorkEntityBuilder.class"),
            pc("digital-doctor-domain", "com/hzmwmt/domain/digital/model/entity/VideoCompositionSubmissionEntity.class"),
            pc("digital-doctor-domain", "com/hzmwmt/domain/digital/model/entity/VideoCompositionSubmissionEntity$VideoCompositionSubmissionEntityBuilder.class"),
            pc("digital-doctor-domain", "com/hzmwmt/domain/digital/service/work/DigitalWorkService.class"),
            pc("digital-doctor-domain", "com/hzmwmt/domain/entity/user/TDigitalWorkComposeAudit.class"),
            pc("digital-doctor-domain", "com/hzmwmt/domain/entity/user/TDigitalWorkComposeAudit$TDigitalWorkComposeAuditBuilder.class"),
            pc("digital-doctor-domain", "com/hzmwmt/domain/mapper/user/DigitalWorkComposeAuditMapper.class"),
            pc("digital-doctor-domain", "com/hzmwmt/domain/service/user/AdminDigitalWorkService.class"),
            pc("digital-doctor-domain", "com/hzmwmt/domain/service/user/impl/AdminDigitalWorkServiceImpl.class"),
            pc("digital-doctor-api", "com/hzmwmt/api/dto/DigitalHumanVoiceAuditionRequestDTO.class"),
            pc("digital-doctor-trigger", "com/hzmwmt/trigger/http/client/DigitalHumanOpenController.class"),
            pc("digital-doctor-infrastructure", "com/hzmwmt/infrastructure/adapter/port/DigitalPort.class"),
            pc("digital-doctor-infrastructure", "com/hzmwmt/infrastructure/adapter/repository/DigitalRepository.class"),
            pc("digital-doctor-infrastructure", "com/hzmwmt/infrastructure/gateway/digital/DigitalApiService.class"),
            pc("digital-doctor-infrastructure", "com/hzmwmt/infrastructure/gateway/digital/DigitalApiService$OpenPlatformAuth.class"),
            pc("digital-doctor-infrastructure", "com/hzmwmt/infrastructure/gateway/digital/IDigitalApiService.class"),
            pc("digital-doctor-infrastructure", "com/hzmwmt/infrastructure/gateway/digital/VideoCompositionRequestPreparer.class")
    );

    private static final Pattern SIGNATURE_ARTIFACT = Pattern.compile(
            "(?i)^META-INF/(?:[^/]+\\.(SF|RSA|DSA|EC)|SIG-[^/]+)$");

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            throw new IllegalArgumentException(
                    "usage: CopywritingWorkflowJarBuilder <baseline.jar> <classes-root> <release-files.txt> <output-dir>");
        }
        Path baselinePath = Path.of(args[0]).toAbsolutePath().normalize();
        Path classesRoot = Path.of(args[1]).toAbsolutePath().normalize();
        Path allowlistPath = Path.of(args[2]).toAbsolutePath().normalize();
        Path outputDir = Path.of(args[3]).toAbsolutePath().normalize();

        requireRegularFile(baselinePath, "baseline JAR");
        requireRegularFile(allowlistPath, "release allowlist");
        requireDirectory(classesRoot, "isolated classes root");
        require(!Files.exists(outputDir, java.nio.file.LinkOption.NOFOLLOW_LINKS),
                "output directory must be a new, nonexistent path: " + outputDir);
        Path outputParent = outputDir.getParent();
        require(outputParent != null && Files.isDirectory(outputParent, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                        && !Files.isSymbolicLink(outputParent),
                "output parent must be an existing non-symlink directory: " + outputParent);
        require(EXPECTED_BASELINE_SHA.equals(sha256(baselinePath)),
                "baseline JAR SHA-256 is not the pinned 64-character production digest");
        require(EXPECTED_ALLOWLIST_SHA.equals(sha256(allowlistPath)),
                "release allowlist SHA-256 is not the pinned reviewed digest");

        ReleaseInputs release = collectReleaseInputs(classesRoot, allowlistPath);
        require(release.sourceCount == 53, "runtime Java source allowlist must contain exactly 53 entries");
        require(release.classPayload.size() == 68, "compiled class closure must contain exactly 68 unique entries");
        require(release.resourcePayload.size() == 9, "runtime XML allowlist must contain exactly 9 entries");
        require(release.missingTopLevels.isEmpty(), "missing top-level compiled classes: " + release.missingTopLevels);
        require(release.totalPayloadCount() == 77, "runtime payload must contain exactly 77 entries");
        verifyExpectedCounts(release);

        Archive outerBaseline = readZip(baselinePath);
        verifyAllNestedLibrariesStored(baselinePath, outerBaseline);
        for (String nestedPath : NESTED.values()) {
            require(outerBaseline.entries.containsKey(nestedPath), "missing production nested JAR: " + nestedPath);
        }

        Map<String, Archive> nestedBaseline = new LinkedHashMap<>();
        Map<String, Archive> nestedCandidate = new LinkedHashMap<>();
        Map<String, byte[]> nestedCandidateBytes = new LinkedHashMap<>();
        for (Map.Entry<String, String> moduleEntry : NESTED.entrySet()) {
            String module = moduleEntry.getKey();
            String outerPath = moduleEntry.getValue();
            Archive before = readZipBytes(outerBaseline.entries.get(outerPath).bytes, outerPath);
            rejectSignedNestedJar(before, outerPath);
            nestedBaseline.put(module, before);

            ModulePayload payload = release.forModule(module);
            rejectVersionedClassOverrides(before, payload, "", outerPath);
            byte[] afterBytes = rewriteArchive(before, payload.entries, payload::isAllowed, false);
            Archive after = readZipBytes(afterBytes, outerPath + " (candidate)");
            verifyLogicalDiff(before, after, payload::isAllowed, "nested " + module);
            nestedCandidate.put(module, after);
            nestedCandidateBytes.put(outerPath, afterBytes);
        }

        TreeMap<String, String> protectedHashes = new TreeMap<>();
        require(PROTECTED.size() == 21, "protected production hotpatch list must contain exactly 21 entries");
        for (ProtectedClass protectedClass : PROTECTED) {
            Archive before = nestedBaseline.get(protectedClass.module);
            Archive after = nestedCandidate.get(protectedClass.module);
            require(before != null && after != null, "unknown protected module: " + protectedClass.module);
            EntryData oldEntry = before.entries.get(protectedClass.path);
            EntryData newEntry = after.entries.get(protectedClass.path);
            require(oldEntry != null, "protected production class missing from baseline: " + protectedClass.label());
            require(newEntry != null, "protected production class missing from candidate: " + protectedClass.label());
            String oldSha = sha256(oldEntry.bytes);
            require(oldSha.equals(sha256(newEntry.bytes)),
                    "protected production class changed: " + protectedClass.label());
            protectedHashes.put(protectedClass.label(), oldSha);
        }

        ModulePayload appPayload = release.forModule("digital-doctor-app");
        rejectVersionedClassOverrides(outerBaseline, appPayload, "BOOT-INF/classes/", "outer application");
        Map<String, byte[]> outerReplacements = new LinkedHashMap<>();
        outerReplacements.putAll(nestedCandidateBytes);
        for (Map.Entry<String, byte[]> payloadEntry : appPayload.entries.entrySet()) {
            outerReplacements.put("BOOT-INF/classes/" + payloadEntry.getKey(), payloadEntry.getValue());
        }
        Predicate<String> outerAllowed = name -> nestedCandidateBytes.containsKey(name)
                || (name.startsWith("BOOT-INF/classes/")
                    && appPayload.isAllowed(name.substring("BOOT-INF/classes/".length())));

        byte[] candidateBytes = rewriteArchive(outerBaseline, outerReplacements, outerAllowed, true);
        Archive outerCandidate = readZipBytes(candidateBytes, CANDIDATE_NAME);
        verifyLogicalDiff(outerBaseline, outerCandidate, outerAllowed, "outer application");

        String devPath = "BOOT-INF/classes/application-dev.yml";
        String prodPath = "BOOT-INF/classes/application-prod.yml";
        requireSameEntry(outerBaseline, outerCandidate, devPath, "application-dev.yml");
        requireSameEntry(outerBaseline, outerCandidate, prodPath, "application-prod.yml");

        Files.createDirectory(outputDir);
        Path candidatePath = outputDir.resolve(CANDIDATE_NAME);
        require(!Files.exists(candidatePath), "refusing to overwrite existing candidate: " + candidatePath);
        Path tempCandidate = Files.createTempFile(outputDir, "." + CANDIDATE_NAME + ".", ".tmp");
        Files.write(tempCandidate, candidateBytes, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
        verifyAllNestedLibrariesStored(tempCandidate, outerCandidate);
        // Re-open and fully read every entry so CRC/truncation errors are fatal.
        Archive diskCandidate = readZip(tempCandidate);
        require(diskCandidate.entries.size() == outerCandidate.entries.size(), "candidate ZIP re-open entry count mismatch");
        atomicMove(tempCandidate, candidatePath);

        String candidateSha = sha256(candidatePath);
        writeLine(outputDir.resolve("candidate.sha256"), candidateSha + "  " + CANDIDATE_NAME);
        writeProtectedManifest(outputDir.resolve("protected-hotpatch.sha256"), protectedHashes);
        writeInjectedManifest(outputDir.resolve("injected-entries.sha256"), release);
        writeDiffManifest(outputDir.resolve("logical-diff.tsv"), outerBaseline, outerCandidate,
                nestedBaseline, nestedCandidate, outerAllowed, release);
        writeLine(outputDir.resolve("logical-diff.sha256"),
                sha256(outputDir.resolve("logical-diff.tsv")) + "  logical-diff.tsv");
        writeAuditManifest(outputDir.resolve("backend-candidate-audit.txt"), baselinePath, candidatePath,
                candidateSha, outerBaseline, outerCandidate, release, protectedHashes);
        writeLine(outputDir.resolve("backend-candidate-audit.sha256"),
                sha256(outputDir.resolve("backend-candidate-audit.txt")) + "  backend-candidate-audit.txt");

        System.out.println("BACKEND_CANDIDATE_OK");
        System.out.println("BASELINE_SHA256=" + EXPECTED_BASELINE_SHA);
        System.out.println("CANDIDATE_SHA256=" + candidateSha);
        System.out.println("SOURCES=53");
        System.out.println("CLASSES=68");
        System.out.println("UNIQUE_CLASSES=68");
        System.out.println("MISSING_TOP_LEVELS=0");
        System.out.println("RESOURCES=9");
        System.out.println("RUNTIME_ENTRIES=77");
        System.out.println("PROTECTED_HOTPATCH_CLASSES=21");
        System.out.println("NON_ALLOWLIST_CONTENT_SHA_UNCHANGED=yes");
        System.out.println("ALL_BOOT_INF_LIB_JARS_STORED=yes");
        System.out.println("ZIP_INTEGRITY=ok");
        System.out.println("ALLOWLIST_SHA256=" + EXPECTED_ALLOWLIST_SHA);
        System.out.println("CANDIDATE=" + candidatePath);
    }

    private static ReleaseInputs collectReleaseInputs(Path classesRoot, Path allowlistPath) throws IOException {
        List<String> lines = Files.readAllLines(allowlistPath, StandardCharsets.UTF_8);
        ReleaseInputs result = new ReleaseInputs();
        Set<String> seenSources = new HashSet<>();
        Set<String> seenResources = new HashSet<>();
        for (String raw : lines) {
            String path = raw.trim().replace('\\', '/');
            if (path.isEmpty() || path.startsWith("#")) {
                continue;
            }
            if (path.matches("^feibo_backend_master/[^/]+/src/main/java/.+\\.java$")) {
                String module = moduleFromReleasePath(path);
                require(EXPECTED_CLASSES.containsKey(module), "unexpected runtime Java module in allowlist: " + module);
                require(seenSources.add(path), "duplicate Java source allowlist entry: " + path);
                result.sourceCount++;
                String relativeJava = path.substring(
                        ("feibo_backend_master/" + module + "/src/main/java/").length());
                String topClass = relativeJava.substring(0, relativeJava.length() - ".java".length()) + ".class";
                result.topLevelClosures.computeIfAbsent(module, ignored -> new LinkedHashSet<>()).add(topClass);

                int slash = topClass.lastIndexOf('/');
                String directory = slash < 0 ? "" : topClass.substring(0, slash + 1);
                String topFile = slash < 0 ? topClass : topClass.substring(slash + 1);
                String base = topFile.substring(0, topFile.length() - ".class".length());
                Path compiledDirectory = classesRoot.resolve(module).resolve("target/classes").resolve(directory);
                if (!Files.isDirectory(compiledDirectory)) {
                    result.missingTopLevels.add(module + ":" + topClass);
                    continue;
                }
                List<Path> closure = new ArrayList<>();
                try (var stream = Files.list(compiledDirectory)) {
                    stream.filter(Files::isRegularFile)
                            .filter(file -> {
                                String name = file.getFileName().toString();
                                return name.equals(base + ".class")
                                        || (name.startsWith(base + "$") && name.endsWith(".class"));
                            })
                            .sorted(Comparator.comparing(file -> file.getFileName().toString()))
                            .forEach(closure::add);
                }
                require(closure.stream().anyMatch(file -> file.getFileName().toString().equals(base + ".class")),
                        "missing top-level compiled class: " + module + ":" + topClass);
                for (Path compiled : closure) {
                    String entry = directory + compiled.getFileName();
                    String key = module + ":" + entry;
                    require(!result.classPayload.containsKey(key), "duplicate compiled class payload: " + key);
                    result.classPayload.put(key, Files.readAllBytes(compiled));
                }
            } else if (path.matches("^feibo_backend_master/[^/]+/src/main/resources/.+\\.xml$")) {
                String module = moduleFromReleasePath(path);
                require(EXPECTED_RESOURCES.containsKey(module), "unexpected runtime XML module in allowlist: " + module);
                require(seenResources.add(path), "duplicate XML resource allowlist entry: " + path);
                String relativeResource = path.substring(
                        ("feibo_backend_master/" + module + "/src/main/resources/").length());
                Path compiled = classesRoot.resolve(module).resolve("target/classes").resolve(relativeResource);
                requireRegularFile(compiled, "compiled filtered XML resource " + module + ":" + relativeResource);
                String key = module + ":" + relativeResource;
                require(!result.resourcePayload.containsKey(key), "duplicate XML resource payload: " + key);
                result.resourcePayload.put(key, Files.readAllBytes(compiled));
            }
        }
        return result;
    }

    private static String moduleFromReleasePath(String path) {
        String prefix = "feibo_backend_master/";
        require(path.startsWith(prefix), "invalid backend release path: " + path);
        int slash = path.indexOf('/', prefix.length());
        require(slash > prefix.length(), "backend module is missing from release path: " + path);
        return path.substring(prefix.length(), slash);
    }

    private static void verifyExpectedCounts(ReleaseInputs release) {
        for (Map.Entry<String, Integer> expected : EXPECTED_CLASSES.entrySet()) {
            int actual = release.classCount(expected.getKey());
            require(actual == expected.getValue(), expected.getKey() + " class count expected "
                    + expected.getValue() + " but was " + actual);
        }
        for (Map.Entry<String, Integer> expected : EXPECTED_RESOURCES.entrySet()) {
            int actual = release.resourceCount(expected.getKey());
            require(actual == expected.getValue(), expected.getKey() + " XML count expected "
                    + expected.getValue() + " but was " + actual);
        }
    }

    private static Archive readZip(Path path) throws IOException {
        Map<String, EntryData> entries = new LinkedHashMap<>();
        Set<String> folded = new HashSet<>();
        try (ZipFile zip = new ZipFile(path.toFile(), StandardCharsets.UTF_8)) {
            var enumeration = zip.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                validateName(entry.getName());
                require(!entries.containsKey(entry.getName()), "duplicate ZIP entry: " + entry.getName() + " in " + path);
                require(folded.add(entry.getName().toLowerCase(Locale.ROOT)),
                        "case-fold ZIP entry collision: " + entry.getName() + " in " + path);
                byte[] bytes;
                try (InputStream input = zip.getInputStream(entry)) {
                    bytes = input.readAllBytes();
                }
                entries.put(entry.getName(), EntryData.from(entry, bytes));
            }
        }
        return new Archive(path.toString(), entries);
    }

    private static Archive readZipBytes(byte[] bytes, String label) throws IOException {
        Map<String, EntryData> entries = new LinkedHashMap<>();
        Set<String> folded = new HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                validateName(entry.getName());
                require(!entries.containsKey(entry.getName()), "duplicate ZIP entry: " + entry.getName() + " in " + label);
                require(folded.add(entry.getName().toLowerCase(Locale.ROOT)),
                        "case-fold ZIP entry collision: " + entry.getName() + " in " + label);
                byte[] content = zip.readAllBytes();
                entries.put(entry.getName(), EntryData.from(entry, content));
                zip.closeEntry();
            }
        }
        RawZipInfo raw = RawZipInfo.readBytes(bytes, label);
        require(raw.methods.keySet().equals(entries.keySet()),
                "local/central ZIP logical entry set or order mismatch in " + label);
        for (EntryData entry : entries.values()) {
            RawMethod method = raw.methods.get(entry.name);
            require(method != null && method.centralMethod == entry.method && method.localMethod == entry.method,
                    "local/central ZIP compression method mismatch in " + label + ": " + entry.name);
        }
        return new Archive(label, entries);
    }

    private static byte[] rewriteArchive(Archive baseline, Map<String, byte[]> replacements,
                                         Predicate<String> allowed, boolean storeAllNestedLibraries)
            throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Set<String> written = new HashSet<>();
        try (ZipOutputStream output = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            for (EntryData old : baseline.entries.values()) {
                if (allowed.test(old.name)) {
                    continue;
                }
                writeZipEntry(output, old.name, old.bytes, old,
                        storeAllNestedLibraries && isNestedLibrary(old.name));
                written.add(old.name);
            }
            List<String> names = new ArrayList<>(replacements.keySet());
            Collections.sort(names);
            for (String name : names) {
                require(allowed.test(name), "replacement is not in logical allowlist: " + name);
                writeZipEntry(output, name, replacements.get(name), null,
                        storeAllNestedLibraries && isNestedLibrary(name));
                written.add(name);
            }
        }
        require(written.size() == new HashSet<>(written).size(), "internal duplicate output entry");
        return buffer.toByteArray();
    }

    private static void writeZipEntry(ZipOutputStream output, String name, byte[] bytes,
                                      EntryData template, boolean forceStored) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        if (template != null && template.time >= 0) {
            entry.setTime(template.time);
        } else {
            entry.setTime(0L);
        }
        int method = forceStored ? ZipEntry.STORED
                : template == null ? ZipEntry.DEFLATED : template.method;
        entry.setMethod(method);
        if (method == ZipEntry.STORED) {
            CRC32 crc = new CRC32();
            crc.update(bytes);
            entry.setSize(bytes.length);
            entry.setCompressedSize(bytes.length);
            entry.setCrc(crc.getValue());
        }
        output.putNextEntry(entry);
        output.write(bytes);
        output.closeEntry();
    }

    private static void verifyLogicalDiff(Archive before, Archive after,
                                          Predicate<String> allowed, String label) {
        Set<String> names = new LinkedHashSet<>();
        names.addAll(before.entries.keySet());
        names.addAll(after.entries.keySet());
        for (String name : names) {
            EntryData oldEntry = before.entries.get(name);
            EntryData newEntry = after.entries.get(name);
            boolean equal = oldEntry != null && newEntry != null
                    && Arrays.equals(oldEntry.bytes, newEntry.bytes);
            if (!equal) {
                require(allowed.test(name), label + " non-allowlist logical entry changed/added/deleted: " + name);
            }
        }
        for (String name : before.entries.keySet()) {
            if (!allowed.test(name)) {
                require(after.entries.containsKey(name), label + " non-allowlist entry deleted: " + name);
                require(sha256(before.entries.get(name).bytes).equals(sha256(after.entries.get(name).bytes)),
                        label + " non-allowlist SHA changed: " + name);
            }
        }
        for (String name : after.entries.keySet()) {
            if (!allowed.test(name)) {
                require(before.entries.containsKey(name), label + " non-allowlist entry added: " + name);
            }
        }
    }

    private static void verifyAllNestedLibrariesStored(Path zipPath, Archive archive) throws IOException {
        RawZipInfo raw = RawZipInfo.read(zipPath);
        int libraryCount = 0;
        for (EntryData entry : archive.entries.values()) {
            if (!isNestedLibrary(entry.name)) {
                continue;
            }
            libraryCount++;
            require(entry.method == ZipEntry.STORED, "central directory method is not Stored: " + entry.name);
            RawMethod methods = raw.methods.get(entry.name);
            require(methods != null, "raw ZIP metadata missing for nested library: " + entry.name);
            require(methods.centralMethod == 0 && methods.localMethod == 0,
                    "nested library must be Stored in local and central headers: " + entry.name);
            require(methods.compressedSize == methods.uncompressedSize
                            && methods.uncompressedSize == entry.bytes.length,
                    "nested library raw Stored sizes do not match payload length: " + entry.name);
            require((methods.centralFlags & ((1 << 0) | (1 << 3) | (1 << 6) | (1 << 13))) == 0,
                    "nested library raw flags are incompatible with executable Stored JAR: " + entry.name);
        }
        require(libraryCount > 4, "unexpectedly few BOOT-INF/lib/*.jar entries: " + libraryCount);
    }

    private static void rejectSignedNestedJar(Archive archive, String label) {
        for (String name : archive.entries.keySet()) {
            require(!SIGNATURE_ARTIFACT.matcher(name).matches(),
                    "modified nested JAR contains signature metadata that would become invalid: " + label + "!" + name);
        }
    }

    private static void rejectVersionedClassOverrides(Archive archive, ModulePayload payload,
                                                      String containerPrefix, String label) {
        for (String name : archive.entries.keySet()) {
            String marker = containerPrefix + "META-INF/versions/";
            if (!name.startsWith(marker)) continue;
            String tail = name.substring(marker.length());
            int versionSlash = tail.indexOf('/');
            if (versionSlash < 1 || versionSlash == tail.length() - 1) continue;
            String logical = tail.substring(versionSlash + 1);
            require(!payload.isAllowed(logical),
                    "multi-release override exists for an allowlisted class/resource: " + label + "!" + name);
        }
    }

    private static void requireSameEntry(Archive before, Archive after, String path, String label) {
        require(before.entries.containsKey(path), "baseline missing " + label);
        require(after.entries.containsKey(path), "candidate missing " + label);
        require(sha256(before.entries.get(path).bytes).equals(sha256(after.entries.get(path).bytes)),
                label + " SHA-256 changed");
    }

    private static void writeProtectedManifest(Path path, Map<String, String> hashes) throws IOException {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, String> entry : hashes.entrySet()) {
            text.append(entry.getValue()).append("  ").append(entry.getKey()).append('\n');
        }
        Files.writeString(path, text.toString(), StandardCharsets.UTF_8);
    }

    private static void writeInjectedManifest(Path path, ReleaseInputs release) throws IOException {
        TreeMap<String, byte[]> ordered = new TreeMap<>();
        ordered.putAll(release.classPayload);
        ordered.putAll(release.resourcePayload);
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, byte[]> entry : ordered.entrySet()) {
            text.append(sha256(entry.getValue())).append("  ").append(entry.getKey()).append('\n');
        }
        Files.writeString(path, text.toString(), StandardCharsets.UTF_8);
    }

    private static void writeDiffManifest(Path path, Archive outerBefore, Archive outerAfter,
                                          Map<String, Archive> nestedBefore,
                                          Map<String, Archive> nestedAfter,
                                          Predicate<String> outerAllowed,
                                          ReleaseInputs release) throws IOException {
        StringBuilder text = new StringBuilder();
        text.append("scope\taction\tentry\tbefore_sha256\tafter_sha256\tbefore_size\tafter_size\tbefore_method\tafter_method\tallowlisted\n");
        appendDiffRows(text, "outer", outerBefore, outerAfter, outerAllowed);
        for (String module : NESTED.keySet()) {
            ModulePayload payload = release.forModule(module);
            appendDiffRows(text, "nested:" + module, nestedBefore.get(module), nestedAfter.get(module), payload::isAllowed);
        }
        Files.writeString(path, text.toString(), StandardCharsets.UTF_8);
    }

    private static void appendDiffRows(StringBuilder text, String scope,
                                       Archive before, Archive after,
                                       Predicate<String> allowed) {
        TreeMap<String, Boolean> names = new TreeMap<>();
        before.entries.keySet().forEach(name -> names.put(name, Boolean.TRUE));
        after.entries.keySet().forEach(name -> names.put(name, Boolean.TRUE));
        for (String name : names.keySet()) {
            EntryData oldEntry = before.entries.get(name);
            EntryData newEntry = after.entries.get(name);
            boolean equal = oldEntry != null && newEntry != null && Arrays.equals(oldEntry.bytes, newEntry.bytes);
            if (equal) continue;
            String action = oldEntry == null ? "add" : newEntry == null ? "delete" : "replace";
            text.append(scope).append('\t').append(action).append('\t').append(name).append('\t')
                    .append(oldEntry == null ? "-" : sha256(oldEntry.bytes)).append('\t')
                    .append(newEntry == null ? "-" : sha256(newEntry.bytes)).append('\t')
                    .append(oldEntry == null ? "-" : oldEntry.bytes.length).append('\t')
                    .append(newEntry == null ? "-" : newEntry.bytes.length).append('\t')
                    .append(oldEntry == null ? "-" : methodName(oldEntry.method)).append('\t')
                    .append(newEntry == null ? "-" : methodName(newEntry.method)).append('\t')
                    .append(allowed.test(name) ? "yes" : "no").append('\n');
        }
    }

    private static String methodName(int method) {
        return method == ZipEntry.STORED ? "stored" : method == ZipEntry.DEFLATED ? "deflated" : Integer.toString(method);
    }

    private static void writeAuditManifest(Path path, Path baseline, Path candidate,
                                           String candidateSha, Archive outerBefore,
                                           Archive outerAfter, ReleaseInputs release,
                                           Map<String, String> protectedHashes) throws IOException {
        String devPath = "BOOT-INF/classes/application-dev.yml";
        String prodPath = "BOOT-INF/classes/application-prod.yml";
        StringBuilder text = new StringBuilder();
        text.append("format=copywriting-workflow-backend-candidate-v1\n");
        text.append("generated_at=").append(Instant.now()).append('\n');
        text.append("baseline_path=").append(baseline).append('\n');
        text.append("baseline_sha256=").append(EXPECTED_BASELINE_SHA).append('\n');
        text.append("candidate_path=").append(candidate).append('\n');
        text.append("candidate_sha256=").append(candidateSha).append('\n');
        text.append("application_dev_sha256=").append(sha256(outerBefore.entries.get(devPath).bytes)).append('\n');
        text.append("application_prod_sha256=").append(sha256(outerBefore.entries.get(prodPath).bytes)).append('\n');
        text.append("sources=53\nclasses=68\nunique_classes=68\nmissing_top_levels=0\nresources=9\nruntime_entries=77\n");
        text.append("api_classes=").append(release.classCount("digital-doctor-api")).append('\n');
        text.append("domain_classes=").append(release.classCount("digital-doctor-domain")).append('\n');
        text.append("domain_resources=").append(release.resourceCount("digital-doctor-domain")).append('\n');
        text.append("infrastructure_classes=").append(release.classCount("digital-doctor-infrastructure")).append('\n');
        text.append("trigger_classes=").append(release.classCount("digital-doctor-trigger")).append('\n');
        text.append("app_classes=").append(release.classCount("digital-doctor-app")).append('\n');
        text.append("app_resources=").append(release.resourceCount("digital-doctor-app")).append('\n');
        text.append("protected_hotpatch_classes=").append(protectedHashes.size()).append('\n');
        text.append("outer_baseline_entries=").append(outerBefore.entries.size()).append('\n');
        text.append("outer_candidate_entries=").append(outerAfter.entries.size()).append('\n');
        text.append("non_allowlist_content_sha_unchanged=yes\n");
        text.append("outer_and_nested_bidirectional_logical_diff=passed\n");
        text.append("duplicate_and_casefold_collision_check=passed\n");
        text.append("modified_nested_signature_metadata_check=passed\n");
        text.append("all_boot_inf_lib_jars_local_and_central_stored=yes\n");
        text.append("zip_integrity=passed\n");
        Files.writeString(path, text.toString(), StandardCharsets.UTF_8);
    }

    private static void atomicMove(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target);
        }
    }

    private static boolean isNestedLibrary(String name) {
        return name.startsWith("BOOT-INF/lib/") && name.endsWith(".jar") && name.indexOf('/', "BOOT-INF/lib/".length()) < 0;
    }

    private static void validateName(String name) {
        require(!name.isEmpty(), "empty ZIP entry name");
        require(!name.startsWith("/") && !name.startsWith("\\") && !name.matches("^[A-Za-z]:.*"),
                "absolute ZIP entry name: " + name);
        for (String component : name.replace('\\', '/').split("/")) {
            require(!component.equals(".."), "parent traversal ZIP entry name: " + name);
        }
    }

    private static void requireRegularFile(Path path, String label) {
        require(Files.isRegularFile(path), label + " is missing or not a regular file: " + path);
    }

    private static void requireDirectory(Path path, String label) {
        require(Files.isDirectory(path), label + " is missing or not a directory: " + path);
    }

    private static void writeLine(Path path, String line) throws IOException {
        Files.writeString(path, line + "\n", StandardCharsets.UTF_8);
    }

    private static String sha256(Path path) throws IOException {
        return sha256(Files.readAllBytes(path));
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder text = new StringBuilder(64);
            for (byte item : digest) {
                text.append(String.format("%02x", item & 0xff));
            }
            return text.toString();
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static ProtectedClass pc(String module, String path) {
        return new ProtectedClass(module, path);
    }

    private record ProtectedClass(String module, String path) {
        String label() { return module + ":" + path; }
    }

    private static final class ReleaseInputs {
        int sourceCount;
        final Map<String, byte[]> classPayload = new LinkedHashMap<>();
        final Map<String, byte[]> resourcePayload = new LinkedHashMap<>();
        final Map<String, Set<String>> topLevelClosures = new LinkedHashMap<>();
        final List<String> missingTopLevels = new ArrayList<>();

        int classCount(String module) {
            return (int) classPayload.keySet().stream().filter(key -> key.startsWith(module + ":")).count();
        }

        int resourceCount(String module) {
            return (int) resourcePayload.keySet().stream().filter(key -> key.startsWith(module + ":")).count();
        }

        int totalPayloadCount() { return classPayload.size() + resourcePayload.size(); }

        ModulePayload forModule(String module) {
            Map<String, byte[]> entries = new LinkedHashMap<>();
            String prefix = module + ":";
            classPayload.forEach((key, value) -> {
                if (key.startsWith(prefix)) entries.put(key.substring(prefix.length()), value);
            });
            resourcePayload.forEach((key, value) -> {
                if (key.startsWith(prefix)) entries.put(key.substring(prefix.length()), value);
            });
            return new ModulePayload(entries, topLevelClosures.getOrDefault(module, Set.of()));
        }
    }

    private static final class ModulePayload {
        final Map<String, byte[]> entries;
        final Set<String> topClasses;
        final Set<String> exactResources;

        ModulePayload(Map<String, byte[]> entries, Set<String> topClasses) {
            this.entries = entries;
            this.topClasses = new LinkedHashSet<>(topClasses);
            this.exactResources = new HashSet<>();
            for (String name : entries.keySet()) {
                if (!name.endsWith(".class")) exactResources.add(name);
            }
        }

        boolean isAllowed(String name) {
            if (exactResources.contains(name)) return true;
            for (String top : topClasses) {
                if (name.equals(top)) return true;
                String base = top.substring(0, top.length() - ".class".length());
                if (name.startsWith(base + "$") && name.endsWith(".class")) return true;
            }
            return false;
        }

    }

    private record Archive(String label, Map<String, EntryData> entries) { }

    private static final class EntryData {
        final String name;
        final byte[] bytes;
        final int method;
        final long time;

        EntryData(String name, byte[] bytes, int method, long time) {
            this.name = name;
            this.bytes = bytes;
            this.method = method;
            this.time = time;
        }

        static EntryData from(ZipEntry entry, byte[] bytes) {
            return new EntryData(entry.getName(), bytes, entry.getMethod(), entry.getTime());
        }
    }

    private record RawMethod(int centralMethod, int localMethod, int centralFlags,
                             int localFlags, long crc, long compressedSize,
                             long uncompressedSize, int payloadOffset) { }

    private static final class RawZipInfo {
        final Map<String, RawMethod> methods;

        RawZipInfo(Map<String, RawMethod> methods) { this.methods = methods; }

        static RawZipInfo read(Path path) throws IOException {
            return readBytes(Files.readAllBytes(path), path.toString());
        }

        static RawZipInfo readBytes(byte[] bytes, String label) {
            int eocd = findEocd(bytes);
            int diskNumber = u16(bytes, eocd + 4);
            int centralDisk = u16(bytes, eocd + 6);
            int diskEntryCount = u16(bytes, eocd + 8);
            int entryCount = u16(bytes, eocd + 10);
            long centralSizeLong = u32(bytes, eocd + 12);
            long centralOffsetLong = u32(bytes, eocd + 16);
            int commentLength = u16(bytes, eocd + 20);
            require(eocd + 22 + commentLength == bytes.length,
                    "EOCD does not close the archive exactly: " + label);
            require(diskNumber == 0 && centralDisk == 0 && diskEntryCount == entryCount,
                    "multi-disk ZIP is rejected: " + label);
            require(entryCount != 0xffff && diskEntryCount != 0xffff
                            && centralSizeLong != 0xffffffffL && centralOffsetLong != 0xffffffffL,
                    "ZIP64 is not supported by the raw Stored-method verifier: " + label);
            int offset = Math.toIntExact(centralOffsetLong);
            int centralEnd = Math.addExact(offset, Math.toIntExact(centralSizeLong));
            require(centralEnd == eocd, "central directory size/offset does not end at EOCD: " + label);
            Map<String, RawMethod> result = new LinkedHashMap<>();
            Set<String> folded = new HashSet<>();
            for (int index = 0; index < entryCount; index++) {
                require(u32(bytes, offset) == 0x02014b50L, "invalid central directory signature in " + label);
                int centralFlags = u16(bytes, offset + 8);
                int centralMethod = u16(bytes, offset + 10);
                long centralCrc = u32(bytes, offset + 16);
                long centralCompressed = u32(bytes, offset + 20);
                long centralUncompressed = u32(bytes, offset + 24);
                int nameLength = u16(bytes, offset + 28);
                int extraLength = u16(bytes, offset + 30);
                int entryCommentLength = u16(bytes, offset + 32);
                int startDisk = u16(bytes, offset + 34);
                long localOffsetLong = u32(bytes, offset + 42);
                require(centralCompressed != 0xffffffffL && centralUncompressed != 0xffffffffL
                                && localOffsetLong != 0xffffffffL && startDisk != 0xffff,
                        "ZIP64 entry fields are rejected: " + label);
                require((centralFlags & 0x800) != 0,
                        "ZIP entry name must carry the UTF-8 flag for unambiguous comparison: " + label);
                require(startDisk == 0, "multi-disk entry is rejected: " + label);
                int localOffset = Math.toIntExact(localOffsetLong);
                require(u32(bytes, localOffset) == 0x04034b50L, "invalid local header signature in " + label);
                int localFlags = u16(bytes, localOffset + 6);
                int localMethod = u16(bytes, localOffset + 8);
                long localCrc = u32(bytes, localOffset + 14);
                long localCompressed = u32(bytes, localOffset + 18);
                long localUncompressed = u32(bytes, localOffset + 22);
                int localNameLength = u16(bytes, localOffset + 26);
                int localExtraLength = u16(bytes, localOffset + 28);
                require(offset + 46L + nameLength + extraLength + entryCommentLength <= centralEnd,
                        "central entry fields exceed declared directory: " + label);
                require(localOffset + 30L + localNameLength + localExtraLength <= bytes.length,
                        "local entry fields exceed archive: " + label);
                byte[] centralNameBytes = Arrays.copyOfRange(bytes, offset + 46, offset + 46 + nameLength);
                byte[] localNameBytes = Arrays.copyOfRange(bytes, localOffset + 30, localOffset + 30 + localNameLength);
                require(Arrays.equals(centralNameBytes, localNameBytes), "local/central raw ZIP name bytes mismatch");
                String centralName = decodeUtf8Strict(centralNameBytes, label);
                require(centralFlags == localFlags, "local/central flags mismatch: " + centralName);
                require(centralMethod == localMethod, "local/central method mismatch: " + centralName);
                boolean dataDescriptor = (centralFlags & (1 << 3)) != 0;
                if (!dataDescriptor) {
                    require(centralCrc == localCrc && centralCompressed == localCompressed
                                    && centralUncompressed == localUncompressed,
                            "local/central CRC or sizes mismatch: " + centralName);
                }
                int payloadOffset = localOffset + 30 + localNameLength + localExtraLength;
                require(payloadOffset >= 0 && payloadOffset + centralCompressed <= bytes.length,
                        "entry payload exceeds archive bounds: " + centralName);
                if (centralMethod == 0) {
                    int rejectedStoredFlags = (1 << 0) | (1 << 3) | (1 << 6) | (1 << 13);
                    require((centralFlags & rejectedStoredFlags) == 0,
                            "Stored entry uses encryption/data-descriptor/masking flags: " + centralName);
                    require(centralCompressed == centralUncompressed,
                            "Stored entry compressed/uncompressed sizes differ: " + centralName);
                    CRC32 crc = new CRC32();
                    crc.update(bytes, payloadOffset, Math.toIntExact(centralCompressed));
                    require(crc.getValue() == centralCrc, "Stored entry CRC does not match raw payload: " + centralName);
                }
                require(!result.containsKey(centralName), "duplicate raw central entry: " + centralName);
                require(folded.add(centralName.toLowerCase(Locale.ROOT)), "raw case-fold collision: " + centralName);
                result.put(centralName, new RawMethod(centralMethod, localMethod, centralFlags,
                        localFlags, centralCrc, centralCompressed, centralUncompressed, payloadOffset));
                offset += 46 + nameLength + extraLength + entryCommentLength;
            }
            require(offset == centralEnd, "central directory parser did not consume its declared size: " + label);
            return new RawZipInfo(result);
        }

        private static String decodeUtf8Strict(byte[] bytes, String label) {
            try {
                return StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                        .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
            } catch (java.nio.charset.CharacterCodingException invalid) {
                throw new IllegalStateException("invalid UTF-8 ZIP entry name: " + label, invalid);
            }
        }

        private static int findEocd(byte[] bytes) {
            int minimum = Math.max(0, bytes.length - 65557);
            for (int index = bytes.length - 22; index >= minimum; index--) {
                if (u32(bytes, index) == 0x06054b50L) return index;
            }
            throw new IllegalStateException("ZIP end-of-central-directory record not found");
        }

        private static int u16(byte[] bytes, int offset) {
            require(offset >= 0 && offset + 2 <= bytes.length, "truncated ZIP metadata");
            return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8);
        }

        private static long u32(byte[] bytes, int offset) {
            require(offset >= 0 && offset + 4 <= bytes.length, "truncated ZIP metadata");
            return ((long) bytes[offset] & 0xff)
                    | (((long) bytes[offset + 1] & 0xff) << 8)
                    | (((long) bytes[offset + 2] & 0xff) << 16)
                    | (((long) bytes[offset + 3] & 0xff) << 24);
        }
    }
}
