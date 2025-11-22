/*
 * Copyright (C) 2008 Google Inc.
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
package com.ringdroid

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.ContentValues
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.provider.MediaStore
import android.text.Editable
import android.text.Html
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.util.DisplayMetrics
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import com.ringdroid.FilesUtil.getStackTrace
import com.ringdroid.MarkerView.MarkerListener
import com.ringdroid.PermissionUtils.hasMicPermissions
import com.ringdroid.PermissionUtils.requestMicPermissions
import com.ringdroid.RingdroidUtils.setDefaultRingTone
import com.ringdroid.WaveformView.WaveformListener
import com.ringdroid.soundfile.SoundFile
import com.ringdroid.soundfile.SoundFile.Companion.create
import com.ringdroid.soundfile.SoundFile.Companion.record
import java.io.File
import java.io.PrintWriter
import java.io.RandomAccessFile
import java.io.StringWriter
import java.util.Locale
import java.util.Objects
import kotlin.math.min

/**
 * The activity for the Ringdroid main editor window. Keeps track of the
 * waveform display, current horizontal offset, marker handles, start / end text
 * boxes, and handles all of the buttons and controls.
 */
class RingdroidEditActivity :
    Activity(),
    MarkerListener,
    WaveformListener {
    private var mLoadingLastUpdateTime: Long = 0
    private var mLoadingKeepGoing = false
    private var mRecordingLastUpdateTime: Long = 0
    private var mRecordingKeepGoing = false
    private var mRecordingTime = 0.0
    private var mFinishActivity = false
    private var mTimerTextView: TextView? = null
    private var mAlertDialog: AlertDialog? = null
    private var mProgressDialog: ProgressDialog? = null
    private var mSoundFile: SoundFile? = null
    private var mFile: File? = null
    private var mFilename: String? = null
    private var mArtist: String? = null
    private var mTitle: String? = null
    private var mNewFileKind = 0
    private var mWasGetContentIntent = false
    private var mWaveformView: WaveformView? = null
    private var mStartMarker: MarkerView? = null
    private var mEndMarker: MarkerView? = null
    private var mStartText: TextView? = null
    private var mEndText: TextView? = null
    private var mInfo: TextView? = null
    private var mInfoContent: String? = null
    private var mPlayButton: ImageButton? = null
    private var mKeyDown = false
    private var mCaption = ""
    private var mWidth = 0
    private var mMaxPos = 0
    private var mStartPos = 0
    private var mEndPos = 0
    private var mStartVisible = false
    private var mEndVisible = false
    private var mLastDisplayedStartPos = 0
    private var mLastDisplayedEndPos = 0
    private var mOffset = 0
    private var mOffsetGoal = 0
    private var mFlingVelocity = 0
    private var mPlayStartMsec = 0
    private var mPlayEndMsec = 0
    private var mHandler: Handler? = null
    private var mIsPlaying = false
    private var mPlayer: SamplePlayer? = null
    private var mTouchDragging = false
    private var mTouchStart = 0f
    private var mTouchInitialOffset = 0
    private var mTouchInitialStartPos = 0
    private var mTouchInitialEndPos = 0
    private var mWaveformTouchStartMsec: Long = 0
    private var mDensity = 0f
    private var mMarkerLeftInset = 0
    private var mMarkerRightInset = 0
    private var mMarkerTopOffset = 0
    private var mMarkerBottomOffset = 0

    //
    // Public methods and protected overrides
    //
    private val mPlayListener: View.OnClickListener = View.OnClickListener { onPlay(mStartPos) }

    private val mRewindListener: View.OnClickListener =
        View.OnClickListener {
            if (mIsPlaying) {
                var newPos = mPlayer!!.currentPosition - 5000
                if (newPos < mPlayStartMsec) newPos = mPlayStartMsec
                mPlayer!!.seekTo(newPos)
            } else {
                mStartMarker!!.requestFocus()
                markerFocus(mStartMarker)
            }
        }

    private val mFfwdListener: View.OnClickListener =
        View.OnClickListener {
            if (mIsPlaying) {
                var newPos = 5000 + mPlayer!!.currentPosition
                if (newPos > mPlayEndMsec) newPos = mPlayEndMsec
                mPlayer!!.seekTo(newPos)
            } else {
                mEndMarker!!.requestFocus()
                markerFocus(mEndMarker)
            }
        }

    private val mMarkStartListener: View.OnClickListener =
        View.OnClickListener {
            if (mIsPlaying) {
                mStartPos = mWaveformView!!.millisecsToPixels(mPlayer!!.currentPosition)
                updateDisplay()
            }
        }

    private val mMarkEndListener: View.OnClickListener =
        View.OnClickListener {
            if (mIsPlaying) {
                mEndPos = mWaveformView!!.millisecsToPixels(mPlayer!!.currentPosition)
                updateDisplay()
                handlePause()
            }
        }

    private val mTextWatcher: TextWatcher =
        object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int,
            ) {
            }

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int,
            ) {
            }

            override fun afterTextChanged(s: Editable?) {
                if (mStartText!!.hasFocus()) {
                    try {
                        mStartPos =
                            mWaveformView!!.secondsToPixels(
                                mStartText!!.text.toString().toDouble(),
                            )
                        updateDisplay()
                    } catch (ignored: NumberFormatException) {
                    }
                }
                if (mEndText!!.hasFocus()) {
                    try {
                        mEndPos =
                            mWaveformView!!.secondsToPixels(mEndText!!.text.toString().toDouble())
                        updateDisplay()
                    } catch (ignored: NumberFormatException) {
                    }
                }
            }
        }

    private var mLoadSoundFileThread: Thread? = null
    private var mRecordAudioThread: Thread? = null
    private var mSaveSoundFileThread: Thread? = null

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PermissionUtils.MIC_PERMISSION_REQUEST) {
            var micPermissionGranted = true

            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    micPermissionGranted = false
                    break
                }
            }

            if (micPermissionGranted) {
                recordAudio()
            } else {
                Toast.makeText(this, R.string.required_mic_permission, Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    /** Called when the activity is first created.  */
    public override fun onCreate(icicle: Bundle?) {
        Log.v("Ringdroid", "EditActivity OnCreate")
        super.onCreate(icicle)

        mPlayer = null
        mIsPlaying = false

        mAlertDialog = null
        mProgressDialog = null

        mLoadSoundFileThread = null
        mRecordAudioThread = null
        mSaveSoundFileThread = null

        val intent = getIntent()

        // If the Ringdroid media select activity was launched via a
        // GET_CONTENT intent, then we shouldn't display a "saved"
        // message when the user saves, we should just return whatever
        // they create.
        mWasGetContentIntent = intent.getBooleanExtra("was_get_content_intent", false)

        mFilename =
            Objects
                .requireNonNull<Uri>(intent.data)
                .toString()
                .replaceFirst("file://".toRegex(), "")
                .replace("%20".toRegex(), " ")
        mSoundFile = null
        mKeyDown = false

        mHandler = Handler(Looper.getMainLooper())

        loadGui()

        mHandler!!.postDelayed(mTimerRunnable, 100)

        if (mFilename != "record") {
            loadFromFile()
        } else {
            if (hasMicPermissions(this)) {
                recordAudio()
            } else {
                requestMicPermissions(this)
            }
        }
    }

    private fun closeThread(thread: Thread?) {
        if (thread != null && thread.isAlive) {
            try {
                thread.join()
            } catch (ignored: InterruptedException) {
            }
        }
    }

    //
    // WaveformListener
    //

    /** Called when the activity is finally destroyed.  */
    override fun onDestroy() {
        Log.v("Ringdroid", "EditActivity OnDestroy")

        mLoadingKeepGoing = false
        mRecordingKeepGoing = false
        closeThread(mLoadSoundFileThread)
        closeThread(mRecordAudioThread)
        closeThread(mSaveSoundFileThread)
        mLoadSoundFileThread = null
        mRecordAudioThread = null
        mSaveSoundFileThread = null
        if (mProgressDialog != null) {
            mProgressDialog!!.dismiss()
            mProgressDialog = null
        }
        if (mAlertDialog != null) {
            mAlertDialog!!.dismiss()
            mAlertDialog = null
        }

        if (mPlayer != null) {
            if (mPlayer!!.isPlaying || mPlayer!!.isPaused) {
                mPlayer!!.stop()
            }
            mPlayer!!.release()
            mPlayer = null
        }

        super.onDestroy()
    }

    /** Called with an Activity we started with an Intent returns.  */
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        dataIntent: Intent?,
    ) {
        Log.v("Ringdroid", "EditActivity onActivityResult")
        if (requestCode == REQUEST_CODE_CHOOSE_CONTACT) {
            // The user finished saving their ringtone and they're
            // just applying it to a contact. When they return here,
            // they're done.
            finish()
        }
    }

    /**
     * Called when the orientation changes and/or the keyboard is shown or hidden.
     * We don't need to recreate the whole activity in this case, but we do need to
     * redo our layout somewhat.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        Log.v("Ringdroid", "EditActivity onConfigurationChanged")
        val saveZoomLevel = mWaveformView!!.zoomLevel
        super.onConfigurationChanged(newConfig)

        loadGui()

        mHandler!!.postDelayed({
            mStartMarker!!.requestFocus()
            markerFocus(mStartMarker)

            mWaveformView!!.zoomLevel = saveZoomLevel
            mWaveformView!!.recomputeHeights(mDensity)
            updateDisplay()
        }, 500)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.edit_options, menu)

        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.action_save).isVisible = true
        menu.findItem(R.id.action_reset).isVisible = true
        menu.findItem(R.id.action_about).isVisible = true
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_save -> {
                onSave()
                return true
            }

            R.id.action_reset -> {
                resetPositions()
                mOffsetGoal = 0
                updateDisplay()
                return true
            }

            R.id.action_about -> {
                onAbout(this)
                return true
            }

            else -> return false
        }
    }

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent?,
    ): Boolean {
        if (keyCode == KeyEvent.KEYCODE_SPACE) {
            onPlay(mStartPos)
            return true
        }

        return super.onKeyDown(keyCode, event)
    }

    //
    // MarkerListener
    //

    /**
     * Every time we get a message that our waveform drew, see if we need to animate
     * and trigger another redraw.
     */
    override fun waveformDraw() {
        mWidth = mWaveformView!!.measuredWidth
        if (mOffsetGoal != mOffset && !mKeyDown) {
            updateDisplay()
        } else if (mIsPlaying) {
            updateDisplay()
        } else if (mFlingVelocity != 0) {
            updateDisplay()
        }
    }

    override fun waveformTouchStart(x: Float) {
        mTouchDragging = true
        mTouchStart = x
        mTouchInitialOffset = mOffset
        mFlingVelocity = 0
        mWaveformTouchStartMsec = this.currentTime
    }

    override fun waveformTouchMove(x: Float) {
        mOffset = trap((mTouchInitialOffset + (mTouchStart - x)).toInt())
        updateDisplay()
    }

    override fun waveformTouchEnd() {
        mTouchDragging = false
        mOffsetGoal = mOffset

        val elapsedMsec = this.currentTime - mWaveformTouchStartMsec
        if (elapsedMsec < 300) {
            if (mIsPlaying) {
                val seekMsec = mWaveformView!!.pixelsToMillisecs((mTouchStart + mOffset).toInt())
                if (seekMsec in mPlayStartMsec..<mPlayEndMsec) {
                    mPlayer!!.seekTo(seekMsec)
                } else {
                    handlePause()
                }
            } else {
                onPlay((mTouchStart + mOffset).toInt())
            }
        }
    }

    override fun waveformFling(vx: Float) {
        mTouchDragging = false
        mOffsetGoal = mOffset
        mFlingVelocity = (-vx).toInt()
        updateDisplay()
    }

    override fun waveformZoomIn() {
        mWaveformView!!.zoomIn()
        mStartPos = mWaveformView!!.start
        mEndPos = mWaveformView!!.end
        mMaxPos = mWaveformView!!.maxPos()
        mOffset = mWaveformView!!.offset
        mOffsetGoal = mOffset
        updateDisplay()
    }

    override fun waveformZoomOut() {
        mWaveformView!!.zoomOut()
        mStartPos = mWaveformView!!.start
        mEndPos = mWaveformView!!.end
        mMaxPos = mWaveformView!!.maxPos()
        mOffset = mWaveformView!!.offset
        mOffsetGoal = mOffset
        updateDisplay()
    }

    override fun markerDraw() {
    }

    override fun markerTouchStart(
        marker: MarkerView?,
        x: Float,
    ) {
        mTouchDragging = true
        mTouchStart = x
        mTouchInitialStartPos = mStartPos
        mTouchInitialEndPos = mEndPos
    }

    //
    // Static About dialog method, also called from RingdroidSelectActivity
    //
    override fun markerTouchMove(
        marker: MarkerView?,
        x: Float,
    ) {
        val delta = x - mTouchStart

        if (marker == mStartMarker) {
            mStartPos = trap((mTouchInitialStartPos + delta).toInt())
            mEndPos = trap((mTouchInitialEndPos + delta).toInt())
        } else {
            mEndPos = trap((mTouchInitialEndPos + delta).toInt())
            if (mEndPos < mStartPos) mEndPos = mStartPos
        }

        updateDisplay()
    }

    //
    // Internal methods
    //
    override fun markerTouchEnd(marker: MarkerView?) {
        mTouchDragging = false
        if (marker == mStartMarker) {
            setOffsetGoalStart()
        } else {
            setOffsetGoalEnd()
        }
    }

    override fun markerLeft(
        marker: MarkerView?,
        velocity: Int,
    ) {
        mKeyDown = true

        if (marker == mStartMarker) {
            val saveStart = mStartPos
            mStartPos = trap(mStartPos - velocity)
            mEndPos = trap(mEndPos - (saveStart - mStartPos))
            setOffsetGoalStart()
        }

        if (marker == mEndMarker) {
            if (mEndPos == mStartPos) {
                mStartPos = trap(mStartPos - velocity)
                mEndPos = mStartPos
            } else {
                mEndPos = trap(mEndPos - velocity)
            }

            setOffsetGoalEnd()
        }

        updateDisplay()
    }

    override fun markerRight(
        marker: MarkerView?,
        velocity: Int,
    ) {
        mKeyDown = true

        if (marker == mStartMarker) {
            val saveStart = mStartPos
            mStartPos += velocity
            if (mStartPos > mMaxPos) mStartPos = mMaxPos
            mEndPos += (mStartPos - saveStart)
            if (mEndPos > mMaxPos) mEndPos = mMaxPos

            setOffsetGoalStart()
        }

        if (marker == mEndMarker) {
            mEndPos += velocity
            if (mEndPos > mMaxPos) mEndPos = mMaxPos

            setOffsetGoalEnd()
        }

        updateDisplay()
    }

    override fun markerEnter(marker: MarkerView?) {
    }

    override fun markerKeyUp() {
        mKeyDown = false
        updateDisplay()
    }

    override fun markerFocus(marker: MarkerView?) {
        mKeyDown = false
        if (marker == mStartMarker) {
            setOffsetGoalStartNoUpdate()
        } else {
            setOffsetGoalEndNoUpdate()
        }

        // Delay updaing the display because if this focus was in
        // response to a touch event, we want to receive the touch
        // event too before updating the display.
        mHandler!!.postDelayed({ this.updateDisplay() }, 100)
    }

    /**
     * Called from both onCreate and onConfigurationChanged (if the user switched
     * layouts)
     */
    private fun loadGui() {
        // Inflate our UI from its XML layout description.
        setContentView(R.layout.editor)

        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        mDensity = metrics.density

        mMarkerLeftInset = (46 * mDensity).toInt()
        mMarkerRightInset = (48 * mDensity).toInt()
        mMarkerTopOffset = (10 * mDensity).toInt()
        mMarkerBottomOffset = (10 * mDensity).toInt()

        mStartText = findViewById(R.id.starttext)
        mStartText!!.addTextChangedListener(mTextWatcher)
        mEndText = findViewById(R.id.endtext)
        mEndText!!.addTextChangedListener(mTextWatcher)

        mPlayButton = findViewById(R.id.play)
        mPlayButton!!.setOnClickListener(mPlayListener)
        val mRewindButton = findViewById<ImageButton>(R.id.rew)
        mRewindButton.setOnClickListener(mRewindListener)
        val mFfwdButton = findViewById<ImageButton>(R.id.ffwd)
        mFfwdButton.setOnClickListener(mFfwdListener)

        val markStartButton = findViewById<TextView>(R.id.mark_start)
        markStartButton.setOnClickListener(mMarkStartListener)
        val markEndButton = findViewById<TextView>(R.id.mark_end)
        markEndButton.setOnClickListener(mMarkEndListener)

        enableDisableButtons()

        mWaveformView = findViewById(R.id.waveform)
        mWaveformView!!.setListener(this)

        mInfo = findViewById(R.id.info)
        mInfo!!.text = mCaption

        mMaxPos = 0
        mLastDisplayedStartPos = -1
        mLastDisplayedEndPos = -1

        if (mSoundFile != null && !mWaveformView!!.hasSoundFile()) {
            mWaveformView!!.setSoundFile(mSoundFile)
            mWaveformView!!.recomputeHeights(mDensity)
            mMaxPos = mWaveformView!!.maxPos()
        }

        mStartMarker = findViewById(R.id.startmarker)
        mStartMarker!!.setListener(this)
        mStartMarker!!.alpha = 1f
        mStartMarker!!.setFocusable(true)
        mStartMarker!!.isFocusableInTouchMode = true
        mStartVisible = true

        mEndMarker = findViewById(R.id.endmarker)
        mEndMarker!!.setListener(this)
        mEndMarker!!.alpha = 1f
        mEndMarker!!.setFocusable(true)
        mEndMarker!!.isFocusableInTouchMode = true
        mEndVisible = true

        updateDisplay()
    }

    private fun loadFromFile() {
        mFile = File(mFilename!!)

        val metadataReader = SongMetadataReader(this, mFilename!!)
        mTitle = metadataReader.mTitle
        mArtist = metadataReader.mArtist

        var titleLabel = mTitle
        if (mArtist != null && !mArtist!!.isEmpty()) {
            titleLabel += " - $mArtist"
        }
        title = titleLabel

        mLoadingLastUpdateTime = this.currentTime
        mLoadingKeepGoing = true
        mFinishActivity = false
        mProgressDialog = ProgressDialog(this@RingdroidEditActivity)
        mProgressDialog!!.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
        mProgressDialog!!.setTitle(R.string.progress_dialog_loading)
        mProgressDialog!!.setCancelable(true)
        mProgressDialog!!.setOnCancelListener { dialog: DialogInterface? ->
            mLoadingKeepGoing = false
            mFinishActivity = true
        }
        mProgressDialog!!.show()

        val listener: SoundFile.ProgressListener =
            SoundFile.ProgressListener { fractionComplete: Double ->
                val now = this.currentTime
                if (now - mLoadingLastUpdateTime > 100) {
                    mProgressDialog!!.progress = (mProgressDialog!!.max * fractionComplete).toInt()
                    mLoadingLastUpdateTime = now
                }
                !mLoadingKeepGoing
            }

        // Load the sound file in a background thread
        mLoadSoundFileThread =
            Thread(
                Runnable {
                    try {
                        mSoundFile = create(mFile!!.absolutePath, listener)

                        if (mSoundFile == null) {
                            mProgressDialog!!.dismiss()
                            val name = mFile!!.name.lowercase(Locale.getDefault())
                            val components: Array<String?> =
                                name.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                            val err: String =
                                if (components.size < 2) {
                                    resources.getString(R.string.no_extension_error)
                                } else {
                                    (
                                        resources.getString(R.string.bad_extension_error) + " " +
                                            components[components.size - 1]
                                    )
                                }
                            val runnable = Runnable { showFinalAlert(Exception(), err) }
                            mHandler!!.post(runnable)
                            return@Runnable
                        }
                        mPlayer = SamplePlayer(mSoundFile!!)
                    } catch (e: Exception) {
                        mProgressDialog!!.dismiss()
                        e.printStackTrace()
                        mInfoContent = e.toString()
                        runOnUiThread(Runnable { mInfo!!.text = mInfoContent })

                        val runnable =
                            Runnable { showFinalAlert(e, resources.getText(R.string.read_error)) }
                        mHandler!!.post(runnable)
                        return@Runnable
                    }
                    mProgressDialog!!.dismiss()
                    if (mLoadingKeepGoing) {
                        val runnable = Runnable { this.finishOpeningSoundFile() }
                        mHandler!!.post(runnable)
                    } else if (mFinishActivity) {
                        this@RingdroidEditActivity.finish()
                    }
                },
            )
        mLoadSoundFileThread!!.start()
    }

    private val mTimerRunnable: Runnable =
        Runnable {
            // Updating an EditText is slow on Android. Make sure
            // we only do the update if the text has actually changed.
            if (mStartPos != mLastDisplayedStartPos && !mStartText!!.hasFocus()) {
                mStartText!!.text = formatTime(mStartPos)
                mLastDisplayedStartPos = mStartPos
            }

            if (mEndPos != mLastDisplayedEndPos && !mEndText!!.hasFocus()) {
                mEndText!!.text = formatTime(mEndPos)
                mLastDisplayedEndPos = mEndPos
            }

            mHandler!!.postDelayed(mTimerRunnable, 100)
        }

    private fun recordAudio() {
        mFile = null
        mTitle = null
        mArtist = null

        mRecordingLastUpdateTime = this.currentTime
        mRecordingKeepGoing = true
        mFinishActivity = false
        val adBuilder = AlertDialog.Builder(this@RingdroidEditActivity)
        adBuilder.setTitle(resources.getText(R.string.progress_dialog_recording))
        adBuilder.setCancelable(true)
        adBuilder.setNegativeButton(
            resources.getText(R.string.progress_dialog_cancel),
        ) { dialog: DialogInterface?, id: Int ->
            mRecordingKeepGoing = false
            mFinishActivity = true
        }
        adBuilder.setPositiveButton(
            resources.getText(R.string.progress_dialog_stop),
        ) { dialog: DialogInterface?, id: Int ->
            mRecordingKeepGoing = false
        }
        // TODO(nfaralli): try to use a FrameLayout and pass it to the following inflate
        // call.
        // Using null, android:layout_width etc. may not work (hence text is at the top
        // of view).
        // On the other hand, if the text is big enough, this is good enough.
        adBuilder.setView(layoutInflater.inflate(R.layout.record_audio, null))
        mAlertDialog = adBuilder.show()
        mTimerTextView = mAlertDialog!!.findViewById(R.id.record_audio_timer)

        val listener: SoundFile.ProgressListener =
            SoundFile.ProgressListener { elapsedTime: Double ->
                val now = this.currentTime
                if (now - mRecordingLastUpdateTime > 5) {
                    mRecordingTime = elapsedTime
                    // Only UI thread can update Views such as TextViews.
                    runOnUiThread {
                        val min = (mRecordingTime / 60).toInt()
                        val sec = (mRecordingTime - 60 * min).toFloat()
                        mTimerTextView!!.text = String.format("%d:%05.2f", min, sec)
                    }
                    mRecordingLastUpdateTime = now
                }
                !mRecordingKeepGoing
            }

        // Record the audio stream in a background thread
        mRecordAudioThread =
            Thread(
                Runnable {
                    try {
                        mSoundFile = record(listener)
                        if (mSoundFile == null) {
                            mAlertDialog!!.dismiss()
                            val runnable =
                                Runnable {
                                    showFinalAlert(
                                        Exception(),
                                        resources.getText(R.string.record_error),
                                    )
                                }
                            mHandler!!.post(runnable)
                            return@Runnable
                        }
                        mPlayer = SamplePlayer(mSoundFile!!)
                    } catch (e: Exception) {
                        mAlertDialog!!.dismiss()
                        e.printStackTrace()
                        mInfoContent = e.toString()
                        runOnUiThread { mInfo!!.text = mInfoContent }

                        val runnable =
                            Runnable { showFinalAlert(e, resources.getText(R.string.record_error)) }
                        mHandler!!.post(runnable)
                        return@Runnable
                    }
                    mAlertDialog!!.dismiss()
                    if (mFinishActivity) {
                        this@RingdroidEditActivity.finish()
                    } else {
                        val runnable = Runnable { this.finishOpeningSoundFile() }
                        mHandler!!.post(runnable)
                    }
                },
            )
        mRecordAudioThread!!.start()
    }

    private fun finishOpeningSoundFile() {
        mWaveformView!!.setSoundFile(mSoundFile)
        mWaveformView!!.recomputeHeights(mDensity)

        mMaxPos = mWaveformView!!.maxPos()
        mLastDisplayedStartPos = -1
        mLastDisplayedEndPos = -1

        mTouchDragging = false

        mOffset = 0
        mOffsetGoal = 0
        mFlingVelocity = 0
        resetPositions()
        if (mEndPos > mMaxPos) mEndPos = mMaxPos

        mCaption = (
            mSoundFile!!.filetype + ", " + mSoundFile!!.sampleRate + " Hz, " +
                mSoundFile!!.avgBitrateKbps + " kbps, " + formatTime(mMaxPos) + " " +
                resources.getString(R.string.time_seconds)
        )
        mInfo!!.text = mCaption

        updateDisplay()
    }

    @Synchronized
    private fun updateDisplay() {
        if (mIsPlaying) {
            val now = mPlayer!!.currentPosition
            val frames = mWaveformView!!.millisecsToPixels(now)
            mWaveformView!!.setPlayback(frames)
            setOffsetGoalNoUpdate(frames - mWidth / 2)
            if (now >= mPlayEndMsec) {
                handlePause()
            }
        }

        if (!mTouchDragging) {
            var offsetDelta: Int

            if (mFlingVelocity != 0) {
                offsetDelta = mFlingVelocity / 30
                if (mFlingVelocity > 80) {
                    mFlingVelocity -= 80
                } else if (mFlingVelocity < -80) {
                    mFlingVelocity += 80
                } else {
                    mFlingVelocity = 0
                }

                mOffset += offsetDelta

                if (mOffset + mWidth / 2 > mMaxPos) {
                    mOffset = mMaxPos - mWidth / 2
                    mFlingVelocity = 0
                }
                if (mOffset < 0) {
                    mOffset = 0
                    mFlingVelocity = 0
                }
                mOffsetGoal = mOffset
            } else {
                offsetDelta = mOffsetGoal - mOffset

                if (offsetDelta > 10) {
                    offsetDelta /= 10
                } else if (offsetDelta > 0) {
                    offsetDelta = 1
                } else if (offsetDelta < -10) {
                    offsetDelta /= 10
                } else if (offsetDelta < 0) {
                    offsetDelta = -1
                }

                mOffset += offsetDelta
            }
        }

        mWaveformView!!.setParameters(mStartPos, mEndPos, mOffset)
        mWaveformView!!.invalidate()

        mStartMarker!!.contentDescription = resources.getText(R.string.start_marker).toString() + " " + formatTime(mStartPos)
        mEndMarker!!.contentDescription = resources.getText(R.string.end_marker).toString() + " " + formatTime(mEndPos)

        var startX = mStartPos - mOffset - mMarkerLeftInset
        if (startX + mStartMarker!!.width >= 0) {
            if (!mStartVisible) {
                // Delay this to avoid flicker
                mHandler!!.postDelayed({
                    mStartVisible = true
                    mStartMarker!!.alpha = 1f
                }, 0)
            }
        } else {
            if (mStartVisible) {
                mStartMarker!!.alpha = 0f
                mStartVisible = false
            }
            startX = 0
        }

        var endX = mEndPos - mOffset - mEndMarker!!.width + mMarkerRightInset
        if (endX + mEndMarker!!.width >= 0) {
            if (!mEndVisible) {
                // Delay this to avoid flicker
                mHandler!!.postDelayed({
                    mEndVisible = true
                    mEndMarker!!.alpha = 1f
                }, 0)
            }
        } else {
            if (mEndVisible) {
                mEndMarker!!.alpha = 0f
                mEndVisible = false
            }
            endX = 0
        }

        var params =
            RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT,
            )
        params.setMargins(
            startX,
            mMarkerTopOffset,
            -mStartMarker!!.width,
            -mStartMarker!!.height,
        )
        mStartMarker!!.layoutParams = params

        params =
            RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT,
            )
        params.setMargins(
            endX,
            mWaveformView!!.measuredHeight - mEndMarker!!.height - mMarkerBottomOffset,
            -mStartMarker!!.width,
            -mStartMarker!!.height,
        )
        mEndMarker!!.layoutParams = params
    }

    private fun enableDisableButtons() {
        if (mIsPlaying) {
            mPlayButton!!.setImageResource(android.R.drawable.ic_media_pause)
            mPlayButton!!.contentDescription = resources.getText(R.string.stop)
        } else {
            mPlayButton!!.setImageResource(android.R.drawable.ic_media_play)
            mPlayButton!!.contentDescription = resources.getText(R.string.play)
        }
    }

    private fun resetPositions() {
        mStartPos = mWaveformView!!.secondsToPixels(0.0)
        mEndPos = mWaveformView!!.secondsToPixels(15.0)
    }

    private fun trap(pos: Int): Int {
        if (pos < 0) return 0
        return min(pos, mMaxPos)
    }

    private fun setOffsetGoalStart() {
        setOffsetGoal(mStartPos - mWidth / 2)
    }

    private fun setOffsetGoalStartNoUpdate() {
        setOffsetGoalNoUpdate(mStartPos - mWidth / 2)
    }

    private fun setOffsetGoalEnd() {
        setOffsetGoal(mEndPos - mWidth / 2)
    }

    private fun setOffsetGoalEndNoUpdate() {
        setOffsetGoalNoUpdate(mEndPos - mWidth / 2)
    }

    private fun setOffsetGoal(offset: Int) {
        setOffsetGoalNoUpdate(offset)
        updateDisplay()
    }

    private fun setOffsetGoalNoUpdate(offset: Int) {
        if (mTouchDragging) {
            return
        }

        mOffsetGoal = offset
        if (mOffsetGoal + mWidth / 2 > mMaxPos) mOffsetGoal = mMaxPos - mWidth / 2
        if (mOffsetGoal < 0) mOffsetGoal = 0
    }

    private fun formatTime(pixels: Int): String =
        if (mWaveformView != null && mWaveformView!!.isInitialized) {
            formatDecimal(mWaveformView!!.pixelsToSeconds(pixels))
        } else {
            ""
        }

    private fun formatDecimal(x: Double): String {
        var xWhole = x.toInt()
        var xFrac = (100 * (x - xWhole) + 0.5).toInt()

        if (xFrac >= 100) {
            xWhole++ // Round up
            xFrac -= 100 // Now we need the remainder after the round up
            if (xFrac < 10) {
                xFrac *= 10 // we need a fraction that is 2 digits long
            }
        }

        return if (xFrac < 10) {
            "$xWhole.0$xFrac"
        } else {
            "$xWhole.$xFrac"
        }
    }

    @Synchronized
    private fun handlePause() {
        if (mPlayer != null && mPlayer!!.isPlaying) {
            mPlayer!!.pause()
        }
        mWaveformView!!.setPlayback(-1)
        mIsPlaying = false
        enableDisableButtons()
    }

    @Synchronized
    private fun onPlay(startPosition: Int) {
        if (mIsPlaying) {
            handlePause()
            return
        }

        if (mPlayer == null) {
            // Not initialized yet
            return
        }

        try {
            mPlayStartMsec = mWaveformView!!.pixelsToMillisecs(startPosition)
            mPlayEndMsec =
                if (startPosition < mStartPos) {
                    mWaveformView!!.pixelsToMillisecs(mStartPos)
                } else if (startPosition > mEndPos) {
                    mWaveformView!!.pixelsToMillisecs(mMaxPos)
                } else {
                    mWaveformView!!.pixelsToMillisecs(mEndPos)
                }
            mPlayer!!.setOnCompletionListener { handlePause() }
            mIsPlaying = true

            mPlayer!!.seekTo(mPlayStartMsec)
            mPlayer!!.start()
            updateDisplay()
            enableDisableButtons()
        } catch (e: Exception) {
            showFinalAlert(e, R.string.play_error)
        }
    }

    /**
     * Show a "final" alert dialog that will exit the activity after the user clicks
     * on the OK button. If an exception is passed, it's assumed to be an error
     * condition, and the dialog is presented as an error, and the stack trace is
     * logged. If there's no exception, it's a success message.
     */
    private fun showFinalAlert(
        e: Exception?,
        message: CharSequence?,
    ) {
        val title: CharSequence?
        if (e != null) {
            Log.e("Ringdroid", "Error: $message")
            Log.e("Ringdroid", getStackTrace(e))
            title = resources.getText(R.string.alert_title_failure)
            setResult(RESULT_CANCELED, Intent())
        } else {
            Log.v("Ringdroid", "Success: $message")
            title = resources.getText(R.string.alert_title_success)
        }

        AlertDialog
            .Builder(this@RingdroidEditActivity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(
                R.string.alert_ok_button,
            ) { dialog: DialogInterface?, whichButton: Int -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun showFinalAlert(
        e: Exception?,
        messageResourceId: Int,
    ) {
        showFinalAlert(e, resources.getText(messageResourceId))
    }

    private fun makeRingtoneFilename(
        title: CharSequence,
        extension: String?,
    ): String? {
        var externalRootDir = Environment.getExternalStorageDirectory().path
        if (!externalRootDir.endsWith("/")) {
            externalRootDir += "/"
        }
        val subdir: String =
            when (mNewFileKind) {
                FileSaveDialog.FILE_KIND_MUSIC ->
                    "media/audio/music/"

                FileSaveDialog.FILE_KIND_ALARM -> "media/audio/alarms/"
                FileSaveDialog.FILE_KIND_NOTIFICATION -> "media/audio/notifications/"
                FileSaveDialog.FILE_KIND_RINGTONE -> "media/audio/ringtones/"
                else ->
                    "media/audio/music/"
            }
        var parentDir = externalRootDir + subdir

        // Create the parent directory
        val parentDirFile = File(parentDir)
        parentDirFile.mkdirs()

        // If we can't write to that special path, try just writing
        // directly to the sdcard
        if (!parentDirFile.isDirectory) {
            parentDir = externalRootDir
        }

        // Turn the title into a filename
        var filename = ""
        for (i in 0..<title.length) {
            if (Character.isLetterOrDigit(title[i])) {
                filename += title[i]
            }
        }

        // Try to make the filename unique
        var path: String? = null
        for (i in 0..99) {
            val testPath =
                if (i > 0) {
                    parentDir + filename + i + extension
                } else {
                    parentDir + filename + extension
                }

            try {
                val f = RandomAccessFile(File(testPath), "r")
                f.close()
            } catch (e: Exception) {
                // Good, the file didn't exist
                path = testPath
                break
            }
        }

        return path
    }

    private fun saveRingtone(title: CharSequence) {
        val startTime = mWaveformView!!.pixelsToSeconds(mStartPos)
        val endTime = mWaveformView!!.pixelsToSeconds(mEndPos)
        val startFrame = mWaveformView!!.secondsToFrames(startTime)
        val endFrame = mWaveformView!!.secondsToFrames(endTime)
        val duration = (endTime - startTime + 0.5).toInt()

        // Create an indeterminate progress dialog
        mProgressDialog = ProgressDialog(this)
        mProgressDialog!!.setProgressStyle(ProgressDialog.STYLE_SPINNER)
        mProgressDialog!!.setTitle(R.string.progress_dialog_saving)
        mProgressDialog!!.isIndeterminate = true
        mProgressDialog!!.setCancelable(false)
        mProgressDialog!!.show()

        // Save the sound file in a background thread
        mSaveSoundFileThread =
            object : Thread() {
                override fun run() {
                    // Try AAC first.
                    var outPath = makeRingtoneFilename(title, ".m4a")
                    if (outPath == null) {
                        val runnable =
                            Runnable { showFinalAlert(Exception(), R.string.no_unique_filename) }
                        mHandler!!.post(runnable)
                        return
                    }
                    var outFile = File(outPath)
                    var fallbackToWAV = false
                    try {
                        // Write the new file
                        mSoundFile!!.writeFile(outFile, startFrame, endFrame - startFrame)
                    } catch (e: Exception) {
                        // log the error and try to create a .wav file instead
                        if (outFile.exists()) {
                            outFile.delete()
                        }
                        val writer = StringWriter()
                        e.printStackTrace(PrintWriter(writer))
                        Log.e("Ringdroid", "Error: Failed to create $outPath")
                        Log.e("Ringdroid", writer.toString())
                        fallbackToWAV = true
                    }

                    // Try to create a .wav file if creating a .m4a file failed.
                    if (fallbackToWAV) {
                        outPath = makeRingtoneFilename(title, ".wav")
                        if (outPath == null) {
                            val runnable =
                                Runnable { showFinalAlert(Exception(), R.string.no_unique_filename) }
                            mHandler!!.post(runnable)
                            return
                        }
                        outFile = File(outPath)
                        try {
                            // create the .wav file
                            mSoundFile!!.writeWAVFile(outFile, startFrame, endFrame - startFrame)
                        } catch (e: Exception) {
                            // Creating the .wav file also failed. Stop the progress dialog, show an
                            // error message and exit.
                            var exception: Exception? = e
                            mProgressDialog!!.dismiss()
                            if (outFile.exists()) {
                                outFile.delete()
                            }
                            mInfoContent = exception.toString()
                            runOnUiThread(Runnable { mInfo!!.text = mInfoContent })

                            val errorMessage: CharSequence
                            if (exception!!.message != null &&
                                exception.message == "No space left on device"
                            ) {
                                errorMessage = resources.getText(R.string.no_space_error)
                                exception = null
                            } else {
                                errorMessage = resources.getText(R.string.write_error)
                            }
                            val finalException = exception
                            val runnable =
                                Runnable { showFinalAlert(finalException, errorMessage) }
                            mHandler!!.post(runnable)
                            return
                        }
                    }

                    // Try to load the new file to make sure it worked
                    try {
                        val listener: SoundFile.ProgressListener =
                            SoundFile.ProgressListener {
                                // Do nothing - we're not going to try to
                                // estimate when reloading a saved sound
                                // since it's usually fast, but hard to
                                // estimate anyway.
                                false // Keep going
                            }
                        create(outPath, listener)
                    } catch (e: Exception) {
                        mProgressDialog!!.dismiss()
                        e.printStackTrace()
                        mInfoContent = e.toString()
                        runOnUiThread(Runnable { mInfo!!.text = mInfoContent })

                        val runnable =
                            Runnable { showFinalAlert(e, resources.getText(R.string.write_error)) }
                        mHandler!!.post(runnable)
                        return
                    }

                    mProgressDialog!!.dismiss()

                    val finalOutPath: String = outPath
                    val runnable = Runnable { afterSavingRingtone(title, finalOutPath!!, duration) }
                    mHandler!!.post(runnable)
                }
            }
        mSaveSoundFileThread!!.start()
    }

    private fun afterSavingRingtone(
        title: CharSequence,
        outPath: String,
        duration: Int,
    ) {
        val outFile = File(outPath)
        val fileSize = outFile.length()
        if (fileSize <= 512) {
            outFile.delete()
            AlertDialog
                .Builder(this)
                .setTitle(R.string.alert_title_failure)
                .setMessage(R.string.too_small_error)
                .setPositiveButton(R.string.alert_ok_button, null)
                .setCancelable(false)
                .show()
            return
        }

        // Create the database record, pointing to the existing file path
        val mimeType =
            if (outPath.endsWith(".m4a")) {
                "audio/mp4a-latm"
            } else if (outPath.endsWith(".wav")) {
                "audio/wav"
            } else {
                // This should never happen.
                "audio/mpeg"
            }

        val artist = "" + resources.getText(R.string.artist_name)

        val values = ContentValues()
        values.put(MediaStore.MediaColumns.DATA, outPath)
        values.put(MediaStore.MediaColumns.TITLE, title.toString())
        values.put(MediaStore.MediaColumns.SIZE, fileSize)
        values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType)

        values.put(MediaStore.Audio.Media.ARTIST, artist)
        values.put(MediaStore.Audio.Media.DURATION, duration)

        values.put(
            MediaStore.Audio.Media.IS_RINGTONE,
            mNewFileKind == FileSaveDialog.FILE_KIND_RINGTONE,
        )
        values.put(
            MediaStore.Audio.Media.IS_NOTIFICATION,
            mNewFileKind == FileSaveDialog.FILE_KIND_NOTIFICATION,
        )
        values.put(MediaStore.Audio.Media.IS_ALARM, mNewFileKind == FileSaveDialog.FILE_KIND_ALARM)
        values.put(MediaStore.Audio.Media.IS_MUSIC, mNewFileKind == FileSaveDialog.FILE_KIND_MUSIC)

        // Insert it into the database
        val uri = MediaStore.Audio.Media.getContentUriForPath(outPath)
        val newUri = contentResolver.insert(uri!!, values)
        setResult(RESULT_OK, Intent().setData(newUri))

        // If Ringdroid was launched to get content, just return
        if (mWasGetContentIntent) {
            finish()
            return
        }

        // There's nothing more to do with music or an alarm. Show a
        // success message and then quit.
        if (mNewFileKind == FileSaveDialog.FILE_KIND_MUSIC || mNewFileKind == FileSaveDialog.FILE_KIND_ALARM) {
            Toast.makeText(this, R.string.save_success_message, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // If it's a notification, give the user the option of making
        // this their default notification. If they say no, we're finished.
        if (mNewFileKind == FileSaveDialog.FILE_KIND_NOTIFICATION) {
            AlertDialog
                .Builder(this@RingdroidEditActivity)
                .setTitle(R.string.alert_title_success)
                .setMessage(R.string.set_default_notification)
                .setPositiveButton(
                    R.string.alert_yes_button,
                ) { dialog: DialogInterface?, whichButton: Int ->
                    setDefaultRingTone(
                        this@RingdroidEditActivity,
                        RingtoneManager.TYPE_NOTIFICATION,
                        newUri,
                        true,
                    )
                }.setNegativeButton(
                    R.string.alert_no_button,
                ) { dialog: DialogInterface?, whichButton: Int -> finish() }
                .setCancelable(false)
                .show()
            return
        }

        // If we get here, that means the type is a ringtone. There are
        // three choices: make this your default ringtone, assign it to a
        // contact, or do nothing.
        val handler: Handler =
            object : Handler(Looper.getMainLooper()) {
                override fun handleMessage(response: Message) {
                    val actionId = response.arg1
                    when (actionId) {
                        R.id.button_make_default ->
                            setDefaultRingTone(
                                this@RingdroidEditActivity,
                                RingtoneManager.TYPE_RINGTONE,
                                newUri,
                                true,
                            )

                        R.id.button_choose_contact -> chooseContactForRingtone(newUri)
                        R.id.button_do_nothing -> finish()
                        else -> finish()
                    }
                }
            }
        val message = Message.obtain(handler)
        val dialog = AfterSaveActionDialog(this, message)
        dialog.show()
    }

    private fun chooseContactForRingtone(uri: Uri?) {
        try {
            val intent = Intent(Intent.ACTION_EDIT, uri)
            intent.setClass(this, ChooseContactActivity::class.java)
            startActivityForResult(intent, REQUEST_CODE_CHOOSE_CONTACT)
        } catch (e: Exception) {
            Log.e("Ringdroid", "Couldn't open Choose Contact window")
        }
    }

    private fun onSave() {
        if (mIsPlaying) {
            handlePause()
        }

        val handler: Handler =
            object : Handler(Looper.getMainLooper()) {
                override fun handleMessage(response: Message) {
                    val newTitle = response.obj as CharSequence
                    mNewFileKind = response.arg1
                    saveRingtone(newTitle)
                }
            }
        val message = Message.obtain(handler)
        val dialog = FileSaveDialog(this, resources, mTitle, message)
        dialog.show()
    }

    private val currentTime: Long
        get() = System.nanoTime() / 1000000

    companion object {
        // Result codes
        private const val REQUEST_CODE_CHOOSE_CONTACT = 1

        @JvmStatic
        fun onAbout(activity: Activity) {
            var versionName: String?
            try {
                val packageManager = activity.packageManager
                val packageName = activity.packageName
                versionName = packageManager.getPackageInfo(packageName, 0).versionName
            } catch (e: PackageManager.NameNotFoundException) {
                versionName = "unknown"
            }

            val html = activity.getString(R.string.about_text_html, versionName)
            val builder =
                AlertDialog
                    .Builder(activity)
                    .setTitle(R.string.about_title)
                    .setPositiveButton(R.string.alert_ok_button, null)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                builder.setMessage(Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY))
            } else {
                builder.setMessage(Html.fromHtml(html)) // deprecated but works on < 24
            }

            val dialog = builder.show()

            // Make links clickable
            val messageView = dialog.findViewById<TextView?>(android.R.id.message)
            if (messageView != null) {
                messageView.movementMethod = LinkMovementMethod.getInstance()
            }
        }
    }
}
