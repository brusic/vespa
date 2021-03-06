// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.searchdefinition.document.Attribute;
import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.DoubleValue;
import com.yahoo.searchlib.rankingexpression.evaluation.MapContext;
import com.yahoo.searchlib.rankingexpression.evaluation.StringValue;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.FunctionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.searchlib.rankingexpression.rule.TensorFunctionNode;
import com.yahoo.searchlib.rankingexpression.transform.ExpressionTransformer;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.functions.Reduce;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Transforms and simplifies tensor expressions.
 *
 * Currently transforms min(tensor,dim) and max(tensor,dim) to
 * reduce(tensor,min/max,dim). This is necessary as the backend does
 * not recognize these forms of min and max.
 *
 * @author lesters
 */
public class TensorTransformer extends ExpressionTransformer {

    private Search search;
    private RankProfile rankprofile;
    private Map<String, RankProfile.Macro> macros;

    public TensorTransformer(RankProfile rankprofile) {
        this.rankprofile = rankprofile;
        this.search = rankprofile.getSearch();
        this.macros = rankprofile.getMacros();
    }

    @Override
    public ExpressionNode transform(ExpressionNode node) {
        if (node instanceof CompositeNode) {
            node = transformChildren((CompositeNode) node);
        }
        if (node instanceof FunctionNode) {
            node = transformFunctionNode((FunctionNode) node);
        }
        return node;
    }

    private ExpressionNode transformFunctionNode(FunctionNode node) {
        switch (node.getFunction()) {
            case min:
            case max:
                return transformMaxAndMinFunctionNode(node);
        }
        return node;
    }

    /**
     * Transforms max and min functions if it can be proven that the first
     * argument resolves to a tensor and the second argument is a valid
     * dimension in the tensor. If these do not hold, the node will not
     * be transformed.
     *
     * The test for whether or not the first argument resolves to a tensor
     * is to evaluate that expression. All values used in the expression
     * is bound to a context with dummy values with enough information to
     * deduce tensor types.
     *
     * There is currently no guarantee that all cases will be found. For
     * instance, if-statements are problematic.
     */
    private ExpressionNode transformMaxAndMinFunctionNode(FunctionNode node) {
        if (node.children().size() != 2) {
            return node;
        }
        ExpressionNode arg1 = node.children().get(0);
        Optional<String> dimension = dimensionName(node.children().get(1));
        if (dimension.isPresent()) {
            try {
                Context context = buildContext(arg1);
                Value value = arg1.evaluate(context);
                if (isTensorWithDimension(value, dimension.get())) {
                    return replaceMaxAndMinFunction(node);
                }
            } catch (IllegalArgumentException e) {
                // Thrown from evaluate if some variables are not bound, for
                // instance for a backend rank feature. Means we don't have
                // enough information to replace expression.
            }
        }
        return node;
    }

    private Optional<String> dimensionName(ExpressionNode arg) {
        if (arg instanceof ReferenceNode && ((ReferenceNode)arg).children().size() == 0) {
            return Optional.of(((ReferenceNode) arg).getName());
        }
        return Optional.empty();
    }

    private boolean isTensorWithDimension(Value value, String dimension) {
        if (value instanceof TensorValue) {
            Tensor tensor = ((TensorValue) value).asTensor();
            TensorType type = tensor.type();
            return type.dimensionNames().contains(dimension);
        }
        return false;
    }

    private ExpressionNode replaceMaxAndMinFunction(FunctionNode node) {
        ExpressionNode arg1 = node.children().get(0);
        ExpressionNode arg2 = node.children().get(1);
        
        TensorFunctionNode.TensorFunctionExpressionNode expression = TensorFunctionNode.wrapArgument(arg1);
        Reduce.Aggregator aggregator = Reduce.Aggregator.valueOf(node.getFunction().name());
        String dimension = ((ReferenceNode) arg2).getName();

        return new TensorFunctionNode(new Reduce(expression, aggregator, dimension));
    }

    /**
     * Creates an evaluation context by iterating through the expression tree, and
     * adding dummy values with correct types to the context.
     */
    private Context buildContext(ExpressionNode node) {
        Context context = new MapContext();
        addRoot(node, context);
        return context;
    }

    private Value emptyStringValue() {
        return new StringValue("");
    }

    private Value emptyDoubleValue() {
        return new DoubleValue(0.0);
    }

    private Value emptyTensorValue(TensorType type) {
        Tensor empty = Tensor.Builder.of(type).build();
        return new TensorValue(empty);
    }

    private void addRoot(ExpressionNode node, Context context) {
        addChildren(node, context);
        if (node instanceof ReferenceNode) {
            ReferenceNode referenceNode = (ReferenceNode) node;
            addIfAttribute(referenceNode, context);
            addIfConstant(referenceNode, context);
            addIfQuery(referenceNode, context);
            addIfTensorFrom(referenceNode, context);
            addIfMacro(referenceNode, context);
        }
    }

    private void addChildren(ExpressionNode node, Context context) {
        if (node instanceof CompositeNode) {
            List<ExpressionNode> children = ((CompositeNode) node).children();
            for (ExpressionNode child : children) {
                addRoot(child, context);
            }
        }
    }

    private void addIfAttribute(ReferenceNode node, Context context) {
        if (!node.getName().equals("attribute")) {
            return;
        }
        if (node.children().size() == 0) {
            return;
        }
        String attribute = node.children().get(0).toString();
        Attribute a = search.getAttribute(attribute);
        if (a == null) {
            return;
        }
        Value v;
        if (a.getType() == Attribute.Type.STRING) {
            v = emptyStringValue();
        } else if (a.getType() == Attribute.Type.TENSOR) {
            v = emptyTensorValue(a.tensorType().orElseThrow(RuntimeException::new));
        } else {
            v = emptyDoubleValue();
        }
        context.put(node.toString(), v);
    }

    private void addIfConstant(ReferenceNode node, Context context) {
        if (!node.getName().equals(ConstantTensorTransformer.CONSTANT)) {
            return;
        }
        if (node.children().size() != 1) {
            return;
        }
        ExpressionNode child = node.children().get(0);
        while (child instanceof CompositeNode && ((CompositeNode) child).children().size() > 0) {
            child = ((CompositeNode) child).children().get(0);
        }
        String name = child.toString();
        addIfConstantInRankProfile(name, node, context);
        addIfConstantInRankingConstants(name, node, context);
    }

    private void addIfConstantInRankProfile(String name, ReferenceNode node, Context context) {
        if (rankprofile.getConstants().containsKey(name)) {
            context.put(node.toString(), rankprofile.getConstants().get(name));
        }
    }

    private void addIfConstantInRankingConstants(String name, ReferenceNode node, Context context) {
        for (RankingConstant rankingConstant : search.getRankingConstants()) {
            if (rankingConstant.getName().equals(name)) {
                context.put(node.toString(), emptyTensorValue(rankingConstant.getTensorType()));
            }
        }
    }

    private void addIfQuery(ReferenceNode node, Context context) {
        if (!node.getName().equals("query")) {
            return;
        }
        if (node.children().size() != 1) {
            return;
        }
        String name = node.children().get(0).toString();
        if (rankprofile.getQueryFeatureTypes().containsKey(name)) {
            String type = rankprofile.getQueryFeatureTypes().get(name);
            Value v;
            if (type.contains("tensor")) {
                v = emptyTensorValue(TensorType.fromSpec(type));
            } else if (type.equalsIgnoreCase("string")) {
                v = emptyStringValue();
            } else {
                v = emptyDoubleValue();
            }
            context.put(node.toString(), v);
        }
    }

    private void addIfTensorFrom(ReferenceNode node, Context context) {
        if (!node.getName().startsWith("tensorFrom")) {
            return;
        }
        if (node.children().size() < 1 || node.children().size() > 2) {
            return;
        }
        ExpressionNode source = node.children().get(0);
        if (source instanceof CompositeNode && ((CompositeNode) source).children().size() > 0) {
            source = ((CompositeNode) source).children().get(0);
        }
        String dimension = source.toString();
        if (node.children().size() == 2) {
            dimension = node.children().get(1).toString();
        }
        TensorType type = (new TensorType.Builder()).mapped(dimension).build();
        context.put(node.toString(), emptyTensorValue(type));
    }

    private void addIfMacro(ReferenceNode node, Context context) {
        RankProfile.Macro macro = macros.get(node.getName());
        if (macro == null) {
            return;
        }
        ExpressionNode root = macro.getRankingExpression().getRoot();
        Context macroContext = buildContext(root);
        addMacroArguments(node, context, macro, macroContext);
        Value value = root.evaluate(macroContext);
        context.put(node.toString(), value);
    }

    private void addMacroArguments(ReferenceNode node, Context context, RankProfile.Macro macro, Context macroContext) {
        if (macro.getFormalParams().size() > 0 && node.children().size() > 0) {
            for (int i = 0; i < macro.getFormalParams().size() && i < node.children().size(); ++i) {
                String param = macro.getFormalParams().get(i);
                ExpressionNode argumentExpression = node.children().get(i);
                Value arg = argumentExpression.evaluate(context);
                macroContext.put(param, arg);
            }
        }
    }

}
