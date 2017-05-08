package io.primeval.codex.io.impl;

import static io.primeval.codex.io.impl.FileCompareTestUtils.compareReactiveFileReadToFile;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.CountDownLatch;

import org.assertj.core.api.Assertions;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import io.primeval.codex.io.file.ReactiveFile;
import io.primeval.codex.test.rules.WithCodex;
import io.primeval.codex.test.rules.WithCodexIO;
import reactor.core.publisher.Flux;

public class ReactiveFileReaderImplTest {

    private static ReactiveFileReaderImpl tested;

    public static WithCodex withCodex = new WithCodex();

    public static WithCodexIO withCodexIO = new WithCodexIO(withCodex);

    @ClassRule
    public static RuleChain chain = RuleChain.outerRule(withCodex).around(withCodexIO);

    @BeforeClass
    public static void setUp() {
        tested = withCodexIO.getReactiveFileReader();
    }

    @Test
    public void shouldReadFileLocked() throws Exception {
        int bufferSize = 1024;
        File file = new File(ReactiveFileReaderImplTest.class.getResource("/LICENSE").getFile());
        try (ReactiveFile reactiveFile = tested
                .readLocked(file.toPath(), ByteBuffer::allocate, bufferSize)
                .getValue()) {
            compareReactiveFileReadToFile(bufferSize, file, reactiveFile);
        }
    }

    @Test
    public void shouldReadFileUnlocked() throws Exception {
        int bufferSize = 2048;
        File file = new File(ReactiveFileReaderImplTest.class.getResource("/LICENSE").getFile());
        try (ReactiveFile reactiveFile = tested.read(file.toPath(), ByteBuffer::allocate, bufferSize)
                .getValue()) {
            compareReactiveFileReadToFile(bufferSize, file, reactiveFile);
        }
    }

    @Test
    public void shouldReadFilePart() throws Exception {
        int bufferSize = 2048;
        File file = new File(ReactiveFileReaderImplTest.class.getResource("/LICENSE").getFile());
        ReactiveFile reactiveFile = tested.read(file.toPath(), ByteBuffer::allocate, bufferSize)
                .getValue();
        Assertions.assertThat(reactiveFile.isOpen()).isTrue();
        Flux<ByteBuffer> flux = Flux.from(reactiveFile.content());
        ByteBuffer bb = flux.next().block();
        Assertions.assertThat(bb.remaining()).isEqualTo(bufferSize);
        CountDownLatch latch = new CountDownLatch(1);
        reactiveFile.close(() -> {
            latch.countDown();
        });
        latch.await();

        Assertions.assertThat(reactiveFile.isOpen()).isFalse();
        Assertions.assertThatThrownBy(() -> {
            flux.next().block();
        }).hasCauseExactlyInstanceOf(ClosedChannelException.class);

    }

    @Test
    public void shouldReadFileUnlockedOffheap() throws Exception {
        int bufferSize = 2048;
        File file = new File(ReactiveFileReaderImplTest.class.getResource("/LICENSE").getFile());
        try (ReactiveFile reactiveFile = tested
                .read(file.toPath(), ByteBuffer::allocateDirect, bufferSize)
                .getValue()) {
            compareReactiveFileReadToFile(bufferSize, file, reactiveFile);
        }
    }

}
