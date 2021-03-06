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
package org.aika.network;


import org.aika.Neuron;
import org.aika.lattice.NodeActivation;
import org.aika.Input;
import org.aika.Input.RangeRelation;
import org.aika.Model;
import org.aika.neuron.Activation;
import org.aika.corpus.Document;
import org.aika.corpus.Range;
import org.aika.lattice.OrNode;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Set;
import java.util.TreeSet;

import static org.aika.corpus.Range.Operator.*;


/**
 *
 * @author Lukas Molzberger
 */
public class RecurrentIdTest {

    @Test
    public void testABPattern() {
        Model m = new Model();

        Neuron inA = m.createNeuron("A");
        Neuron inB = m.createNeuron("B");


        OrNode outCNode = m.initNeuron(m.createNeuron("C"),
                0.001,
                new Input()
                        .setNeuron(inA)
                        .setWeight(1.0f)
                        .setRecurrent(false)
                        .setBiasDelta(1.0)
                        .setRelativeRid(0)
                        .setRangeMatch(RangeRelation.EQUALS)
                        .setRangeOutput(true),
                new Input()
                        .setNeuron(inB)
                        .setWeight(1.0f)
                        .setRecurrent(false)
                        .setBiasDelta(1.0)
                        .setRelativeRid(1)
                        .setRangeMatch(RangeRelation.EQUALS)
                        .setRangeOutput(true)
        ).get().node.get();


        Document doc = m.createDocument("aaaaaaaaaa", 0);

        inA.addInput(doc, 0, 1, 20);
        inB.addInput(doc, 0, 1, 21);

        Activation outC1 = NodeActivation.get(doc, outCNode, 20, new Range(0, 1), LESS_THAN, GREATER_THAN, null, null);

        System.out.println(doc.neuronActivationsToString(true, false, true));

        Assert.assertNotNull(outC1);
    }


    @Test
    public void testABCPattern() {
        Model m = new Model();

        Neuron inA = m.createNeuron("A");
        Neuron inB = m.createNeuron("B");
        Neuron inC = m.createNeuron("C");

        OrNode outDNode = m.initNeuron(m.createNeuron("D"),
                0.001,
                new Input()
                        .setNeuron(inA)
                        .setWeight(1.0f)
                        .setRecurrent(false)
                        .setBiasDelta(1.0f)
                        .setRelativeRid(3)
                        .setRangeMatch(RangeRelation.EQUALS)
                        .setRangeOutput(true),
                new Input()
                        .setNeuron(inB)
                        .setWeight(1.0f)
                        .setRecurrent(false)
                        .setBiasDelta(1.0f)
                        .setRelativeRid(0)
                        .setRangeMatch(RangeRelation.EQUALS)
                        .setRangeOutput(true),
                new Input()
                        .setNeuron(inC)
                        .setWeight(1.0f)
                        .setRecurrent(false)
                        .setBiasDelta(1.0f)
                        .setRelativeRid(6)
                        .setRangeMatch(RangeRelation.EQUALS)
                        .setRangeOutput(true)
        ).get().node.get();

        Document doc = m.createDocument("aaaaaaaaaa", 0);

        inA.addInput(doc, 0, 1, 13);
        inB.addInput(doc, 0, 1, 10);
        inC.addInput(doc, 0, 1, 16);

        Activation outD1 = NodeActivation.get(doc, outDNode, 10, new Range(0, 1), EQUALS, EQUALS, null, null);

        Assert.assertNotNull(outD1);
    }


    @Test
    public void testHuettenheim() {
        Model m = new Model();

        HashMap<Character, Neuron> chars = new HashMap<>();
        for (char c = 'a'; c <= 'z'; c++) {
            Neuron rec = m.createNeuron("IN-" + c);
            chars.put(c, rec);
        }

        String[] testWords = {"Hüttenheim"};

        for (String word : testWords) {
            Set<Input> inputs = new TreeSet<>();
            for (int i = 0; i < word.length(); i++) {
                char c = word.toLowerCase().charAt(i);

                Neuron rec = chars.get(c);
                if (rec != null) {
                    boolean begin = i == 0;
                    boolean end = i + 1 == word.length();
                    inputs.add(
                            new Input()
                                .setNeuron(rec)
                                .setWeight(begin || end ? 2.0f : 1.0f)
                                .setRecurrent(false)
                                .setBiasDelta(1.0f)
                                .setRelativeRid(i)
                                .setStartRangeMatch(begin ? EQUALS : LESS_THAN)
                                .setEndRangeMatch(end ? EQUALS : GREATER_THAN)
                                .setStartRangeOutput(begin)
                                .setEndRangeOutput(end)
                    );
                }
            }

            Neuron n = m.initNeuron(m.createNeuron("PATTERN"), 0.5, inputs);

            System.out.println(n.get().node.get().logicToString());

            Document doc = m.createDocument("abc Hüttenheim cba", 0);

            for (int i = 0; i < doc.length(); i++) {
                char c = doc.getContent().toLowerCase().charAt(i);

                Neuron rec = chars.get(c);
                if (rec != null) {
                    Range r = new Range(i, doc.length());

                    rec.addInput(doc, r.begin, r.end, i, doc.bottom);
                }
            }

            assert n.get().node.get().getActivations(doc).size() >= 1;
        }
    }
}
