package org.example;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.zip.GZIPInputStream;

public class Main {
    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException, TimeoutException {
        var toZip = "zip me";

        String[] cmd = {"gzip", "-c"};
        try (RunningProcess runningProcess = ProcessRunner.startProcess(cmd, toZip.getBytes(StandardCharsets.UTF_8), Duration.ofSeconds(5))) {

            System.out.println("started process with pid: " + runningProcess.getProcess().pid());
            var result = runningProcess.waitFor();
            System.out.println("exit value: " + result.exitValue());
            var zipped = result.stdout();

            var unzippedAgain = unzip(zipped);

            System.out.println("as expected: "+ unzippedAgain.equals(toZip));
        }
    }

     static String unzip(byte[] zipped) throws IOException {
        var bytes = new GZIPInputStream(new ByteArrayInputStream(zipped)).readAllBytes();
        return new String(bytes, StandardCharsets.UTF_8);
    }
}