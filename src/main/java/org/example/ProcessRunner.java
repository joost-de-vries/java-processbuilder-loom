package org.example;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

public class ProcessRunner {
    public static RunningProcess startProcess(String[] cmd, byte[] stdin, Duration timeoutAfter) throws IOException {
        return startProcessInternal(cmd, Optional.of(stdin), timeoutAfter);
    }

    static RunningProcess startProcess(String[] cmd, Duration timeoutAfter) throws IOException {
        return startProcessInternal(cmd, Optional.empty(), timeoutAfter);
    }

    private static RunningProcess startProcessInternal(String[] cmd, Optional<byte[]> stdin, Duration timeoutAfter) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder(cmd);

        var process = processBuilder.start();

        return new RunningProcess(process, stdin, timeoutAfter);
    }
}
