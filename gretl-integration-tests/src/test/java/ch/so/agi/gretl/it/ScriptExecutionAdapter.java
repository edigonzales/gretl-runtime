package ch.so.agi.gretl.it;

import java.nio.file.Path;
import java.util.*;

/** Sources go directly to the actual product launcher; no package boundary exists here. */
final class ScriptExecutionAdapter {
    static ArtifactJobRunner.ScriptLaunch prepare(ArtifactJobRunner.Request request, Path source,
                                                  Path report, CandidateDistribution distribution) {
        return new ArtifactJobRunner.ScriptLaunch(distribution, arguments(request, source, report, distribution.launcher()));
    }
    static List<String> arguments(ArtifactJobRunner.Request request, Path source, Path report, Path launcher) {
        var args = new ArrayList<>(List.of(launcher.toString(), request.invocation().verb().name().toLowerCase(Locale.ROOT),
                request.input().resolveAgainst(source).toString()));
        appendOptions(args, request, report, false);
        return List.copyOf(args);
    }
    static void appendOptions(List<String> args, ArtifactJobRunner.Request request, Path report, boolean jar) {
        var invocation = request.invocation();
        if (jar) {
            if (invocation.verb() == ArtifactJobRunner.Verb.CHECK) throw new IllegalArgumentException("JAR has no CHECK command");
            if (invocation.verb() == ArtifactJobRunner.Verb.PLAN) args.add("--plan");
        }
        if (invocation.help()) args.add("--help");
        if (invocation.graphFormat() != null) args.addAll(List.of("--graph", invocation.graphFormat()));
        if (invocation.graphOutput() != null) args.addAll(List.of("--output", invocation.graphOutput().toString()));
        args.addAll(List.of("--config", request.directory().resolve("runtime.json").toString()));
        if (invocation.verb() == ArtifactJobRunner.Verb.RUN && !invocation.help())
            args.addAll(List.of("--report", report.toString(), "--log-format", "json"));
        for (String target : request.targets()) args.addAll(List.of("--target", target));
        request.parameters().forEach((key, value) -> args.addAll(List.of("--param", key + "=" + value)));
    }
}
