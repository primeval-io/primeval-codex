package io.primeval.codex.io.impl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.util.promise.Deferred;
import org.osgi.util.promise.Promise;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.primeval.codex.io.IODispatcher;
import io.primeval.codex.io.file.ReactiveFile;
import io.primeval.codex.io.file.ReactiveFileReader;
import io.primeval.codex.promise.PromiseHelper;
import io.primeval.codex.util.Procedure;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoProcessor;

@Component
public final class ReactiveFileReaderImpl implements ReactiveFileReader {
    static final Logger LOGGER = LoggerFactory.getLogger(ReactiveFileReaderImpl.class);

    private IODispatcher ioDispatcher;

    @Override
    public Promise<ReactiveFile> read(Path path, IntFunction<ByteBuffer> byteBufferFactory, int bufferSize) {
        return ioDispatcher.dispatch(() -> {
            return getReactiveFileBlocking(ioDispatcher, path, byteBufferFactory, bufferSize);
        });
    }

    static ReactiveFile getReactiveFileBlocking(IODispatcher ioDispatcher, Path path,
            IntFunction<ByteBuffer> byteBufferFactory, int bufferSize)
            throws IOException {
        AsynchronousFileChannel fileChannel = AsynchronousFileChannel.open(path, StandardOpenOption.READ);
        long fileLength = fileChannel.size();

        return createReactiveFile(ioDispatcher, path, byteBufferFactory, bufferSize, fileChannel, fileLength,
                Procedure.NOOP);
    }

    @Override
    public Promise<ReactiveFile> readLocked(Path path, IntFunction<ByteBuffer> byteBufferFactory, int bufferSize) {
        return ioDispatcher.dispatch(() -> {
            AsynchronousFileChannel fileChannel = AsynchronousFileChannel.open(path, StandardOpenOption.READ);
            long fileLength = fileChannel.size();

            return lock(fileChannel).map(lock -> {
                return createReactiveFile(ioDispatcher, path, byteBufferFactory, bufferSize, fileChannel, fileLength,
                        lock::release);

            });

        }).flatMap(x -> x);
    }

    private Promise<FileLock> lock(AsynchronousFileChannel fileChannel) {
        Deferred<FileLock> deferred = new Deferred<>();
        fileChannel.lock(0, Long.MAX_VALUE, true, null, new CompletionHandler<FileLock, Void>() {

            @Override
            public void completed(FileLock result, Void attachment) {
                try {
                    deferred.resolve(result);
                } catch (Throwable e) {
                    deferred.fail(e);
                }
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                deferred.fail(exc);
            }
        });
        return deferred.getPromise();
    }

    private static ReactiveFile createReactiveFile(IODispatcher ioDispatcher, Path path,
            IntFunction<ByteBuffer> byteBufferFactory, int bufferSize,
            AsynchronousFileChannel fileChannel, long fileLength, Procedure cleanUp) {

        AsynchronousFileHandler fileHandler = new AsynchronousFileHandler(ioDispatcher, fileChannel,
                byteBufferFactory,
                fileLength, bufferSize);

        Consumer<Procedure> closeHandler = proc -> fileHandler.closeFile(path, cleanUp, proc);

        Supplier<Publisher<ByteBuffer>> contentSupplier = () -> {
            AsynchronousStatefulReader reader = fileHandler.newReader();
            return Mono.fromSupplier(reader::readFromChannel).repeat(reader::hasMore)
                    .concatMap(Function.identity(), 1);
        };

        return new ReactiveFileImpl(path, fileLength, contentSupplier, fileHandler::isOpen, closeHandler);
    }

    @Reference
    public void setIODispatcher(IODispatcher ioDispatcher) {
        this.ioDispatcher = ioDispatcher;

    }
}

final class AsynchronousFileHandler {

    public final IntFunction<ByteBuffer> byteBufferFactory;
    public final long fileLength;
    public final int buffersize;

    private final IODispatcher dispatcher;
    private final AsynchronousFileChannel channel;

    private volatile boolean closed = false;

    public AsynchronousFileHandler(IODispatcher dispatcher, AsynchronousFileChannel channel,
            IntFunction<ByteBuffer> byteBufferFactory, long fileLength, int buffersize) {
        this.dispatcher = dispatcher;
        this.channel = channel;
        this.byteBufferFactory = byteBufferFactory;
        this.fileLength = fileLength;
        this.buffersize = buffersize;
    }

    boolean isOpen() {
        return !closed;
    }

    void closeFile(Path path, Procedure cleanUp) {
        close(path, cleanUp);
    }

    void closeFile(Path path, Procedure cleanUp, Procedure onClose) {
        close(path, cleanUp).onResolve(Procedure.toRunnable(onClose));
    }

    private Promise<Void> close(Path path, Procedure cleanUp) {
        if (closed) {
            return PromiseHelper.VOID;
        }
        return dispatcher.dispatch(() -> {
            if (!closed) {
                closed = true;
                try {
                    cleanUp.call();
                } catch (Throwable e) {
                    ReactiveFileReaderImpl.LOGGER.error("An error occured during cleanup of file {}", path, e);
                }
                try {
                    channel.close();
                } catch (Throwable e) {
                    ReactiveFileReaderImpl.LOGGER.error("An error occured when closing the fileChannel to file {}",
                            path,
                            e);
                }
            }
        });
    }

    public AsynchronousFileChannel getChannel() {
        return channel;
    }

    public AsynchronousStatefulReader newReader() {
        return new AsynchronousStatefulReader(this);
    }

}

final class AsynchronousStatefulReader {

    private final AsynchronousFileHandler fileHandler;

    volatile long position = 0L;
    private static final AtomicLongFieldUpdater<AsynchronousStatefulReader> POSITION_UPDATER = AtomicLongFieldUpdater
            .newUpdater(AsynchronousStatefulReader.class, "position");

    private volatile boolean hasMore = true;

    public AsynchronousStatefulReader(AsynchronousFileHandler fileHandler) {
        this.fileHandler = fileHandler;

    }

    Mono<ByteBuffer> readFromChannel() {

        long requestedPosition = position;

        int buffersize = fileHandler.buffersize;
        long nextPosition = POSITION_UPDATER.addAndGet(this, buffersize);
        hasMore = nextPosition < fileHandler.fileLength;

        MonoProcessor<ByteBuffer> monoP = MonoProcessor.create();

        ByteBuffer byteBuffer = fileHandler.byteBufferFactory.apply(buffersize);

        CompletionHandler<Integer, MonoProcessor<ByteBuffer>> completionHandler = new CompletionHandler<Integer, MonoProcessor<ByteBuffer>>() {

            @Override
            public void completed(Integer result, MonoProcessor<ByteBuffer> processor) {
                boolean hasRemaining = byteBuffer.hasRemaining();
                byteBuffer.position(0);

                int res = result;
                if (!hasRemaining) {
                    processor.onNext(byteBuffer);
                } else {
                    byteBuffer.limit(res);
                    processor.onNext(byteBuffer.slice());
                }
            }

            @Override
            public void failed(Throwable exc, MonoProcessor<ByteBuffer> processor) {
                processor.onError(exc);
            }
        };
        fileHandler.getChannel().read(byteBuffer, requestedPosition, monoP,
                completionHandler);
        return monoP;
    }

    public boolean hasMore() {
        return fileHandler.isOpen() && hasMore;
    }

}