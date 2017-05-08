package io.primeval.codex.io.impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.List;

import org.assertj.core.api.Assertions;

import io.primeval.codex.io.file.ReactiveFile;
import io.primeval.common.bytebuffer.ByteBufferListInputStream;
import reactor.core.publisher.Flux;

public class FileCompareTestUtils {

    static void compareReactiveFileReadToFile(int bufferSize, File file, ReactiveFile reactiveFile)
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
