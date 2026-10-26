package ch.so.agi.gretl.it;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.jar.*;
import javax.tools.ToolProvider;

/** Newly compiled external provider and transitive library in a controlled Maven file repository. */
final class ExternalPackageFixture {
    final Path root, repository, provider, helper;
    final String group = "fixture.g" + UUID.randomUUID().toString().replace("-", "");
    final String nonce = UUID.randomUUID().toString();
    ExternalPackageFixture(Path root) throws Exception {
        this.root = root; repository = Files.createDirectories(root.resolve("controlled repository"));
        Path helperClasses = compile("external/dependency/ProbeHelper.java", "package external.dependency; public final class ProbeHelper { public static String marker() { return \"" + nonce + "\"; } public static String stamp(String self) { return self + \"-" + nonce + "\"; } }", "");
        helper = publish("helper", helperClasses, Map.of("META-INF/groovy/org.codehaus.groovy.runtime.ExtensionModule",
                "moduleName=external-fixture\nmoduleVersion=1.0\nextensionClasses=external.dependency.ProbeHelper\n"), "");
        Path api = Path.of("../gretl-api/target/gretl-api-0.1.0-SNAPSHOT.jar").toAbsolutePath().normalize();
        assertTrue(Files.isRegularFile(api));
        String source = Files.readString(Path.of("../examples/file-size-extension/src/main/java/example/ExampleProvider.java"))
                .replace("package example;", "package external.probe;")
                .replace("return new Result(Files.size(request.input()));", "if (!external.dependency.ProbeHelper.marker().equals(\"" + nonce + "\")) throw new IllegalStateException(\"wrong external build\"); return new Result(Files.size(request.input()));");
        Path classes = compile("external/probe/ExampleProvider.java", source, api + java.io.File.pathSeparator + helper);
        provider = publish("external-file-size", classes,
                Map.of("META-INF/services/ch.so.agi.gretl.runtime.api.OperationProvider", "external.probe.ExampleProvider\n"),
                "<dependency><groupId>" + group + "</groupId><artifactId>helper</artifactId><version>1.0</version></dependency>"
                + "<dependency><groupId>ch.so.agi.gretl</groupId><artifactId>gretl-api</artifactId><version>0.1.0-SNAPSHOT</version><scope>provided</scope></dependency>");
    }
    Path loggingProvider() throws Exception {
        Path runtime = Path.of("../gretl-dist/target/dist/lib/runtime").toAbsolutePath().normalize();
        Path api = Path.of("../gretl-api/target/gretl-api-0.1.0-SNAPSHOT.jar").toAbsolutePath().normalize();
        String classpath;
        try (var files = Files.list(runtime)) {
            classpath = files.filter(p -> p.toString().endsWith(".jar")).map(Path::toString)
                    .collect(java.util.stream.Collectors.joining(java.io.File.pathSeparator));
        }
        Path classes = compile("external/logging/ScopeProvider.java",
                Files.readString(Path.of("src/test/resources/logging/ScopeProvider.java")), api + java.io.File.pathSeparator + classpath);
        return publish("logging-provider", classes,
                Map.of("META-INF/services/ch.so.agi.gretl.runtime.api.OperationProvider", "external.logging.ScopeProvider\n"),
                "<dependency><groupId>ch.so.agi.gretl</groupId><artifactId>gretl-api</artifactId><version>0.1.0-SNAPSHOT</version><scope>provided</scope></dependency>"
                + "<dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>2.0.20</version><scope>provided</scope></dependency>"
                + "<dependency><groupId>ch.qos.logback</groupId><artifactId>logback-classic</artifactId><version>1.6.5</version><scope>provided</scope></dependency>");
    }
    String coordinate() { return group + ":external-file-size:1.0"; }
    String helperCoordinate() { return group + ":helper:1.0"; }
    Path pollution() throws Exception {
        return publish("unrelated", compile("external/unrelated/NotSelected.java",
                "package external.unrelated; public final class NotSelected { }", ""), Map.of(), "");
    }
    private Path compile(String name, String code, String classpath) throws Exception {
        Path source = root.resolve("java-" + name); Files.createDirectories(source.getParent()); Files.writeString(source, code);
        Path classes = Files.createDirectories(root.resolve("classes-" + UUID.randomUUID()));
        var args = new ArrayList<>(List.of("--release", "25", "-encoding", "UTF-8", "-d", classes.toString()));
        if (!classpath.isEmpty()) args.addAll(List.of("-cp", classpath));
        args.add(source.toString());
        try (var log = Files.newOutputStream(root.resolve("javac-" + source.getFileName() + ".log"))) {
            assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, log, log, args.toArray(String[]::new)));
        }
        return classes;
    }
    Path publish(String artifact, Path classes, Map<String, String> resources, String dependencies) throws Exception {
        Path directory = Files.createDirectories(repository.resolve(group.replace('.', '/')).resolve(artifact).resolve("1.0"));
        String pom = "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"><modelVersion>4.0.0</modelVersion><groupId>" + group
                + "</groupId><artifactId>" + artifact + "</artifactId><version>1.0</version><dependencies>" + dependencies + "</dependencies></project>";
        Files.writeString(directory.resolve(artifact + "-1.0.pom"), pom);
        Path file = directory.resolve(artifact + "-1.0.jar");
        try (var jar = new JarOutputStream(Files.newOutputStream(file))) {
            try (var files = Files.walk(classes)) {
                for (Path path : files.filter(Files::isRegularFile).sorted().toList()) put(jar, classes.relativize(path).toString().replace('\\', '/'), Files.readAllBytes(path));
            }
            for (var entry : resources.entrySet()) put(jar, entry.getKey(), entry.getValue().getBytes(StandardCharsets.UTF_8));
            String metadata = "META-INF/maven/" + group + "/" + artifact + "/";
            put(jar, metadata + "pom.xml", pom.getBytes(StandardCharsets.UTF_8));
            put(jar, metadata + "pom.properties", ("groupId=" + group + "\nartifactId=" + artifact + "\nversion=1.0\n").getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }
    private static void put(JarOutputStream jar, String name, byte[] bytes) throws Exception {
        var entry = new JarEntry(name); entry.setTime(0); jar.putNextEntry(entry); jar.write(bytes); jar.closeEntry();
    }
    record Command(int code, String out, String err) { }
    Command pack(Path descriptor, Path output, String label, List<Path> pollution, boolean offline) throws Exception {
        Path distribution = Path.of("../gretl-dist/target/dist").toAbsolutePath().normalize();
        String classpath = List.of(distribution.resolve("lib/runtime/*"), distribution.resolve("lib/tasks/*"), distribution.resolve("lib/extensions/*"))
                .stream().map(Path::toString).collect(java.util.stream.Collectors.joining(java.io.File.pathSeparator));
        for (Path extra : pollution) classpath += java.io.File.pathSeparator + extra;
        var command = List.of(Path.of(System.getProperty("java.home"), "bin/java").toString(),
                "-Dexternal.marker=" + root.resolve("must-not-execute"),
                "-Dgretl.packager.catalog=" + distribution.resolve("package-catalog.json"),
                "-Dgretl.packager.maven=" + Path.of("../mvnw").toAbsolutePath().normalize(),
                "-Dgretl.packager.repositories=" + repository.toUri(), "-Dgretl.packager.offline=" + offline,
                "-cp", classpath, "ch.so.agi.gretl.cli.Main", "package", descriptor.toString(), "--output", output.toString());
        Files.writeString(root.resolve(label + "-command.json"), ch.so.agi.gretl.runtime.logging.Json.write(command));
        Process process = ArtifactJobRunner.process(command, root, Map.of(), label).start();
        try { assertTrue(process.waitFor(240, TimeUnit.SECONDS), "packager timed out"); }
        finally { if (process.isAlive()) ArtifactJobRunner.stop(process); }
        return new Command(process.exitValue(), Files.readString(root.resolve(label + ".stdout.log")), Files.readString(root.resolve(label + ".stderr.log")));
    }
}
