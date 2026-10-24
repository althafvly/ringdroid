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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class WAVHeader {
    private WAVHeader() {
    }

    public static byte[] getWAVHeader(int sampleRate, int numChannels, int numSamples) {
        int bytesPerFrame = 2 * numChannels;
        int dataSize = numSamples * bytesPerFrame;
        return ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
                .put(new byte[]{'R', 'I', 'F', 'F'}).putInt(36 + dataSize)
                .put(new byte[]{'W', 'A', 'V', 'E', 'f', 'm', 't', ' '}).putInt(16)
                .putShort((short) 1).putShort((short) numChannels).putInt(sampleRate)
                .putInt(sampleRate * bytesPerFrame).putShort((short) bytesPerFrame)
                .putShort((short) 16).put(new byte[]{'d', 'a', 't', 'a'}).putInt(dataSize).array();
    }
}
