package ch.so.agi.gretl.language;

import java.util.*;

/** Pure graph/target rules shared by the runner and static tooling. Never retains executable objects. */
public final class PlanSemantics {
    public enum EdgeKind { DEPENDS_ON, AFTER }
    public record Node(String id,int registrationIndex) {
        public Node { Objects.requireNonNull(id);if(registrationIndex<0)throw new IllegalArgumentException("Negative registration index"); }
    }
    public record Edge(String fromId,String toId,EdgeKind kind) {
        public Edge { Objects.requireNonNull(fromId);Objects.requireNonNull(toId);Objects.requireNonNull(kind); }
    }
    public record DeclaredGraph(List<Node> nodes,List<Edge> edges) {
        public DeclaredGraph { nodes=List.copyOf(nodes);edges=List.copyOf(edges); }
    }
    public record GraphProblem(String code,String message,String taskId,List<Edge> edges) {
        public GraphProblem { edges=List.copyOf(edges); }
    }
    public static final class GraphException extends IllegalArgumentException {
        private static final long serialVersionUID=1L;
        private final transient GraphProblem problem;
        GraphException(GraphProblem problem){super(problem.message());this.problem=problem;}
        public GraphProblem problem(){return problem;}
        public String code(){return problem.code();}
    }
    public record PlanSelection(List<String> requestedTargets,List<String> orderedIds,List<Edge> activeEdges,Map<String,Set<String>> dataPredecessors) {
        public PlanSelection {
            requestedTargets=List.copyOf(requestedTargets);orderedIds=List.copyOf(orderedIds);activeEdges=List.copyOf(activeEdges);
            var copy=new LinkedHashMap<String,Set<String>>();dataPredecessors.forEach((id,ids)->copy.put(id,Collections.unmodifiableSet(new LinkedHashSet<>(ids))));dataPredecessors=Collections.unmodifiableMap(copy);
        }
    }

    public List<GraphProblem> validateDeclaredDependencies(DeclaredGraph graph){
        var nodes=index(graph);var problems=unknownLinks(graph,nodes);
        if(!problems.isEmpty())return problems;
        var data=graph.edges().stream().filter(e->e.kind()==EdgeKind.DEPENDS_ON).toList();
        return findCycle(nodes.keySet(),data).map(c->List.of(new GraphProblem("DEPENDENCY_CYCLE","Zyklus im vollstaendigen dependsOn-Graph: "+renderCycle(c),c.getFirst().fromId(),c))).orElse(List.of());
    }
    public PlanSelection plan(DeclaredGraph graph,List<String> requestedTargets,List<String> defaultTargets){
        var nodes=index(graph);var validation=validateDeclaredDependencies(graph);if(!validation.isEmpty())throw new GraphException(validation.getFirst());
        var targets=normalizeTargets(requestedTargets,defaultTargets);
        for(String target:targets)if(!nodes.containsKey(target))throw problem("UNKNOWN_TARGET","Unbekanntes Ziel: "+target,target);
        var incoming=incoming(graph);var selected=new LinkedHashSet<String>();var pending=new ArrayDeque<>(targets);
        while(!pending.isEmpty()){
            String id=pending.removeFirst();if(!selected.add(id))continue;
            for(var edge:incoming.getOrDefault(id,List.of()))if(edge.kind()==EdgeKind.DEPENDS_ON)pending.addLast(edge.fromId());
        }
        var active=graph.edges().stream().filter(e->selected.contains(e.fromId())&&selected.contains(e.toId())).toList();
        var cycle=findCycle(selected,active);if(cycle.isPresent())throw new GraphException(new GraphProblem("PLAN_CYCLE","Zyklus im ausgewaehlten Plan: "+renderCycle(cycle.get()),cycle.get().getFirst().fromId(),cycle.get()));
        var ordered=topologicalSort(selected,active,nodes);var predecessors=new LinkedHashMap<String,Set<String>>();
        for(String id:selected)predecessors.put(id,dataPredecessors(incoming,id));
        return new PlanSelection(targets,ordered,active,predecessors);
    }
    public Set<String> dataPredecessors(DeclaredGraph graph,String taskId){
        var nodes=index(graph);var problems=unknownLinks(graph,nodes);if(!problems.isEmpty())throw new GraphException(problems.getFirst());
        if(!nodes.containsKey(taskId))throw problem("UNKNOWN_TARGET","Unbekanntes Ziel: "+taskId,taskId);
        return dataPredecessors(incoming(graph),taskId);
    }
    private static LinkedHashMap<String,Node> index(DeclaredGraph graph){
        Objects.requireNonNull(graph,"graph");var result=new LinkedHashMap<String,Node>();var indices=new HashSet<Integer>();
        for(var node:graph.nodes())if(result.putIfAbsent(node.id(),node)!=null||!indices.add(node.registrationIndex()))throw new IllegalArgumentException("Duplicate graph node or registration index");
        return result;
    }
    private static List<GraphProblem> unknownLinks(DeclaredGraph graph,Map<String,Node> nodes){
        var result=new ArrayList<GraphProblem>();
        for(var edge:graph.edges())if(!nodes.containsKey(edge.fromId())||!nodes.containsKey(edge.toId()))result.add(new GraphProblem("UNKNOWN_TASK_REFERENCE","Task "+edge.toId()+" has unknown "+(edge.kind()==EdgeKind.DEPENDS_ON?"predecessor: ":"after reference: ")+edge.fromId(),edge.toId(),List.of(edge)));
        return List.copyOf(result);
    }
    private static List<String> normalizeTargets(List<String> requested,List<String> defaults){
        List<String> raw=requested==null||requested.isEmpty()?Objects.requireNonNull(defaults):requested;
        if(raw.isEmpty())throw problem("MISSING_TARGET","Keine Ziele angegeben und keine Defaults registriert",null);
        var targets=new LinkedHashSet<String>();for(String target:raw){if(target==null||target.isBlank())throw problem("INVALID_TARGET","Target must not be blank",target);targets.add(target);}return List.copyOf(targets);
    }
    private static Map<String,List<Edge>> incoming(DeclaredGraph graph){
        var result=new LinkedHashMap<String,List<Edge>>();for(var edge:graph.edges())result.computeIfAbsent(edge.toId(),id->new ArrayList<>()).add(edge);return result;
    }
    private static Set<String> dataPredecessors(Map<String,List<Edge>> incoming,String taskId){
        var result=new LinkedHashSet<String>();var pending=new ArrayDeque<String>();
        for(var edge:incoming.getOrDefault(taskId,List.of()))if(edge.kind()==EdgeKind.DEPENDS_ON)pending.addLast(edge.fromId());
        while(!pending.isEmpty()){
            String id=pending.removeFirst();if(!result.add(id))continue;
            for(var edge:incoming.getOrDefault(id,List.of()))if(edge.kind()==EdgeKind.DEPENDS_ON)pending.addLast(edge.fromId());
        }
        return Collections.unmodifiableSet(result);
    }
    private static List<String> topologicalSort(Set<String> selected,List<Edge> edges,Map<String,Node> nodes){
        var indegree=new HashMap<String,Integer>();var outgoing=new HashMap<String,List<Edge>>();
        for(String id:selected){indegree.put(id,0);outgoing.put(id,new ArrayList<>());}
        var seen=new HashSet<String>();
        for(var edge:edges){
            String key=edge.fromId()+"\u0000"+edge.toId()+"\u0000"+edge.kind();if(!seen.add(key))continue;
            outgoing.get(edge.fromId()).add(edge);indegree.merge(edge.toId(),1,Integer::sum);
        }
        var ready=new PriorityQueue<String>(Comparator.comparingInt(id->nodes.get(id).registrationIndex()));
        indegree.forEach((id,count)->{if(count==0)ready.add(id);});var ordered=new ArrayList<String>();
        while(!ready.isEmpty()){
            String id=ready.remove();ordered.add(id);
            for(var edge:outgoing.get(id))if(indegree.merge(edge.toId(),-1,Integer::sum)==0)ready.add(edge.toId());
        }
        if(ordered.size()!=selected.size())throw problem("PLAN_CYCLE","The selected graph could not be fully sorted",null);
        return List.copyOf(ordered);
    }
    /** Iterative DFS preserves the old edge walk while avoiding stack growth for long static chains. */
    private static Optional<List<Edge>> findCycle(Set<String> nodes,List<Edge> edges){
        var outgoing=new LinkedHashMap<String,List<Edge>>();for(String id:nodes)outgoing.put(id,new ArrayList<>());
        for(var edge:edges)outgoing.computeIfAbsent(edge.fromId(),id->new ArrayList<>()).add(edge);
        record Frame(String id,Iterator<Edge> remaining) { }
        var colors=new HashMap<String,Integer>();var path=new ArrayList<Edge>();var frames=new ArrayDeque<Frame>();
        for(String start:nodes){
            if(colors.getOrDefault(start,0)!=0)continue;colors.put(start,1);frames.push(new Frame(start,outgoing.getOrDefault(start,List.of()).iterator()));
            while(!frames.isEmpty()){
                var frame=frames.peek();if(!frame.remaining().hasNext()){
                    colors.put(frame.id(),2);frames.pop();if(!frames.isEmpty())path.removeLast();continue;
                }
                var edge=frame.remaining().next();int color=colors.getOrDefault(edge.toId(),0);
                if(color==1){
                    var cycle=new ArrayList<Edge>();boolean collecting=false;
                    for(var previous:path){if(previous.fromId().equals(edge.toId()))collecting=true;if(collecting)cycle.add(previous);}
                    cycle.add(edge);return Optional.of(List.copyOf(cycle));
                }
                if(color==0){path.add(edge);colors.put(edge.toId(),1);frames.push(new Frame(edge.toId(),outgoing.getOrDefault(edge.toId(),List.of()).iterator()));}
            }
        }
        return Optional.empty();
    }
    private static String renderCycle(List<Edge> cycle){
        var result=new StringBuilder();for(var edge:cycle){if(!result.isEmpty())result.append(" -> ");result.append(edge.fromId()).append(" --").append(edge.kind()).append("--> ").append(edge.toId());}return result.isEmpty()?"<unknown>":result.toString();
    }
    private static GraphException problem(String code,String message,String id){return new GraphException(new GraphProblem(code,message,id,List.of()));}
}
