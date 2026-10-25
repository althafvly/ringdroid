package com.ringdroid.data

/** The part of a sound to save: its first audio frame, its length in frames and its duration. */
internal class Clip(val startFrame: Int, val numFrames: Int, val durationMs: Int)
