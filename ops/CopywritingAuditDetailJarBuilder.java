import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
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
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Builds the copywriting audit-detail/history backend candidate from the exact
 * 2026-08-18 production JAR. Existing non-allowlisted local ZIP records are
 * copied byte-for-byte at both the outer and modified nested-JAR layers.
 */
public final class CopywritingAuditDetailJarBuilder {
    private static final String EXPECTED_BASELINE_SHA =
            "65368b3d5746d8284d8154bf70f073acad49b052c5bcc29071a0b1432d31ef68";
    private static final long EXPECTED_BASELINE_SIZE = 213_838_200L;
    private static final String EXPECTED_ALLOWLIST_SHA =
            "ebf2f36075421030497ef8defc5be8e21ded1e2f562cf49d263e243abe6d9a0f";
    private static final String CANDIDATE_NAME =
            "digital-doctor-app.copywriting-audit-detail.candidate.jar";
    private static final int EXPECTED_NESTED_LIBRARY_COUNT = 353;

    private static final Map<String, String> NESTED = linkedMap(
            "digital-doctor-api", "BOOT-INF/lib/digital-doctor-api-1.0.jar",
            "digital-doctor-domain", "BOOT-INF/lib/digital-doctor-domain-1.0.jar",
            "digital-doctor-infrastructure", "BOOT-INF/lib/digital-doctor-infrastructure-1.0.jar",
            "digital-doctor-trigger", "BOOT-INF/lib/digital-doctor-trigger-1.0.jar");

    private static final Map<String, Integer> EXPECTED_CLASSES = Map.of(
            "digital-doctor-api", 5,
            "digital-doctor-domain", 9,
            "digital-doctor-infrastructure", 4,
            "digital-doctor-trigger", 1);
    private static final Map<String, Integer> EXPECTED_RESOURCES = Map.of(
            "digital-doctor-app", 2);

    private static final String COPYWRITING_MAPPER =
            "BOOT-INF/classes/mybatis/mapper/CopywritingMapper.xml";
    private static final String COPYWRITING_MAPPER_SHA =
            "55ea05fa4b72204e710a506624f4fa2cbbcd2791c353a9e98ab234637f44554f";
    private static final String CHAT_CONFIGURATION =
            "BOOT-INF/classes/com/hzmwmt/config/ChatModelConfiguration.class";
    private static final String CHAT_CONFIGURATION_SHA =
            "845f0f17abecb55bb49cd358d8c198f8490fda131ea2da7d11b3fc17e416955f";
    private static final String APPLICATION_DEV = "BOOT-INF/classes/application-dev.yml";
    private static final String APPLICATION_DEV_SHA =
            "0918280810c0835d56e07bab2fa3b19a0f397498273bffb49095c099953bf65b";
    private static final String APPLICATION_PROD = "BOOT-INF/classes/application-prod.yml";
    private static final String APPLICATION_PROD_SHA =
            "50c66e95bb543668cdc1bee90ab82d0096e37ae85ee2e889de04d67ad848be10";
    private static final String BASELINE_DOMAIN_JAR_SHA =
            "bb058331ea0b0b0859ca64f0f67261946a6c0ec0bba35e46285825ea5663dde5";
    private static final String KIMI_K3_CLASS =
            "com/hzmwmt/domain/ai/service/chat/model/strategy/KimiK3ModelStrategyService.class";
    private static final String KIMI_K3_CLASS_SHA =
            "68bc8167407d28459d8cda69d262518172340488d84555de275406f59d184cfa";

    private static final List<ProtectedEntry> PROTECTED = List.of(
            pe("digital-doctor-api", "com/hzmwmt/api/dto/DigitalHumanVoiceAuditionRequestDTO.class", "2da1fb51b212d75016e7afde3348f51c3067f68e4006713bbdf8e871a687cc9e"),
            pe("digital-doctor-domain", "com/hzmwmt/domain/ai/service/chat/model/strategy/SzDoctorModelStrategyService.class", "51896eac596b022327e93543c169b49076d3fc2b7b7437b17cd231aec3909853"),
            pe("digital-doctor-domain", "com/hzmwmt/domain/controller/user/AdminDigitalWorkController.class", "9296c0f41fee65279803bc94352f8a4168b5a5f0a5b67b3dbd3d18ed64c5ab2d"),
            pe("digital-doctor-domain", "com/hzmwmt/domain/digital/adapter/port/IDigitalPort.class", "d22230377bdd84318e5dcbeae0d5d6f6017755e77fe472ddb63ddbc6568db0be"),
            pe("digital-doctor-domain", "com/hzmwmt/domain/digital/model/entity/DigitalWorkEntity$DigitalWorkEntityBuilder.class", "f0a90c08dacbbcab83156e5563e11e76956c5975e40d59230fb506dd96302f1b"),
            pe("digital-doctor-domain", "com/hzmwmt/domain/digital/model/entity/DigitalWorkEntity.class", "4070385244d0aaca0ead0d36c0a032e4bc0c7044dde8d7fb74ada71ec5d4e096"),
            pe("digital-doctor-domain", "com/hzmwmt/domain/digital/model/entity/VideoCompositionSubmissionEntity$VideoCompositionSubmissionEntityBuilder.class", "932364c54454d1491de037bbc112640ab83d25ad95d275c299245a7468810d10"),
            pe("digital-doctor-domain", "com/hzmwmt/domain/digital/model/entity/VideoCompositionSubmissionEntity.class", "1c466bcef427f53d33a5e756885ec557cad160250c4a99ba90751fa748bafb18"),
            pe("digital-doctor-domain", "com/hzmwmt/domain/digital/service/work/DigitalWorkService.class", "c3eb65dfc0e9f17b93ed4dff288d7478b6563ad5aeeada43aede20da06f173c5"),
            pe("digital-doctor-domain", "com/hzmwmt/domain/entity/user/TDigitalWorkComposeAudit$TDigitalWorkComposeAuditBuilder.class", "f812865880299744d91f665e0dfae7adb0b04eb23f9ef9966ed4a2a4d5819e7b"),
            pe("digital-doctor-domain", "com/hzmwmt/domain/entity/user/TDigitalWorkComposeAudit.class", "c7bbe8e79a05743ce9dd6626b730b7c4cc83d97dc37d9eca7d40d3e68497612d"),
            pe("digital-doctor-domain", "com/hzmwmt/domain/mapper/user/DigitalWorkComposeAuditMapper.class", "6b09019abbc5fc41e67191d3ed9bd91d87394a53a31d59bd98f3c11f20b23057"),
            pe("digital-doctor-domain", "com/hzmwmt/domain/service/user/AdminDigitalWorkService.class", "4d1cef7e82032a358a24f092000ed7536ca2f2740a406d014f1d895780365846"),
            pe("digital-doctor-domain", "com/hzmwmt/domain/service/user/impl/AdminDigitalWorkServiceImpl.class", "eb7252ca847c16ed4f88aed29812853614e84dfbf41eb56be9d27ca1a2d050ac"),
            pe("digital-doctor-infrastructure", "com/hzmwmt/infrastructure/adapter/port/DigitalPort.class", "b49175fa3f89adf018e88b9a1099327a092aabadc4e56058f2c0921858438e6d"),
            pe("digital-doctor-infrastructure", "com/hzmwmt/infrastructure/adapter/repository/DigitalRepository.class", "ba6b3abb342416c1c28edbbe5734ab96dcb325e4f5a3494e2d48fd57c40468ee"),
            pe("digital-doctor-infrastructure", "com/hzmwmt/infrastructure/gateway/digital/DigitalApiService$OpenPlatformAuth.class", "a6f5fc8c23f2a6571a7e892c4bb71118a41aed3e2e9cff79328bb80ee3bd772d"),
            pe("digital-doctor-infrastructure", "com/hzmwmt/infrastructure/gateway/digital/DigitalApiService.class", "619c4c29031c28867cd60f5a921a69dc9dc1e53466bf379e06e8c506a9a4cc5c"),
            pe("digital-doctor-infrastructure", "com/hzmwmt/infrastructure/gateway/digital/IDigitalApiService.class", "c69e5bea0bfa0d3029f5f36758379aaa795cb5ac992a68163142d6e5c283ea63"),
            pe("digital-doctor-infrastructure", "com/hzmwmt/infrastructure/gateway/digital/VideoCompositionRequestPreparer.class", "60b97ba5abaf1ce50d8efc1f3abfd195cf5f49fb2ab23a8e6757439ae09e14cb"),
            pe("digital-doctor-trigger", "com/hzmwmt/trigger/http/client/DigitalHumanOpenController.class", "81cd160b82a295453759700dca04c28c5d60ce110ad84bde13916ca0c7bfe537"));

    private static final Pattern SIGNATURE_ARTIFACT = Pattern.compile(
            "(?i)^META-INF/(?:[^/]+\\.(SF|RSA|DSA|EC)|SIG-[^/]+)$");

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            throw new IllegalArgumentException(
                    "usage: CopywritingAuditDetailJarBuilder <baseline.jar> <classes-root> <release-files.txt> <output-dir>");
        }
        Path baselinePath = Path.of(args[0]).toAbsolutePath().normalize();
        Path classesRoot = Path.of(args[1]).toAbsolutePath().normalize();
        Path allowlistPath = Path.of(args[2]).toAbsolutePath().normalize();
        Path outputDir = Path.of(args[3]).toAbsolutePath().normalize();

        requireRegularFile(baselinePath, "baseline JAR");
        requireDirectory(classesRoot, "isolated classes root");
        requireRegularFile(allowlistPath, "release allowlist");
        require(!Files.exists(outputDir), "output directory must not already exist: " + outputDir);
        require(Files.size(baselinePath) == EXPECTED_BASELINE_SIZE, "baseline size mismatch");
        require(EXPECTED_BASELINE_SHA.equals(sha256(baselinePath)), "baseline SHA-256 mismatch");
        require(EXPECTED_ALLOWLIST_SHA.equals(sha256(allowlistPath)), "allowlist SHA-256 mismatch");

        ReleaseInputs release = collectReleaseInputs(classesRoot, allowlistPath);
        require(release.sourceCount == 14, "expected exactly 14 Java sources");
        require(release.classPayload.size() == 19, "expected exactly 19 class-closure entries");
        require(release.resourcePayload.size() == 2, "expected exactly 2 XML resources");
        require(release.totalPayloadCount() == 21, "expected exactly 21 runtime payload entries");
        verifyExpectedCounts(release);
        verifyApprovalCopywritingAggregateBinaryClosure(release);

        byte[] baselineBytes = Files.readAllBytes(baselinePath);
        RawZip outerBefore = RawZip.parse(baselineBytes, "production baseline");
        outerBefore.requireSafeArchive();
        verifyAllNestedLibrariesStored(outerBefore, EXPECTED_NESTED_LIBRARY_COUNT);
        requireEntryHash(outerBefore, COPYWRITING_MAPPER, COPYWRITING_MAPPER_SHA, "production mapper hotfix");
        requireEntryHash(outerBefore, CHAT_CONFIGURATION, CHAT_CONFIGURATION_SHA, "Kimi chat configuration");
        requireEntryHash(outerBefore, APPLICATION_DEV, APPLICATION_DEV_SHA, "application-dev.yml");
        requireEntryHash(outerBefore, APPLICATION_PROD, APPLICATION_PROD_SHA, "application-prod.yml");
        requireEntryHash(outerBefore, NESTED.get("digital-doctor-domain"), BASELINE_DOMAIN_JAR_SHA,
                "production Kimi domain JAR");

        Map<String, RawZip> nestedBefore = new LinkedHashMap<>();
        Map<String, RawZip> nestedAfter = new LinkedHashMap<>();
        Map<String, byte[]> nestedCandidateBytes = new LinkedHashMap<>();
        List<DiffRow> nestedDiff = new ArrayList<>();
        RawStats nestedRaw = new RawStats();

        for (Map.Entry<String, String> moduleEntry : NESTED.entrySet()) {
            String module = moduleEntry.getKey();
            String outerPath = moduleEntry.getValue();
            Entry outerEntry = requireEntry(outerBefore, outerPath);
            byte[] beforeBytes = outerEntry.uncompressed(outerBefore.source);
            RawZip before = RawZip.parse(beforeBytes, outerPath + " baseline");
            before.requireSafeArchive();
            rejectSignedArchive(before, outerPath);
            ModulePayload payload = release.forModule(module);
            rejectVersionedClassOverrides(before, payload, outerPath);

            byte[] afterBytes = before.rewrite(payload.entries);
            RawZip after = RawZip.parse(afterBytes, outerPath + " candidate");
            after.requireSafeArchive();
            List<DiffRow> moduleDiff = verifyLogicalDiff(
                    "nested:" + module, before, after, payload.entries.keySet());
            nestedDiff.addAll(moduleDiff);
            nestedRaw.add(verifyRawNonAllowlisted(before, after, payload.entries.keySet()));
            nestedBefore.put(module, before);
            nestedAfter.put(module, after);
            nestedCandidateBytes.put(outerPath, afterBytes);
        }

        require(nestedDiff.size() == 19, "nested logical diff must contain exactly 19 entries");
        requireEntryHash(nestedBefore.get("digital-doctor-domain"), KIMI_K3_CLASS, KIMI_K3_CLASS_SHA,
                "baseline Kimi K3 strategy");
        requireEntryHash(nestedAfter.get("digital-doctor-domain"), KIMI_K3_CLASS, KIMI_K3_CLASS_SHA,
                "candidate Kimi K3 strategy");

        TreeMap<String, String> protectedHashes = new TreeMap<>();
        require(PROTECTED.size() == 21, "protected legacy hotpatch set must contain 21 classes");
        for (ProtectedEntry item : PROTECTED) {
            RawZip before = nestedBefore.get(item.module);
            RawZip after = nestedAfter.get(item.module);
            require(before != null && after != null, "unknown protected module: " + item.module);
            requireEntryHash(before, item.path, item.expectedSha, "baseline protected " + item.label());
            requireEntryHash(after, item.path, item.expectedSha, "candidate protected " + item.label());
            protectedHashes.put(item.label(), item.expectedSha);
        }
        protectedHashes.put("digital-doctor-domain:" + KIMI_K3_CLASS, KIMI_K3_CLASS_SHA);

        ModulePayload appPayload = release.forModule("digital-doctor-app");
        Map<String, byte[]> outerReplacements = new LinkedHashMap<>(nestedCandidateBytes);
        for (Map.Entry<String, byte[]> resource : appPayload.entries.entrySet()) {
            outerReplacements.put("BOOT-INF/classes/" + resource.getKey(), resource.getValue());
        }
        require(outerReplacements.size() == 6, "outer replacement set must contain exactly 6 entries");

        byte[] candidateBytes = outerBefore.rewrite(outerReplacements);
        RawZip outerAfter = RawZip.parse(candidateBytes, CANDIDATE_NAME);
        outerAfter.requireSafeArchive();
        List<DiffRow> outerDiff = verifyLogicalDiff(
                "outer", outerBefore, outerAfter, outerReplacements.keySet());
        require(outerDiff.size() == 6, "outer logical diff must contain exactly 6 entries");
        RawStats outerRaw = verifyRawNonAllowlisted(outerBefore, outerAfter, outerReplacements.keySet());
        verifyAllNestedLibrariesStored(outerAfter, EXPECTED_NESTED_LIBRARY_COUNT);

        requireEntryHash(outerAfter, COPYWRITING_MAPPER, COPYWRITING_MAPPER_SHA, "candidate mapper hotfix");
        requireEntryHash(outerAfter, CHAT_CONFIGURATION, CHAT_CONFIGURATION_SHA, "candidate chat configuration");
        requireEntryHash(outerAfter, APPLICATION_DEV, APPLICATION_DEV_SHA, "candidate application-dev.yml");
        requireEntryHash(outerAfter, APPLICATION_PROD, APPLICATION_PROD_SHA, "candidate application-prod.yml");
        protectedHashes.put("outer:" + COPYWRITING_MAPPER, COPYWRITING_MAPPER_SHA);
        protectedHashes.put("outer:" + CHAT_CONFIGURATION, CHAT_CONFIGURATION_SHA);
        protectedHashes.put("outer:" + APPLICATION_DEV, APPLICATION_DEV_SHA);
        protectedHashes.put("outer:" + APPLICATION_PROD, APPLICATION_PROD_SHA);

        Files.createDirectory(outputDir);
        Path candidate = outputDir.resolve(CANDIDATE_NAME);
        Path temporary = Files.createTempFile(outputDir, ".candidate-", ".tmp");
        Files.write(temporary, candidateBytes);
        verifyZipFileIntegrity(temporary, outerAfter.entries.size());
        Files.move(temporary, candidate, StandardCopyOption.ATOMIC_MOVE);
        String candidateSha = sha256(candidate);

        writeLine(outputDir.resolve("candidate.sha256"), candidateSha + "  " + CANDIDATE_NAME);
        writeInjectedManifest(outputDir.resolve("injected-entries.sha256"), release);
        writeProtectedManifest(outputDir.resolve("protected-entries.sha256"), protectedHashes);
        writeDiffManifest(outputDir.resolve("logical-diff.tsv"), outerDiff, nestedDiff);
        writeRawAudit(outputDir.resolve("raw-preservation.txt"), outerRaw, nestedRaw);
        writeAudit(outputDir.resolve("backend-candidate-audit.txt"), baselinePath, candidate,
                candidateSha, outerBefore, outerAfter, outerRaw, nestedRaw, protectedHashes.size());
        Files.copy(allowlistPath, outputDir.resolve("release-files.txt"));
        writeLine(outputDir.resolve("SHA256SUMS.txt"), checksums(outputDir));

        System.out.println("COPYWRITING_AUDIT_DETAIL_CANDIDATE_OK");
        System.out.println("BASELINE_SHA256=" + EXPECTED_BASELINE_SHA);
        System.out.println("BASELINE_SIZE=" + EXPECTED_BASELINE_SIZE);
        System.out.println("CANDIDATE_SHA256=" + candidateSha);
        System.out.println("CANDIDATE_SIZE=" + Files.size(candidate));
        System.out.println("JAVA_SOURCES=14");
        System.out.println("CLASS_CLOSURE=19");
        System.out.println("RESOURCES=2");
        System.out.println("OUTER_LOGICAL_DIFF=6");
        System.out.println("NESTED_LOGICAL_DIFF=19");
        System.out.println("NON_ALLOWLIST_LOGICAL_UNCHANGED=yes");
        System.out.println("NON_ALLOWLIST_RAW_LOCAL_RECORD_UNCHANGED=yes");
        System.out.println("ALL_NESTED_LIBRARIES_STORED=yes");
        System.out.println("NESTED_LIBRARY_COUNT=" + EXPECTED_NESTED_LIBRARY_COUNT);
        System.out.println("PROTECTED_ENTRIES=" + protectedHashes.size());
        System.out.println("ZIP_INTEGRITY=ok");
        System.out.println("CANDIDATE=" + candidate);
    }

    private static ReleaseInputs collectReleaseInputs(Path classesRoot, Path allowlist) throws IOException {
        ReleaseInputs result = new ReleaseInputs();
        Set<String> seen = new HashSet<>();
        for (String raw : Files.readAllLines(allowlist, StandardCharsets.UTF_8)) {
            String path = raw.trim().replace('\\', '/');
            if (path.isEmpty() || path.startsWith("#")) continue;
            require(seen.add(path), "duplicate allowlist path: " + path);
            if (path.matches("^feibo_backend_master/[^/]+/src/main/java/.+\\.java$")) {
                String module = moduleFromPath(path);
                require(EXPECTED_CLASSES.containsKey(module), "unexpected Java module: " + module);
                result.sourceCount++;
                String prefix = "feibo_backend_master/" + module + "/src/main/java/";
                String javaPath = path.substring(prefix.length());
                String classPath = javaPath.substring(0, javaPath.length() - 5) + ".class";
                int slash = classPath.lastIndexOf('/');
                String directory = slash < 0 ? "" : classPath.substring(0, slash + 1);
                String file = slash < 0 ? classPath : classPath.substring(slash + 1);
                String base = file.substring(0, file.length() - 6);
                Path compiledDirectory = classesRoot.resolve(module).resolve("target/classes").resolve(directory);
                requireDirectory(compiledDirectory, "compiled class directory");
                List<Path> closure = new ArrayList<>();
                try (var stream = Files.list(compiledDirectory)) {
                    stream.filter(Files::isRegularFile)
                            .filter(item -> {
                                String name = item.getFileName().toString();
                                return name.equals(base + ".class")
                                        || (name.startsWith(base + "$") && name.endsWith(".class"));
                            })
                            .sorted(Comparator.comparing(item -> item.getFileName().toString()))
                            .forEach(closure::add);
                }
                require(closure.stream().anyMatch(item -> item.getFileName().toString().equals(base + ".class")),
                        "missing compiled top-level class: " + module + ":" + classPath);
                for (Path item : closure) {
                    String entry = directory + item.getFileName();
                    result.addClass(module, entry, Files.readAllBytes(item));
                }
            } else if (path.matches("^feibo_backend_master/[^/]+/src/main/resources/.+\\.xml$")) {
                String module = moduleFromPath(path);
                require(EXPECTED_RESOURCES.containsKey(module), "unexpected resource module: " + module);
                String prefix = "feibo_backend_master/" + module + "/src/main/resources/";
                String resource = path.substring(prefix.length());
                Path compiled = classesRoot.resolve(module).resolve("target/classes").resolve(resource);
                requireRegularFile(compiled, "compiled filtered XML");
                result.addResource(module, resource, Files.readAllBytes(compiled));
            } else {
                throw new IllegalStateException("unsupported allowlist path: " + path);
            }
        }
        return result;
    }

    private static void verifyExpectedCounts(ReleaseInputs release) {
        for (Map.Entry<String, Integer> expected : EXPECTED_CLASSES.entrySet()) {
            require(release.classCount(expected.getKey()) == expected.getValue(),
                    expected.getKey() + " class count mismatch: " + release.classCount(expected.getKey()));
        }
        for (Map.Entry<String, Integer> expected : EXPECTED_RESOURCES.entrySet()) {
            require(release.resourceCount(expected.getKey()) == expected.getValue(),
                    expected.getKey() + " resource count mismatch: " + release.resourceCount(expected.getKey()));
        }
    }

    /**
     * This aggregate is a Lombok-generated two-class ABI unit.  The controller
     * and repository payloads call the new builder/getter methods, so replacing
     * only their callers leaves the production baseline builder behind and
     * fails at request dispatch with NoSuchMethodError.  Pin both generated
     * classes and their exact linkage signatures as part of the release gate.
     */
    private static void verifyApprovalCopywritingAggregateBinaryClosure(ReleaseInputs release)
            throws IOException {
        String aggregate = "digital-doctor-domain:com/hzmwmt/domain/copywriting/model/aggregate/"
                + "ApprovalCopywritingAggregate.class";
        String builder = "digital-doctor-domain:com/hzmwmt/domain/copywriting/model/aggregate/"
                + "ApprovalCopywritingAggregate$ApprovalCopywritingAggregateBuilder.class";
        requireClassMethods(release, aggregate, Set.of(
                "builder()Lcom/hzmwmt/domain/copywriting/model/aggregate/"
                        + "ApprovalCopywritingAggregate$ApprovalCopywritingAggregateBuilder;",
                "getExpertReviewer()Ljava/lang/Boolean;",
                "getOrganizationReviewer()Ljava/lang/Boolean;",
                "<init>(Lcom/hzmwmt/domain/copywriting/model/entity/CopywritingEntity;"
                        + "Lcom/hzmwmt/domain/copywriting/model/entity/CopywritingCategoryEntity;"
                        + "Lcom/hzmwmt/domain/copywriting/model/entity/CopywritingSourceEntity;"
                        + "Lcom/hzmwmt/domain/copywriting/model/entity/CopywritingApprovalFlowEntity;"
                        + "Lcom/hzmwmt/domain/copywriting/model/entity/CopywritingApprovalHistoryEntity;"
                        + "Lcom/hzmwmt/domain/copywriting/model/entity/ApprovalUserEntity;"
                        + "Lcom/hzmwmt/domain/copywriting/model/entity/ApprovalUserEntity;"
                        + "Ljava/lang/Boolean;Ljava/lang/Boolean;)V"));
        requireClassMethods(release, builder, Set.of(
                "expertReviewer(Ljava/lang/Boolean;)Lcom/hzmwmt/domain/copywriting/model/aggregate/"
                        + "ApprovalCopywritingAggregate$ApprovalCopywritingAggregateBuilder;",
                "organizationReviewer(Ljava/lang/Boolean;)Lcom/hzmwmt/domain/copywriting/model/aggregate/"
                        + "ApprovalCopywritingAggregate$ApprovalCopywritingAggregateBuilder;",
                "build()Lcom/hzmwmt/domain/copywriting/model/aggregate/ApprovalCopywritingAggregate;"));
    }

    private static void requireClassMethods(ReleaseInputs release, String key, Set<String> expected)
            throws IOException {
        byte[] bytes = release.classPayload.get(key);
        require(bytes != null, "missing required binary-closure class: " + key);
        Set<String> actual = readClassMethodSignatures(bytes, key);
        require(actual.containsAll(expected),
                "required binary linkage signatures missing from " + key + ": " + difference(expected, actual));
    }

    private static Set<String> difference(Set<String> expected, Set<String> actual) {
        LinkedHashSet<String> missing = new LinkedHashSet<>(expected);
        missing.removeAll(actual);
        return missing;
    }

    private static Set<String> readClassMethodSignatures(byte[] bytes, String label) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            require(input.readInt() == 0xCAFEBABE, "invalid class magic: " + label);
            input.readUnsignedShort();
            input.readUnsignedShort();
            int constantPoolCount = input.readUnsignedShort();
            String[] utf8 = new String[constantPoolCount];
            for (int i = 1; i < constantPoolCount; i++) {
                int tag = input.readUnsignedByte();
                switch (tag) {
                    case 1 -> utf8[i] = input.readUTF();
                    case 3, 4 -> input.skipNBytes(4);
                    case 5, 6 -> {
                        input.skipNBytes(8);
                        i++;
                    }
                    case 7, 8, 16, 19, 20 -> input.skipNBytes(2);
                    case 9, 10, 11, 12, 17, 18 -> input.skipNBytes(4);
                    case 15 -> input.skipNBytes(3);
                    default -> throw new IllegalStateException(
                            "unsupported class constant-pool tag " + tag + ": " + label);
                }
            }
            input.skipNBytes(6);
            int interfaceCount = input.readUnsignedShort();
            input.skipNBytes((long) interfaceCount * 2L);
            int fieldCount = input.readUnsignedShort();
            for (int i = 0; i < fieldCount; i++) skipClassMember(input);
            int methodCount = input.readUnsignedShort();
            LinkedHashSet<String> methods = new LinkedHashSet<>();
            for (int i = 0; i < methodCount; i++) {
                input.readUnsignedShort();
                int nameIndex = input.readUnsignedShort();
                int descriptorIndex = input.readUnsignedShort();
                require(utf8[nameIndex] != null && utf8[descriptorIndex] != null,
                        "invalid method name/descriptor index: " + label);
                methods.add(utf8[nameIndex] + utf8[descriptorIndex]);
                skipClassAttributes(input);
            }
            return methods;
        }
    }

    private static void skipClassMember(DataInputStream input) throws IOException {
        input.skipNBytes(6);
        skipClassAttributes(input);
    }

    private static void skipClassAttributes(DataInputStream input) throws IOException {
        int attributeCount = input.readUnsignedShort();
        for (int i = 0; i < attributeCount; i++) {
            input.readUnsignedShort();
            input.skipNBytes(Integer.toUnsignedLong(input.readInt()));
        }
    }

    private static List<DiffRow> verifyLogicalDiff(String scope, RawZip before, RawZip after,
                                                    Set<String> allowed) {
        require(after.byName.keySet().containsAll(before.byName.keySet()), scope + " deleted an entry");
        Set<String> added = new LinkedHashSet<>(after.byName.keySet());
        added.removeAll(before.byName.keySet());
        require(allowed.containsAll(added), scope + " added a non-allowlisted entry: " + added);
        List<DiffRow> rows = new ArrayList<>();
        Set<String> changed = new LinkedHashSet<>();
        for (String name : after.byName.keySet()) {
            Entry oldEntry = before.byName.get(name);
            Entry newEntry = after.byName.get(name);
            if (oldEntry == null) {
                changed.add(name);
                byte[] payload = newEntry.uncompressed(after.source);
                rows.add(new DiffRow(scope, "add", name, "-", sha256(payload), 0,
                        payload.length, "-", methodName(newEntry.method)));
                continue;
            }
            require(oldEntry.method == newEntry.method, scope + " compression method changed: " + name);
            byte[] oldPayload = oldEntry.uncompressed(before.source);
            byte[] newPayload = newEntry.uncompressed(after.source);
            if (!Arrays.equals(oldPayload, newPayload)) {
                require(allowed.contains(name), scope + " non-allowlisted logical content changed: " + name);
                changed.add(name);
                rows.add(new DiffRow(scope, "replace", name, sha256(oldPayload), sha256(newPayload),
                        oldPayload.length, newPayload.length, methodName(oldEntry.method),
                        methodName(newEntry.method)));
            }
        }
        require(changed.equals(new LinkedHashSet<>(allowed)),
                scope + " diff did not exactly equal allowlist; changed=" + changed + " allowed=" + allowed);
        return rows;
    }

    private static RawStats verifyRawNonAllowlisted(RawZip before, RawZip after, Set<String> allowed) {
        int rawLocal = 0;
        int rawCentral = 0;
        for (Entry oldEntry : before.entries) {
            Entry newEntry = after.byName.get(oldEntry.name);
            require(newEntry != null, "candidate missing entry: " + oldEntry.name);
            if (allowed.contains(oldEntry.name)) continue;
            require(Arrays.equals(oldEntry.rawLocalRecord(before.source), newEntry.rawLocalRecord(after.source)),
                    "non-allowlisted raw local record changed: " + oldEntry.name);
            require(Arrays.equals(oldEntry.normalizedCentralRecord(before.source),
                            newEntry.normalizedCentralRecord(after.source)),
                    "non-allowlisted central metadata changed: " + oldEntry.name);
            rawLocal++;
            rawCentral++;
        }
        return new RawStats(rawLocal, rawCentral);
    }

    private static void verifyAllNestedLibrariesStored(RawZip outer, int expectedCount) {
        int count = 0;
        for (Entry entry : outer.entries) {
            if (!isNestedLibrary(entry.name)) continue;
            count++;
            require(entry.method == 0 && entry.localMethod == 0,
                    "nested library is not Stored in local and central headers: " + entry.name);
            require(entry.compressedSize == entry.uncompressedSize,
                    "nested Stored sizes differ: " + entry.name);
        }
        require(count == expectedCount, "nested library count mismatch: " + count);
    }

    private static void rejectSignedArchive(RawZip archive, String label) {
        for (String name : archive.byName.keySet()) {
            require(!SIGNATURE_ARTIFACT.matcher(name).matches(),
                    "refusing to modify signed nested JAR " + label + ": " + name);
        }
    }

    private static void rejectVersionedClassOverrides(RawZip archive, ModulePayload payload, String label) {
        for (String entry : payload.entries.keySet()) {
            if (!entry.endsWith(".class")) continue;
            for (String existing : archive.byName.keySet()) {
                if (existing.startsWith("META-INF/versions/") && existing.endsWith("/" + entry)) {
                    throw new IllegalStateException("multi-release override collision in " + label + ": " + existing);
                }
            }
        }
    }

    private static void requireEntryHash(RawZip archive, String path, String expected, String label) {
        Entry entry = requireEntry(archive, path);
        String actual = sha256(entry.uncompressed(archive.source));
        require(expected.equals(actual), label + " SHA-256 mismatch: " + path + " actual=" + actual);
    }

    private static Entry requireEntry(RawZip archive, String path) {
        Entry entry = archive.byName.get(path);
        require(entry != null, "missing ZIP entry: " + path);
        return entry;
    }

    private static void verifyZipFileIntegrity(Path path, int expectedEntries) throws IOException {
        int count = 0;
        try (ZipFile zip = new ZipFile(path.toFile(), StandardCharsets.UTF_8)) {
            var items = zip.entries();
            while (items.hasMoreElements()) {
                ZipEntry item = items.nextElement();
                try (InputStream input = zip.getInputStream(item)) {
                    input.transferTo(java.io.OutputStream.nullOutputStream());
                }
                count++;
            }
        }
        require(count == expectedEntries, "ZipFile integrity entry count mismatch: " + count);
    }

    private static void writeInjectedManifest(Path path, ReleaseInputs release) throws IOException {
        TreeMap<String, byte[]> sorted = new TreeMap<>();
        sorted.putAll(release.classPayload);
        sorted.putAll(release.resourcePayload);
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, byte[]> item : sorted.entrySet()) {
            text.append(sha256(item.getValue())).append("  ").append(item.getKey()).append('\n');
        }
        Files.writeString(path, text.toString(), StandardCharsets.UTF_8);
    }

    private static void writeProtectedManifest(Path path, Map<String, String> hashes) throws IOException {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, String> item : hashes.entrySet()) {
            text.append(item.getValue()).append("  ").append(item.getKey()).append('\n');
        }
        Files.writeString(path, text.toString(), StandardCharsets.UTF_8);
    }

    private static void writeDiffManifest(Path path, List<DiffRow> outer, List<DiffRow> nested)
            throws IOException {
        StringBuilder text = new StringBuilder(
                "scope\taction\tentry\tbefore_sha256\tafter_sha256\tbefore_size\tafter_size\tbefore_method\tafter_method\n");
        List<DiffRow> rows = new ArrayList<>(outer);
        rows.addAll(nested);
        rows.sort(Comparator.comparing(DiffRow::scope).thenComparing(DiffRow::entry));
        for (DiffRow row : rows) {
            text.append(row.scope).append('\t').append(row.action).append('\t').append(row.entry).append('\t')
                    .append(row.beforeSha).append('\t').append(row.afterSha).append('\t')
                    .append(row.beforeSize).append('\t').append(row.afterSize).append('\t')
                    .append(row.beforeMethod).append('\t').append(row.afterMethod).append('\n');
        }
        Files.writeString(path, text.toString(), StandardCharsets.UTF_8);
    }

    private static void writeRawAudit(Path path, RawStats outer, RawStats nested) throws IOException {
        Files.writeString(path, String.join("\n",
                "outer_non_allowlist_raw_local_records_unchanged=" + outer.localRecords,
                "outer_non_allowlist_normalized_central_records_unchanged=" + outer.centralRecords,
                "nested_non_allowlist_raw_local_records_unchanged=" + nested.localRecords,
                "nested_non_allowlist_normalized_central_records_unchanged=" + nested.centralRecords,
                "non_allowlist_logical_payload_sha_unchanged=yes",
                "all_nested_libraries_local_and_central_stored=yes",
                "duplicate_and_casefold_collision_check=passed",
                "zip64_and_multidisk_rejected=yes",
                "") , StandardCharsets.UTF_8);
    }

    private static void writeAudit(Path path, Path baseline, Path candidate, String candidateSha,
                                   RawZip outerBefore, RawZip outerAfter, RawStats outerRaw,
                                   RawStats nestedRaw, int protectedCount) throws IOException {
        Files.writeString(path, String.join("\n",
                "release=copywriting-audit-detail-history",
                "baseline=" + baseline,
                "baseline_sha256=" + EXPECTED_BASELINE_SHA,
                "baseline_size=" + EXPECTED_BASELINE_SIZE,
                "candidate=" + candidate,
                "candidate_sha256=" + candidateSha,
                "candidate_size=" + Files.size(candidate),
                "allowlist_sha256=" + EXPECTED_ALLOWLIST_SHA,
                "java_sources=14",
                "class_closure_entries=19",
                "xml_resources=2",
                "runtime_payload_entries=21",
                "outer_logical_changed_entries=6",
                "nested_logical_changed_entries=19",
                "outer_entry_count_before=" + outerBefore.entries.size(),
                "outer_entry_count_after=" + outerAfter.entries.size(),
                "nested_library_count=" + EXPECTED_NESTED_LIBRARY_COUNT,
                "protected_entry_count=" + protectedCount,
                "protected_copywriting_mapper_sha256=" + COPYWRITING_MAPPER_SHA,
                "protected_kimi_k3_class_sha256=" + KIMI_K3_CLASS_SHA,
                "protected_chat_configuration_sha256=" + CHAT_CONFIGURATION_SHA,
                "protected_application_dev_sha256=" + APPLICATION_DEV_SHA,
                "protected_application_prod_sha256=" + APPLICATION_PROD_SHA,
                "outer_non_allowlist_raw_local_records=" + outerRaw.localRecords,
                "nested_non_allowlist_raw_local_records=" + nestedRaw.localRecords,
                "non_allowlist_logical_payload_sha_unchanged=yes",
                "non_allowlist_raw_local_record_bytes_unchanged=yes",
                "non_allowlist_central_metadata_unchanged_except_local_offset=yes",
                "all_nested_libraries_stored=yes",
                "inner_class_closure_gate=passed",
                "approval_copywriting_aggregate_binary_linkage_gate=passed",
                "outer_and_nested_bidirectional_diff_gate=passed",
                "zip_integrity=passed",
                "database_change=none",
                "production_touched=no",
                ""), StandardCharsets.UTF_8);
    }

    private static String checksums(Path outputDir) throws IOException {
        List<Path> files = new ArrayList<>();
        try (var stream = Files.list(outputDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(item -> !item.getFileName().toString().equals("SHA256SUMS.txt"))
                    .sorted(Comparator.comparing(item -> item.getFileName().toString()))
                    .forEach(files::add);
        }
        StringBuilder result = new StringBuilder();
        for (Path item : files) {
            result.append(sha256(item)).append("  ").append(item.getFileName()).append('\n');
        }
        return result.toString();
    }

    private static String moduleFromPath(String path) {
        String prefix = "feibo_backend_master/";
        int slash = path.indexOf('/', prefix.length());
        require(path.startsWith(prefix) && slash > prefix.length(), "invalid release path: " + path);
        return path.substring(prefix.length(), slash);
    }

    private static boolean isNestedLibrary(String name) {
        return name.startsWith("BOOT-INF/lib/") && name.endsWith(".jar")
                && name.indexOf('/', "BOOT-INF/lib/".length()) < 0;
    }

    private static String methodName(int method) {
        return method == 0 ? "stored" : method == 8 ? "deflated" : Integer.toString(method);
    }

    private static void validateName(String name) {
        require(!name.isEmpty() && !name.startsWith("/") && !name.contains("\\"),
                "unsafe ZIP entry name: " + name);
        require(!name.matches("^[A-Za-z]:.*"), "drive-qualified ZIP entry: " + name);
        for (String component : name.split("/")) {
            require(!component.equals(".."), "path traversal ZIP entry: " + name);
        }
        for (int i = 0; i < name.length(); i++) {
            char value = name.charAt(i);
            require(value >= 32 && value != 127, "control character in ZIP entry: " + name);
        }
    }

    private static String sha256(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            return sha256(input);
        }
    }

    private static String sha256(InputStream input) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[128 * 1024];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (count > 0) digest.update(buffer, 0, count);
            }
            return hex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format("%02x", value & 0xff));
        return result.toString();
    }

    private static void writeLine(Path path, String value) throws IOException {
        Files.writeString(path, value.endsWith("\n") ? value : value + "\n", StandardCharsets.UTF_8);
    }

    private static void requireRegularFile(Path path, String label) {
        require(Files.isRegularFile(path) && !Files.isSymbolicLink(path), label + " is not a regular file: " + path);
    }

    private static void requireDirectory(Path path, String label) {
        require(Files.isDirectory(path) && !Files.isSymbolicLink(path), label + " is not a directory: " + path);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static ProtectedEntry pe(String module, String path, String expectedSha) {
        return new ProtectedEntry(module, path, expectedSha);
    }

    private static Map<String, String> linkedMap(String... values) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) result.put(values[i], values[i + 1]);
        return Collections.unmodifiableMap(result);
    }

    private static final class ReleaseInputs {
        int sourceCount;
        final Map<String, byte[]> classPayload = new LinkedHashMap<>();
        final Map<String, byte[]> resourcePayload = new LinkedHashMap<>();

        void addClass(String module, String entry, byte[] bytes) {
            String key = module + ":" + entry;
            require(!classPayload.containsKey(key), "duplicate class payload: " + key);
            classPayload.put(key, bytes);
        }

        void addResource(String module, String entry, byte[] bytes) {
            String key = module + ":" + entry;
            require(!resourcePayload.containsKey(key), "duplicate resource payload: " + key);
            resourcePayload.put(key, bytes);
        }

        int classCount(String module) {
            return (int) classPayload.keySet().stream().filter(item -> item.startsWith(module + ":")).count();
        }

        int resourceCount(String module) {
            return (int) resourcePayload.keySet().stream().filter(item -> item.startsWith(module + ":")).count();
        }

        int totalPayloadCount() {
            return classPayload.size() + resourcePayload.size();
        }

        ModulePayload forModule(String module) {
            LinkedHashMap<String, byte[]> result = new LinkedHashMap<>();
            String prefix = module + ":";
            classPayload.forEach((key, value) -> {
                if (key.startsWith(prefix)) result.put(key.substring(prefix.length()), value);
            });
            resourcePayload.forEach((key, value) -> {
                if (key.startsWith(prefix)) result.put(key.substring(prefix.length()), value);
            });
            return new ModulePayload(result);
        }
    }

    private static final class ModulePayload {
        final Map<String, byte[]> entries;

        ModulePayload(Map<String, byte[]> entries) {
            this.entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
        }
    }

    private static final class RawZip {
        final byte[] source;
        final int centralOffset;
        final int centralSize;
        final int eocdOffset;
        final List<Entry> entries;
        final Map<String, Entry> byName;

        RawZip(byte[] source, int centralOffset, int centralSize, int eocdOffset,
               List<Entry> entries, Map<String, Entry> byName) {
            this.source = source;
            this.centralOffset = centralOffset;
            this.centralSize = centralSize;
            this.eocdOffset = eocdOffset;
            this.entries = entries;
            this.byName = byName;
        }

        static RawZip parse(byte[] bytes, String label) {
            int eocd = findEocd(bytes);
            require(u16(bytes, eocd + 4) == 0 && u16(bytes, eocd + 6) == 0,
                    "multi-disk ZIP rejected: " + label);
            int count = u16(bytes, eocd + 10);
            require(count != 0xffff && u16(bytes, eocd + 8) == count,
                    "ZIP64 or split entry count rejected: " + label);
            long centralSizeLong = u32(bytes, eocd + 12);
            long centralOffsetLong = u32(bytes, eocd + 16);
            require(centralSizeLong != 0xffffffffL && centralOffsetLong != 0xffffffffL,
                    "ZIP64 central directory rejected: " + label);
            int centralSize = Math.toIntExact(centralSizeLong);
            int centralOffset = Math.toIntExact(centralOffsetLong);
            require(centralOffset + centralSize == eocd,
                    "central directory does not end exactly at EOCD: " + label);
            require(eocd + 22 + u16(bytes, eocd + 20) == bytes.length,
                    "EOCD/comment does not close archive: " + label);

            List<Entry> entries = new ArrayList<>();
            Map<String, Entry> byName = new LinkedHashMap<>();
            Set<String> folded = new HashSet<>();
            int cursor = centralOffset;
            for (int i = 0; i < count; i++) {
                require(u32(bytes, cursor) == 0x02014b50L, "invalid central header: " + label);
                int flags = u16(bytes, cursor + 8);
                int method = u16(bytes, cursor + 10);
                long crc = u32(bytes, cursor + 16);
                long compressed = u32(bytes, cursor + 20);
                long uncompressed = u32(bytes, cursor + 24);
                int nameLength = u16(bytes, cursor + 28);
                int extraLength = u16(bytes, cursor + 30);
                int commentLength = u16(bytes, cursor + 32);
                long localOffsetLong = u32(bytes, cursor + 42);
                require(compressed != 0xffffffffL && uncompressed != 0xffffffffL
                                && localOffsetLong != 0xffffffffL,
                        "ZIP64 entry rejected: " + label);
                require((flags & 0x800) != 0, "entry name lacks UTF-8 flag: " + label);
                String name = new String(bytes, cursor + 46, nameLength, StandardCharsets.UTF_8);
                validateName(name);
                require(!byName.containsKey(name), "duplicate ZIP entry: " + name);
                require(folded.add(name.toLowerCase(Locale.ROOT)), "case-fold ZIP collision: " + name);
                int localOffset = Math.toIntExact(localOffsetLong);
                require(u32(bytes, localOffset) == 0x04034b50L, "invalid local header: " + name);
                int localFlags = u16(bytes, localOffset + 6);
                int localMethod = u16(bytes, localOffset + 8);
                int localNameLength = u16(bytes, localOffset + 26);
                int localExtraLength = u16(bytes, localOffset + 28);
                String localName = new String(bytes, localOffset + 30, localNameLength, StandardCharsets.UTF_8);
                require(name.equals(localName), "local/central name mismatch: " + name);
                require(localFlags == flags, "local/central flags mismatch: " + name);
                require(localMethod == method, "local/central method mismatch: " + name);
                int dataOffset = Math.addExact(localOffset, 30 + localNameLength + localExtraLength);
                Entry entry = new Entry(name, flags, method, localMethod, crc,
                        Math.toIntExact(compressed), Math.toIntExact(uncompressed), localOffset,
                        dataOffset, cursor, 46 + nameLength + extraLength + commentLength);
                require(!byName.containsKey(name), "duplicate ZIP entry: " + name);
                entries.add(entry);
                byName.put(name, entry);
                cursor += entry.centralRecordLength;
            }
            require(cursor == eocd, "central directory length mismatch: " + label);
            List<Entry> localOrder = new ArrayList<>(entries);
            localOrder.sort(Comparator.comparingInt(item -> item.localOffset));
            int previousEnd = 0;
            for (int i = 0; i < localOrder.size(); i++) {
                Entry entry = localOrder.get(i);
                int rawEnd = i + 1 < localOrder.size() ? localOrder.get(i + 1).localOffset : centralOffset;
                require(entry.localOffset >= previousEnd && entry.dataOffset + entry.compressedSize <= rawEnd,
                        "overlapping or truncated local record: " + entry.name);
                entry.rawRecordEnd = rawEnd;
                previousEnd = rawEnd;
            }
            return new RawZip(bytes, centralOffset, centralSize, eocd, entries, byName);
        }

        void requireSafeArchive() {
            require(!entries.isEmpty(), "archive has no entries");
            for (Entry entry : entries) {
                require(entry.method == 0 || entry.method == 8,
                        "unsupported compression method: " + entry.name);
                require((entry.flags & ((1 << 0) | (1 << 6) | (1 << 13))) == 0,
                        "encrypted ZIP entry rejected: " + entry.name);
            }
        }

        byte[] rewrite(Map<String, byte[]> replacements) throws IOException {
            require(!replacements.isEmpty(), "replacement set is empty");
            Set<String> folded = new HashSet<>();
            for (String existing : byName.keySet()) folded.add(existing.toLowerCase(Locale.ROOT));
            List<String> additions = new ArrayList<>();
            Map<String, Material> materials = new HashMap<>();
            for (Map.Entry<String, byte[]> replacement : replacements.entrySet()) {
                validateName(replacement.getKey());
                Entry existing = byName.get(replacement.getKey());
                if (existing == null) {
                    require(folded.add(replacement.getKey().toLowerCase(Locale.ROOT)),
                            "new entry case-fold collision: " + replacement.getKey());
                    additions.add(replacement.getKey());
                    materials.put(replacement.getKey(), Material.createNew(replacement.getKey(), replacement.getValue()));
                } else {
                    materials.put(replacement.getKey(), existing.materialForReplacement(source, replacement.getValue()));
                }
            }
            Collections.sort(additions);
            List<Entry> localOrder = new ArrayList<>(entries);
            localOrder.sort(Comparator.comparingInt(item -> item.localOffset));
            ByteArrayOutputStream output = new ByteArrayOutputStream(source.length + 128 * 1024);
            int firstLocal = localOrder.get(0).localOffset;
            output.write(source, 0, firstLocal);
            Map<String, Integer> offsets = new HashMap<>();
            for (Entry entry : localOrder) {
                offsets.put(entry.name, output.size());
                Material material = materials.get(entry.name);
                if (material == null) {
                    output.write(source, entry.localOffset, entry.rawRecordEnd - entry.localOffset);
                } else {
                    output.write(material.localRecord);
                }
            }
            for (String name : additions) {
                offsets.put(name, output.size());
                output.write(materials.get(name).localRecord);
            }
            int newCentralOffset = output.size();
            for (Entry entry : entries) {
                byte[] central = entry.rawCentralRecord(source);
                put32(central, 42, offsets.get(entry.name));
                Material material = materials.get(entry.name);
                if (material != null) material.applyToCentral(central);
                output.write(central);
            }
            for (String name : additions) {
                output.write(materials.get(name).newCentralRecord(offsets.get(name)));
            }
            int newCentralSize = output.size() - newCentralOffset;
            int newCount = entries.size() + additions.size();
            require(newCount < 0xffff, "ZIP64 entry count would be required");
            byte[] eocd = Arrays.copyOfRange(source, eocdOffset, source.length);
            put16(eocd, 8, newCount);
            put16(eocd, 10, newCount);
            put32(eocd, 12, newCentralSize);
            put32(eocd, 16, newCentralOffset);
            output.write(eocd);
            return output.toByteArray();
        }
    }

    private static final class Entry {
        final String name;
        final int flags;
        final int method;
        final int localMethod;
        final long crc;
        final int compressedSize;
        final int uncompressedSize;
        final int localOffset;
        final int dataOffset;
        final int centralOffset;
        final int centralRecordLength;
        int rawRecordEnd;

        Entry(String name, int flags, int method, int localMethod, long crc, int compressedSize,
              int uncompressedSize, int localOffset, int dataOffset, int centralOffset,
              int centralRecordLength) {
            this.name = name;
            this.flags = flags;
            this.method = method;
            this.localMethod = localMethod;
            this.crc = crc;
            this.compressedSize = compressedSize;
            this.uncompressedSize = uncompressedSize;
            this.localOffset = localOffset;
            this.dataOffset = dataOffset;
            this.centralOffset = centralOffset;
            this.centralRecordLength = centralRecordLength;
        }

        byte[] rawLocalRecord(byte[] archive) {
            return Arrays.copyOfRange(archive, localOffset, rawRecordEnd);
        }

        byte[] rawCentralRecord(byte[] archive) {
            return Arrays.copyOfRange(archive, centralOffset, centralOffset + centralRecordLength);
        }

        byte[] normalizedCentralRecord(byte[] archive) {
            byte[] result = rawCentralRecord(archive);
            put32(result, 42, 0);
            return result;
        }

        byte[] compressedPayload(byte[] archive) {
            return Arrays.copyOfRange(archive, dataOffset, dataOffset + compressedSize);
        }

        byte[] uncompressed(byte[] archive) {
            byte[] compressed = compressedPayload(archive);
            byte[] result;
            if (method == 0) {
                result = compressed;
            } else if (uncompressedSize == 0) {
                result = new byte[0];
            } else {
                try {
                    java.util.zip.Inflater inflater = new java.util.zip.Inflater(true);
                    inflater.setInput(compressed);
                    result = new byte[uncompressedSize];
                    int offset = 0;
                    while (!inflater.finished() && offset < result.length) {
                        int count = inflater.inflate(result, offset, result.length - offset);
                        require(count > 0 || inflater.finished(), "deflate stream stalled: " + name);
                        offset += count;
                    }
                    require(inflater.finished() && offset == result.length,
                            "deflate output size mismatch: " + name);
                    inflater.end();
                } catch (java.util.zip.DataFormatException failure) {
                    throw new IllegalStateException("invalid deflate stream: " + name, failure);
                }
            }
            CRC32 crc32 = new CRC32();
            crc32.update(result);
            require(crc32.getValue() == crc, "CRC mismatch: " + name);
            return result;
        }

        Material materialForReplacement(byte[] archive, byte[] payload) throws IOException {
            byte[] compressed = method == 0 ? payload : deflate(payload);
            CRC32 crc32 = new CRC32();
            crc32.update(payload);
            int localNameLength = u16(archive, localOffset + 26);
            int localExtraLength = u16(archive, localOffset + 28);
            int headerLength = 30 + localNameLength + localExtraLength;
            byte[] header = Arrays.copyOfRange(archive, localOffset, localOffset + headerLength);
            int newFlags = flags & ~(1 << 3);
            put16(header, 6, newFlags);
            put32(header, 14, crc32.getValue());
            put32(header, 18, compressed.length);
            put32(header, 22, payload.length);
            ByteArrayOutputStream local = new ByteArrayOutputStream(header.length + compressed.length);
            local.write(header);
            local.write(compressed);
            return new Material(name, newFlags, method, crc32.getValue(), compressed.length,
                    payload.length, local.toByteArray(), false);
        }
    }

    private static final class Material {
        final String name;
        final int flags;
        final int method;
        final long crc;
        final int compressedSize;
        final int uncompressedSize;
        final byte[] localRecord;
        final boolean added;

        Material(String name, int flags, int method, long crc, int compressedSize,
                 int uncompressedSize, byte[] localRecord, boolean added) {
            this.name = name;
            this.flags = flags;
            this.method = method;
            this.crc = crc;
            this.compressedSize = compressedSize;
            this.uncompressedSize = uncompressedSize;
            this.localRecord = localRecord;
            this.added = added;
        }

        static Material createNew(String name, byte[] payload) throws IOException {
            byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
            byte[] compressed = deflate(payload);
            CRC32 crc32 = new CRC32();
            crc32.update(payload);
            int flags = 0x800;
            byte[] header = new byte[30 + nameBytes.length];
            put32(header, 0, 0x04034b50L);
            put16(header, 4, 20);
            put16(header, 6, flags);
            put16(header, 8, 8);
            put32(header, 14, crc32.getValue());
            put32(header, 18, compressed.length);
            put32(header, 22, payload.length);
            put16(header, 26, nameBytes.length);
            System.arraycopy(nameBytes, 0, header, 30, nameBytes.length);
            ByteArrayOutputStream local = new ByteArrayOutputStream(header.length + compressed.length);
            local.write(header);
            local.write(compressed);
            return new Material(name, flags, 8, crc32.getValue(), compressed.length,
                    payload.length, local.toByteArray(), true);
        }

        void applyToCentral(byte[] central) {
            put16(central, 8, flags);
            put16(central, 10, method);
            put32(central, 16, crc);
            put32(central, 20, compressedSize);
            put32(central, 24, uncompressedSize);
        }

        byte[] newCentralRecord(int localOffset) {
            require(added, "central record requested for non-added material");
            byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
            byte[] central = new byte[46 + nameBytes.length];
            put32(central, 0, 0x02014b50L);
            put16(central, 4, 20);
            put16(central, 6, 20);
            put16(central, 8, flags);
            put16(central, 10, method);
            put32(central, 16, crc);
            put32(central, 20, compressedSize);
            put32(central, 24, uncompressedSize);
            put16(central, 28, nameBytes.length);
            put32(central, 42, localOffset);
            System.arraycopy(nameBytes, 0, central, 46, nameBytes.length);
            return central;
        }
    }

    private static byte[] deflate(byte[] payload) {
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        deflater.setInput(payload);
        deflater.finish();
        ByteArrayOutputStream output = new ByteArrayOutputStream(payload.length);
        byte[] buffer = new byte[8192];
        while (!deflater.finished()) {
            int count = deflater.deflate(buffer);
            require(count > 0, "deflater stalled");
            output.write(buffer, 0, count);
        }
        deflater.end();
        return output.toByteArray();
    }

    private static int findEocd(byte[] bytes) {
        int minimum = Math.max(0, bytes.length - 65_557);
        for (int offset = bytes.length - 22; offset >= minimum; offset--) {
            if (u32(bytes, offset) == 0x06054b50L
                    && offset + 22 + u16(bytes, offset + 20) == bytes.length) return offset;
        }
        throw new IllegalStateException("EOCD not found");
    }

    private static int u16(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8);
    }

    private static long u32(byte[] bytes, int offset) {
        return (u16(bytes, offset) & 0xffffL) | ((u16(bytes, offset + 2) & 0xffffL) << 16);
    }

    private static void put16(byte[] bytes, int offset, long value) {
        require(value >= 0 && value <= 0xffffL, "16-bit ZIP field overflow");
        bytes[offset] = (byte) value;
        bytes[offset + 1] = (byte) (value >>> 8);
    }

    private static void put32(byte[] bytes, int offset, long value) {
        require(value >= 0 && value <= 0xffffffffL, "32-bit ZIP field overflow");
        put16(bytes, offset, value & 0xffffL);
        put16(bytes, offset + 2, (value >>> 16) & 0xffffL);
    }

    private record ProtectedEntry(String module, String path, String expectedSha) {
        String label() { return module + ":" + path; }
    }

    private record DiffRow(String scope, String action, String entry, String beforeSha,
                           String afterSha, int beforeSize, int afterSize,
                           String beforeMethod, String afterMethod) { }

    private static final class RawStats {
        int localRecords;
        int centralRecords;

        RawStats() { }
        RawStats(int localRecords, int centralRecords) {
            this.localRecords = localRecords;
            this.centralRecords = centralRecords;
        }
        void add(RawStats other) {
            localRecords += other.localRecords;
            centralRecords += other.centralRecords;
        }
    }
}
