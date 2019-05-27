package analyzer.level1;

import soot.Local;
import soot.Unit;
import soot.Value;
import soot.jimple.InvokeExpr;
import soot.jimple.internal.JAssignStmt;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.scalar.FlowSet;
import soot.toolkits.scalar.ForwardFlowAnalysis;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DefininedByValueOfCall extends ForwardFlowAnalysis<Unit, Set<Local>> {

    public DefininedByValueOfCall(DirectedGraph<Unit> graph) {
        super(graph);
        doAnalysis();
    }

    @Override
    protected Set<Local> newInitialFlow() {
        return new HashSet<>();
    }

    @Override
    protected void merge(Set<Local> in1, Set<Local> in2, Set<Local> out) {
        out.clear();
        in1.stream().filter(in2::contains).forEach(out::add);
    }

    @Override
    protected void copy(Set<Local> in, Set<Local> out) {
        out.clear();
        out.addAll(in);
    }

    @Override
    protected void flowThrough(Set<Local> in, Unit unit, Set<Local> out) {
        copy(in, out);
        // TODO: instanceof is used for demonstration.. it is better to use a StmtSwitch for the real implementation
        if (unit instanceof JAssignStmt) {
            JAssignStmt assignStmt = (JAssignStmt) unit;
            Value target = assignStmt.getLeftOp();
            Value source = assignStmt.getRightOp();
            if (target instanceof  Local && source instanceof  Local && in.contains(source)) {
               out.add((Local) target);
            } else if (target instanceof  Local && source instanceof InvokeExpr) {
                InvokeExpr invokeExpr = (InvokeExpr) source;
                if (invokeExpr.getMethod().getName().contains("valueOf")){
                   out.add((Local)target);
                };
            }
        }
    }

}
