package com.kve.testsupport;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Runs Minecraft's own registry bootstrap ({@code Bootstrap#bootStrap()})
 * exactly once per test JVM, before any test gets a chance to touch a class
 * like {@code ItemStack} or {@code Items} that assumes
 * {@code BuiltInRegistries} has already been populated.
 * <p>
 * Without this, the first test anywhere in the module to reference
 * {@code ItemStack} fails with {@code IllegalArgumentException: Not
 * bootstrapped (called from registry ResourceKey[minecraft:root /
 * minecraft:root])} out of {@code Bootstrap#checkBootstrapCalled()}, and
 * every other test that also touches {@code ItemStack} afterwards then
 * fails too - not with the same error, but with
 * {@code NoClassDefFoundError}, because the JVM permanently marks a class
 * whose static initializer once threw as erroneous (JLS 12.4.2) and never
 * retries it. So this only has to run successfully once, but it has to
 * happen before that very first touch anywhere in the suite.
 * <p>
 * Auto-detected via {@code src/test/resources/META-INF/services/
 * org.junit.jupiter.api.extension.Extension} + {@code junit-platform.properties}
 * (extension autodetection) - no {@code @ExtendWith} needed on individual
 * test classes, including ones added later.
 */
public class MinecraftBootstrapExtension implements BeforeAllCallback {

    private static volatile boolean bootstrapped = false;

    @Override
    public synchronized void beforeAll(ExtensionContext context) {
        if (bootstrapped) return;
        SharedConstants.tryDetectVersion();
        try {
            Bootstrap.bootStrap();
        } catch (ExceptionInInitializerError e) {
            Bootstrap.checkBootstrapCalled(() -> "MinecraftBootstrapExtension");
        }
        bootstrapped = true;
    }
}
