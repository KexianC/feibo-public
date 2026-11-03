package com.hzmwmt.util;

import com.hzmwmt.common.exception.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public final class FileSecurityUtils {

    private static final Pattern SAFE_FILENAME_CHARS = Pattern.compile("[^A-Za-z0-9._-]");

    private FileSecurityUtils() {
    }

    public static void validateUpload(MultipartFile file, Set<String> allowedExtensions, long maxBytes) {
        if (file == null || file.isEmpty()) {
            throw new AppException("FILE_EMPTY", "Uploaded file is empty.");
        }
        if (file.getSize() <= 0 || file.getSize() > maxBytes) {
            throw new AppException("FILE_TOO_LARGE", "Uploaded file exceeds the allowed size.", HttpStatus.PAYLOAD_TOO_LARGE);
        }
        String extension = extension(cleanOriginalFilename(file));
        if (!allowedExtensions.contains(extension)) {
            throw new AppException("NOT_SUPPORTED_FILE_TYPE", "Unsupported file type.");
        }
    }

    public static String cleanOriginalFilename(MultipartFile file) {
        String original = file == null ? null : file.getOriginalFilename();
        if (!StringUtils.hasText(original)) {
            throw new AppException("FILE_NAME_INVALID", "Uploaded file name is invalid.");
        }
        String cleaned = StringUtils.cleanPath(original).replace('\\', '/');
        if (cleaned.contains("..")) {
            throw new AppException("FILE_NAME_INVALID", "Uploaded file name is invalid.");
        }
        int slashIndex = cleaned.lastIndexOf('/');
        String fileName = slashIndex >= 0 ? cleaned.substring(slashIndex + 1) : cleaned;
        if (!StringUtils.hasText(fileName)) {
            throw new AppException("FILE_NAME_INVALID", "Uploaded file name is invalid.");
        }
        return SAFE_FILENAME_CHARS.matcher(fileName).replaceAll("_");
    }

    public static String extension(String fileName) {
        int dotIndex = fileName == null ? -1 : fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            throw new AppException("FILE_NAME_INVALID", "Uploaded file extension is missing.");
        }
        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    public static String storedFileName(String safeOriginalFilename) {
        return UUID.randomUUID() + "_" + safeOriginalFilename;
    }

    public static Path resolveUnderBase(String baseDir, String childName) {
        Path basePath = Paths.get(baseDir).toAbsolutePath().normalize();
        Path targetPath = basePath.resolve(childName).toAbsolutePath().normalize();
        assertPathInsideBase(basePath, targetPath);
        return targetPath;
    }

    public static void assertPathInsideBase(String baseDir, Path targetPath) {
        assertPathInsideBase(Paths.get(baseDir).toAbsolutePath().normalize(), targetPath.toAbsolutePath().normalize());
    }

    public static void assertPathInsideBase(Path basePath, Path targetPath) {
        if (!targetPath.startsWith(basePath)) {
            throw new AppException("FILE_PATH_INVALID", "File path is outside the storage directory.", HttpStatus.FORBIDDEN);
        }
    }
}
