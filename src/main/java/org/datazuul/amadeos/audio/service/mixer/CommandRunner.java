package org.datazuul.amadeos.audio.service.mixer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Runs a short command and returns its output; replaced by a fake in tests. */
public interface CommandRunner {
    String run(List<String> command) throws IOException;

    /** Runs the program with a 3 s time limit; fails on a non-zero exit code. */
    static CommandRunner system() {
        return command -> {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            try {
                byte[] out = process.getInputStream().readAllBytes();
                if (!process.waitFor(3, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    throw new IOException(command.get(0) + " did not finish");
                }
                String text = new String(out, StandardCharsets.UTF_8);
                if (process.exitValue() != 0) {
                    throw new IOException(command.get(0) + " failed: " + text.strip());
                }
                return text;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted", e);
            }
        };
    }
}
