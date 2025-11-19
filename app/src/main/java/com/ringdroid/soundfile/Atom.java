/*
 * Copyright (C) 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ringdroid.soundfile;

public class Atom { // note: latest versions of spec simply call it 'box' instead of 'atom'.
    private final int mType;
    private final byte mVersion; // if negative, then the atom does not contain version and flags data.
    private final int mFlags;
    private int mSize; // includes atom header (8 bytes)
    private byte[] mData; // an atom can either contain data or children, but not both.
    private Atom[] mChildren;

    // create an empty atom of the given type.
    public Atom(String type) {
        mSize = 8;
        mType = getTypeInt(type);
        mData = null;
        mChildren = null;
        mVersion = -1;
        mFlags = 0;
    }

    // create an empty atom of type type, with a given version and flags.
    public Atom(String type, byte version, int flags) {
        mSize = 12;
        mType = getTypeInt(type);
        mData = null;
        mChildren = null;
        mVersion = version;
        mFlags = flags;
    }

    // set the size field of the atom based on its content.
    private void setSize() {
        int size = 8; // type + size
        if (mVersion >= 0) {
            size += 4; // version + flags
        }
        if (mData != null) {
            size += mData.length;
        } else if (mChildren != null) {
            for (Atom child : mChildren) {
                size += child.getSize();
            }
        }
        mSize = size;
    }

    // get the size of the this atom.
    public int getSize() {
        return mSize;
    }

    private int getTypeInt(String type_str) {
        int type = 0;
        type |= (byte) (type_str.charAt(0)) << 24;
        type |= (byte) (type_str.charAt(1)) << 16;
        type |= (byte) (type_str.charAt(2)) << 8;
        type |= (byte) (type_str.charAt(3));
        return type;
    }

    public String getTypeStr() {
        String type = "";
        type += (char) ((byte) ((mType >> 24) & 0xFF));
        type += (char) ((byte) ((mType >> 16) & 0xFF));
        type += (char) ((byte) ((mType >> 8) & 0xFF));
        type += (char) ((byte) (mType & 0xFF));
        return type;
    }

    public byte[] getData() {
        return mData;
    }

    public void setData(byte[] data) {
        if (mChildren != null || data == null) {
            return;
        }
        mData = data;
        setSize();
    }

    public void addChild(Atom child) {
        if (mData != null || child == null) {
            return;
        }
        int numChildren = 1;
        if (mChildren != null) {
            numChildren += mChildren.length;
        }
        Atom[] children = new Atom[numChildren];
        if (mChildren != null) {
            System.arraycopy(mChildren, 0, children, 0, mChildren.length);
        }
        children[numChildren - 1] = child;
        mChildren = children;
        setSize();
    }

    // return the child atom of the corresponding type.
    // type can contain grand children: e.g. type = "trak.mdia.minf"
    // return null if the atom does not contain such a child.
    public Atom getChild(String type) {
        if (mChildren == null) {
            return null;
        }
        String[] types = type.split("\\.", 2);
        for (Atom child : mChildren) {
            if (child.getTypeStr().equals(types[0])) {
                if (types.length == 1) {
                    return child;
                } else {
                    return child.getChild(types[1]);
                }
            }
        }
        return null;
    }

    // return a byte array containing the full content of the atom (including
    // header)
    public byte[] getBytes() {
        byte[] atom_bytes = new byte[mSize];
        int offset = 0;

        atom_bytes[offset++] = (byte) ((mSize >> 24) & 0xFF);
        atom_bytes[offset++] = (byte) ((mSize >> 16) & 0xFF);
        atom_bytes[offset++] = (byte) ((mSize >> 8) & 0xFF);
        atom_bytes[offset++] = (byte) (mSize & 0xFF);
        atom_bytes[offset++] = (byte) ((mType >> 24) & 0xFF);
        atom_bytes[offset++] = (byte) ((mType >> 16) & 0xFF);
        atom_bytes[offset++] = (byte) ((mType >> 8) & 0xFF);
        atom_bytes[offset++] = (byte) (mType & 0xFF);
        if (mVersion >= 0) {
            atom_bytes[offset++] = mVersion;
            atom_bytes[offset++] = (byte) ((mFlags >> 16) & 0xFF);
            atom_bytes[offset++] = (byte) ((mFlags >> 8) & 0xFF);
            atom_bytes[offset++] = (byte) (mFlags & 0xFF);
        }
        if (mData != null) {
            System.arraycopy(mData, 0, atom_bytes, offset, mData.length);
        } else if (mChildren != null) {
            byte[] child_bytes;
            for (Atom child : mChildren) {
                child_bytes = child.getBytes();
                System.arraycopy(child_bytes, 0, atom_bytes, offset, child_bytes.length);
                offset += child_bytes.length;
            }
        }
        return atom_bytes;
    }
}
