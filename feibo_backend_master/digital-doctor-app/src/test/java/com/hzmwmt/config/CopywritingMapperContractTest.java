package com.hzmwmt.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopywritingMapperContractTest {

    private static final Pattern COPYWRITING_LIST_QUERY = Pattern.compile(
            "<select\\s+id=\"selectCopyWritingListByCopywritingIds\"[^>]*>(.*?)</select>",
            Pattern.DOTALL);

    @Test
    void copywritingListQueryMustSelectSubmitterUserId() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/mybatis/mapper/CopywritingMapper.xml")) {
            assertNotNull(stream, "CopywritingMapper.xml must be available on the classpath");
            String mapperXml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            Matcher matcher = COPYWRITING_LIST_QUERY.matcher(mapperXml);

            assertTrue(matcher.find(), "selectCopyWritingListByCopywritingIds query must exist");
            assertTrue(Pattern.compile("\\buser_id\\b").matcher(matcher.group(1)).find(),
                    "selectCopyWritingListByCopywritingIds must select user_id for submitter mapping");
        }
    }
}
