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

package com.ringdroid.editor;

import com.ringdroid.R;
import com.ringdroid.soundfile.SoundFile;
import com.ringdroid.soundfile.SoundRecorder;
import com.ringdroid.util.ContactRingtonePicker;
import com.ringdroid.util.PermissionUtils;
import com.ringdroid.util.RingdroidUtils;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.Configuration;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.ringdroid.databinding.DialogProgressBinding;
import com.ringdroid.databinding.EditorBinding;
import com.ringdroid.databinding.EditorToolbarBinding;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

/**
 * The activity for the Ringdroid main editor window. Keeps track of the waveform display, current
 * horizontal offset, marker handles, start / end text boxes, and handles all of the buttons and
 * controls.
 */
public class RingdroidEditActivity extends ComponentActivity
        implements
            MarkerView.MarkerListener,
            WaveformView.WaveformListener {
    private static final String TAG = "RingdroidEditActivity";
    private final ContactRingtonePicker mContactPicker = new ContactRingtonePicker(this,
            this::finish);
    private volatile boolean mLoadingKeepGoing;
    private volatile boolean mRecordingKeepGoing;
    private volatile boolean mFinishActivity;
    private LoadingDialog mLoadingDialog;
    private RecordingDialog mRecordingDialog;
    private AlertDialog mProgressDialog;
    private SoundFile mSoundFile;
    private File mFile;
    private String mFilename;
    private String mArtist;
    private String mTitle;
    private int mNewFileKind;
    private boolean mWasGetContentIntent;
    private WaveformView mWaveformView;
    private MarkerView mStartMarker;
    private MarkerView mEndMarker;
    private TextView mStartText;
    private TextView mEndText;
    private TextView mInfo;
    private ImageButton mPlayButton;
    private boolean mKeyDown;
    private String mCaption = "";
    private int mWidth;
    private int mMaxPos;
    private int mStartPos;
    private int mEndPos;
    private boolean mStartVisible;
    private boolean mEndVisible;
    private int mLastDisplayedStartPos;
    private int mLastDisplayedEndPos;
    private int mOffset;
    private int mOffsetGoal;
    private int mFlingVelocity;
    private int mPlayStartMsec;
    private int mPlayEndMsec;
    private Handler mHandler;
    private boolean mIsPlaying;
    private SamplePlayer mPlayer;
    private boolean mTouchDragging;
    private float mTouchStart;
    private int mTouchInitialOffset;
    private int mTouchInitialStartPos;
    private int mTouchInitialEndPos;
    private long mWaveformTouchStartMsec;
    private float mDensity;
    private int mMarkerLeftInset;
    private int mMarkerRightInset;
    private int mMarkerTopOffset;
    private int mMarkerBottomOffset;
    //
    // Public methods and protected overrides
    //
    private final OnClickListener mPlayListener = new OnClickListener() {
        public void onClick(View sender) {
            onPlay(mStartPos);
        }
    };
    private final OnClickListener mRewindListener = new OnClickListener() {
        public void onClick(View sender) {
            if (mIsPlaying) {
                int newPos = mPlayer.getCurrentPosition() - 5000;
                if (newPos < mPlayStartMsec)
                    newPos = mPlayStartMsec;
                mPlayer.seekTo(newPos);
            } else {
                mStartMarker.requestFocus();
                markerFocus(mStartMarker);
            }
        }
    };
    private final OnClickListener mFfwdListener = new OnClickListener() {
        public void onClick(View sender) {
            if (mIsPlaying) {
                int newPos = 5000 + mPlayer.getCurrentPosition();
                if (newPos > mPlayEndMsec)
                    newPos = mPlayEndMsec;
                mPlayer.seekTo(newPos);
            } else {
                mEndMarker.requestFocus();
                markerFocus(mEndMarker);
            }
        }
    };
    private final OnClickListener mMarkStartListener = new OnClickListener() {
        public void onClick(View sender) {
            if (mIsPlaying) {
                mStartPos = mWaveformView.millisecsToPixels(mPlayer.getCurrentPosition());
                updateDisplay();
            }
        }
    };
    private final OnClickListener mMarkEndListener = new OnClickListener() {
        public void onClick(View sender) {
            if (mIsPlaying) {
                mEndPos = mWaveformView.millisecsToPixels(mPlayer.getCurrentPosition());
                updateDisplay();
                handlePause();
            }
        }
    };
    private final TextWatcher mTextWatcher = new TextWatcher() {
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        public void afterTextChanged(Editable s) {
            if (!mWaveformView.isInitialized()) {
                return;
            }
            if (mStartText.hasFocus()) {
                try {
                    mStartPos = mWaveformView
                            .secondsToPixels(Double.parseDouble(mStartText.getText().toString()));
                    updateDisplay();
                } catch (NumberFormatException ignored) {
                }
            }
            if (mEndText.hasFocus()) {
                try {
                    mEndPos = mWaveformView
                            .secondsToPixels(Double.parseDouble(mEndText.getText().toString()));
                    updateDisplay();
                } catch (NumberFormatException ignored) {
                }
            }
        }
    };
    private EditorBinding binding;
    private float mAmplifyFactor = 1.0f;
    private final ActivityResultLauncher<String> mMicPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (PermissionUtils.hasMicPermissions(this)) {
                    recordAudio();
                } else {
                    Toast.makeText(this, R.string.required_mic_permission, Toast.LENGTH_LONG)
                            .show();
                    finish();
                }
            });

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle icicle) {
        Log.v(TAG, "EditActivity OnCreate");
        super.onCreate(icicle);

        mPlayer = null;
        mIsPlaying = false;

        mProgressDialog = null;

        Intent intent = getIntent();

        // If the Ringdroid media select activity was launched via a
        // GET_CONTENT intent, then we shouldn't display a "saved"
        // message when the user saves, we should just return whatever
        // they create.
        mWasGetContentIntent = intent.getBooleanExtra("was_get_content_intent", false);

        Uri data = intent.getData();
        if (data == null) {
            Toast.makeText(this, R.string.alert_title_failure, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        mFilename = "file".equals(data.getScheme()) ? data.getPath() : data.toString();
        if (mFilename == null) {
            Toast.makeText(this, R.string.alert_title_failure, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        mSoundFile = null;
        mKeyDown = false;

        mHandler = new Handler(Looper.getMainLooper());

        loadGui();

        mHandler.postDelayed(mTimerRunnable, 100);

        if (!mFilename.equals("record")) {
            loadFromFile();
        } else {
            if (PermissionUtils.hasMicPermissions(this)) {
                recordAudio();
            } else {
                mMicPermissionLauncher.launch(PermissionUtils.getMicPermission());
            }
        }
    }

    //
    // WaveformListener
    //

    /**
     * Called when the activity is finally destroyed.
     */
    @Override
    protected void onDestroy() {
        Log.v(TAG, "EditActivity OnDestroy");

        // CRITICAL: Remove all handler callbacks to prevent memory leak
        // The mTimerRunnable keeps reposting itself every 100ms, so we must stop it
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
        }

        mLoadingKeepGoing = false;
        mRecordingKeepGoing = false;
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
        if (mLoadingDialog != null) {
            mLoadingDialog.dismiss();
        }
        if (mRecordingDialog != null) {
            mRecordingDialog.dismiss();
        }

        if (mPlayer != null) {
            if (mPlayer.isPlaying() || mPlayer.isPaused()) {
                mPlayer.stop();
            }
            mPlayer.release();
            mPlayer = null;
        }

        // Clear all listeners to prevent memory leaks
        if (mWaveformView != null) {
            mWaveformView.setListener(null);
        }
        if (mStartMarker != null) {
            mStartMarker.setListener(null);
        }
        if (mEndMarker != null) {
            mEndMarker.setListener(null);
        }

        // Release the SoundFile's large audio buffers to prevent memory leaks
        // Note: WaveformView also holds a reference, so we need to clear it there first
        if (mWaveformView != null) {
            mWaveformView.setSoundFile(null);
        }
        if (mSoundFile != null) {
            mSoundFile.release();
            mSoundFile = null;
        }

        super.onDestroy();
    }

    /**
     * Called when the orientation changes and/or the keyboard is shown or hidden. We don't need to
     * recreate the whole activity in this case, but we do need to redo our layout somewhat.
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        Log.v(TAG, "EditActivity onConfigurationChanged");
        final int saveZoomLevel = mWaveformView.getZoomLevel();
        super.onConfigurationChanged(newConfig);

        loadGui();

        mHandler.postDelayed(() -> {
            mStartMarker.requestFocus();
            markerFocus(mStartMarker);

            mWaveformView.setZoomLevel(saveZoomLevel);
            mWaveformView.recomputeHeights(mDensity);

            updateDisplay();
        }, 500);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.edit_options, menu);

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(R.id.action_save).setVisible(true);
        menu.findItem(R.id.action_reset).setVisible(true);
        menu.findItem(R.id.action_amplify).setVisible(true);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_save) {
            onSave();
            return true;
        } else if (id == R.id.action_reset) {
            resetPositions();
            mOffsetGoal = 0;
            updateDisplay();
            return true;
        } else if (id == R.id.action_amplify) {
            onAmplify();
            return true;
        }

        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_SPACE) {
            onPlay(mStartPos);
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    //
    // MarkerListener
    //

    /**
     * Every time we get a message that our waveform drew, see if we need to animate and trigger
     * another redraw.
     */
    public void waveformDraw() {
        mWidth = mWaveformView.getMeasuredWidth();
        if (mOffsetGoal != mOffset && !mKeyDown)
            updateDisplay();
        else if (mIsPlaying) {
            updateDisplay();
        } else if (mFlingVelocity != 0) {
            updateDisplay();
        }
    }

    public void waveformTouchStart(float x) {
        mTouchDragging = true;
        mTouchStart = x;
        mTouchInitialOffset = mOffset;
        mFlingVelocity = 0;
        mWaveformTouchStartMsec = getCurrentTime();
    }

    public void waveformTouchMove(float x) {
        mOffset = trap((int) (mTouchInitialOffset + (mTouchStart - x)));
        updateDisplay();
    }

    public void waveformTouchEnd() {
        mTouchDragging = false;
        mOffsetGoal = mOffset;

        long elapsedMsec = getCurrentTime() - mWaveformTouchStartMsec;
        if (elapsedMsec < 300) {
            if (mIsPlaying) {
                int seekMsec = mWaveformView.pixelsToMillisecs((int) (mTouchStart + mOffset));
                if (seekMsec >= mPlayStartMsec && seekMsec < mPlayEndMsec) {
                    mPlayer.seekTo(seekMsec);
                } else {
                    handlePause();
                }
            } else {
                onPlay((int) (mTouchStart + mOffset));
            }
        }
    }

    public void waveformFling(float vx) {
        mTouchDragging = false;
        mOffsetGoal = mOffset;
        mFlingVelocity = (int) (-vx);
        updateDisplay();
    }

    private void syncWaveformPositions() {
        mStartPos = mWaveformView.getStart();
        mEndPos = mWaveformView.getEnd();
        mMaxPos = mWaveformView.maxPos();
        mOffset = mWaveformView.getOffset();
        mOffsetGoal = mOffset;
        updateDisplay();
    }

    public void waveformZoomIn() {
        if (!mWaveformView.isInitialized()) {
            return;
        }
        mWaveformView.zoomIn();
        syncWaveformPositions();
    }

    public void waveformZoomOut() {
        if (!mWaveformView.isInitialized()) {
            return;
        }
        mWaveformView.zoomOut();
        syncWaveformPositions();
    }

    public void markerDraw() {
    }

    public void markerTouchStart(MarkerView marker, float x) {
        mTouchDragging = true;
        mTouchStart = x;
        mTouchInitialStartPos = mStartPos;
        mTouchInitialEndPos = mEndPos;
    }

    //
    // Static About dialog method, also called from RingdroidSelectActivity
    //

    public void markerTouchMove(MarkerView marker, float x) {
        float delta = x - mTouchStart;

        if (marker == mStartMarker) {
            mStartPos = trap((int) (mTouchInitialStartPos + delta));
            mEndPos = trap((int) (mTouchInitialEndPos + delta));
        } else {
            mEndPos = trap((int) (mTouchInitialEndPos + delta));
            if (mEndPos < mStartPos)
                mEndPos = mStartPos;
        }

        updateDisplay();
    }

    //
    // Internal methods
    //

    public void markerTouchEnd(MarkerView marker) {
        mTouchDragging = false;
        if (marker == mStartMarker) {
            setOffsetGoalStart();
        } else {
            setOffsetGoalEnd();
        }
    }

    public void markerLeft(MarkerView marker, int velocity) {
        mKeyDown = true;

        if (marker == mStartMarker) {
            int saveStart = mStartPos;
            mStartPos = trap(mStartPos - velocity);
            mEndPos = trap(mEndPos - (saveStart - mStartPos));
            setOffsetGoalStart();
        }

        if (marker == mEndMarker) {
            if (mEndPos == mStartPos) {
                mStartPos = trap(mStartPos - velocity);
                mEndPos = mStartPos;
            } else {
                mEndPos = trap(mEndPos - velocity);
            }

            setOffsetGoalEnd();
        }

        updateDisplay();
    }

    public void markerRight(MarkerView marker, int velocity) {
        mKeyDown = true;

        if (marker == mStartMarker) {
            int saveStart = mStartPos;
            mStartPos += velocity;
            if (mStartPos > mMaxPos)
                mStartPos = mMaxPos;
            mEndPos += (mStartPos - saveStart);
            if (mEndPos > mMaxPos)
                mEndPos = mMaxPos;

            setOffsetGoalStart();
        }

        if (marker == mEndMarker) {
            mEndPos += velocity;
            if (mEndPos > mMaxPos)
                mEndPos = mMaxPos;

            setOffsetGoalEnd();
        }

        updateDisplay();
    }

    public void markerEnter(MarkerView marker) {
    }

    public void markerKeyUp() {
        mKeyDown = false;
        updateDisplay();
    }

    public void markerFocus(MarkerView marker) {
        mKeyDown = false;
        if (marker == mStartMarker) {
            setOffsetGoalStartNoUpdate();
        } else {
            setOffsetGoalEndNoUpdate();
        }

        // Delay updaing the display because if this focus was in
        // response to a touch event, we want to receive the touch
        // event too before updating the display.
        mHandler.postDelayed(this::updateDisplay, 100);
    }

    /**
     * Called from both onCreate and onConfigurationChanged (if the user switched layouts)
     */
    private void loadGui() {
        // Inflate our UI from its XML layout description.
        binding = EditorBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        EditorToolbarBinding toolbarBinding = binding.editorToolbar;
        RingdroidUtils.applyStatusBarInsets(toolbarBinding.toolbarContainer);

        toolbarBinding.btnBack.setOnClickListener(v -> finish());
        toolbarBinding.btnAmplify.setOnClickListener(v -> onAmplify());
        toolbarBinding.btnReset.setOnClickListener(v -> {
            resetPositions();
            mOffsetGoal = 0;
            updateDisplay();
        });
        toolbarBinding.btnSave.setOnClickListener(v -> onSave());

        updateToolbarTitle();

        mDensity = getResources().getDisplayMetrics().density;

        mMarkerLeftInset = (int) (46 * mDensity);
        mMarkerRightInset = (int) (48 * mDensity);
        mMarkerTopOffset = (int) (10 * mDensity);
        mMarkerBottomOffset = (int) (10 * mDensity);

        mStartText = binding.starttext;
        mStartText.addTextChangedListener(mTextWatcher);
        mEndText = binding.endtext;
        mEndText.addTextChangedListener(mTextWatcher);

        mPlayButton = binding.play;
        mPlayButton.setOnClickListener(mPlayListener);
        ImageButton mRewindButton = binding.rew;
        mRewindButton.setOnClickListener(mRewindListener);
        ImageButton mFfwdButton = binding.ffwd;
        mFfwdButton.setOnClickListener(mFfwdListener);

        ImageButton zoomInButton = binding.zoomIn;
        ImageButton zoomOutButton = binding.zoomOut;
        zoomInButton.setOnClickListener(v -> {
            waveformZoomIn();
            waveformFling(1);
        });
        zoomOutButton.setOnClickListener(v -> {
            waveformZoomOut();
            waveformFling(1);
        });

        TextView markStartButton = binding.markStart;
        markStartButton.setOnClickListener(mMarkStartListener);
        TextView markEndButton = binding.markEnd;
        markEndButton.setOnClickListener(mMarkEndListener);

        enableDisableButtons();

        mWaveformView = binding.waveform;
        mWaveformView.setListener(this);

        mInfo = binding.info;
        mInfo.setText(mCaption);

        mMaxPos = 0;
        mLastDisplayedStartPos = -1;
        mLastDisplayedEndPos = -1;

        if (mSoundFile != null && !mWaveformView.hasSoundFile()) {
            mWaveformView.setSoundFile(mSoundFile);
            mWaveformView.recomputeHeights(mDensity);
            mMaxPos = mWaveformView.maxPos();
        }

        mStartMarker = binding.startmarker;
        mStartMarker.setListener(this);
        mStartMarker.setAlpha(1f);
        mStartMarker.setFocusable(true);
        mStartMarker.setFocusableInTouchMode(true);
        mStartVisible = true;

        mEndMarker = binding.endmarker;
        mEndMarker.setListener(this);
        mEndMarker.setAlpha(1f);
        mEndMarker.setFocusable(true);
        mEndMarker.setFocusableInTouchMode(true);
        mEndVisible = true;

        updateDisplay();
    }

    private void updateToolbarTitle() {
        if (binding == null) {
            return;
        }
        EditorToolbarBinding toolbarBinding = binding.editorToolbar;
        if (mTitle != null && !mTitle.isEmpty()) {
            toolbarBinding.toolbarTitle.setText(mTitle);
            if (mArtist != null && !mArtist.isEmpty()) {
                toolbarBinding.toolbarSubtitle.setText(mArtist);
                toolbarBinding.toolbarSubtitle.setVisibility(View.VISIBLE);
            } else {
                toolbarBinding.toolbarSubtitle.setVisibility(View.GONE);
            }
        } else {
            toolbarBinding.toolbarTitle.setText(R.string.edit_intent);
            toolbarBinding.toolbarSubtitle.setVisibility(View.GONE);
        }
    }

    private void loadFromFile() {
        mFile = new File(mFilename);

        SongMetadataReader metadataReader = new SongMetadataReader(mFilename);
        mTitle = metadataReader.mTitle;
        mArtist = metadataReader.mArtist;

        String titleLabel = mTitle;
        if (mArtist != null && !mArtist.isEmpty()) {
            titleLabel += " - " + mArtist;
        }
        setTitle(titleLabel);
        updateToolbarTitle();

        mLoadingKeepGoing = true;
        mFinishActivity = false;
        LoadingDialog loadingDialog = new LoadingDialog(this, mTitle, () -> {
            mLoadingKeepGoing = false;
            mFinishActivity = true;
        });
        mLoadingDialog = loadingDialog;
        if (!isFinishing() && !isDestroyed()) {
            loadingDialog.show();
        }

        final SoundFile.ProgressListener listener = fraction -> {
            loadingDialog.setProgress(fraction);
            return !mLoadingKeepGoing;
        };

        RingdroidUtils.BACKGROUND.execute(() -> {
            SoundFile soundFile = null;
            try {
                soundFile = SoundFile.create(getCacheDir(), mFile.getAbsolutePath(), listener);
                loadingDialog.dismiss();
                if (soundFile == null) {
                    if (mLoadingKeepGoing) {
                        String name = mFile.getName().toLowerCase(Locale.ROOT);
                        String[] components = name.split("\\.");
                        String err;
                        if (components.length < 2) {
                            err = getResources().getString(R.string.no_extension_error);
                        } else {
                            err = getResources().getString(R.string.bad_extension_error) + " "
                                    + components[components.length - 1];
                        }
                        mHandler.post(() -> showFinalAlert(null, err));
                        return;
                    }
                } else if (mLoadingKeepGoing) {
                    if (soundFile.getSamples() == null) {
                        throw new IllegalStateException("Decoded samples are unavailable");
                    }
                    postSoundFile(soundFile, new SamplePlayer(soundFile));
                    return;
                } else {
                    soundFile.release();
                }
            } catch (IOException | SoundFile.InvalidInputException | IllegalArgumentException
                    | IllegalStateException | UnsupportedOperationException | SecurityException e) {
                if (soundFile != null) {
                    soundFile.release();
                }
                loadingDialog.dismiss();
                postError(e, R.string.read_error);
                return;
            }
            if (mFinishActivity) {
                finish();
            }
        });
    }

    private void recordAudio() {
        mFile = null;
        mTitle = null;
        mArtist = null;

        mRecordingKeepGoing = true;
        mFinishActivity = false;
        if (isFinishing() || isDestroyed()) {
            return;
        }
        RecordingDialog recordDialog = new RecordingDialog(this, () -> mRecordingKeepGoing = false,
                () -> {
                    mRecordingKeepGoing = false;
                    mFinishActivity = true;
                });
        mRecordingDialog = recordDialog;
        recordDialog.show();

        final SoundFile.ProgressListener listener = elapsedSeconds -> {
            recordDialog.setElapsedSeconds(elapsedSeconds);
            return !mRecordingKeepGoing;
        };

        RingdroidUtils.BACKGROUND.execute(() -> {
            SoundFile soundFile = null;
            try {
                soundFile = SoundRecorder.record(listener);
                if (soundFile == null) {
                    recordDialog.dismiss();
                    postError(null, R.string.record_error);
                    return;
                }
                SamplePlayer player = new SamplePlayer(soundFile);
                recordDialog.dismiss();
                if (mFinishActivity) {
                    player.release();
                    soundFile.release();
                    finish();
                } else {
                    postSoundFile(soundFile, player);
                }
            } catch (IllegalArgumentException | IllegalStateException
                    | UnsupportedOperationException | SecurityException e) {
                if (soundFile != null) {
                    soundFile.release();
                }
                recordDialog.dismiss();
                postError(e, R.string.record_error);
            }
        });
    }

    // Hands a loaded sound file to the UI thread, or releases it if the editor is gone.
    private void postSoundFile(SoundFile soundFile, SamplePlayer player) {
        mHandler.post(() -> {
            if (isDestroyed()) {
                player.release();
                soundFile.release();
                return;
            }
            mSoundFile = soundFile;
            mPlayer = player;
            finishOpeningSoundFile();
        });
    }

    private void finishOpeningSoundFile() {
        mWaveformView.setSoundFile(mSoundFile);
        mWaveformView.recomputeHeights(mDensity);

        mMaxPos = mWaveformView.maxPos();
        mLastDisplayedStartPos = -1;
        mLastDisplayedEndPos = -1;

        mTouchDragging = false;

        mOffset = 0;
        mOffsetGoal = 0;
        mFlingVelocity = 0;
        resetPositions();
        if (mEndPos > mMaxPos)
            mEndPos = mMaxPos;

        mCaption = mSoundFile.getFiletype() + ", " + mSoundFile.getSampleRate() + " Hz, "
                + mSoundFile.getAvgBitrateKbps() + " kbps, " + formatTime(mMaxPos) + " "
                + getResources().getString(R.string.time_seconds);
        mInfo.setText(mCaption);

        updateDisplay();
    }
    private final Runnable mTimerRunnable = new Runnable() {
        public void run() {
            // Updating an EditText is slow on Android. Make sure
            // we only do the update if the text has actually changed.
            if (mStartPos != mLastDisplayedStartPos && !mStartText.hasFocus()) {
                mStartText.setText(formatTime(mStartPos));
                mLastDisplayedStartPos = mStartPos;
            }

            if (mEndPos != mLastDisplayedEndPos && !mEndText.hasFocus()) {
                mEndText.setText(formatTime(mEndPos));
                mLastDisplayedEndPos = mEndPos;
            }

            mHandler.postDelayed(mTimerRunnable, 100);
        }
    };

    private synchronized void updateDisplay() {
        if (mIsPlaying) {
            int now = mPlayer.getCurrentPosition();
            int frames = mWaveformView.millisecsToPixels(now);
            mWaveformView.setPlayback(frames);
            setOffsetGoalNoUpdate(frames - mWidth / 2);
            if (now >= mPlayEndMsec) {
                handlePause();
            }
        }

        if (!mTouchDragging) {
            int offsetDelta;

            if (mFlingVelocity != 0) {
                offsetDelta = mFlingVelocity / 30;
                if (mFlingVelocity > 80) {
                    mFlingVelocity -= 80;
                } else if (mFlingVelocity < -80) {
                    mFlingVelocity += 80;
                } else {
                    mFlingVelocity = 0;
                }

                mOffset += offsetDelta;

                if (mOffset + mWidth / 2 > mMaxPos) {
                    mOffset = mMaxPos - mWidth / 2;
                    mFlingVelocity = 0;
                }
                if (mOffset < 0) {
                    mOffset = 0;
                    mFlingVelocity = 0;
                }
                mOffsetGoal = mOffset;
            } else {
                offsetDelta = mOffsetGoal - mOffset;

                if (offsetDelta > 10)
                    offsetDelta = offsetDelta / 10;
                else if (offsetDelta > 0)
                    offsetDelta = 1;
                else if (offsetDelta < -10)
                    offsetDelta = offsetDelta / 10;
                else if (offsetDelta < 0)
                    offsetDelta = -1;

                mOffset += offsetDelta;
            }
        }

        mWaveformView.setParameters(mStartPos, mEndPos, mOffset);
        mWaveformView.invalidate();

        // Only update content descriptions when positions actually change to avoid
        // excessive string allocations
        if (mStartPos != mLastDisplayedStartPos) {
            try {
                String desc = getResources().getText(R.string.start_marker) + " "
                        + formatTime(mStartPos);
                if (desc.length() > 200) { // Sanity check
                    desc = desc.substring(0, 200);
                }
                mStartMarker.setContentDescription(desc);
            } catch (OutOfMemoryError e) {
                // If we can't allocate the string, just skip it
                Log.e(TAG, "OOM setting start marker content description");
            }
        }
        if (mEndPos != mLastDisplayedEndPos) {
            try {
                String desc = getResources().getText(R.string.end_marker) + " "
                        + formatTime(mEndPos);
                if (desc.length() > 200) { // Sanity check
                    desc = desc.substring(0, 200);
                }
                mEndMarker.setContentDescription(desc);
            } catch (OutOfMemoryError e) {
                // If we can't allocate the string, just skip it
                Log.e(TAG, "OOM setting end marker content description");
            }
        }

        int startX = mStartPos - mOffset - mMarkerLeftInset;
        if (startX + mStartMarker.getWidth() >= 0) {
            if (!mStartVisible) {
                // Delay this to avoid flicker
                mHandler.postDelayed(() -> {
                    mStartVisible = true;
                    mStartMarker.setAlpha(1f);
                }, 0);
            }
        } else {
            if (mStartVisible) {
                mStartMarker.setAlpha(0f);
                mStartVisible = false;
            }
            startX = 0;
        }

        int endX = mEndPos - mOffset - mEndMarker.getWidth() + mMarkerRightInset;
        if (endX + mEndMarker.getWidth() >= 0) {
            if (!mEndVisible) {
                // Delay this to avoid flicker
                mHandler.postDelayed(() -> {
                    mEndVisible = true;
                    mEndMarker.setAlpha(1f);
                }, 0);
            }
        } else {
            if (mEndVisible) {
                mEndMarker.setAlpha(0f);
                mEndVisible = false;
            }
            endX = 0;
        }

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(startX, mMarkerTopOffset, -mStartMarker.getWidth(),
                -mStartMarker.getHeight());
        mStartMarker.setLayoutParams(params);

        params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(endX,
                mWaveformView.getMeasuredHeight() - mEndMarker.getHeight() - mMarkerBottomOffset,
                -mStartMarker.getWidth(), -mStartMarker.getHeight());
        mEndMarker.setLayoutParams(params);
    }

    private void enableDisableButtons() {
        if (mIsPlaying) {
            mPlayButton.setImageResource(R.drawable.baseline_pause_24);
            mPlayButton.setContentDescription(getResources().getText(R.string.stop));
        } else {
            mPlayButton.setImageResource(R.drawable.baseline_play_arrow_24);
            mPlayButton.setContentDescription(getResources().getText(R.string.play));
        }
    }

    private void resetPositions() {
        mStartPos = mWaveformView.secondsToPixels(0.0);
        mEndPos = mWaveformView.secondsToPixels(15.0);
    }

    private int trap(int pos) {
        if (pos < 0)
            return 0;
        return Math.min(pos, mMaxPos);
    }

    private void setOffsetGoalStart() {
        setOffsetGoal(mStartPos - mWidth / 2);
    }

    private void setOffsetGoalStartNoUpdate() {
        setOffsetGoalNoUpdate(mStartPos - mWidth / 2);
    }

    private void setOffsetGoalEnd() {
        setOffsetGoal(mEndPos - mWidth / 2);
    }

    private void setOffsetGoalEndNoUpdate() {
        setOffsetGoalNoUpdate(mEndPos - mWidth / 2);
    }

    private void setOffsetGoal(int offset) {
        setOffsetGoalNoUpdate(offset);
        updateDisplay();
    }

    private void setOffsetGoalNoUpdate(int offset) {
        if (mTouchDragging) {
            return;
        }

        mOffsetGoal = offset;
        if (mOffsetGoal + mWidth / 2 > mMaxPos)
            mOffsetGoal = mMaxPos - mWidth / 2;
        if (mOffsetGoal < 0)
            mOffsetGoal = 0;
    }

    private String formatTime(int pixels) {
        if (mWaveformView != null && mWaveformView.isInitialized()) {
            return formatDecimal(mWaveformView.pixelsToSeconds(pixels));
        } else {
            return "";
        }
    }

    private String formatDecimal(double x) {
        return String.format(Locale.US, "%.2f", x);
    }

    private synchronized void handlePause() {
        if (mPlayer != null && mPlayer.isPlaying()) {
            mPlayer.pause();
        }
        mWaveformView.setPlayback(-1);
        mIsPlaying = false;
        enableDisableButtons();
    }

    private synchronized void onPlay(int startPosition) {
        if (mIsPlaying) {
            handlePause();
            return;
        }

        if (mPlayer == null) {
            // Not initialized yet
            return;
        }

        try {
            mPlayStartMsec = mWaveformView.pixelsToMillisecs(startPosition);
            if (startPosition < mStartPos) {
                mPlayEndMsec = mWaveformView.pixelsToMillisecs(mStartPos);
            } else if (startPosition > mEndPos) {
                mPlayEndMsec = mWaveformView.pixelsToMillisecs(mMaxPos);
            } else {
                mPlayEndMsec = mWaveformView.pixelsToMillisecs(mEndPos);
            }
            mPlayer.setOnCompletionListener(this::handlePause);
            mIsPlaying = true;

            mPlayer.seekTo(mPlayStartMsec);
            mPlayer.start();
            updateDisplay();
            enableDisableButtons();
        } catch (IllegalStateException | NullPointerException | IllegalArgumentException e) {
            showFinalAlert(e, R.string.play_error);
        }
    }

    /**
     * Shows an error dialog that closes the activity when dismissed. The cause, if any, is logged.
     */
    private void showFinalAlert(Throwable cause, CharSequence message) {
        Log.e(TAG, "Error: " + message, cause);
        setResult(RESULT_CANCELED, new Intent());
        if (isFinishing() || isDestroyed()) {
            return;
        }
        new AlertDialog.Builder(this).setTitle(R.string.alert_title_failure).setMessage(message)
                .setPositiveButton(R.string.alert_ok_button, (dialog, whichButton) -> finish())
                .setCancelable(false).show();
    }

    private void showFinalAlert(Throwable e, int messageResourceId) {
        showFinalAlert(e, getResources().getText(messageResourceId));
    }

    private void saveRingtone(final CharSequence title) {
        double startTime = mWaveformView.pixelsToSeconds(mStartPos);
        double endTime = mWaveformView.pixelsToSeconds(mEndPos);
        final int startFrame = mWaveformView.secondsToFrames(startTime);
        final int endFrame = mWaveformView.secondsToFrames(endTime);
        final int durationMs = (int) ((endTime - startTime) * 1000 + 0.5);

        DialogProgressBinding progressBinding = DialogProgressBinding.inflate(getLayoutInflater());
        mProgressDialog = new AlertDialog.Builder(this).setView(progressBinding.getRoot())
                .setCancelable(false).create();
        RingdroidUtils.setTransparentBackground(mProgressDialog);
        if (!isFinishing() && !isDestroyed()) {
            mProgressDialog.show();
        }

        final AlertDialog progressDialog = mProgressDialog;
        RingtoneSaver saver = new RingtoneSaver(this, mSoundFile, mAmplifyFactor, mNewFileKind);
        RingdroidUtils.BACKGROUND.execute(() -> {
            try {
                Uri uri = saver.save(title, startFrame, endFrame - startFrame, durationMs);
                progressDialog.dismiss();
                mHandler.post(() -> afterSavingRingtone(uri));
            } catch (RingtoneSaver.SaveException e) {
                progressDialog.dismiss();
                if (e.messageId == R.string.too_small_error) {
                    mHandler.post(() -> new AlertDialog.Builder(this)
                            .setTitle(R.string.alert_title_failure).setMessage(e.messageId)
                            .setPositiveButton(R.string.alert_ok_button, null).show());
                } else {
                    postError(e.getCause(), e.messageId);
                }
            }
        });
    }

    private void postError(Throwable e, int messageId) {
        mHandler.post(() -> {
            if (e != null) {
                setSafeText(mInfo, e.toString());
            }
            showFinalAlert(e, messageId);
        });
    }

    private void afterSavingRingtone(Uri newUri) {
        setResult(RESULT_OK, new Intent().setData(newUri));

        // If Ringdroid was launched to get content, just return
        if (mWasGetContentIntent) {
            finish();
            return;
        }

        // There's nothing more to do with music or an alarm. Show a
        // success message and then quit.
        if (mNewFileKind == FileSaveDialog.FILE_KIND_MUSIC
                || mNewFileKind == FileSaveDialog.FILE_KIND_ALARM) {
            Toast.makeText(this, R.string.save_success_message, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // If it's a notification, give the user the option of making
        // this their default notification. If they say no, we're finished.
        if (mNewFileKind == FileSaveDialog.FILE_KIND_NOTIFICATION) {
            new AlertDialog.Builder(RingdroidEditActivity.this)
                    .setTitle(R.string.alert_title_success)
                    .setMessage(R.string.set_default_notification)
                    .setPositiveButton(R.string.alert_yes_button, (dialog, whichButton) -> {
                        RingdroidUtils.setDefaultRingTone(RingdroidEditActivity.this,
                                RingtoneManager.TYPE_NOTIFICATION, newUri, true);
                    })
                    .setNegativeButton(R.string.alert_no_button, (dialog, whichButton) -> finish())
                    .setCancelable(false).show();
            return;
        }

        // If we get here, that means the type is a ringtone. There are
        // three choices: make this your default ringtone, assign it to a
        // contact, or do nothing.

        new AfterSaveActionDialog(this, actionId -> {
            if (actionId == AfterSaveActionDialog.ACTION_MAKE_DEFAULT) {
                RingdroidUtils.setDefaultRingTone(this, RingtoneManager.TYPE_RINGTONE, newUri,
                        true);
            } else if (actionId == AfterSaveActionDialog.ACTION_CHOOSE_CONTACT) {
                chooseContactForRingtone(newUri);
            } else {
                finish();
            }
        }).show();
    }

    private void chooseContactForRingtone(Uri uri) {
        try {
            mContactPicker.pick(uri);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "Couldn't open the contact picker", e);
        }
    }

    private void onSave() {
        if (mIsPlaying) {
            handlePause();
        }

        new FileSaveDialog(this, mTitle, (title, fileKind) -> {
            mNewFileKind = fileKind;
            saveRingtone(title);
        }).show();
    }

    private long getCurrentTime() {
        return System.nanoTime() / 1000000;
    }

    private void onAmplify() {
        AmplifyDialog.show(this, mAmplifyFactor, factor -> mAmplifyFactor = factor);
    }

    /**
     * Safely set text to a TextView with length limiting to prevent OutOfMemoryError. This is
     * especially important for error messages and stack traces which can be very large.
     *
     * @param textView
     *            The TextView to set text on
     * @param text
     *            The text to set (can be null)
     */
    private void setSafeText(TextView textView, String text) {
        if (text == null) {
            textView.setText("");
            return;
        }

        // Limit text to 5000 characters to prevent OutOfMemoryError during text layout
        final int MAX_TEXT_LENGTH = 5000;
        if (text.length() > MAX_TEXT_LENGTH) {
            text = text.substring(0, MAX_TEXT_LENGTH) + "\n\n[Text truncated due to length]";
        }

        textView.setText(text);
    }

}
