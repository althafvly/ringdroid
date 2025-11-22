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
package com.ringdroid.soundfile

class Atom {
    // note: latest versions of spec simply call it 'box' instead of 'atom'.
    private val mType: Int
    private val mVersion: Byte // if negative, then the atom does not contain version and flags data.
    private val mFlags: Int

    // get the size of the this atom.
    var size: Int // includes atom header (8 bytes)

    private var mData: ByteArray? // an atom can either contain data or children, but not both.
    private var mChildren: Array<Atom?>?

    // create an empty atom of the given type.
    constructor(type: String) {
        this.size = 8
        mType = getTypeInt(type)
        mData = null
        mChildren = null
        mVersion = -1
        mFlags = 0
    }

    // create an empty atom of type type, with a given version and flags.
    constructor(type: String, version: Byte, flags: Int) {
        size = 12
        mType = getTypeInt(type)
        mData = null
        mChildren = null
        mVersion = version
        mFlags = flags
    }

    // set the size field of the atom based on its content.
    private fun setSize() {
        var size = 8 // type + size
        if (mVersion >= 0) {
            size += 4 // version + flags
        }
        if (mData != null) {
            size += mData!!.size
        } else if (mChildren != null) {
            for (child in mChildren) {
                if (child != null) {
                    size += child.size
                }
            }
        }
        this.size = size
    }

    private fun getTypeInt(typeStr: String): Int {
        var type = 0
        type = type or ((typeStr[0]).code.toByte().toInt() shl 24)
        type = type or ((typeStr[1]).code.toByte().toInt() shl 16)
        type = type or ((typeStr[2]).code.toByte().toInt() shl 8)
        type = type or (typeStr[3]).code.toByte().toInt()
        return type
    }

    fun typeStr(): String {
        var type = ""
        type += Char((((mType shr 24) and 0xFF).toByte()).toUShort())
        type += Char((((mType shr 16) and 0xFF).toByte()).toUShort())
        type += Char((((mType shr 8) and 0xFF).toByte()).toUShort())
        type += Char(((mType and 0xFF).toByte()).toUShort())
        return type
    }

    var data: ByteArray?
        get() = mData
        set(data) {
            if (mChildren != null || data == null) {
                return
            }
            mData = data
            setSize()
        }

    fun addChild(child: Atom?) {
        if (mData != null || child == null) {
            return
        }
        var numChildren = 1
        if (mChildren != null) {
            numChildren += mChildren!!.size
        }
        val children: Array<Atom?> = arrayOfNulls(numChildren)
        if (mChildren != null) {
            System.arraycopy(mChildren!!, 0, children, 0, mChildren!!.size)
        }
        children[numChildren - 1] = child
        mChildren = children
        setSize()
    }

    // return the child atom of the corresponding type.
    // type can contain grand children: e.g. type = "trak.mdia.minf"
    // return null if the atom does not contain such a child.
    fun getChild(type: String): Atom? {
        if (mChildren == null) {
            return null
        }
        val types: Array<String?> = type.split("\\.".toRegex(), limit = 2).toTypedArray()
        for (child in mChildren) {
            if (child != null && child.typeStr() == types[0]) {
                return if (types.size == 1) {
                    child
                } else {
                    child.getChild(types[1]!!)
                }
            }
        }
        return null
    }

    fun bytes(): ByteArray {
        // return a byte array containing the full content of the atom (including
        val atomBytes = ByteArray(size)
        var offset = 0

        atomBytes[offset++] = ((size shr 24) and 0xFF).toByte()
        atomBytes[offset++] = ((size shr 16) and 0xFF).toByte()
        atomBytes[offset++] = ((size shr 8) and 0xFF).toByte()
        atomBytes[offset++] = (size and 0xFF).toByte()
        atomBytes[offset++] = ((mType shr 24) and 0xFF).toByte()
        atomBytes[offset++] = ((mType shr 16) and 0xFF).toByte()
        atomBytes[offset++] = ((mType shr 8) and 0xFF).toByte()
        atomBytes[offset++] = (mType and 0xFF).toByte()
        if (mVersion >= 0) {
            atomBytes[offset++] = mVersion
            atomBytes[offset++] = ((mFlags shr 16) and 0xFF).toByte()
            atomBytes[offset++] = ((mFlags shr 8) and 0xFF).toByte()
            atomBytes[offset++] = (mFlags and 0xFF).toByte()
        }
        if (mData != null) {
            System.arraycopy(mData, 0, atomBytes, offset, mData!!.size)
        } else if (mChildren != null) {
            var childBytes: ByteArray
            for (child in mChildren) {
                if (child != null) {
                    childBytes = child.bytes()
                    System.arraycopy(childBytes, 0, atomBytes, offset, childBytes.size)
                    offset += childBytes.size
                }
            }
        }
        return atomBytes
    }
}
