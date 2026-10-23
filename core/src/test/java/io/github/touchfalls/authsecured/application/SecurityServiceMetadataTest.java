package io.github.touchfalls.authsecured.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SecurityServiceMetadataTest {

    @Test
    void keepsJsonObjectsAndWrapsPlainText() {
        assertEquals("{}", SecurityService.normalizeMetadata(null));
        assertEquals("{}", SecurityService.normalizeMetadata("  "));
        assertEquals("{\"reason\":\"x\"}", SecurityService.normalizeMetadata("{\"reason\":\"x\"}"));
        assertEquals("{\"detail\":\"authentication deadline expired\"}",
                SecurityService.normalizeMetadata("authentication deadline expired"));
        assertEquals("{\"detail\":\"a\\\"b\\\\c\\u000ad\"}", SecurityService.normalizeMetadata("a\"b\\c\nd"));
    }
}
