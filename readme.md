
# java ProcessBuilder and Loom structured concurrency

A simple utility method to run a process from Java using Loom structured concurrency.

The `ProcessBuilder` api requires us to read the standard output and standard error in separate threads.
Also we need to destroy the process after we are done with it. Also for error cases.
Running a process is unpredictable; we need to specify a timeout.
We want the process id before we start waiting for the process to finish. So we can test error scenario's where the process is killed by the OS or Kuberneter.

Loom structured concurrency can help us with this. The blocking calls can be handled with lightweight Loom threads. And the structured concurrency helps us make sure to clean up resources.

```java
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
```
Our `startProcess` method returns a `RunningProcess` object that implements `AutoCloseable`.
It combines the `java.lang.Process` with the `StructuredTaskScope` that captures the threads that read the standard output and standard error.
By using the `try-with-resources` statement we make sure that the process is destroyed and the threads are finished or interrupted.

If we call `startProcess` without a try-with-resources statement our IDE suggests introducing a try-with-resources statement.

```java
    public ProcessResult waitFor() throws InterruptedException, TimeoutException, ExecutionException {
        scope.fork(this::readStdin);

        var stdout = scope.fork(() -> readInputStream(new BufferedInputStream(process.getInputStream())));
        var stderr = scope.fork(() -> readInputStream(new BufferedInputStream(process.getErrorStream())));
        var exitValue = scope.fork(process::waitFor);

        scope
                .joinUntil(Instant.now().plus(timeoutAfter)) // await all three using a timeout
                .throwIfFailed(); // if any of the three failed, throw an exception

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

```
