package com.ringdroid.soundfile;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class AtomTest {
    @Test
    public void testBasicAtom() {
        Atom atom = new Atom("test");
        assertEquals("test", atom.getTypeStr());
        assertEquals(8, atom.getSize());
    }

    @Test
    public void testAtomWithVersion() {
        Atom atom = new Atom("test", (byte) 1, 0x123456);
        assertEquals(12, atom.getSize());
    }

    @Test
    public void testSetData() {
        Atom atom = new Atom("data");
        byte[] data = new byte[]{1, 2, 3, 4};
        atom.setData(data);
        assertEquals(12, atom.getSize());
        assertArrayEquals(data, atom.getData());
    }

    @Test
    public void testAddChild() {
        Atom parent = new Atom("prnt");
        Atom child = new Atom("chld");
        parent.addChild(child);
        assertEquals(16, parent.getSize()); // 8 (parent header) + 8 (child size)

        Atom retrieved = parent.getChild("chld");
        assertNotNull(retrieved);
        assertEquals("chld", retrieved.getTypeStr());
    }

    @Test
    public void testGetBytes() {
        Atom atom = new Atom("test");
        byte[] bytes = atom.getBytes();
        assertEquals(8, bytes.length);
        // Size
        assertEquals(0, bytes[0]);
        assertEquals(0, bytes[1]);
        assertEquals(0, bytes[2]);
        assertEquals(8, bytes[3]);
        // Type
        assertEquals('t', bytes[4]);
        assertEquals('e', bytes[5]);
        assertEquals('s', bytes[6]);
        assertEquals('t', bytes[7]);
    }
}
