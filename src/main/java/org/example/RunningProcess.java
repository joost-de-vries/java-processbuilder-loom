package org.example;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.TimeoutException;

public class RunningProcess implements AutoCloseable {

    RunningProcess(Process process, Optional<byte[]> stdin, Duration timeoutAfter) {
        this.process = process;
        this.stdin = stdin;
        this.timeoutAfter = timeoutAfter;

        this.scope = new StructuredTaskScope.ShutdownOnFailure();
    }

    public ProcessResult waitFor() throws InterruptedException, TimeoutException, ExecutionException {
        scope.fork(this::readStdin);

        var stdout = scope.fork(() -> readInputStream(new BufferedInputStream(process.getInputStream())));
        var stderr = scope.fork(() -> readInputStream(new BufferedInputStream(process.getErrorStream())));
        var exitValue = scope.fork(process::waitFor);

        scope
                .joinUntil(Instant.now().plus(timeoutAfter))
                .throwIfFailed();

        return new ProcessResult(exitValue.get(), stdout.get(), stderr.get(), process.pid());
    }

    private boolean readStdin() {
        stdin.ifPresent(bytes -> {
            try (var outputStream = new BufferedOutputStream(process.getOutputStream())) {
                outputStream.write(bytes);

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        return true;
    }

    @Override
    public void close() {
        try {
            process.destroy();
        } catch (Exception _) {
        }

        scope.close();
    }

    public Process getProcess() {
        return process;
    }

    private final Process process;
    private final Optional<byte[]> stdin;
    private final Duration timeoutAfter;
    private final StructuredTaskScope.ShutdownOnFailure scope;

    private static byte[] readInputStream(BufferedInputStream inputStream) throws IOException {
        try (inputStream) {
            return inputStream.readAllBytes();
        }
    }
}
