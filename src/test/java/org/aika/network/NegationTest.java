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
import org.aika.corpus.Document;
import org.aika.corpus.InterprNode;
import org.aika.corpus.Range;
import org.aika.lattice.AndNode;
import org.aika.lattice.Node;
import org.aika.lattice.OrNode;
import org.junit.Assert;
import org.junit.Test;

import static org.aika.Input.RangeRelation.NONE;
import static org.aika.corpus.Range.Operator.EQUALS;

/**
 *
 * @author Lukas Molzberger
 */
public class NegationTest {


    @Test
    public void testTwoNegativeInputs1() {
        Model m = new Model();
        Neuron inA = m.createNeuron("A");
        Neuron inB = m.createNeuron("B");
        Neuron inC = m.createNeuron("C");

        Neuron abcN = m.createNeuron("ABC");

        m.initNeuron(abcN,
                5.0,
                new Input()
                        .setNeuron(inA)
                        .setWeight(10.0f)
                        .setRecurrent(false)
                        .setRelativeRid(0)
                        .setBiasDelta(0.95),
                new Input()
                        .setNeuron(inB)
                        .setWeight(-10.0f)
                        .setRecurrent(true)
                        .setRelativeRid(0)
                        .setBiasDelta(1.0),
                new Input()
                        .setNeuron(inC)
                        .setWeight(-10.0f)
                        .setRecurrent(true)
                        .setRelativeRid(0)
                        .setBiasDelta(1.0)
        );

        Document doc = m.createDocument("aaaaaaaaaaa", 0);

        inA.addInput(doc, 0, 11);

        System.out.println(doc.neuronActivationsToString(true, false, true));
        Assert.assertNotNull(NodeActivation.get(doc, abcN.get().node.get(), null, new Range(0, 11), EQUALS, EQUALS, null, null));

        InterprNode o1 = InterprNode.addPrimitive(doc);

        inB.addInput(doc, 2, 7, o1);

        System.out.println(doc.neuronActivationsToString(true, false, true));

        InterprNode o2 = InterprNode.addPrimitive(doc);

        inC.addInput(doc, 4, 9, o2);

        System.out.println(doc.neuronActivationsToString(true, false, true));

        Assert.assertNotNull(NodeActivation.get(doc, abcN.get().node.get(), null, new Range(0, 11), EQUALS, EQUALS, null, null));
    }


    @Test
    public void testTwoNegativeInputs2() {
        Model m = new Model();

        Neuron inA = m.createNeuron("A");

        Neuron inB = m.createNeuron("B");

        Neuron inC = m.createNeuron("C");

        Neuron abcN = m.createNeuron("ABC");

        Neuron outN = m.initNeuron(m.createNeuron("OUT"),
                -0.001,
                new Input()
                        .setNeuron(abcN)
                        .setWeight(1.0f)
                        .setRecurrent(false)
                        .setRelativeRid(0)
                        .setBiasDelta(0.0)
        );

        m.initNeuron(abcN,
                0.001,
                new Input()
                        .setNeuron(inA)
                        .setWeight(1.0f)
                        .setRecurrent(false)
                        .setRelativeRid(0)
                        .setBiasDelta(1.0),
                new Input()
                        .setNeuron(inB)
                        .setWeight(-1.0f)
                        .setRecurrent(true)
                        .setRelativeRid(0)
                        .setBiasDelta(1.0),
                new Input()
                        .setNeuron(inC)
                        .setWeight(-1.0f)
                        .setRecurrent(true)
                        .setRelativeRid(0)
                        .setBiasDelta(1.0)
        );

        Document doc = m.createDocument("aaaaaaaaaaa", 0);

        inA.addInput(doc, 0, 11);

        System.out.println(doc.neuronActivationsToString(true, false, true));

        InterprNode ob = InterprNode.addPrimitive(doc);
        inB.addInput(doc, 2, 7, ob);

        System.out.println(doc.neuronActivationsToString(true, false, true));

        InterprNode oc = InterprNode.addPrimitive(doc);
        inC.addInput(doc, 4, 9, oc);

        System.out.println(doc.neuronActivationsToString(true, false, true));

//        Assert.assertNull(Activation.get(t, outN.node, 0, new Range(0, 11), Range.Relation.EQUALS, null, null, null));

        inB.removeInput(doc, 2, 7, ob);

        System.out.println(doc.neuronActivationsToString(true, false, true));

        inC.removeInput(doc, 4, 9, oc);

        System.out.println(doc.neuronActivationsToString(true, false, true));

        Assert.assertNotNull(NodeActivation.get(doc, outN.get().node.get(), null, new Range(0, 11), EQUALS, EQUALS, null, null));
    }


    @Test
    public void testSimpleNegation1() {
        Model m = new Model();

        Neuron inA = m.createNeuron("A");

        Neuron asN = m.createNeuron("AS");

        Neuron inS = m.createNeuron("S");

        Neuron outN = m.initNeuron(m.createNeuron("OUT"),
                -0.001,
                new Input()
                        .setNeuron(asN)
                        .setWeight(1.0f)
                        .setRecurrent(false)
                        .setRelativeRid(0)
                        .setBiasDelta(0.0)
        );

        m.initNeuron(asN,
                0.001,
                new Input()
                        .setNeuron(inA)
                        .setWeight(1.0f)
                        .setRecurrent(false)
                        .setRelativeRid(0)
                        .setBiasDelta(1.0)
                        .setRangeMatch(RangeRelation.EQUALS)
                        .setRangeOutput(true),
                new Input()
                        .setNeuron(inS)
                        .setWeight(-1.0f)
                        .setRecurrent(true)
                        .setRelativeRid(0)
                        .setBiasDelta(1.0)
                        .setRangeMatch(NONE)
        );

        Document doc = m.createDocument("aaaaaaaaaaa", 0);

        InterprNode o = InterprNode.addPrimitive(doc);

        inS.addInput(doc, 3, 8, o);

        System.out.println(doc.neuronActivationsToString(true, false, true));

        inA.addInput(doc, 0, 11);

        System.out.println(doc.neuronActivationsToString(true, false, true));

        Assert.assertNotNull(NodeActivation.get(doc, outN.get().node.get(), null, new Range(0, 11), EQUALS, EQUALS, null, null));
        Assert.assertFalse(NodeActivation.get(doc, outN.get().node.get(), null, new Range(0, 11), EQUALS, EQUALS, null, null).key.o.largestCommonSubset.conflicts.primary.isEmpty());

        inS.removeInput(doc, 3, 8, o);

        System.out.println(doc.neuronActivationsToString(true, false, true));

        Assert.assertTrue(NodeActivation.get(doc, outN.get().node.get(), null, new Range(0, 11), EQUALS, EQUALS, null, null).key.o.largestCommonSubset.conflicts.primary.isEmpty());

        inA.removeInput(doc, 0, 11);

        System.out.println(doc.neuronActivationsToString(true, false, true));

        doc.clearActivations();
    }


    @Test
    public void testSimpleNegation2() {
        Model m = new Model();

        Neuron inA = m.createNeuron("A");

        Neuron asN = m.createNeuron("AS");

        Neuron inS = m.createNeuron("S");

        Neuron outN = m.initNeuron(m.createNeuron("OUT"),
                -0.001,
                new Input()
                        .setNeuron(asN)
                        .setWeight(1.0f)
                        .setRecurrent(false)
                        .setRelativeRid(0)
                        .setBiasDelta(0.0)
        );

        m.initNeuron(asN,
                0.001,
                new Input()
                        .setNeuron(inA)
                        .setWeight(1.0f)
                        .setRecurrent(false)
                        .setRelativeRid(0)
                        .setBiasDelta(1.0)
                        .setRangeMatch(RangeRelation.EQUALS)
                        .setRangeOutput(true),
                new Input()
                        .setNeuron(inS)
                        .setWeight(-1.0f)
                        .setRecurrent(true)
                        .setRelativeRid(0)
                        .setBiasDelta(1.0)
                        .setRangeMatch(RangeRelation.CONTAINS)
        );

        Document doc = m.createDocument("aaaaaaaaaaa", 0);

        InterprNode o = InterprNode.addPrimitive(doc);

        inS.addInput(doc, 3, 8, o);

        System.out.println(doc.neuronActivationsToString(true, false, true));

        inA.addInput(doc, 0, 11);

        System.out.println(doc.neuronActivationsToString(true, false, true));

        Assert.assertNotNull(NodeActivation.get(doc, outN.get().node.get(), null, new Range(0, 11), EQUALS, EQUALS, null, null));
        Assert.assertFalse(NodeActivation.get(doc, outN.get().node.get(), null, new Range(0, 11), EQUALS, EQUALS, null, null).key.o.largestCommonSubset.conflicts.primary.isEmpty());

        inA.removeInput(doc, 0, 11);

        System.out.println(doc.neuronActivationsToString(true, false, true));

        inS.removeInput(doc, 3, 8, o);

        doc.clearActivations();
    }


    @Test
    public void testSimpleNegation3() {
        Model m = new Model();

        Neuron inA = m.createNeuron("A");

        Neuron asN = m.createNeuron("AS");

        Neuron inS = m.createNeuron("S");

        Neuron outN = m.initNeuron(m.createNeuron("OUT"),
                -0.001,
                new Input()
                        .setNeuron(asN)
                        .setWeight(1.0f)
                        .setRecurrent(false)
                        .setRelativeRid(0)
                        .setBiasDelta(0.0)
        );

        m.initNeuron(asN,
                0.001,
                new Input()
                        .setNeuron(inA)
                        .setWeight(1.0f)
                        .setRecurrent(false)
                        .setRelativeRid(0)
                        .setBiasDelta(1.0)
                        .setRangeMatch(RangeRelation.EQUALS)
                        .setRangeOutput(true),
                new Input()
                        .setNeuron(inS)
                        .setWeight(-1.0f)
                        .setRecurrent(true)
                        .setRelativeRid(0)
                        .setBiasDelta(1.0)
                        .setRangeMatch(RangeRelation.CONTAINS)
        );

        Document doc = m.createDocument("aaaaaaaaaaa", 0);

        InterprNode o = InterprNode.addPrimitive(doc);

        inA.addInput(doc, 0, 11);

        System.out.println(doc.neuronActivationsToString(true, false, true));

        inS.addInput(doc, 3, 8, o);

        System.out.println(doc.neuronActivationsToString(true, false, true));

        Assert.assertNotNull(NodeActivation.get(doc, outN.get().node.get(), null, new Range(0, 11), EQUALS, EQUALS, null, null));
        Assert.assertFalse(NodeActivation.get(doc, outN.get().node.get(), null, new Range(0, 11), EQUALS, EQUALS, null, null).key.o.largestCommonSubset.conflicts.primary.isEmpty());

        inS.removeInput(doc, 3, 8, o);

        System.out.println(doc.neuronActivationsToString(true, false, true));

        Assert.assertTrue(NodeActivation.get(doc, outN.get().node.get(), null, new Range(0, 11), EQUALS, EQUALS, null, null).key.o.largestCommonSubset.conflicts.primary.isEmpty());

        inA.removeInput(doc, 0, 11);

        System.out.println(doc.neuronActivationsToString(true, false, true));

        doc.clearActivations();
    }


    @Test
    public void testNegation1() {
        Model m = new Model();
        Neuron inA = m.createNeuron("A");
        Neuron inB = m.createNeuron("B");

        Neuron asN = m.createNeuron("AS");
        Neuron absN = m.createNeuron("ABS");
        Neuron bsN = m.createNeuron("BS");

        Neuron inS = m.initNeuron(m.createNeuron("S"),
                -0.001,
                new Input()
                        .setNeuron(asN)
                        .setWeight(1.0f)
                        .setRecurrent(false)
                        .setRelativeRid(0)
                        .setBiasDelta(0.0)
                        .setRangeMatch(RangeRelation.EQUALS)
                        .setRangeOutput(true),
                new Input()
                        .setNeuron(absN)
                        .setWeight(1.0f)
                        .setRecurrent(false)
                        .setRelativeRid(0)
                        .setBiasDelta(0.0)
                        .setRangeMatch(RangeRelation.EQUALS)
                        .setRangeOutput(true)
        );

        m.initNeuron(asN,
                0.001,
                new Input()
                        .setNeuron(inA)
                        .setWeight(1.0f)
                        .setRecurrent(false)
                        .setRelativeRid(0)
                        .setBiasDelta(1.0)
                        .setRangeMatch(RangeRelation.EQUALS)
                        .setRangeOutput(true),
                new Input()
                        .setNeuron(inS)
                        .setWeight(-1.0f)
                        .setRecurrent(true)
                        .setRelativeRid(0)
                        .setBiasDelta(1.0)
                        .setRangeMatch(RangeRelation.CONTAINS)
        );
        m.initNeuron(absN,
                0.001,
                new Input()
                        .setNeuron(inA)
                        .setWeight(1.0f)
                        .setRecurrent(false)
                        .setRelativeRid(0)
                        .setBiasDelta(1.0)
                        .setStartRangeMatch(EQUALS)
                        .setStartRangeOutput(true),
                new Input()
                        .setNeuron(inB)
                        .setWeight(1.0f)
                        .setRecurrent(false)
                        .setRelativeRid(0)
                        .setBiasDelta(1.0)
                        .setEndRangeMatch(EQUALS)
                        .setEndRangeOutput(true),
                new Input()
                        .setNeuron(inS)
                        .setWeight(-1.0f)
                        .setRecurrent(true)
                        .setRelativeRid(0)
                        .setBiasDelta(1.0)
                        .setRangeMatch(RangeRelation.CONTAINS)
        );

        {
            Document doc = m.createDocument("aaaaaaaaaa", 0);

            inA.addInput(doc, 0, 6);
            System.out.println(doc.neuronActivationsToString(true, false, true));

            inB.addInput(doc, 0, 6);

            System.out.println(doc.neuronActivationsToString(true, false, true));

            Assert.assertNotNull(NodeActivation.get(doc, inS.get().node.get(), null, new Range(0, 6), EQUALS, EQUALS, null, null));
            Assert.assertEquals(2, NodeActivation.get(doc, inS.get().node.get(), null, new Range(0, 6), EQUALS, EQUALS, null, null).key.o.orInterprNodes.size());

            doc.clearActivations();
        }

        {
            Document doc = m.createDocument("aaaaaaaaaa", 0);

            inA.addInput(doc, 0, 6);
            System.out.println(doc.neuronActivationsToString(true, false, true));

            inB.addInput(doc, 3, 9);

            System.out.println(doc.neuronActivationsToString(true, false, true));

//            Assert.assertNotNull(Activation.get(t, inS.node, 0, new Range(0, 6), EQUALS, EQUALS, null, null, null));
            Assert.assertNotNull(NodeActivation.get(doc, inS.get().node.get(), null, new Range(0, 9), EQUALS, EQUALS, null, null));
//            Assert.assertEquals(1, Activation.get(t, inS.node, 0, new Range(0, 6), EQUALS, EQUALS, null, null, null).key.o.orInterprNodes.size());
            Assert.assertEquals(1, NodeActivation.get(doc, inS.get().node.get(), null, new Range(0, 6), EQUALS, EQUALS, null, null).key.o.orInterprNodes.size());
            Assert.assertEquals(1, NodeActivation.get(doc, inS.get().node.get(), null, new Range(0, 9), EQUALS, EQUALS, null, null).key.o.orInterprNodes.size());

            doc.clearActivations();
        }
    }


    @Test
    public void testNegation2() {
        Model m = new Model();

        Neuron inA = m.createNeuron("A");
        Neuron inB = m.createNeuron("B");
        Neuron inC = m.createNeuron("C");

        Neuron asN = m.createNeuron("AS");
        Neuron ascN = m.createNeuron("ASC");
        Neuron bsN = m.createNeuron("BS");

        Neuron inS = m.initNeuron(m.createNeuron("S"),
                -0.001,
                new Input()
                        .setNeuron(asN)
                        .setWeight(1.0f)
                        .setRecurrent(false)
                        .setRelativeRid(0)
                        .setBiasDelta(0.0)
                        .setRangeMatch(RangeRelation.EQUALS)
                        .setRangeOutput(true),
                new Input()
                        .setNeuron(ascN)
                        .setWeight(1.0f)
                        .setRecurrent(false)
                        .setRelativeRid(0)
                        .setBiasDelta(0.0)
                        .setRangeMatch(RangeRelation.EQUALS)
                        .setRangeOutput(true),
                new Input()
                        .setNeuron(bsN)
                        .setWeight(1.0f)
                        .setRecurrent(false)
                        .setRelativeRid(0)
                        .setBiasDelta(0.0)
                        .setRangeMatch(RangeRelation.EQUALS)
                        .setRangeOutput(true)
        );

        m.initNeuron(asN,
                0.001,
                new Input()
                        .setNeuron(inA)
                        .setWeight(1.0f)
                        .setRecurrent(false)
                        .setRelativeRid(0)
                        .setBiasDelta(1.0)
                        .setRangeMatch(RangeRelation.EQUALS)
                        .setRangeOutput(true),
                new Input()
                        .setNeuron(inS)
                        .setWeight(-1.0f)
                        .setRecurrent(true)
                        .setRelativeRid(0)
                        .setBiasDelta(1.0)
                        .setRangeMatch(RangeRelation.EQUALS)
                        .setRangeOutput(true)
        );
        m.initNeuron(ascN,
                0.001,
                new Input()
                        .setNeuron(inA)
                        .setWeight(1.0f)
                        .setRecurrent(false)
                        .setRelativeRid(0)
                        .setBiasDelta(1.0)
                        .setRangeMatch(RangeRelation.EQUALS)
                        .setRangeOutput(true),
                new Input()
                        .setNeuron(inC)
                        .setWeight(1.0f)
                        .setRecurrent(false)
                        .setRelativeRid(0)
                        .setBiasDelta(1.0)
                        .setRangeMatch(RangeRelation.EQUALS)
                        .setRangeOutput(true),
                new Input()
                        .setNeuron(inS)
                        .setWeight(-1.0f)
                        .setRecurrent(true)
                        .setRelativeRid(0)
                        .setBiasDelta(1.0)
                        .setRangeMatch(RangeRelation.EQUALS)
                        .setRangeOutput(true)
        );

        m.initNeuron(bsN,
                0.001,
                new Input()
                        .setNeuron(inB)
                        .setWeight(1.0f)
                        .setRecurrent(false)
                        .setRelativeRid(0)
                        .setBiasDelta(1.0)
                        .setRangeMatch(RangeRelation.EQUALS)
                        .setRangeOutput(true),
                new Input()
                        .setNeuron(inS)
                        .setWeight(-1.0f)
                        .setRecurrent(true)
                        .setRelativeRid(0)
                        .setBiasDelta(1.0)
                        .setRangeMatch(RangeRelation.EQUALS)
                        .setRangeOutput(true)
        );

        Neuron outA = m.initNeuron(m.createNeuron("OUT A"),
                -0.001,
                new Input()
                        .setNeuron(asN)
                        .setWeight(1.0f)
                        .setRecurrent(false)
                        .setRelativeRid(0)
                        .setBiasDelta(0.0)
                        .setRangeMatch(RangeRelation.EQUALS)
                        .setRangeOutput(true)
        );
        Neuron outAC = m.initNeuron(m.createNeuron("OUT AC"),
                -0.001,
                new Input()
                        .setNeuron(ascN)
                        .setWeight(1.0f)
                        .setRecurrent(false)
                        .setRelativeRid(0)
                        .setBiasDelta(0.0)
                        .setRangeMatch(RangeRelation.EQUALS)
                        .setRangeOutput(true)
        );
        Neuron outB = m.initNeuron(m.createNeuron("OUT B"),
                -0.001,
                new Input()
                        .setNeuron(bsN)
                        .setWeight(1.0f)
                        .setRecurrent(false)
                        .setRelativeRid(0)
                        .setBiasDelta(0.0)
                        .setRangeMatch(RangeRelation.EQUALS)
                        .setRangeOutput(true)
        );

        Document doc = m.createDocument("aaaaaaaaaa", 0);


//        asN.node.weight = 0.45;
//        ascN.node.weight = 1.0;

//        bsN.node.weight = 0.5;


        inA.addInput(doc, 0, 6);

        System.out.println(doc.neuronActivationsToString(true, false, true));

        inB.addInput(doc, 0, 6);

        System.out.println(doc.neuronActivationsToString(true, false, true));

        inC.addInput(doc, 0, 6);

        System.out.println(doc.neuronActivationsToString(true, false, true));

        doc.process();

        System.out.println(doc.nodeActivationsToString( false, true));
    }




    /**
     *
     *       -----
     *  A ---| &  |------
     *     -*| C  |     |       ------
     *     | ------     |   G---| &  |
     *      \           |       | H  |-----
     *       \/-----------------|    |
     *       /\-----------------|    |
     *      /           |       ------
     *     | ------     |
     *     -*| &  |------
     *  B ---| D  |
     *       ------
     *
     */

    @Test
    public void testOptions() {
        Model m = new Model();

        Neuron inA = m.createNeuron("A");
        Node inANode = inA.get().node.get();

        Neuron inB = m.createNeuron("B");
        Node inBNode = inB.get().node.get();


        Neuron pC = m.createNeuron("C");
        Neuron pD = m.createNeuron("D");

        m.initNeuron(pC,
                0.001,
                new Input()
                        .setNeuron(inA)
                        .setWeight(2.0f)
                        .setRecurrent(false)
                        .setRelativeRid(0)
                        .setBiasDelta(1.0),
                new Input()
                        .setNeuron(pD)
                        .setWeight(-2.0f)
                        .setRecurrent(true)
                        .setRelativeRid(0)
                        .setBiasDelta(1.0)
        );

        m.initNeuron(pD,
                0.001,
                new Input()
                        .setNeuron(inB)
                        .setWeight(2.0f)
                        .setRecurrent(false)
                        .setRelativeRid(0)
                        .setBiasDelta(1.0),
                new Input()
                        .setNeuron(pC)
                        .setWeight(-2.0f)
                        .setRecurrent(true)
                        .setRelativeRid(0)
                        .setBiasDelta(1.0)
        );


        Neuron inG = m.createNeuron("G");
        OrNode inGNode = inG.get().node.get();

        Neuron pH = m.initNeuron(m.createNeuron("H"),
                0.001,
                new Input()
                        .setNeuron(pC)
                        .setWeight(2.0f)
                        .setRecurrent(false)
                        .setRelativeRid(0)
                        .setBiasDelta(1.0),
                new Input()
                        .setNeuron(pD)
                        .setWeight(2.0f)
                        .setRecurrent(false)
                        .setRelativeRid(0)
                        .setBiasDelta(1.0),
                new Input()
                        .setNeuron(inG)
                        .setWeight(2.0f)
                        .setRecurrent(false)
                        .setRelativeRid(0)
                        .setBiasDelta(1.0)
        );

        Document doc = m.createDocument("aaaaaaaaaa", 0);

        inA.addInput(doc, 0, 1);
        inB.addInput(doc, 0, 1);
        inG.addInput(doc, 0, 1);

        System.out.println(doc.neuronActivationsToString(true, false, true));

        Assert.assertNotNull(pC.get().node.get().getFirstActivation(doc));
        Assert.assertNotNull(pD.get().node.get().getFirstActivation(doc));

        // Die Optionen 0 und 2 stehen in Konflikt. Da sie aber jetzt in Oder Optionen eingebettet sind, werden sie nicht mehr ausgefiltert.
//        Assert.assertNull(pH.node.getFirstActivation(t));
    }


}
