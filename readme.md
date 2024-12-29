
# java ProcessBuilder and Loom structured concurrency

A simple utility method to run a process from Java using Loom structured concurrency. 
Using some newer Java features.

Our requirements:
- The `java.lang.ProcessBuilder` api requires us to read the standard output and standard error of the process in separate threads.  
- We need to destroy the process after we are done with it. For error cases as well.  
- Running a process is unpredictable; we need to specify a timeout. Because a hanging process is a resource leak.
- We want the `process id` before we start waiting for the process to finish. So we can test error scenarios where the process is killed by the OS or by Kubernetes.

Loom structured concurrency can help us with this. The blocking calls can be handled with lightweight Loom threads. And the structured concurrency helps us with error handling and making sure to clean up resources.

```java
    var toZip = "zip me";

    String[] cmd = {"gzip", "-c"};
    try (RunningProcess runningProcess = startProcess(cmd, toZip.getBytes(StandardCharsets.UTF_8), Duration.ofSeconds(5))) {

        System.out.println("started process with pid: " + runningProcess.getProcess().pid());
        var result = runningProcess.waitFor();
        System.out.println("exit value: " + result.exitValue());
        var zipped = result.stdout();

        var unzippedAgain = unzip(zipped);

        System.out.println("as expected: "+ unzippedAgain.equals(toZip));
    }
```
Our `startProcess` method returns our `RunningProcess` object that implements `AutoCloseable`. And thus we support `try-with-resources`.
```java
    private static RunningProcess startProcessInternal(String[] cmd, Optional<byte[]> stdin, Duration timeoutAfter) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder(cmd);
    
        var process = processBuilder.start();
    
        return new RunningProcess(process, stdin, timeoutAfter);
    }
```
It combines the resources we need to cleanup: the `java.lang.Process` plus the `java.util.concurrent.StructuredTaskScope` that captures the threads that read standard output and standard error.
By using `try-with-resources` we make sure that both the process is always destroyed and the threads are always finished or interrupted.

Btw If we call `startProcess` without a `try-with-resources` statement our IDE suggests introducing it so that makes the method almost self documenting.

Our `RunningProcess.waitFor` method:
```java
    public ProcessResult waitFor() throws InterruptedException, TimeoutException, ExecutionException {
        scope.fork(this::readStdin);
    
        var stdout = scope.fork(() -> readInputStream(new BufferedInputStream(process.getInputStream())));
        var stderr = scope.fork(() -> readInputStream(new BufferedInputStream(process.getErrorStream())));
        var exitValue = scope.fork(process::waitFor);
    
        scope
                .joinUntil(Instant.now().plus(timeoutAfter)) // await all four using a timeout
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
We fork lightweight threads to write stdin, read stdout, read stderr and wait for the process to finish. And gather them in a `StructuredTaskScope.ShutdownOnFailure` scope.  

Alternatives:  
We could follow the lead of `java.lang.Process.onExit` and use `CompletableFuture`s to read the input streams. For instance using `CompletableFuture.supplyAsync`. That would run the task on the `ForkJoinPool.commonPool()`. But then we need to take special care to mark the task as blocking. Otherwise our pool will quickly run out. For instance using `ForkJoinPool.managedBlock`. It's doable but involves more code and joining the 4 `CompletableFuture`s is not that straightforward in Java. Java not having something like a `do notation` (for comprehension ...) to easily combine futures.  
We could create a `Runnable` class to read from input stream. But then the threads will have to report back errors to the main thread. 


Java has improved a lot in recent years!
- Loom structured concurrency and lightweight threads.
- Records and sealed interfaces and pattern matching for data oriented programming.
- try-with-resources for AutoCloseable resources.
- `var` for local variables.