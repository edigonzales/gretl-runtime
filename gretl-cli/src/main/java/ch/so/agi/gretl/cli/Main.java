package ch.so.agi.gretl.cli;

import java.nio.file.Path;

/** Process entry point. */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        int exit = new GretlCli(System.out, System.err, System.getenv(),
                Path.of("").toAbsolutePath().normalize(),
                Main.class.getClassLoader()).run(args);
        System.exit(exit);
    }
}
