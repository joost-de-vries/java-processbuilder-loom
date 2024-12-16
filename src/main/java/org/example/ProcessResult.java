package org.example;

public record ProcessResult(int exitValue, byte[] stdout, byte[] stderr, long processId) {
    public ProcessResult {
        if (stdout == null) {
            throw new IllegalArgumentException("stdout must not be null");
        }
        if (stderr == null) {
            throw new IllegalArgumentException("stderr must not be null");
        }
    }
}
