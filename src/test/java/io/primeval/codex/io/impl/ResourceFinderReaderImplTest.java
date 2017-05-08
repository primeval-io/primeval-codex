package io.primeval.codex.io.impl;

import static io.primeval.codex.io.impl.FileCompareTestUtils.compareReactiveFileReadToFile;

import java.io.File;
import java.nio.ByteBuffer;

import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import io.primeval.codex.io.file.ReactiveFile;
import io.primeval.codex.test.rules.WithCodex;
import io.primeval.codex.test.rules.WithCodexIO;

public class ResourceFinderReaderImplTest {

    private static ResourceFinderReaderImpl tested;

    public static WithCodex withCodex = new WithCodex();

    public static WithCodexIO withCodexIO = new WithCodexIO(withCodex);

    @ClassRule
    public static RuleChain chain = RuleChain.outerRule(withCodex).around(withCodexIO);

    @BeforeClass
    public static void setUp() {
        tested = withCodexIO.getResourceFinder();
    }

    @Test
    public void shouldReadResource() throws Exception {
        int bufferSize = 2048;
        File file = new File(ReactiveFileReaderImplTest.class.getResource("/LICENSE").getFile());

        try (ReactiveFile reactiveFile = tested
                .readResource(ResourceFinderReaderImplTest.class, "LICENSE", ByteBuffer::allocate, bufferSize)
                .getValue()) {
            compareReactiveFileReadToFile(bufferSize, file, reactiveFile);
        }
    }
}
