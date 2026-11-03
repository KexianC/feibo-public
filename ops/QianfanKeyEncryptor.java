import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

/** Produces AIModelApiKeyCipher-compatible ciphertext without printing secrets. */
public final class QianfanKeyEncryptor {
    private static final SecureRandom RANDOM = new SecureRandom();

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            throw new IllegalArgumentException(
                    "usage: QianfanKeyEncryptor <encryption-secret-file> <api-key-file> <output-file> <model-id>...");
        }
        String encryptionSecret = Files.readString(Path.of(args[0]), StandardCharsets.UTF_8).trim();
        List<String> keyLines = Files.readAllLines(Path.of(args[1]), StandardCharsets.UTF_8).stream()
                .filter(line -> line.startsWith("QIANFAN_API_KEY="))
                .toList();
        if (keyLines.size() != 1) {
            throw new IllegalArgumentException("invalid Qianfan key file");
        }
        String apiKey = keyLines.get(0).substring("QIANFAN_API_KEY=".length()).trim();
        byte[] keyBytes = Base64.getDecoder().decode(encryptionSecret);
        if (keyBytes.length != 32 || !apiKey.startsWith("bce-v3/") || apiKey.length() < 40) {
            throw new IllegalArgumentException("invalid encryption or provider key material");
        }
        SecretKeySpec encryptionKey = new SecretKeySpec(keyBytes, "AES");
        StringBuilder output = new StringBuilder();
        for (int index = 3; index < args.length; index++) {
            long modelId = Long.parseLong(args[index]);
            output.append(modelId)
                    .append('\t')
                    .append(encrypt(encryptionKey, modelId, apiKey))
                    .append('\n');
        }
        Files.writeString(
                Path.of(args[2]),
                output.toString(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
    }

    private static String encrypt(SecretKeySpec key, long modelId, String apiKey) throws Exception {
        byte[] iv = new byte[12];
        RANDOM.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        cipher.updateAAD(("ai-model-key:v1:" + modelId).getBytes(StandardCharsets.UTF_8));
        byte[] encrypted = cipher.doFinal(apiKey.getBytes(StandardCharsets.UTF_8));
        byte[] envelope = ByteBuffer.allocate(iv.length + encrypted.length)
                .put(iv)
                .put(encrypted)
                .array();
        return "v1:" + Base64.getEncoder().encodeToString(envelope);
    }
}
