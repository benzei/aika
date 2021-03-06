/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.aika.neuron;


import org.aika.*;
import org.aika.neuron.Activation.State;
import org.aika.neuron.Activation.SynapseActivation;
import org.aika.corpus.*;
import org.aika.corpus.SearchNode.Coverage;
import org.aika.corpus.Range.Operator;
import org.aika.lattice.InputNode;
import org.aika.lattice.Node;
import org.aika.lattice.Node.ThreadState;
import org.aika.lattice.NodeActivation;
import org.aika.lattice.OrNode;
import org.aika.neuron.Synapse.Key;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.aika.corpus.Range.Mapping.END;
import static org.aika.corpus.Range.Mapping.START;
import static org.aika.corpus.Range.Operator.*;
import static org.aika.neuron.Activation.State.*;

/**
 * The {@code INeuron} class represents a internal neuron implementation in Aikas neural network and is connected to other neurons through
 * input synapses and output synapses. The activation value of a neuron is calculated by computing the weighted sum
 * (input act. value * synapse weight) of the input synapses, adding the bias to it and sending the resulting value
 * through a transfer function (the upper part of tanh).
 * <p>
 * <p>The neuron does not store its activations by itself. The activation objects are stored within the
 * logic nodes. To access the activations of this neuron simply use the member variable {@code node} or use
 * the method {@code getFinalActivations(Document doc)} to ge the final activations of this neuron.
 *
 * @author Lukas Molzberger
 */
public class INeuron extends AbstractNode<Neuron> implements Comparable<INeuron> {

    private static final Logger log = LoggerFactory.getLogger(INeuron.class);

    public static double WEIGHT_TOLERANCE = 0.001;
    public static double TOLERANCE = 0.000001;
    public static int MAX_SELF_REFERENCING_DEPTH = 5;

    public String label;
    public String outputText;

    public volatile double bias;
    public volatile double negDirSum;
    public volatile double negRecSum;
    public volatile double posRecSum;

    public volatile double maxRecurrentSum = 0.0;


    public TreeMap<Synapse, Synapse> inputSynapses = new TreeMap<>(Synapse.INPUT_SYNAPSE_COMP);

    public TreeMap<Key, Provider<InputNode>> outputNodes = new TreeMap<>();

    public Provider<OrNode> node;

    public volatile double activationSum;
    public volatile int numberOfActivations;

    public ReadWriteLock lock = new ReadWriteLock();


    private INeuron() {
    }


    public INeuron(Model m) {
        this(m, null);
    }


    public INeuron(Model m, String label) {
        this(m, label, null);
    }


    public INeuron(Model m, String label, String outputText) {
        this.label = label;
        this.outputText = outputText;

        provider = new Neuron(m, this);

        OrNode node = new OrNode(m);

        node.neuron = provider;
        this.node = node.provider;

        provider.setModified();
    }

    /**
     * Propagate an input activation into the network.
     *
     * @param doc   The current document
     * @param begin The range begin
     * @param end   The range end
     * @param rid   The relational id (e.g. the word position)
     * @param o     The interpretation node
     * @param value The activation value of this input activation
     */
    public Activation addInput(Document doc, int begin, int end, Integer rid, InterprNode o, double value) {
        Node.addActivationAndPropagate(doc, new NodeActivation.Key(node.get(), new Range(begin, end), rid, o), Collections.emptySet());

        doc.propagate();

        Activation act = NodeActivation.get(doc, node.get(), rid, new Range(begin, end), EQUALS, EQUALS, o, InterprNode.Relation.EQUALS);
        State s = new State(value, 0, NormWeight.ZERO_WEIGHT);
        act.rounds.set(0, s);
        act.finalState = s;
        act.upperBound = value;
        act.isInput = true;

        doc.inputNeuronActivations.add(act);
        doc.finallyActivatedNeurons.add(act.key.n.neuron.get());

        doc.ubQueue.add(act);

        doc.propagate();

        return act;
    }


    public void removeInput(Document doc, int begin, int end, Integer rid, InterprNode o) {
        Range r = new Range(begin, end);
        NodeActivation act = NodeActivation.get(doc, node.get(), rid, r, EQUALS, EQUALS, o, InterprNode.Relation.EQUALS);
        Node.removeActivationAndPropagate(doc, act, Collections.emptySet());

        doc.propagate();
        doc.inputNeuronActivations.remove(act);
    }


    public double avgActivation() {
        return numberOfActivations > 0.0 ? activationSum / numberOfActivations : 1.0;
    }


    public void publish() {
    }


    public void unpublish() {
    }


    public void remove() {
        unpublish();

        for (Synapse s : inputSynapses.values()) {
            INeuron in = s.input.get();
            in.provider.lock.acquireWriteLock();
            in.provider.inMemoryOutputSynapses.remove(s);
            in.provider.lock.releaseWriteLock();
        }

        provider.lock.acquireReadLock();
        for (Synapse s : provider.inMemoryOutputSynapses.values()) {
            INeuron out = s.output.get();
            out.lock.acquireWriteLock();
            out.inputSynapses.remove(s);
            out.lock.releaseWriteLock();
        }
        provider.lock.releaseReadLock();
    }


    public void propagateAddedActivation(Document doc, Activation act) {
        doc.ubQueue.add(act);
    }


    public void propagateRemovedActivation(Document doc, NodeActivation act) {
        for (Provider<InputNode> out : outputNodes.values()) {
            out.get().removeActivation(doc, act);
        }
    }


    public void computeBounds(Activation act) {
        double ub = bias + posRecSum - (negDirSum + negRecSum);
        double lb = bias + posRecSum - (negDirSum + negRecSum);

        for (SynapseActivation sa : act.neuronInputs) {
            Synapse s = sa.s;
            Activation iAct = sa.input;

            if (iAct == act || iAct.isRemoved) continue;

            if (s.isNegative()) {
                if (!checkSelfReferencing(act.key.o, iAct.key.o, null, 0) && act.key.o.contains(iAct.key.o, true)) {
                    ub += iAct.lowerBound * s.w;
                }

                lb += s.w;
            } else {
                ub += iAct.upperBound * s.w;
                lb += iAct.lowerBound * s.w;
            }
        }

        act.upperBound = transferFunction(ub);
        act.lowerBound = transferFunction(lb);
    }


    public State computeWeight(int round, Activation act, SearchNode sn, Document doc) {
        Coverage c = sn.getCoverage(act.key.o);
        if(c == Coverage.UNKNOWN) return State.ZERO;

        double st = bias - (negDirSum + negRecSum);
        double[] sum = {st, 0.0};

        int fired = -1;

        for (InputState is: getInputStates(act, round, sn)) {
            Synapse s = is.sa.s;
            Activation iAct = is.sa.input;

            if (iAct == act || iAct.isRemoved) continue;

            int t = s.key.isRecurrent ? REC : DIR;
            sum[t] += is.s.value * s.w;

            if (!s.key.isRecurrent && !s.isNegative() && sum[DIR] + sum[REC] >= 0.0 && fired < 0) {
                fired = iAct.rounds.get(round).fired + 1;
            }
        }

        double drSum = sum[DIR] + sum[REC];

        // Compute only the recurrent part is above the threshold.
        NormWeight newWeight = NormWeight.create(
                c == Coverage.SELECTED ? (sum[DIR] + negRecSum) < 0.0 ? Math.max(0.0, drSum) : sum[REC] - negRecSum : 0.0,
                (sum[DIR] + negRecSum) < 0.0 ? Math.max(0.0, sum[DIR] + negRecSum + maxRecurrentSum) : maxRecurrentSum
        );

        if (doc.debugActId == act.id && doc.debugActWeight <= newWeight.w) {
            storeDebugOutput(doc, act, newWeight, drSum, round, sn);
        }

        return new State(
                c == Coverage.SELECTED ? transferFunction(drSum) : 0.0,
                c == Coverage.SELECTED ? fired : -1,
                newWeight
        );
    }


    private State getInitialState(Coverage c) {
        return new State(
                c == Coverage.SELECTED ? 1.0 : 0.0,
                0,
                NormWeight.ZERO_WEIGHT
        );
    }


    private State getInputState(int round, SearchNode sn, InterprNode o, Synapse s, Activation iAct) {
        InterprNode io = iAct.key.o;

        State is = State.ZERO;
        if (s.key.isRecurrent) {
            if (!s.isNegative() || !checkSelfReferencing(o, io, sn, 0)) {
                is = round == 0 ? getInitialState(sn.getCoverage(io)) : iAct.rounds.get(round - 1);
            }
        } else {
            is = iAct.rounds.get(round);
        }
        return is;
    }


    private List<InputState> getInputStates(Activation act, int round, SearchNode sn) {
        InterprNode o = act.key.o;
        ArrayList<InputState> tmp = new ArrayList<>();
        Synapse lastSynapse = null;
        InputState maxInputState = null;
        for (SynapseActivation sa : act.neuronInputs) {
            if (lastSynapse != null && lastSynapse != sa.s) {
                tmp.add(maxInputState);
                maxInputState = null;
            }

            State s = getInputState(round, sn, o, sa.s, sa.input);
            if (maxInputState == null || maxInputState.s.value < s.value) {
                maxInputState = new InputState(sa, s);
            }
            lastSynapse = sa.s;
        }
        if (maxInputState != null) {
            tmp.add(maxInputState);
        }

        return tmp;
    }


    private static class InputState {
        public InputState(SynapseActivation sa, State s) {
            this.sa = sa;
            this.s = s;
        }

        SynapseActivation sa;
        State s;
    }


    private void storeDebugOutput(Document doc, Activation act, NormWeight nw, double sum, int round, SearchNode sn) {
        StringBuilder sb = new StringBuilder();
        sb.append("Activation ID: " + doc.debugActId + "\n");
        sb.append("Neuron: " + label + "\n");
        sb.append("Sum: " + sum + "\n");
        sb.append("Bias: " + bias + "\n");
        sb.append("Round: " + round + "\n");
        sb.append("Positive Recurrent Sum: " + posRecSum + "\n");
        sb.append("Negative Recurrent Sum: " + negRecSum + "\n");
        sb.append("Negative Direct Sum: " + negDirSum + "\n");
        sb.append("Inputs:\n");

        for (InputState is : getInputStates(act, round, sn)) {
            sb.append("    " + is.sa.input.key.n.neuron.get().label);
            sb.append("  SynWeight: " + is.sa.s.w);
            sb.append("  ActValue: " + is.s);
            sb.append("\n");
        }
        sb.append("Weight: " + nw.w + "\n");
        sb.append("Norm: " + nw.n + "\n");
        sb.append("\n");
        doc.debugOutput = sb.toString();
    }


    public void computeErrorSignal(Document doc, Activation act) {
        act.errorSignal = act.initialErrorSignal;
        for (SynapseActivation sa : act.neuronOutputs) {
            Synapse s = sa.s;
            Activation oAct = sa.output;

            act.errorSignal += s.w * oAct.errorSignal * (1.0 - act.finalState.value);
        }

        for (SynapseActivation sa : act.neuronInputs) {
            doc.bQueue.add(sa.input);
        }
    }


    public void train(Document doc, Activation targetAct, double learnRate, TrainConfig.SynapseEvaluation se) {
        if (Math.abs(targetAct.errorSignal) < TOLERANCE) return;

        long v = NodeActivation.visitedCounter++;

        double x = learnRate * targetAct.errorSignal;
        bias += x;
        for (INeuron n : doc.finallyActivatedNeurons) {
            for(Activation iAct: n.getFinalActivations(doc)) {
                Synapse.Key sk = se.evaluate(iAct, targetAct);
                if(sk != null) {
                    trainSynapse(iAct, sk, x, v);
                }
            }
        }

        Converter.convert(doc.m, doc.threadId, this, inputSynapses.values());
    }


    private void trainSynapse(Activation iAct, Synapse.Key sk, double x, long v) {
        if (iAct.visitedNeuronTrain == v) return;
        iAct.visitedNeuronTrain = v;

        INeuron inputNeuron = iAct.key.n.neuron.get();
        if(inputNeuron == this) {
            return;
        }
        double deltaW = x * iAct.finalState.value;

        Provider<InputNode> inp = inputNeuron.outputNodes.get(sk.createInputNodeKey());
        Synapse synapse = null;
        InputNode in = null;
        if(inp != null) {
            in = inp.get();
            synapse = in.getSynapse(sk.relativeRid, provider);
        }

        if(synapse == null) {
            synapse = new Synapse(inputNeuron.provider, sk);
            synapse.output = provider;

            if(in == null) {
                in = InputNode.add(provider.m, sk.createInputNodeKey(), synapse.input.get());
            }
            in.setSynapse(synapse);
            synapse.link();
        }

        synapse.nw += deltaW;
    }


    private static boolean checkSelfReferencing(InterprNode nx, InterprNode ny, SearchNode en, int depth) {
        if (nx == ny && (en == null || en.isCovered(ny.markedSelected))) return true;

        if (depth > MAX_SELF_REFERENCING_DEPTH) return false;

        if (ny.orInterprNodes != null) {
            for (InterprNode n : ny.orInterprNodes.values()) {
                if (checkSelfReferencing(nx, n, en, depth + 1)) return true;
            }
        }

        return false;
    }


    public static double transferFunction(double x) {
        return x > 0.0 ? (2.0 * sigmoid(x)) - 1.0 : 0.0;
    }


    public static double sigmoid(double x) {
        return (1 / (1 + Math.pow(Math.E, (-x))));
    }


    public void count(Document doc) {
        ThreadState<OrNode, Activation> th = node.get().getThreadState(doc.threadId, false);
        if (th == null) return;
        for (Activation act : th.activations.values()) {
            if (act.finalState != null && act.finalState.value > 0.0) {
                activationSum += act.finalState.value;
                numberOfActivations++;
            }
        }
    }

    /**
     * Sets the incoming and outgoing links between neuron activations.
     *
     * @param doc
     * @param act
     */
    public void linkNeuronRelations(Document doc, Activation act) {
        int v = doc.visitedCounter++;
        lock.acquireReadLock();
        linkNeuronActs(doc, act, v, 0);
        linkNeuronActs(doc, act, v, 1);
        lock.releaseReadLock();
    }


    private void linkNeuronActs(Document doc, Activation act, int v, int dir) {
        ArrayList<Activation> recNegTmp = new ArrayList<>();

        provider.lock.acquireReadLock();
        NavigableMap<Synapse, Synapse> syns = (dir == 0 ? provider.inMemoryInputSynapses : provider.inMemoryOutputSynapses);

        for (Synapse s : getActiveSynapses(doc, dir, syns)) {
            Neuron p = (dir == 0 ? s.input : s.output);
            INeuron an = p.getIfNotSuspended();
            if (an != null) {
                OrNode n = an.node.get();
                ThreadState th = n.getThreadState(doc.threadId, false);
                if (th == null || th.activations.isEmpty()) continue;

                linkActSyn(n, doc, act, dir, recNegTmp, s);
            }
        }
        provider.lock.releaseReadLock();

        for (Activation rAct : recNegTmp) {
            Activation oAct = (dir == 0 ? act : rAct);
            Activation iAct = (dir == 0 ? rAct : act);

            markConflicts(iAct, oAct, v);

            addConflict(doc, oAct.key.o, iAct.key.o, iAct, Collections.singleton(act), v);
        }
    }


    private static void linkActSyn(OrNode n, Document doc, Activation act, int dir, ArrayList<Activation> recNegTmp, Synapse s) {
        Synapse.Key sk = s.key;

        Integer rid;
        if (dir == 0) {
            rid = sk.absoluteRid != null ? sk.absoluteRid : Utils.nullSafeAdd(act.key.rid, false, sk.relativeRid, false);
        } else {
            rid = Utils.nullSafeSub(act.key.rid, false, sk.relativeRid, false);
        }


        Operator begin = replaceFirstAndLast(sk.startRangeMatch);
        Operator end = replaceFirstAndLast(sk.endRangeMatch);
        Range r = act.key.r;
        if (dir == 0) {
            Operator tb = begin;
            Operator te = end;
            begin = Operator.invert(sk.startRangeMapping == START ? tb : (sk.endRangeMapping == START ? te : NONE));
            end = Operator.invert(sk.endRangeMapping == END ? te : (sk.startRangeMapping == END ? tb : NONE));

            if (sk.startRangeMapping != START || sk.endRangeMapping != END) {
                r = new Range(s.key.endRangeMapping == START ? r.end : (sk.startRangeMapping == START ? r.begin : null), sk.startRangeMapping == END ? r.begin : (sk.endRangeMapping == END ? r.end : null));
            }
        } else {
            if (sk.startRangeMapping != START || sk.endRangeMapping != END) {
                r = new Range(sk.startRangeMapping == END ? r.end : (sk.startRangeMapping == START ? r.begin : null), sk.endRangeMapping == START ? r.begin : (sk.endRangeMapping == END ? r.end : null));
            }
        }

        Stream<Activation> tmp = NodeActivation.select(
                doc,
                n,
                rid,
                r,
                begin,
                end,
                null,
                null
        );

        final int d = dir;
        tmp.forEach(rAct -> {
            Activation oAct = (d == 0 ? act : rAct);
            Activation iAct = (d == 0 ? rAct : act);

            SynapseActivation sa = new SynapseActivation(s, iAct, oAct);
            iAct.addSynapseActivation(0, sa);
            oAct.addSynapseActivation(1, sa);

            if (s.isNegative() && sk.isRecurrent) {
                recNegTmp.add(rAct);
            }
        });
    }


    private static Collection<Synapse> getActiveSynapses(Document doc, int dir, NavigableMap<Synapse, Synapse> syns) {
        // Optimization in case the set of synapses is very large
        if (syns.size() < 10 || doc.activatedNeurons.size() * 20 > syns.size()) {
            return syns.values();
        }

        Collection<Synapse> synsTmp;
        ArrayList<Synapse> newSyns = new ArrayList<>();
        Synapse lk = new Synapse(null, Synapse.Key.MIN_KEY);
        Synapse uk = new Synapse(null, Synapse.Key.MAX_KEY);

        for (INeuron n : doc.activatedNeurons) {
            if (dir == 0) {
                lk.input = n.provider;
                uk.input = n.provider;
            } else {
                lk.output = n.provider;
                uk.output = n.provider;
            }

            // Using addAll is not efficient here.
            for (Synapse s : syns.subMap(lk, true, uk, true).values()) {
                newSyns.add(s);
            }
        }

        synsTmp = newSyns;
        return synsTmp;
    }


    private static Operator replaceFirstAndLast(Operator rm) {
        return rm == FIRST || rm == LAST ? EQUALS : rm;
    }


    public static void unlinkNeuronRelations(Document doc, Activation act) {
        int v = doc.visitedCounter++;
        for (int dir = 0; dir < 2; dir++) {
            for (SynapseActivation sa : (dir == 0 ? act.neuronInputs : act.neuronOutputs)) {
                Synapse s = sa.s;
                Activation rAct = dir == 0 ? sa.input : sa.output;

                if (s.isNegative() && s.key.isRecurrent) {
                    Activation oAct = (dir == 0 ? act : rAct);
                    Activation iAct = (dir == 0 ? rAct : act);

                    markConflicts(iAct, oAct, v);

                    removeConflict(doc, oAct.key.o, iAct.key.o, iAct, act, v);
                }
            }
        }

        for (int dir = 0; dir < 2; dir++) {
            for (SynapseActivation sa : (dir == 0 ? act.neuronInputs : act.neuronOutputs)) {
                Activation rAct = dir == 0 ? sa.input : sa.output;
                rAct.removeSynapseActivation(dir, sa);
            }
        }
    }


    private static void addConflict(Document doc, InterprNode io, InterprNode o, NodeActivation act, Collection<NodeActivation> inputActs, long v) {
        if (o.markedConflict == v || o.orInterprNodes == null) {
            if (!isAllowed(doc.threadId, io, o, inputActs)) {
                Conflicts.add(doc, act, io, o);
            }
        } else {
            for (InterprNode no : o.orInterprNodes.values()) {
                addConflict(doc, io, no, act, inputActs, v);
            }
        }
    }


    private static boolean isAllowed(int threadId, InterprNode io, InterprNode o, Collection<NodeActivation> inputActs) {
        if (io != null && o.contains(io, false)) return true;
        for (NodeActivation act : inputActs) {
            if (act.key.n.isAllowedOption(threadId, o, act, Node.visitedCounter++)) return true;
        }
        return false;
    }


    private static void removeConflict(Document doc, InterprNode io, InterprNode o, NodeActivation act, NodeActivation nAct, long v) {
        if (o.markedConflict == v || o.orInterprNodes == null) {
            if (!nAct.key.n.isAllowedOption(doc.threadId, o, nAct, Node.visitedCounter++)) {
                assert io != null;

                Conflicts.remove(doc, act, io, o);
            }
        } else {
            for (InterprNode no : o.orInterprNodes.values()) {
                removeConflict(doc, io, no, act, nAct, v);
            }
        }
    }


    private static void markConflicts(Activation iAct, Activation oAct, int v) {
        oAct.key.o.markedConflict = v;
        for (SynapseActivation sa : iAct.neuronOutputs) {
            if (sa.s.key.isRecurrent && sa.s.isNegative()) {
                sa.output.key.o.markedConflict = v;
            }
        }
    }


    public Synapse getInputSynapse(Synapse s) {
        return inputSynapses.getOrDefault(s, s);
    }


    public int compareTo(INeuron n) {
        if (provider.id < n.provider.id) return -1;
        else if (provider.id > n.provider.id) return 1;
        else return 0;
    }


    @Override
    public void write(DataOutput out) throws IOException {
        out.writeBoolean(true);

        out.writeBoolean(label != null);
        if(label != null) {
            out.writeUTF(label);
        }

        out.writeBoolean(outputText != null);
        if(outputText != null) {
            out.writeUTF(outputText);
        }


        out.writeDouble(bias);
        out.writeDouble(negDirSum);
        out.writeDouble(negRecSum);
        out.writeDouble(posRecSum);
        out.writeDouble(maxRecurrentSum);

        out.writeInt(outputNodes.size());
        for (Map.Entry<Key, Provider<InputNode>> me : outputNodes.entrySet()) {
            me.getKey().write(out);
            out.writeInt(me.getValue().id);
        }

        out.writeBoolean(node != null);
        if (node != null) {
            out.writeInt(node.id);
        }

        out.writeDouble(activationSum);
        out.writeInt(numberOfActivations);

        for (Synapse s : inputSynapses.values()) {
            if (s.input != null) {
                out.writeBoolean(true);
                s.write(out);
            }
        }
        out.writeBoolean(false);
    }


    @Override
    public void readFields(DataInput in, Model m) throws IOException {
        if(in.readBoolean()) {
            label = in.readUTF();
        }
        if(in.readBoolean()) {
            outputText = in.readUTF();
        }

        bias = in.readDouble();
        negDirSum = in.readDouble();
        negRecSum = in.readDouble();
        posRecSum = in.readDouble();
        maxRecurrentSum = in.readDouble();

        int s = in.readInt();
        for (int i = 0; i < s; i++) {
            Key k = Key.read(in, m);
            Provider<InputNode> n = m.lookupNodeProvider(in.readInt());
            outputNodes.put(k, n);
        }

        if (in.readBoolean()) {
            Integer nId = in.readInt();
            node = m.lookupNodeProvider(nId);
        }

        activationSum = in.readDouble();
        numberOfActivations = in.readInt();

        while (in.readBoolean()) {
            Synapse syn = Synapse.read(in, m);

            inputSynapses.put(syn, syn);
        }
    }


    @Override
    public void suspend() {
        for (Synapse s : inputSynapses.values()) {
            s.input.lock.acquireWriteLock();
            s.input.inMemoryOutputSynapses.remove(s);
            s.input.lock.releaseWriteLock();

            InputNode iNode = s.inputNode.getIfNotSuspended();
            if (iNode != null) {
                iNode.removeSynapse(s);
            }
        }

        provider.lock.acquireReadLock();
        for (Synapse s : provider.inMemoryOutputSynapses.values()) {
            s.output.lock.acquireWriteLock();
            s.output.inMemoryInputSynapses.remove(s);
            s.output.lock.releaseWriteLock();
        }
        provider.lock.releaseReadLock();
    }


    @Override
    public void reactivate() {
        for (Synapse s : inputSynapses.values()) {
            s.input.lock.acquireWriteLock();
            s.input.inMemoryOutputSynapses.put(s, s);
            s.input.lock.releaseWriteLock();

            if (!s.input.isSuspended()) {
                s.output.lock.acquireWriteLock();
                s.output.inMemoryInputSynapses.put(s, s);
                s.output.lock.releaseWriteLock();
            }

            InputNode iNode = s.inputNode.getIfNotSuspended();
            if (iNode != null) {
                iNode.setSynapse(s);
            }
        }
        provider.lock.acquireReadLock();
        for (Synapse s : provider.inMemoryOutputSynapses.values()) {
            s.output.lock.acquireWriteLock();
            s.output.inMemoryInputSynapses.put(s, s);
            s.output.lock.releaseWriteLock();
        }
        provider.lock.releaseReadLock();
    }


    public static Neuron init(Model m, int threadId, Neuron pn, double bias, Set<Synapse> inputs) {
        INeuron n = pn.get();
        n.provider.m.stat.neurons++;
        n.bias = bias;

        ArrayList<Synapse> modifiedSynapses = new ArrayList<>();
        for (Synapse s : inputs) {
            assert !s.key.startRangeOutput || s.key.startRangeMatch == Range.Operator.EQUALS || s.key.startRangeMatch == Range.Operator.FIRST;
            assert !s.key.endRangeOutput || s.key.endRangeMatch == Range.Operator.EQUALS || s.key.endRangeMatch == Range.Operator.FIRST;

            s.output = n.provider;
            s.link();

            modifiedSynapses.add(s);
        }

        if (!Converter.convert(m, threadId, n, modifiedSynapses)) return null;

        n.publish();

        return n.provider;
    }


    public static INeuron addSynapse(Model m, int threadId, Neuron pn, double biasDelta, Synapse s) {
        INeuron n = pn.get();
        n.bias += biasDelta;

        s.output = n.provider;
        s.link();

        if (!Converter.convert(m, threadId, n, Collections.singletonList(s))) return null;
        return n;
    }


    public static INeuron readNeuron(DataInput in, Neuron p) throws IOException {
        INeuron n = new INeuron();
        n.provider = p;
        n.readFields(in, p.m);
        return n;
    }


    public String toString() {
        return "n(" + label + ")";
    }


    public String toStringWithSynapses() {
        SortedSet<Synapse> is = new TreeSet<>(new Comparator<Synapse>() {
            @Override
            public int compare(Synapse s1, Synapse s2) {
                int r = Double.compare(s2.w, s1.w);
                if (r != 0) return r;
                return Integer.compare(s1.input.id, s2.input.id);
            }
        });

        is.addAll(inputSynapses.values());

        StringBuilder sb = new StringBuilder();
        sb.append(toString());
        sb.append("<");
        sb.append("B:");
        sb.append(Utils.round(bias));
        for (Synapse s : is) {
            sb.append(", ");
            sb.append(Utils.round(s.w));
            sb.append(":");
            sb.append(s.key.relativeRid);
            sb.append(":");
            sb.append(s.input.toString());
        }
        sb.append(">");
        return sb.toString();
    }


    /**
     * {@code getFinalActivations} is a convenience method to retrieve all activations of the given neuron that
     * are part of the final interpretation. Before calling this method, the {@code doc.process()} needs to
     * be called first.
     *
     * @param doc The current document
     * @return A collection with all final activations of this neuron.
     */
    public Collection<Activation> getFinalActivations(Document doc) {
        Stream<Activation> s = NodeActivation.select(doc, node.get(), null, null, null, null, null, null);
        return s.filter(act -> act.finalState != null && act.finalState.value > 0.0)
                .collect(Collectors.toList());
    }


    public static class NormWeight {
        public final static NormWeight ZERO_WEIGHT = new NormWeight(0.0, 0.0);

        public final double w;
        public final double n;

        private NormWeight(double w, double n) {
            this.w = w;
            this.n = n;
        }

        public static NormWeight create(double w, double n) {
            assert w >= 0.0 && n >= 0.0;
            if (w == 0.0 && n == 0.0) return ZERO_WEIGHT;
            return new NormWeight(w, n);
        }

        public NormWeight add(NormWeight nw) {
            if (nw == null || nw == ZERO_WEIGHT) return this;
            return new NormWeight(w + nw.w, n + nw.n);
        }

        public NormWeight sub(NormWeight nw) {
            if (nw == null || nw == ZERO_WEIGHT) return this;
            return new NormWeight(w - nw.w, n - nw.n);
        }

        public double getNormWeight() {
            return n > 0 ? w / n : 0.0;
        }


        public boolean equals(NormWeight nw) {
            return (Math.abs(w - nw.w) <= INeuron.WEIGHT_TOLERANCE && Math.abs(n - nw.n) <= INeuron.WEIGHT_TOLERANCE);
        }

        public String toString() {
            return "W:" + w + " N:" + n + " NW:" + getNormWeight();
        }
    }

}
