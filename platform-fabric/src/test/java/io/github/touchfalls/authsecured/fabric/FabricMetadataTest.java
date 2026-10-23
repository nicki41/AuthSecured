package io.github.touchfalls.authsecured.fabric;

import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class FabricMetadataTest {
    @Test
    void minecraftJavaMixinAndBytecodeTargetsAgree() throws Exception {
        String minecraft = System.getProperty("authsecured.minecraftVersion");
        int javaRelease = minecraft.startsWith("26.") ? 25 : 21;
        String metadata;
        String mixins;
        try (var mod = getClass().getClassLoader().getResourceAsStream("fabric.mod.json");
             var mixin = getClass().getClassLoader().getResourceAsStream("authsecured.mixins.json")) {
            assertNotNull(mod);
            assertNotNull(mixin);
            metadata = new String(mod.readAllBytes(), StandardCharsets.UTF_8);
            mixins = new String(mixin.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(metadata.contains("\"minecraft\": \"=" + minecraft + "\""));
        assertTrue(metadata.contains("\"java\": \">=" + javaRelease + "\""));
        assertTrue(metadata.contains("io.github.touchfalls.authsecured.fabric.AuthSecuredFabricMod"));
        assertTrue(mixins.contains("\"compatibilityLevel\": \"JAVA_" + javaRelease + "\""));
        assertTrue(mixins.contains("io.github.touchfalls.authsecured.fabric.mixin"));

        String classPath = AuthSecuredFabricMod.class.getName().replace('.', '/') + ".class";
        try (var stream = new DataInputStream(getClass().getClassLoader().getResourceAsStream(classPath))) {
            assertEquals(0xCAFEBABE, stream.readInt());
            stream.readUnsignedShort();
            assertEquals(javaRelease + 44, stream.readUnsignedShort());
        }
    }
}
