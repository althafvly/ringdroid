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

class MP4Header(
    sampleRate: Int,
    numChannels: Int,
    frameSize: IntArray?,
    bitrate: Int,
) {
    // size of each AAC frames, in bytes. First one should be 2.
    private var mFrameSize: IntArray?

    // size of the biggest frame.
    private var mMaxFrameSize: Int

    // size of the AAC stream.
    private var mTotSize: Int

    // bitrate used to encode the AAC stream.
    private val mBitrate: Int

    // time used for 'creation time' and 'modification time' fields.
    private val mTime: ByteArray

    // duration of stream in milliseconds.
    private val mDurationMS: ByteArray

    // number of samples in the stream.
    private val mNumSamples: ByteArray

    // the complete header.
    private var mHeader: ByteArray? = null

    // sampling frequency in Hz (e.g. 44100).
    private var mSampleRate: Int

    // number of channels.
    private val mChannels: Int

    // Creates a new MP4Header object that should be used to generate an .m4a file
    // header.
    init {
        require(frameSize != null && frameSize.size >= 2 && frameSize[0] == 2) {
            "frameSize must be non-null, size >= 2, and first element == 2"
        }

        mSampleRate = sampleRate
        mChannels = numChannels
        mFrameSize = frameSize
        mBitrate = bitrate
        mMaxFrameSize = mFrameSize!![0]
        mTotSize = mFrameSize!![0]
        for (i in 1..<mFrameSize!!.size) {
            if (mMaxFrameSize < mFrameSize!![i]) {
                mMaxFrameSize = mFrameSize!![i]
            }
            mTotSize += mFrameSize!![i]
        }
        var time = System.currentTimeMillis() / 1000
        // number of seconds between 1904 and 1970
        time += ((66 * 365 + 16) * 24 * 60 * 60).toLong()
        mTime = ByteArray(4)
        mTime[0] = ((time shr 24) and 0xFFL).toByte()
        mTime[1] = ((time shr 16) and 0xFFL).toByte()
        mTime[2] = ((time shr 8) and 0xFFL).toByte()
        mTime[3] = (time and 0xFFL).toByte()
        // 1st frame does not contain samples.
        val numSamples = 1024 * (frameSize.size - 1)
        var durationMS = (numSamples * 1000) / mSampleRate
        // round the duration up.
        if ((numSamples * 1000) % mSampleRate > 0) {
            durationMS++
        }
        mNumSamples =
            byteArrayOf(
                ((numSamples shr 26) and 0XFF).toByte(),
                ((numSamples shr 16) and 0XFF).toByte(),
                ((numSamples shr 8) and 0XFF).toByte(),
                (numSamples and 0XFF).toByte(),
            )
        mDurationMS =
            byteArrayOf(
                ((durationMS shr 26) and 0XFF).toByte(),
                ((durationMS shr 16) and 0XFF).toByte(),
                ((durationMS shr 8) and 0XFF).toByte(),
                (durationMS and 0XFF).toByte(),
            )
        setHeader()
    }

    private fun setHeader() {
        // create the atoms needed to build the header.
        val aFtyp: Atom = fTYPAtom()
        val aMoov: Atom = mOOVAtom()
        val aMdat = Atom("mdat") // create an empty atom. The AAC stream data should follow

        // immediately after. The correct size will be set later.
        // set the correct chunk offset in the stco atom.
        val aStco = aMoov.getChild("trak.mdia.minf.stbl.stco")
        if (aStco == null) {
            mHeader = null
            return
        }
        val data = aStco.data
        val chunkOffset = aFtyp.size + aMoov.size + aMdat.size
        // here stco should contain only one chunk offset.
        var offset = data!!.size - 4
        data[offset++] = ((chunkOffset shr 24) and 0xFF).toByte()
        data[offset++] = ((chunkOffset shr 16) and 0xFF).toByte()
        data[offset++] = ((chunkOffset shr 8) and 0xFF).toByte()
        data[offset++] = (chunkOffset and 0xFF).toByte()

        // create the header byte array based on the previous atoms.
        val header = ByteArray(chunkOffset) // here chunk_offset is also the size of the header
        offset = 0
        for (atom in arrayOf(aFtyp, aMoov, aMdat)) {
            val atomBytes = atom.bytes()
            System.arraycopy(atomBytes, 0, header, offset, atomBytes.size)
            offset += atomBytes.size
        }

        // set the correct size of the mdat atom
        val size = 8 + mTotSize
        offset -= 8
        header[offset++] = ((size shr 24) and 0xFF).toByte()
        header[offset++] = ((size shr 16) and 0xFF).toByte()
        header[offset++] = ((size shr 8) and 0xFF).toByte()
        header[offset++] = (size and 0xFF).toByte()

        mHeader = header
    }

    private fun fTYPAtom(): Atom {
        val atom = Atom("ftyp")
        atom.data =
            byteArrayOf(
                'M'.code.toByte(),
                '4'.code.toByte(),
                'A'.code.toByte(),
                ' '.code.toByte(), // Major brand
                0,
                0,
                0,
                0, // Minor version
                'M'.code.toByte(),
                '4'.code.toByte(),
                'A'.code.toByte(),
                ' '.code.toByte(), // compatible brands
                'm'.code.toByte(),
                'p'.code.toByte(),
                '4'.code.toByte(),
                '2'.code.toByte(),
                'i'.code.toByte(),
                's'.code.toByte(),
                'o'.code.toByte(),
                'm'.code.toByte(),
            )
        return atom
    }

    private fun mOOVAtom(): Atom {
        val atom = Atom("moov")
        atom.addChild(mVHDAtom())
        atom.addChild(tRAKAtom())
        return atom
    }

    private fun mVHDAtom(): Atom {
        val atom =
            Atom("mvhd", 0.toByte(), 0)
        atom.data =
            byteArrayOf(
                mTime[0],
                mTime[1],
                mTime[2],
                mTime[3], // creation time.
                mTime[0],
                mTime[1],
                mTime[2],
                mTime[3], // modification time.
                0,
                0,
                0x03,
                0xE8.toByte(), // timescale = 1000 => duration expressed in ms.
                mDurationMS[0],
                mDurationMS[1],
                mDurationMS[2],
                mDurationMS[3], // duration in ms.
                0,
                1,
                0,
                0, // rate = 1.0
                1,
                0, // volume = 1.0
                0,
                0, // reserved
                0,
                0,
                0,
                0, // reserved
                0,
                0,
                0,
                0, // reserved
                0,
                1,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0, // unity matrix
                0,
                0,
                0,
                0,
                0,
                1,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0x40,
                0,
                0,
                0,
                0,
                0,
                0,
                0, // pre-defined
                0,
                0,
                0,
                0, // pre-defined
                0,
                0,
                0,
                0, // pre-defined
                0,
                0,
                0,
                0, // pre-defined
                0,
                0,
                0,
                0, // pre-defined
                0,
                0,
                0,
                0, // pre-defined
                0,
                0,
                0,
                2, // next track ID
            )
        return atom
    }

    private fun tRAKAtom(): Atom {
        val atom = Atom("trak")
        atom.addChild(tKHDAtom())
        atom.addChild(mDIAAtom())
        return atom
    }

    private fun tKHDAtom(): Atom {
        val atom =
            Atom(
                "tkhd",
                0.toByte(),
                0x07,
            ) // track enabled, in movie, and in preview.
        atom.data =
            byteArrayOf(
                mTime[0],
                mTime[1],
                mTime[2],
                mTime[3], // creation time.
                mTime[0],
                mTime[1],
                mTime[2],
                mTime[3], // modification time.
                0,
                0,
                0,
                1, // track ID
                0,
                0,
                0,
                0, // reserved
                mDurationMS[0],
                mDurationMS[1],
                mDurationMS[2],
                mDurationMS[3], // duration in ms.
                0,
                0,
                0,
                0, // reserved
                0,
                0,
                0,
                0, // reserved
                0,
                0, // layer
                0,
                0, // alternate group
                1,
                0, // volume = 1.0
                0,
                0, // reserved
                0,
                1,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0, // unity matrix
                0,
                0,
                0,
                0,
                0,
                1,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0x40,
                0,
                0,
                0,
                0,
                0,
                0,
                0, // width
                0,
                0,
                0,
                0, // height
            )
        return atom
    }

    private fun mDIAAtom(): Atom {
        val atom = Atom("mdia")
        atom.addChild(mDHDAtom())
        atom.addChild(hDLRAtom())
        atom.addChild(mINFAtom())
        return atom
    }

    private fun mDHDAtom(): Atom {
        val atom =
            Atom("mdhd", 0.toByte(), 0)
        atom.data =
            byteArrayOf(
                mTime[0],
                mTime[1],
                mTime[2],
                mTime[3], // creation time.
                mTime[0],
                mTime[1],
                mTime[2],
                mTime[3], // modification time.
                (mSampleRate shr 24).toByte(),
                (mSampleRate shr 16).toByte(), // timescale = Fs =>
                (mSampleRate shr 8).toByte(),
                (mSampleRate).toByte(), // duration expressed in samples.
                mNumSamples[0],
                mNumSamples[1],
                mNumSamples[2],
                mNumSamples[3], // duration
                0,
                0, // languages
                0,
                0, // pre-defined
            )
        return atom
    }

    private fun hDLRAtom(): Atom {
        val atom =
            Atom("hdlr", 0.toByte(), 0)
        atom.data =
            byteArrayOf(
                0,
                0,
                0,
                0, // pre-defined
                's'.code.toByte(),
                'o'.code.toByte(),
                'u'.code.toByte(),
                'n'.code.toByte(), // handler type
                0,
                0,
                0,
                0, // reserved
                0,
                0,
                0,
                0, // reserved
                0,
                0,
                0,
                0, // reserved
                'S'.code.toByte(),
                'o'.code.toByte(),
                'u'.code.toByte(),
                'n'.code.toByte(), // name (used only for debugging and inspection purposes).
                'd'.code.toByte(),
                'H'.code.toByte(),
                'a'.code.toByte(),
                'n'.code.toByte(),
                'd'.code.toByte(),
                'l'.code.toByte(),
                'e'.code.toByte(),
                '\u0000'.code.toByte(),
            )
        return atom
    }

    private fun mINFAtom(): Atom {
        val atom = Atom("minf")
        atom.addChild(sMHDAtom())
        atom.addChild(dINFAtom())
        atom.addChild(sTBLAtom())
        return atom
    }

    private fun sMHDAtom(): Atom {
        val atom =
            Atom("smhd", 0.toByte(), 0)
        atom.data =
            byteArrayOf(
                0,
                0, // balance (center)
                0,
                0, // reserved
            )
        return atom
    }

    private fun dINFAtom(): Atom {
        val atom = Atom("dinf")
        atom.addChild(dREFAtom())
        return atom
    }

    private fun dREFAtom(): Atom {
        val atom =
            Atom("dref", 0.toByte(), 0)
        val url: ByteArray = uRLAtom.bytes()
        val data = ByteArray(4 + url.size)
        data[3] = 0x01 // entry count = 1
        System.arraycopy(url, 0, data, 4, url.size)
        atom.data = data
        return atom
    }

    private val uRLAtom: Atom = Atom("url ", 0.toByte(), 0x01)

    private fun sTBLAtom(): Atom {
        val atom = Atom("stbl")
        atom.addChild(sTSDAtom())
        atom.addChild(sTTSAtom())
        atom.addChild(sTSCAtom())
        atom.addChild(sTSZAtom())
        atom.addChild(sTCOAtom())
        return atom
    }

    private fun sTSDAtom(): Atom {
        val atom =
            Atom("stsd", 0.toByte(), 0)
        val mp4a = mP4AAtom().bytes()
        val data = ByteArray(4 + mp4a.size)
        data[3] = 0x01 // entry count = 1
        System.arraycopy(mp4a, 0, data, 4, mp4a.size)
        atom.data = data
        return atom
    }

    private fun mP4AAtom(): Atom {
        // See also Part 14 section 5.6.1 of ISO/IEC 14496 for this atom.
        val atom = Atom("mp4a")
        val ase =
            byteArrayOf(
                // Audio Sample Entry data
                0,
                0,
                0,
                0,
                0,
                0, // reserved
                0,
                1, // data reference index
                0,
                0,
                0,
                0, // reserved
                0,
                0,
                0,
                0, // reserved
                (mChannels shr 8).toByte(),
                mChannels.toByte(), // channel count
                0,
                0x10, // sample size
                0,
                0, // pre-defined
                0,
                0, // reserved
                (mSampleRate shr 8).toByte(),
                (mSampleRate).toByte(),
                0,
                0, // sample rate
            )
        val esds: ByteArray = eSDSAtom().bytes()
        val data = ByteArray(ase.size + esds.size)
        System.arraycopy(ase, 0, data, 0, ase.size)
        System.arraycopy(esds, 0, data, ase.size, esds.size)
        atom.data = data
        return atom
    }

    private fun eSDSAtom(): Atom {
        val atom =
            Atom("esds", 0.toByte(), 0)
        atom.data = eSDescriptor()
        return atom
    }

    private fun eSDescriptor(): ByteArray {
        // Returns an ES Descriptor for an ISO/IEC 14496-3 audio stream, AAC LC,
        val samplingFrequencies =
            intArrayOf(
                96000,
                88200,
                64000,
                48000,
                44100,
                32000,
                24000,
                22050,
                16000,
                12000,
                11025,
                8000,
                7350,
            )
        // First 5 bytes of the ES Descriptor.
        val eSDescriptorTop =
            byteArrayOf(0x03, 0x19, 0x00, 0x00, 0x00)
        // First 4 bytes of Decoder Configuration Descriptor. Audio ISO/IEC 14496-3,
        // AudioStream.
        val decConfigDescrTop = byteArrayOf(0x04, 0x11, 0x40, 0x15)
        // Audio Specific Configuration: AAC LC, 1024 samples/frame/channel.
        // Sampling frequency and channels configuration are not set yet.
        val audioSpecificConfig = byteArrayOf(0x05, 0x02, 0x10, 0x00)
        val slConfigDescr =
            byteArrayOf(0x06, 0x01, 0x02) // specific for MP4 file.
        var bufferSize = 0x300
        while (bufferSize < 2 * mMaxFrameSize) {
            // TODO(nfaralli): what should be the minimum size of the decoder buffer?
            // Should it be a multiple of 256?
            bufferSize += 0x100
        }

        // create the Decoder Configuration Descriptor
        val decConfigDescr = ByteArray(2 + decConfigDescrTop[1])
        System.arraycopy(
            decConfigDescrTop,
            0,
            decConfigDescr,
            0,
            decConfigDescrTop.size,
        )
        var offset: Int = decConfigDescrTop.size
        decConfigDescr[offset++] = ((bufferSize shr 16) and 0xFF).toByte()
        decConfigDescr[offset++] = ((bufferSize shr 8) and 0xFF).toByte()
        decConfigDescr[offset++] = (bufferSize and 0xFF).toByte()
        decConfigDescr[offset++] = ((mBitrate shr 24) and 0xFF).toByte()
        decConfigDescr[offset++] = ((mBitrate shr 16) and 0xFF).toByte()
        decConfigDescr[offset++] = ((mBitrate shr 8) and 0xFF).toByte()
        decConfigDescr[offset++] = (mBitrate and 0xFF).toByte()
        decConfigDescr[offset++] = ((mBitrate shr 24) and 0xFF).toByte()
        decConfigDescr[offset++] = ((mBitrate shr 16) and 0xFF).toByte()
        decConfigDescr[offset++] = ((mBitrate shr 8) and 0xFF).toByte()
        decConfigDescr[offset++] = (mBitrate and 0xFF).toByte()
        var index: Int
        index = 0
        while (index < samplingFrequencies.size) {
            if (samplingFrequencies[index] == mSampleRate) {
                break
            }
            index++
        }
        if (index == samplingFrequencies.size) {
            // TODO(nfaralli): log something here.
            // Invalid sampling frequency. Default to 44100Hz...
            index = 4
        }
        audioSpecificConfig[2] =
            (audioSpecificConfig[2].toInt() or ((index shr 1) and 0x07)).toByte()
        audioSpecificConfig[3] =
            (audioSpecificConfig[3].toInt() or (((index and 1) shl 7) or ((mChannels and 0x0F) shl 3))).toByte()
        System.arraycopy(
            audioSpecificConfig,
            0,
            decConfigDescr,
            offset,
            audioSpecificConfig.size,
        )

        // create the ES Descriptor
        val eSDescriptor = ByteArray(2 + eSDescriptorTop[1])
        System.arraycopy(eSDescriptorTop, 0, eSDescriptor, 0, eSDescriptorTop.size)
        offset = eSDescriptorTop.size
        System.arraycopy(decConfigDescr, 0, eSDescriptor, offset, decConfigDescr.size)
        offset += decConfigDescr.size
        System.arraycopy(slConfigDescr, 0, eSDescriptor, offset, slConfigDescr.size)
        return eSDescriptor
    }

    private fun sTTSAtom(): Atom {
        val atom =
            Atom("stts", 0.toByte(), 0)
        val numAudioFrames = mFrameSize!!.size - 1
        atom.data =
            byteArrayOf(
                0,
                0,
                0,
                0x02, // entry count
                0,
                0,
                0,
                0x01, // first frame contains no audio
                0,
                0,
                0,
                0,
                ((numAudioFrames shr 24) and 0xFF).toByte(),
                ((numAudioFrames shr 16) and 0xFF).toByte(),
                ((numAudioFrames shr 8) and 0xFF).toByte(),
                (numAudioFrames and 0xFF).toByte(),
                0,
                0,
                0x04,
                0, // delay between
                // frames = 1024
                // samples (cf.
                // timescale =
                // Fs)
            )
        return atom
    }

    private fun sTSCAtom(): Atom {
        val atom =
            Atom("stsc", 0.toByte(), 0)
        val numFrames = mFrameSize!!.size
        atom.data =
            byteArrayOf(
                0,
                0,
                0,
                0x01, // entry count
                0,
                0,
                0,
                0x01, // first chunk
                ((numFrames shr 24) and 0xFF).toByte(),
                ((numFrames shr 16) and 0xFF).toByte(), // samples per
                ((numFrames shr 8) and 0xFF).toByte(),
                (numFrames and 0xFF).toByte(), // chunk
                0,
                0,
                0,
                0x01, // sample description index
            )
        return atom
    }

    private fun sTSZAtom(): Atom {
        val atom =
            Atom("stsz", 0.toByte(), 0)
        val numFrames = mFrameSize!!.size
        val data = ByteArray(8 + 4 * numFrames)
        var offset = 0
        data[offset++] = 0 // sample size (=0 => each frame can have a different size)
        data[offset++] = 0
        data[offset++] = 0
        data[offset++] = 0
        data[offset++] = ((numFrames shr 24) and 0xFF).toByte() // sample count
        data[offset++] = ((numFrames shr 16) and 0xFF).toByte()
        data[offset++] = ((numFrames shr 8) and 0xFF).toByte()
        data[offset++] = (numFrames and 0xFF).toByte()
        for (size in mFrameSize!!) {
            data[offset++] = ((size shr 24) and 0xFF).toByte()
            data[offset++] = ((size shr 16) and 0xFF).toByte()
            data[offset++] = ((size shr 8) and 0xFF).toByte()
            data[offset++] = (size and 0xFF).toByte()
        }
        atom.data = data
        return atom
    }

    private fun sTCOAtom(): Atom {
        val atom =
            Atom("stco", 0.toByte(), 0)
        atom.data =
            byteArrayOf(
                0,
                0,
                0,
                0x01, // entry count
                0,
                0,
                0,
                0, // chunk offset. Set to 0 here. Must be set later. Here it should be
                // the size of the complete header, as the AAC stream will follow
                // immediately.
            )
        return atom
    }

    companion object {
        @JvmStatic
        fun getMP4Header(
            sampleRate: Int,
            numChannels: Int,
            frameSize: IntArray?,
            bitrate: Int,
        ): ByteArray? = MP4Header(sampleRate, numChannels, frameSize, bitrate).mHeader
    }
}
