package io.primeval.codex.file.impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.assertj.core.api.Assertions;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import io.primeval.codex.file.ReactiveFile;
import io.primeval.codex.test.rules.WithCodex;
import io.primeval.common.bytebuffer.ByteBufferListInputStream;
import reactor.core.publisher.Flux;

public class ReactiveFileReaderImplTest {

    private static ReactiveFileReaderImpl reactiveFileReaderImpl;

    @ClassRule
    public static WithCodex withCodex = new WithCodex();

    @BeforeClass
    public static void setUp() {
        reactiveFileReaderImpl = new ReactiveFileReaderImpl();
        reactiveFileReaderImpl.setDetachedDispatcherManager(withCodex.getDetachedDispatcherManager());
        reactiveFileReaderImpl.activate();
    }

    @AfterClass
    public static void tearDown() {
        reactiveFileReaderImpl.deactivate();
    }

    @Test
    public void shouldReadFileLocked() throws Exception {
        int bufferSize = 1024;
        File file = new File(ReactiveFileReaderImplTest.class.getResource("/LICENSE").getFile());
        try (ReactiveFile reactiveFile = reactiveFileReaderImpl
                .readLocked(file.toPath(), ByteBuffer::allocate, bufferSize)
                .getValue()) {
            compareReactiveFileReadToFile(bufferSize, file, reactiveFile);
        }
    }

    @Test
    public void shouldReadFileUnlocked() throws Exception {
        int bufferSize = 2048;
        File file = new File(ReactiveFileReaderImplTest.class.getResource("/LICENSE").getFile());
        try (ReactiveFile reactiveFile = reactiveFileReaderImpl.read(file.toPath(), ByteBuffer::allocate, bufferSize)
                .getValue()) {
            compareReactiveFileReadToFile(bufferSize, file, reactiveFile);
        }
    }

    @Test
    public void shouldReadFilePart() throws Exception {
        int bufferSize = 2048;
        File file = new File(ReactiveFileReaderImplTest.class.getResource("/LICENSE").getFile());
        ReactiveFile reactiveFile = reactiveFileReaderImpl.read(file.toPath(), ByteBuffer::allocate, bufferSize)
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
        try (ReactiveFile reactiveFile = reactiveFileReaderImpl
                .read(file.toPath(), ByteBuffer::allocateDirect, bufferSize)
                .getValue()) {
            compareReactiveFileReadToFile(bufferSize, file, reactiveFile);
        }
    }

    private void compareReactiveFileReadToFile(int bufferSize, File file, ReactiveFile reactiveFile)
            throws IOException, FileNotFoundException {
        long length = reactiveFile.length();
        Assertions.assertThat(length).isEqualTo(file.length());
        List<ByteBuffer> bb = Flux.from(reactiveFile.content()).collectList().block();
        Assertions.assertThat(bb).hasSize((int) (length / bufferSize) + (length % bufferSize == 0 ? 0 : 1));
        Charset utf8Charset = Charset.forName("utf-8");
        try (BufferedReader brActual = new BufferedReader(
                new InputStreamReader(new ByteBufferListInputStream(bb), utf8Charset));
                BufferedReader brExpected = new BufferedReader(
                        new InputStreamReader(new FileInputStream(file), utf8Charset));) {
            String lineActual = null, lineExpected = null;
            while (((lineActual = brActual.readLine()) != null || lineActual == null)
                    && (lineExpected = brExpected.readLine()) != null) {
                Assertions.assertThat(lineActual).isEqualTo(lineExpected);
            }
            Assertions.assertThat(lineActual).isEqualTo(lineExpected).isNull();
        }
    }
}
