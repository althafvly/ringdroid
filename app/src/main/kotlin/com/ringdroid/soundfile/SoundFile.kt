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

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaRecorder
import android.util.Log
import com.ringdroid.FilesUtil.getStackTrace
import com.ringdroid.soundfile.MP4Header.Companion.getMP4Header
import com.ringdroid.soundfile.WAVHeader.Companion.getWAVHeader
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer
import java.util.Locale
import java.util.Objects
import kotlin.math.abs
import kotlin.math.sqrt

class SoundFile // A SoundFile object should only be created using the static methods create()
// and record().
    private constructor() {
        private var mProgressListener: ProgressListener? = null
        private var mInputFile: File? = null

        // Member variables representing frame data
        var filetype: String? = null
        private var mFileSize = 0
        var avgBitrateKbps: Int = 0 // Average bit rate in kbps.
        var sampleRate: Int = 0
        var channels: Int = 0

        // Number of samples per channel.
        var numSamples: Int = 0 // total number of samples per channel in audio file
        private var mDecodedBytes: ByteBuffer? = null // Raw audio data
        private var mDecodedSamples: ShortBuffer? = null // shared buffer with mDecodedBytes.

        // Should be removed when the app will use directly the samples instead of the
        // frames.
        // mDecodedSamples has the following format:
        // {s1c1, s1c2, ..., s1cM, s2c1, ..., s2cM, ..., sNc1, ..., sNcM}
        // where sicj is the ith sample of the jth channel (a sample is a signed short)
        // M is the number of channels (e.g. 2 for stereo) and N is the number of
        // samples per channel.
        // Member variables for hack (making it work with old version, until app just
        // uses the samples).
        var numFrames: Int = 0

        // Should be removed when the app will use directly the samples instead of the
        // frames.
        lateinit var frameGains: IntArray
        private var mFrameLens: IntArray? = null
        private var mFrameOffsets: IntArray? = null

        // Should be removed when the app will use directly the samples instead of the
        val samplesPerFrame: Int = 1024 // just a fixed value here...

        fun samples(): ShortBuffer? =
            if (mDecodedSamples != null) {
                mDecodedSamples!!.asReadOnlyBuffer()
            } else {
                null
            }

        private fun setProgressListener(progressListener: ProgressListener?) {
            mProgressListener = progressListener
        }

        @Throws(IOException::class, InvalidInputException::class)
        private fun readFile(inputFile: File?) {
            val extractor = MediaExtractor()
            var format: MediaFormat? = null

            mInputFile = inputFile
            val components: Array<String?> =
                mInputFile!!
                    .path
                    .split("\\.".toRegex())
                    .dropLastWhile { it.isEmpty() }
                    .toTypedArray()
            this.filetype = components[components.size - 1]
            mFileSize = mInputFile!!.length().toInt()
            extractor.setDataSource(mInputFile!!.path)
            val numTracks = extractor.trackCount
            // find and select the first audio track present in the file.
            var i = 0
            while (i < numTracks) {
                format = extractor.getTrackFormat(i)
                if (Objects
                        .requireNonNull<String>(format.getString(MediaFormat.KEY_MIME))
                        .startsWith("audio/")
                ) {
                    extractor.selectTrack(i)
                    break
                }
                i++
            }
            if (i == numTracks) {
                throw InvalidInputException("No audio track found in $mInputFile")
            }
            checkNotNull(format)
            this.channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            this.sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            // Expected total number of samples per channel.
            val expectedNumSamples =
                ((format.getLong(MediaFormat.KEY_DURATION) / 1000000f) * this.sampleRate + 0.5f).toInt()

            val codec =
                MediaCodec.createDecoderByType(
                    Objects.requireNonNull<String>(
                        format.getString(MediaFormat.KEY_MIME),
                    ),
                )
            codec.configure(format, null, null, 0)
            codec.start()

            var decodedSamplesSize = 0 // size of the output buffer containing decoded samples.
            var decodedSamples: ByteArray? = null
            val inputBuffers = codec.inputBuffers
            var outputBuffers = codec.outputBuffers
            var sampleSize: Int
            val info = MediaCodec.BufferInfo()
            var presentationTime: Long
            var totSizeRead = 0
            var doneReading = false

            // Set the size of the decoded samples buffer to 1MB (~6sec of a stereo stream
            // at 44.1kHz).
            // For longer streams, the buffer size will be increased later on, calculating a
            // rough
            // estimate of the total size needed to store all the samples in order to resize
            // the buffer
            // only once.
            mDecodedBytes = ByteBuffer.allocate(1 shl 20)
            var firstSampleData = true
            while (true) {
                // read data from file and feed it to the decoder input buffers.
                val inputBufferIndex = codec.dequeueInputBuffer(100)
                if (!doneReading && inputBufferIndex >= 0) {
                    sampleSize = extractor.readSampleData(inputBuffers[inputBufferIndex]!!, 0)
                    if (firstSampleData && format.getString(MediaFormat.KEY_MIME) == "audio/mp4a-latm" &&
                        sampleSize == 2
                    ) {
                        // For some reasons on some devices (e.g. the Samsung S3) you should not
                        // provide the first two bytes of an AAC stream, otherwise the MediaCodec will
                        // crash. These two bytes do not contain music data but basic info on the
                        // stream (e.g. channel configuration and sampling frequency), and skipping them
                        // seems OK with other devices (MediaCodec has already been configured and
                        // already knows these parameters).
                        extractor.advance()
                        totSizeRead += sampleSize
                    } else if (sampleSize < 0) {
                        // All samples have been read.
                        codec.queueInputBuffer(
                            inputBufferIndex,
                            0,
                            0,
                            -1,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                        )
                        doneReading = true
                    } else {
                        presentationTime = extractor.sampleTime
                        codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, presentationTime, 0)
                        extractor.advance()
                        totSizeRead += sampleSize
                        if (mProgressListener != null) {
                            if (mProgressListener!!.reportProgress(((totSizeRead).toFloat() / mFileSize).toDouble())) {
                                // We are asked to stop reading the file. Returning immediately. The
                                // SoundFile object is invalid and should NOT be used afterward!
                                extractor.release()
                                codec.stop()
                                codec.release()
                                return
                            }
                        }
                    }
                    firstSampleData = false
                }

                // Get decoded stream from the decoder output buffers.
                val outputBufferIndex = codec.dequeueOutputBuffer(info, 100)
                if (outputBufferIndex >= 0 && info.size > 0) {
                    if (decodedSamplesSize < info.size) {
                        decodedSamplesSize = info.size
                        decodedSamples = ByteArray(decodedSamplesSize)
                    }
                    outputBuffers[outputBufferIndex]!!.get(decodedSamples!!, 0, info.size)
                    outputBuffers[outputBufferIndex]!!.clear()
                    // Check if buffer is big enough. Resize it if it's too small.
                    if (mDecodedBytes!!.remaining() < info.size) {
                        // Getting a rough estimate of the total size, allocate 20% more, and
                        // make sure to allocate at least 5MB more than the initial size.
                        val position = mDecodedBytes!!.position()
                        var newSize = ((position * (1.0 * mFileSize / totSizeRead)) * 1.2).toInt()
                        if (newSize - position < info.size + 5 * (1 shl 20)) {
                            newSize = position + info.size + 5 * (1 shl 20)
                        }
                        var newDecodedBytes: ByteBuffer? = null
                        // Try to allocate memory. If we are OOM, try to run the garbage collector.
                        var retry = 10
                        while (retry > 0) {
                            try {
                                newDecodedBytes = ByteBuffer.allocate(newSize)
                                break
                            } catch (oome: OutOfMemoryError) {
                                // setting android:largeHeap="true" in <application> seem to help not
                                // reaching this section.
                                retry--
                            }
                        }
                        if (retry == 0) {
                            // Failed to allocate memory... Stop reading more data and finalize the
                            // instance with the data decoded so far.
                            break
                        }
                        // ByteBuffer newDecodedBytes = ByteBuffer.allocate(newSize);
                        mDecodedBytes!!.rewind()
                        checkNotNull(newDecodedBytes)
                        newDecodedBytes.put(mDecodedBytes!!)
                        mDecodedBytes = newDecodedBytes
                        mDecodedBytes!!.position(position)
                    }
                    mDecodedBytes!!.put(decodedSamples, 0, info.size)
                    codec.releaseOutputBuffer(outputBufferIndex, false)
                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    outputBuffers = codec.outputBuffers
                }
                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0 ||
                    (mDecodedBytes!!.position() / (2 * this.channels)) >= expectedNumSamples
                ) {
                    // We got all the decoded data from the decoder. Stop here.
                    // Theoretically dequeueOutputBuffer(info, ...) should have set info.flags to
                    // MediaCodec.BUFFER_FLAG_END_OF_STREAM. However some phones (e.g. Samsung S3)
                    // won't do that for some files (e.g. with mono AAC files), in which case
                    // subsequent
                    // calls to dequeueOutputBuffer may result in the application crashing, without
                    // even an exception being thrown... Hence the second check.
                    // (for mono AAC files, the S3 will actually double each sample, as if the
                    // stream
                    // was stereo. The resulting stream is half what it's supposed to be and with a
                    // much
                    // lower pitch.)
                    break
                }
            }
            this.numSamples = mDecodedBytes!!.position() / (this.channels * 2) // One sample = 2 bytes.
            mDecodedBytes!!.rewind()
            mDecodedBytes!!.order(ByteOrder.LITTLE_ENDIAN)
            mDecodedSamples = mDecodedBytes!!.asShortBuffer()
            this.avgBitrateKbps =
                ((mFileSize * 8) * (sampleRate.toFloat() / this.numSamples) / 1000).toInt()

            extractor.release()
            codec.stop()
            codec.release()

            // Temporary hack to make it work with the old version.
            this.numFrames = this.numSamples / this.samplesPerFrame
            if (this.numSamples % this.samplesPerFrame != 0) {
                this.numFrames++
            }
            this.frameGains = IntArray(this.numFrames)
            mFrameLens = IntArray(this.numFrames)
            mFrameOffsets = IntArray(this.numFrames)
            var j: Int
            var gain: Int
            var value: Int
            val frameLens =
                (((1000 * this.avgBitrateKbps).toFloat() / 8) * (this.samplesPerFrame.toFloat() / this.sampleRate)).toInt()
            i = 0
            while (i < this.numFrames) {
                gain = -1
                j = 0
                while (j < this.samplesPerFrame) {
                    value = 0
                    for (k in 0..<this.channels) {
                        if (mDecodedSamples!!.remaining() > 0) {
                            value += abs(mDecodedSamples!!.get().toInt())
                        }
                    }
                    value /= this.channels
                    if (gain < value) {
                        gain = value
                    }
                    j++
                }
                this.frameGains[i] =
                    sqrt(gain.toDouble()).toInt() // here gain = sqrt(max value of 1st channel)...
                mFrameLens!![i] = frameLens // totally not accurate...
                mFrameOffsets!![i] =
                    (
                        i * ((1000 * this.avgBitrateKbps).toFloat() / 8) * // = i * frameLens
                            (this.samplesPerFrame.toFloat() / this.sampleRate)
                    ).toInt()
                i++
            }
            mDecodedSamples!!.rewind()
        }

        @SuppressLint("MissingPermission")
        private fun recordAudio() {
            if (mProgressListener == null) {
                // A progress listener is mandatory here, as it will let us know when to stop
                // recording.
                return
            }
            mInputFile = null
            this.filetype = "raw"
            mFileSize = 0
            this.sampleRate = 44100
            this.channels = 1 // record mono audio.
            val buffer = ShortArray(1024) // buffer contains 1 mono frame of 1024 16 bits samples
            var minBufferSize =
                AudioRecord.getMinBufferSize(
                    this.sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                )
            // make sure minBufferSize can contain at least 1 second of audio (16 bits
            // sample).
            if (minBufferSize < this.sampleRate * 2) {
                minBufferSize = this.sampleRate * 2
            }
            val audioRecord =
                AudioRecord(
                    MediaRecorder.AudioSource.DEFAULT,
                    this.sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBufferSize,
                )

            // Allocate memory for 20 seconds first. Reallocate later if more is needed.
            mDecodedBytes = ByteBuffer.allocate(20 * this.sampleRate * 2)
            mDecodedBytes!!.order(ByteOrder.LITTLE_ENDIAN)
            mDecodedSamples = mDecodedBytes!!.asShortBuffer()
            audioRecord.startRecording()
            while (true) {
                // check if mDecodedSamples can contain 1024 additional samples.
                if (mDecodedSamples!!.remaining() < 1024) {
                    // Try to allocate memory for 10 additional seconds.
                    val newCapacity = mDecodedBytes!!.capacity() + 10 * this.sampleRate * 2
                    val newDecodedBytes: ByteBuffer
                    try {
                        newDecodedBytes = ByteBuffer.allocate(newCapacity)
                    } catch (oome: OutOfMemoryError) {
                        break
                    }
                    val position = mDecodedSamples!!.position()
                    mDecodedBytes!!.rewind()
                    newDecodedBytes.put(mDecodedBytes!!)
                    mDecodedBytes = newDecodedBytes
                    mDecodedBytes!!.order(ByteOrder.LITTLE_ENDIAN)
                    mDecodedBytes!!.rewind()
                    mDecodedSamples = mDecodedBytes!!.asShortBuffer()
                    mDecodedSamples!!.position(position)
                }
                // TODO(nfaralli): maybe use the read method that takes a direct ByteBuffer
                // argument.
                audioRecord.read(buffer, 0, buffer.size)
                mDecodedSamples!!.put(buffer)
                // Let the progress listener know how many seconds have been recorded.
                // The returned value tells us if we should keep recording or stop.
                if (mProgressListener!!.reportProgress(((mDecodedSamples!!.position()).toFloat() / this.sampleRate).toDouble())) {
                    break
                }
            }
            audioRecord.stop()
            audioRecord.release()
            this.numSamples = mDecodedSamples!!.position()
            mDecodedSamples!!.rewind()
            mDecodedBytes!!.rewind()
            this.avgBitrateKbps = this.sampleRate * 16 / 1000

            // Temporary hack to make it work with the old version.
            this.numFrames = this.numSamples / this.samplesPerFrame
            if (this.numSamples % this.samplesPerFrame != 0) {
                this.numFrames++
            }
            this.frameGains = IntArray(this.numFrames)
            mFrameLens = null // not needed for recorded audio
            mFrameOffsets = null
            var j: Int
            var gain: Int
            var value: Int
            // not needed for recorded audio
            var i = 0
            while (i < this.numFrames) {
                gain = -1
                j = 0
                while (j < this.samplesPerFrame) {
                    value =
                        if (mDecodedSamples!!.remaining() > 0) {
                            abs(mDecodedSamples!!.get().toInt())
                        } else {
                            0
                        }
                    if (gain < value) {
                        gain = value
                    }
                    j++
                }
                this.frameGains[i] =
                    sqrt(gain.toDouble()).toInt() // here gain = sqrt(max value of 1st channel)...
                i++
            }
            mDecodedSamples!!.rewind()
            // DumpSamples(); // Uncomment this line to dump the samples in a TSV file.
        }

        // should be removed in the near future...
        @Throws(IOException::class)
        fun writeFile(
            outputFile: File?,
            startFrame: Int,
            numFrames: Int,
        ) {
            val startTime = startFrame.toFloat() * this.samplesPerFrame / this.sampleRate
            val endTime = (startFrame + numFrames).toFloat() * this.samplesPerFrame / this.sampleRate
            writeFile(outputFile, startTime, endTime)
        }

        @Throws(IOException::class)
        fun writeFile(
            outputFile: File?,
            startTime: Float,
            endTime: Float,
        ) {
            val startOffset = (startTime * this.sampleRate).toInt() * 2 * this.channels
            var numSamples = ((endTime - startTime) * this.sampleRate).toInt()
            // Some devices have problems reading mono AAC files (e.g. Samsung S3). Making
            // it stereo.
            val numChannels = if (this.channels == 1) 2 else this.channels

            val mimeType = "audio/mp4a-latm"
            val bitrate = 64000 * numChannels // rule of thumb for a good quality: 64kbps per channel.
            val codec = MediaCodec.createEncoderByType(mimeType)
            val format =
                MediaFormat.createAudioFormat(
                    mimeType,
                    this.sampleRate,
                    numChannels,
                )
            format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            // Get an estimation of the encoded data based on the bitrate. Add 10% to it.
            var estimatedEncodedSize = ((endTime - startTime) * (bitrate.toDouble() / 8) * 1.1).toInt()
            var encodedBytes = ByteBuffer.allocate(estimatedEncodedSize)
            val inputBuffers = codec.inputBuffers
            var outputBuffers = codec.outputBuffers
            val info = MediaCodec.BufferInfo()
            var doneReading = false
            var presentationTime: Long = 0

            val frameSize = 1024 // number of samples per frame per channel for an mp4 (AAC) stream.
            var buffer = ByteArray(frameSize * numChannels * 2) // a sample is coded with a short.
            mDecodedBytes!!.position(startOffset)
            numSamples += (2 * frameSize) // Adding 2 frames, Cf. priming frames for AAC.
            var totNumFrames = 1 + (numSamples / frameSize) // first AAC frame = 2 bytes
            if (numSamples % frameSize != 0) {
                totNumFrames++
            }
            val frameSizes = IntArray(totNumFrames)
            var numOutFrames = 0
            var numFrames = 0
            var numSamplesLeft = numSamples
            var encodedSamplesSize = 0 // size of the output buffer containing the encoded samples.
            var encodedSamples: ByteArray? = null
            while (true) {
                // Feed the samples to the encoder.
                val inputBufferIndex = codec.dequeueInputBuffer(100)
                if (!doneReading && inputBufferIndex >= 0) {
                    if (numSamplesLeft <= 0) {
                        // All samples have been read.
                        codec.queueInputBuffer(
                            inputBufferIndex,
                            0,
                            0,
                            -1,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                        )
                        doneReading = true
                    } else {
                        inputBuffers[inputBufferIndex]!!.clear()
                        if (buffer.size > inputBuffers[inputBufferIndex]!!.remaining()) {
                            // Input buffer is smaller than one frame. This should never happen.
                            continue
                        }
                        // bufferSize is a hack to create a stereo file from a mono stream.
                        val bufferSize = if (this.channels == 1) (buffer.size / 2) else buffer.size
                        if (mDecodedBytes!!.remaining() < bufferSize) {
                            for (i in mDecodedBytes!!.remaining()..<bufferSize) {
                                buffer[i] = 0 // pad with extra 0s to make a full frame.
                            }
                            mDecodedBytes!!.get(buffer, 0, mDecodedBytes!!.remaining())
                        } else {
                            mDecodedBytes!!.get(buffer, 0, bufferSize)
                        }
                        if (this.channels == 1) {
                            var i = bufferSize - 1
                            while (i >= 1) {
                                buffer[2 * i + 1] = buffer[i]
                                buffer[2 * i] = buffer[i - 1]
                                buffer[2 * i - 1] = buffer[2 * i + 1]
                                buffer[2 * i - 2] = buffer[2 * i]
                                i -= 2
                            }
                        }
                        numSamplesLeft -= frameSize
                        inputBuffers[inputBufferIndex]!!.put(buffer)
                        presentationTime =
                            (((numFrames++) * frameSize * 1e6) / this.sampleRate).toLong()
                        codec.queueInputBuffer(inputBufferIndex, 0, buffer.size, presentationTime, 0)
                    }
                }

                // Get the encoded samples from the encoder.
                val outputBufferIndex = codec.dequeueOutputBuffer(info, 100)
                if (outputBufferIndex >= 0 && info.size > 0 && info.presentationTimeUs >= 0) {
                    if (numOutFrames < frameSizes.size) {
                        frameSizes[numOutFrames++] = info.size
                    }
                    if (encodedSamplesSize < info.size) {
                        encodedSamplesSize = info.size
                        encodedSamples = ByteArray(encodedSamplesSize)
                    }
                    outputBuffers[outputBufferIndex]!!.get(encodedSamples!!, 0, info.size)
                    outputBuffers[outputBufferIndex]!!.clear()
                    codec.releaseOutputBuffer(outputBufferIndex, false)
                    if (encodedBytes.remaining() < info.size) { // Hopefully this should not happen.
                        estimatedEncodedSize = (estimatedEncodedSize * 1.2).toInt() // Add 20%.
                        val newEncodedBytes = ByteBuffer.allocate(estimatedEncodedSize)
                        val position = encodedBytes.position()
                        encodedBytes.rewind()
                        newEncodedBytes.put(encodedBytes)
                        encodedBytes = newEncodedBytes
                        encodedBytes.position(position)
                    }
                    encodedBytes.put(encodedSamples, 0, info.size)
                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    outputBuffers = codec.outputBuffers
                }
                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    // We got all the encoded data from the encoder.
                    break
                }
            }
            val encodedSize = encodedBytes.position()
            encodedBytes.rewind()
            codec.stop()
            codec.release()

            // Write the encoded stream to the file, 4kB at a time.
            buffer = ByteArray(4096)
            try {
                val outputStream = FileOutputStream(outputFile)
                outputStream.write(getMP4Header(this.sampleRate, numChannels, frameSizes, bitrate))
                while (encodedSize - encodedBytes.position() > buffer.size) {
                    encodedBytes.get(buffer)
                    outputStream.write(buffer)
                }
                val remaining = encodedSize - encodedBytes.position()
                if (remaining > 0) {
                    encodedBytes.get(buffer, 0, remaining)
                    outputStream.write(buffer, 0, remaining)
                }
                outputStream.close()
            } catch (e: IOException) {
                Log.e("Ringdroid", "Failed to create the .m4a file.")
                Log.e("Ringdroid", getStackTrace(e))
            }
        }

        // Method used to swap the left and right channels (needed for stereo WAV
        // files).
        // buffer contains the PCM data: {sample 1 right, sample 1 left, sample 2 right,
        // etc.}
        // The size of a sample is assumed to be 16 bits (for a single channel).
        // When done, buffer will contain {sample 1 left, sample 1 right, sample 2 left,
        // etc.}
        private fun swapLeftRightChannels(buffer: ByteArray) {
            val left = ByteArray(2)
            val right = ByteArray(2)
            if (buffer.size % 4 != 0) { // 2 channels, 2 bytes per sample (for one channel).
                // Invalid buffer size.
                return
            }
            var offset = 0
            while (offset < buffer.size) {
                left[0] = buffer[offset]
                left[1] = buffer[offset + 1]
                right[0] = buffer[offset + 2]
                right[1] = buffer[offset + 3]
                buffer[offset] = right[0]
                buffer[offset + 1] = right[1]
                buffer[offset + 2] = left[0]
                buffer[offset + 3] = left[1]
                offset += 4
            }
        }

        // should be removed in the near future...
        @Throws(IOException::class)
        fun writeWAVFile(
            outputFile: File?,
            startFrame: Int,
            numFrames: Int,
        ) {
            val startTime = startFrame.toFloat() * this.samplesPerFrame / this.sampleRate
            val endTime = (startFrame + numFrames).toFloat() * this.samplesPerFrame / this.sampleRate
            writeWAVFile(outputFile, startTime, endTime)
        }

        @Throws(IOException::class)
        fun writeWAVFile(
            outputFile: File?,
            startTime: Float,
            endTime: Float,
        ) {
            val startOffset = (startTime * this.sampleRate).toInt() * 2 * this.channels
            val numSamples = ((endTime - startTime) * this.sampleRate).toInt()

            // Start by writing the RIFF header.
            val outputStream = FileOutputStream(outputFile)
            outputStream.write(getWAVHeader(this.sampleRate, this.channels, numSamples))

            // Write the samples to the file, 1024 at a time.
            val buffer = ByteArray(1024 * this.channels * 2) // Each sample is coded with a short.
            mDecodedBytes!!.position(startOffset)
            var numBytesLeft = numSamples * this.channels * 2
            while (numBytesLeft >= buffer.size) {
                if (mDecodedBytes!!.remaining() < buffer.size) {
                    // This should not happen.
                    for (i in mDecodedBytes!!.remaining()..<buffer.size) {
                        buffer[i] = 0 // pad with extra 0s to make a full frame.
                    }
                    mDecodedBytes!!.get(buffer, 0, mDecodedBytes!!.remaining())
                } else {
                    mDecodedBytes!!.get(buffer)
                }
                if (this.channels == 2) {
                    swapLeftRightChannels(buffer)
                }
                outputStream.write(buffer)
                numBytesLeft -= buffer.size
            }
            if (numBytesLeft > 0) {
                if (mDecodedBytes!!.remaining() < numBytesLeft) {
                    // This should not happen.
                    for (i in mDecodedBytes!!.remaining()..<numBytesLeft) {
                        buffer[i] = 0 // pad with extra 0s to make a full frame.
                    }
                    mDecodedBytes!!.get(buffer, 0, mDecodedBytes!!.remaining())
                } else {
                    mDecodedBytes!!.get(buffer, 0, numBytesLeft)
                }
                if (this.channels == 2) {
                    swapLeftRightChannels(buffer)
                }
                outputStream.write(buffer, 0, numBytesLeft)
            }
            outputStream.close()
        }

        // Progress listener interface.
        fun interface ProgressListener {
            /**
             * Will be called by the SoundFile class periodically with values between 0.0
             * and 1.0. Return true to continue loading the file or recording the audio, and
             * false to cancel or stop recording.
             */
            fun reportProgress(fractionComplete: Double): Boolean
        }

        // Custom exception for invalid inputs.
        class InvalidInputException(
            message: String?,
        ) : Exception(message) {
            companion object {
                // Serial version ID generated by Eclipse.
                private val serialVersionUID = -2505698991597837165L
            }
        }

        companion object {
            @JvmStatic
            val supportedExtensions: Array<String>
                // TODO(nfaralli): what is the real list of supported extensions? Is it device
                get() =
                    arrayOf<String>(
                        "mp3",
                        "wav",
                        "3gpp",
                        "3gp",
                        "amr",
                        "aac",
                        "m4a",
                        "ogg",
                    )

            @JvmStatic
            fun isFilenameSupported(filename: String): Boolean {
                val extensions: Array<String> = supportedExtensions
                for (extension in extensions) {
                    if (filename.endsWith(".$extension")) {
                        return true
                    }
                }
                return false
            }

            // Create and return a SoundFile object using the file fileName.
            @JvmStatic
            @Throws(IOException::class, InvalidInputException::class)
            fun create(
                fileName: String,
                progressListener: ProgressListener?,
            ): SoundFile? {
                // First check that the file exists and that its extension is supported.
                val f = File(fileName)
                if (!f.exists()) {
                    throw FileNotFoundException(fileName)
                }
                val name = f.name.lowercase(Locale.getDefault())
                val components: Array<String?> =
                    name.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (components.size < 2) {
                    return null
                }
                if (!listOf(*supportedExtensions)
                        .contains(components[components.size - 1])
                ) {
                    return null
                }
                val soundFile = SoundFile()
                soundFile.setProgressListener(progressListener)
                soundFile.readFile(f)
                return soundFile
            }

            // Create and return a SoundFile object by recording a mono audio stream.
            @JvmStatic
            fun record(progressListener: ProgressListener?): SoundFile? {
                if (progressListener == null) {
                    // must have a progessListener to stop the recording.
                    return null
                }
                val soundFile = SoundFile()
                soundFile.setProgressListener(progressListener)
                soundFile.recordAudio()
                return soundFile
            }
        }
    }
