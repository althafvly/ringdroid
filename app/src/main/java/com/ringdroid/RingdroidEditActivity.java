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

package com.ringdroid;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.text.Editable;
import android.text.Html;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.ringdroid.soundfile.SoundFile;

import java.io.File;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.StringWriter;
import java.util.Locale;
import java.util.Objects;

/**
 * The activity for the Ringdroid main editor window. Keeps track of the
 * waveform display, current horizontal offset, marker handles, start / end text
 * boxes, and handles all of the buttons and controls.
 */
public class RingdroidEditActivity extends Activity
        implements
            MarkerView.MarkerListener,
            WaveformView.WaveformListener {
    // Result codes
    private static final int REQUEST_CODE_CHOOSE_CONTACT = 1;
    private final String TAG = this.getClass().getName();
    private long mLoadingLastUpdateTime;
    private boolean mLoadingKeepGoing;
    private long mRecordingLastUpdateTime;
    private boolean mRecordingKeepGoing;
    private double mRecordingTime;
    private boolean mFinishActivity;
    private TextView mTimerTextView;
    private AlertDialog mAlertDialog;
    private AlertDialog mProgressDialog;
    private ProgressBar mLoadingProgressBar;
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
    private String mInfoContent;
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
    private Context mContext;
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
            if (mStartText.hasFocus()) {
                try {
                    mStartPos = mWaveformView.secondsToPixels(Double.parseDouble(mStartText.getText().toString()));
                    updateDisplay();
                } catch (NumberFormatException ignored) {
                }
            }
            if (mEndText.hasFocus()) {
                try {
                    mEndPos = mWaveformView.secondsToPixels(Double.parseDouble(mEndText.getText().toString()));
                    updateDisplay();
                } catch (NumberFormatException ignored) {
                }
            }
        }
    };
    private Thread mLoadSoundFileThread;
    private Thread mRecordAudioThread;
    private Thread mSaveSoundFileThread;

    public static void onAbout(final Activity activity) {
        String versionName;
        try {
            PackageManager packageManager = activity.getPackageManager();
            String packageName = activity.getPackageName();
            versionName = packageManager.getPackageInfo(packageName, 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            versionName = "unknown";
        }

        String html = activity.getString(R.string.about_text_html, versionName);
        AlertDialog.Builder builder = new AlertDialog.Builder(activity).setTitle(R.string.about_title)
                .setPositiveButton(R.string.alert_ok_button, null);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            builder.setMessage(Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY));
        } else {
            @SuppressWarnings("deprecation")
            Spanned sp = Html.fromHtml(html);
            builder.setMessage(sp);
        }

        AlertDialog dialog = builder.show();

        // Make links clickable
        TextView messageView = dialog.findViewById(android.R.id.message);
        if (messageView != null) {
            messageView.setMovementMethod(LinkMovementMethod.getInstance());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PermissionUtils.MIC_PERMISSION_REQUEST) {
            boolean micPermissionGranted = true;

            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    micPermissionGranted = false;
                    break;
                }
            }

            if (micPermissionGranted) {
                recordAudio();
            } else {
                Toast.makeText(this, R.string.required_mic_permission, Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle icicle) {
        Log.v(TAG, "EditActivity OnCreate");
        super.onCreate(icicle);

        mPlayer = null;
        mIsPlaying = false;
        mContext = this.getApplicationContext();

        mAlertDialog = null;
        mProgressDialog = null;

        mLoadSoundFileThread = null;
        mRecordAudioThread = null;
        mSaveSoundFileThread = null;

        Intent intent = getIntent();

        // If the Ringdroid media select activity was launched via a
        // GET_CONTENT intent, then we shouldn't display a "saved"
        // message when the user saves, we should just return whatever
        // they create.
        mWasGetContentIntent = intent.getBooleanExtra("was_get_content_intent", false);

        mFilename = Objects.requireNonNull(intent.getData()).toString().replaceFirst("file://", "").replaceAll("%20",
                " ");
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
                PermissionUtils.requestMicPermissions(this);
            }
        }
    }

    private void closeThread(Thread thread) {
        if (thread != null && thread.isAlive()) {
            try {
                thread.join();
            } catch (InterruptedException ignored) {
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

        mLoadingKeepGoing = false;
        mRecordingKeepGoing = false;
        closeThread(mLoadSoundFileThread);
        closeThread(mRecordAudioThread);
        closeThread(mSaveSoundFileThread);
        mLoadSoundFileThread = null;
        mRecordAudioThread = null;
        mSaveSoundFileThread = null;
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
        if (mAlertDialog != null) {
            mAlertDialog.dismiss();
            mAlertDialog = null;
        }

        if (mPlayer != null) {
            if (mPlayer.isPlaying() || mPlayer.isPaused()) {
                mPlayer.stop();
            }
            mPlayer.release();
            mPlayer = null;
        }

        super.onDestroy();
    }

    /**
     * Called with an Activity we started with an Intent returns.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent dataIntent) {
        Log.v(TAG, "EditActivity onActivityResult");
        if (requestCode == REQUEST_CODE_CHOOSE_CONTACT) {
            // The user finished saving their ringtone and they're
            // just applying it to a contact. When they return here,
            // they're done.
            finish();
        }
    }

    /**
     * Called when the orientation changes and/or the keyboard is shown or hidden.
     * We don't need to recreate the whole activity in this case, but we do need to
     * redo our layout somewhat.
     */
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
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
        menu.findItem(R.id.action_about).setVisible(true);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_save) {
            onSave();
            return true;
        } else if (id == R.id.action_reset) {
            resetPositions();
            mOffsetGoal = 0;
            updateDisplay();
            return true;
        } else if (id == R.id.action_about) {
            onAbout(this);
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
     * Every time we get a message that our waveform drew, see if we need to animate
     * and trigger another redraw.
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

    public void waveformZoomIn() {
        mWaveformView.zoomIn();
        mStartPos = mWaveformView.getStart();
        mEndPos = mWaveformView.getEnd();
        mMaxPos = mWaveformView.maxPos();
        mOffset = mWaveformView.getOffset();
        mOffsetGoal = mOffset;
        updateDisplay();
    }

    public void waveformZoomOut() {
        mWaveformView.zoomOut();
        mStartPos = mWaveformView.getStart();
        mEndPos = mWaveformView.getEnd();
        mMaxPos = mWaveformView.maxPos();
        mOffset = mWaveformView.getOffset();
        mOffsetGoal = mOffset;
        updateDisplay();
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
     * Called from both onCreate and onConfigurationChanged (if the user switched
     * layouts)
     */
    private void loadGui() {
        // Inflate our UI from its XML layout description.
        setContentView(R.layout.editor);

        mDensity = getDisplayDensity();

        mMarkerLeftInset = (int) (46 * mDensity);
        mMarkerRightInset = (int) (48 * mDensity);
        mMarkerTopOffset = (int) (10 * mDensity);
        mMarkerBottomOffset = (int) (10 * mDensity);

        mStartText = findViewById(R.id.starttext);
        mStartText.addTextChangedListener(mTextWatcher);
        mEndText = findViewById(R.id.endtext);
        mEndText.addTextChangedListener(mTextWatcher);

        mPlayButton = findViewById(R.id.play);
        mPlayButton.setOnClickListener(mPlayListener);
        ImageButton mRewindButton = findViewById(R.id.rew);
        mRewindButton.setOnClickListener(mRewindListener);
        ImageButton mFfwdButton = findViewById(R.id.ffwd);
        mFfwdButton.setOnClickListener(mFfwdListener);

        TextView markStartButton = findViewById(R.id.mark_start);
        markStartButton.setOnClickListener(mMarkStartListener);
        TextView markEndButton = findViewById(R.id.mark_end);
        markEndButton.setOnClickListener(mMarkEndListener);

        enableDisableButtons();

        mWaveformView = findViewById(R.id.waveform);
        mWaveformView.setListener(this);

        mInfo = findViewById(R.id.info);
        mInfo.setText(mCaption);

        mMaxPos = 0;
        mLastDisplayedStartPos = -1;
        mLastDisplayedEndPos = -1;

        if (mSoundFile != null && !mWaveformView.hasSoundFile()) {
            mWaveformView.setSoundFile(mSoundFile);
            mWaveformView.recomputeHeights(mDensity);
            mMaxPos = mWaveformView.maxPos();
        }

        mStartMarker = findViewById(R.id.startmarker);
        mStartMarker.setListener(this);
        mStartMarker.setAlpha(1f);
        mStartMarker.setFocusable(true);
        mStartMarker.setFocusableInTouchMode(true);
        mStartVisible = true;

        mEndMarker = findViewById(R.id.endmarker);
        mEndMarker.setListener(this);
        mEndMarker.setAlpha(1f);
        mEndMarker.setFocusable(true);
        mEndMarker.setFocusableInTouchMode(true);
        mEndVisible = true;

        updateDisplay();
    }

    @SuppressWarnings("deprecation")
    private float getDisplayDensity() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // WindowMetrics does not provide density, so use Resources
            return getResources().getDisplayMetrics().density;
        } else {
            Display display = getWindowManager().getDefaultDisplay();
            DisplayMetrics tmp = new DisplayMetrics();
            display.getMetrics(tmp);
            return tmp.density;
        }
    }

    private void loadFromFile() {
        mFile = new File(mFilename);

        SongMetadataReader metadataReader = new SongMetadataReader(this, mFilename);
        mTitle = metadataReader.mTitle;
        mArtist = metadataReader.mArtist;

        String titleLabel = mTitle;
        if (mArtist != null && !mArtist.isEmpty()) {
            titleLabel += " - " + mArtist;
        }
        setTitle(titleLabel);

        mLoadingLastUpdateTime = getCurrentTime();
        mLoadingKeepGoing = true;
        mFinishActivity = false;
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_progress_horizontal, null);
        ProgressBar progressBar = dialogView.findViewById(R.id.progress_bar);
        progressBar.setMax(100);
        mLoadingProgressBar = progressBar;

        mProgressDialog = new AlertDialog.Builder(this).setTitle(R.string.progress_dialog_loading).setView(dialogView)
                .setCancelable(true).setOnCancelListener(dialog -> {
                    mLoadingKeepGoing = false;
                    mFinishActivity = true;
                }).create();
        mProgressDialog.show();

        final SoundFile.ProgressListener listener = fractionComplete -> {
            long now = getCurrentTime();
            if (now - mLoadingLastUpdateTime > 100) {
                if (mLoadingProgressBar != null) {
                    int progress = (int) (fractionComplete * 100);
                    mLoadingProgressBar.setProgress(progress);
                }
                mLoadingLastUpdateTime = now;
            }
            return !mLoadingKeepGoing;
        };

        // Load the sound file in a background thread
        mLoadSoundFileThread = new Thread(() -> {
            try {
                mSoundFile = SoundFile.create(mFile.getAbsolutePath(), listener);

                if (mSoundFile == null) {
                    mProgressDialog.dismiss();
                    String name = mFile.getName().toLowerCase();
                    String[] components = name.split("\\.");
                    String err;
                    if (components.length < 2) {
                        err = getResources().getString(R.string.no_extension_error);
                    } else {
                        err = getResources().getString(R.string.bad_extension_error) + " "
                                + components[components.length - 1];
                    }
                    final String finalErr = err;
                    Runnable runnable = () -> showFinalAlert(new Exception(), finalErr);
                    mHandler.post(runnable);
                    return;
                }
                mPlayer = new SamplePlayer(mSoundFile);
            } catch (final Exception e) {
                mProgressDialog.dismiss();
                Log.e(TAG, "Unexpected error: " + e.getMessage(), e);
                mInfoContent = e.toString();
                runOnUiThread(() -> mInfo.setText(mInfoContent));

                Runnable runnable = () -> showFinalAlert(e, getResources().getText(R.string.read_error));
                mHandler.post(runnable);
                return;
            }
            mProgressDialog.dismiss();
            if (mLoadingKeepGoing) {
                Runnable runnable = this::finishOpeningSoundFile;
                mHandler.post(runnable);
            } else if (mFinishActivity) {
                RingdroidEditActivity.this.finish();
            }
        });
        mLoadSoundFileThread.start();
    }

    private void recordAudio() {
        mFile = null;
        mTitle = null;
        mArtist = null;

        mRecordingLastUpdateTime = getCurrentTime();
        mRecordingKeepGoing = true;
        mFinishActivity = false;
        AlertDialog.Builder adBuilder = new AlertDialog.Builder(RingdroidEditActivity.this);
        adBuilder.setTitle(getResources().getText(R.string.progress_dialog_recording));
        adBuilder.setCancelable(true);
        adBuilder.setNegativeButton(getResources().getText(R.string.progress_dialog_cancel), (dialog, id) -> {
            mRecordingKeepGoing = false;
            mFinishActivity = true;
        });
        adBuilder.setPositiveButton(getResources().getText(R.string.progress_dialog_stop),
                (dialog, id) -> mRecordingKeepGoing = false);
        // TODO(nfaralli): try to use a FrameLayout and pass it to the following inflate
        // call.
        // Using null, android:layout_width etc. may not work (hence text is at the top
        // of view).
        // On the other hand, if the text is big enough, this is good enough.
        adBuilder.setView(getLayoutInflater().inflate(R.layout.record_audio, null));
        mAlertDialog = adBuilder.show();
        mTimerTextView = mAlertDialog.findViewById(R.id.record_audio_timer);

        final SoundFile.ProgressListener listener = elapsedTime -> {
            long now = getCurrentTime();
            if (now - mRecordingLastUpdateTime > 5) {
                mRecordingTime = elapsedTime;
                // Only UI thread can update Views such as TextViews.
                runOnUiThread(() -> {
                    int min = (int) (mRecordingTime / 60);
                    float sec = (float) (mRecordingTime - 60 * min);
                    mTimerTextView.setText(String.format(Locale.getDefault(), "%d:%05.2f", min, sec));
                });
                mRecordingLastUpdateTime = now;
            }
            return !mRecordingKeepGoing;
        };

        // Record the audio stream in a background thread
        mRecordAudioThread = new Thread(() -> {
            try {
                mSoundFile = SoundFile.record(listener);
                if (mSoundFile == null) {
                    mAlertDialog.dismiss();
                    Runnable runnable = () -> showFinalAlert(new Exception(),
                            getResources().getText(R.string.record_error));
                    mHandler.post(runnable);
                    return;
                }
                mPlayer = new SamplePlayer(mSoundFile);
            } catch (final Exception e) {
                mAlertDialog.dismiss();
                Log.e(TAG, "Unexpected error: " + e.getMessage(), e);
                mInfoContent = e.toString();
                runOnUiThread(() -> mInfo.setText(mInfoContent));

                Runnable runnable = () -> showFinalAlert(e, getResources().getText(R.string.record_error));
                mHandler.post(runnable);
                return;
            }
            mAlertDialog.dismiss();
            if (mFinishActivity) {
                RingdroidEditActivity.this.finish();
            } else {
                Runnable runnable = this::finishOpeningSoundFile;
                mHandler.post(runnable);
            }
        });
        mRecordAudioThread.start();
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

        mStartMarker.setContentDescription(getResources().getText(R.string.start_marker) + " " + formatTime(mStartPos));
        mEndMarker.setContentDescription(getResources().getText(R.string.end_marker) + " " + formatTime(mEndPos));

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

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(startX, mMarkerTopOffset, -mStartMarker.getWidth(), -mStartMarker.getHeight());
        mStartMarker.setLayoutParams(params);

        params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(endX, mWaveformView.getMeasuredHeight() - mEndMarker.getHeight() - mMarkerBottomOffset,
                -mStartMarker.getWidth(), -mStartMarker.getHeight());
        mEndMarker.setLayoutParams(params);
    }

    private void enableDisableButtons() {
        if (mIsPlaying) {
            mPlayButton.setImageResource(android.R.drawable.ic_media_pause);
            mPlayButton.setContentDescription(getResources().getText(R.string.stop));
        } else {
            mPlayButton.setImageResource(android.R.drawable.ic_media_play);
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
        int xWhole = (int) x;
        int xFrac = (int) (100 * (x - xWhole) + 0.5);

        if (xFrac >= 100) {
            xWhole++; // Round up
            xFrac -= 100; // Now we need the remainder after the round up
            if (xFrac < 10) {
                xFrac *= 10; // we need a fraction that is 2 digits long
            }
        }

        if (xFrac < 10)
            return xWhole + ".0" + xFrac;
        else
            return xWhole + "." + xFrac;
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
        } catch (Exception e) {
            showFinalAlert(e, R.string.play_error);
        }
    }

    /**
     * Show a "final" alert dialog that will exit the activity after the user clicks
     * on the OK button. If an exception is passed, it's assumed to be an error
     * condition, and the dialog is presented as an error, and the stack trace is
     * logged. If there's no exception, it's a success message.
     */
    private void showFinalAlert(Exception e, CharSequence message) {
        CharSequence title;
        if (e != null) {
            Log.e(TAG, "Error: " + message);
            Log.e(TAG, FilesUtil.getStackTrace(e));
            title = getResources().getText(R.string.alert_title_failure);
            setResult(RESULT_CANCELED, new Intent());
        } else {
            Log.v(TAG, "Success: " + message);
            title = getResources().getText(R.string.alert_title_success);
        }

        new AlertDialog.Builder(RingdroidEditActivity.this).setTitle(title).setMessage(message)
                .setPositiveButton(R.string.alert_ok_button, (dialog, whichButton) -> finish()).setCancelable(false)
                .show();
    }

    private void showFinalAlert(Exception e, int messageResourceId) {
        showFinalAlert(e, getResources().getText(messageResourceId));
    }

    private String getSubDir() {
        String saveDir = PermissionUtils.hasMediaAudioPermission(mContext)
                ? Environment.DIRECTORY_RINGTONES
                : "media/audio";
        StringBuilder subdir = new StringBuilder(saveDir);
        switch (mNewFileKind) {
            case FileSaveDialog.FILE_KIND_MUSIC :
                subdir.append("/music/");
                break;

            case FileSaveDialog.FILE_KIND_ALARM :
                subdir.append("/alarms/");
                break;

            case FileSaveDialog.FILE_KIND_NOTIFICATION :
                subdir.append("/notifications/");
                break;

            case FileSaveDialog.FILE_KIND_RINGTONE :
                subdir.append("/ringtones/");
                break;

            default :
                subdir.append("/others/");
                break;
        }

        return subdir.toString();
    }

    private String makeRingtoneFilename(CharSequence title, String extension) {
        String externalRootDir = Environment.getExternalStorageDirectory().getPath();
        if (!externalRootDir.endsWith("/")) {
            externalRootDir += "/";
        }

        String parentdir = externalRootDir + getSubDir();

        // Create the parent directory
        File parentDirFile = new File(parentdir);
        boolean status = parentDirFile.mkdirs();
        Log.d(TAG, "Created folder: " + parentdir + "status:" + status);

        // If we can't write to that special path, try just writing
        // directly to the sdcard
        if (!parentDirFile.isDirectory()) {
            parentdir = externalRootDir;
        }

        // Turn the title into a filename
        StringBuilder filename = new StringBuilder();
        for (int i = 0; i < title.length(); i++) {
            if (Character.isLetterOrDigit(title.charAt(i))) {
                filename.append(title.charAt(i));
            }
        }

        // Try to make the filename unique
        String path = null;
        for (int i = 0; i < 100; i++) {
            String testPath;
            if (i > 0)
                testPath = parentdir + filename + i + extension;
            else
                testPath = parentdir + filename + extension;

            try {
                RandomAccessFile f = new RandomAccessFile(new File(testPath), "r");
                f.close();
            } catch (Exception e) {
                // Good, the file didn't exist
                path = testPath;
                break;
            }
        }

        return path;
    }

    private void saveRingtone(final CharSequence title) {
        double startTime = mWaveformView.pixelsToSeconds(mStartPos);
        double endTime = mWaveformView.pixelsToSeconds(mEndPos);
        final int startFrame = mWaveformView.secondsToFrames(startTime);
        final int endFrame = mWaveformView.secondsToFrames(endTime);
        final int duration = (int) (endTime - startTime + 0.5);

        // Create an indeterminate progress dialog
        View view = getLayoutInflater().inflate(R.layout.dialog_progress, null);

        mProgressDialog = new AlertDialog.Builder(this).setTitle(R.string.progress_dialog_saving).setView(view)
                .setCancelable(false).create();
        mProgressDialog.show();

        ContentValues values = new ContentValues();
        values.put(MediaStore.Audio.Media.IS_RINGTONE, mNewFileKind == FileSaveDialog.FILE_KIND_RINGTONE);
        values.put(MediaStore.Audio.Media.IS_NOTIFICATION, mNewFileKind == FileSaveDialog.FILE_KIND_NOTIFICATION);
        values.put(MediaStore.Audio.Media.IS_ALARM, mNewFileKind == FileSaveDialog.FILE_KIND_ALARM);
        values.put(MediaStore.Audio.Media.IS_MUSIC, mNewFileKind == FileSaveDialog.FILE_KIND_MUSIC);
        values.put(MediaStore.Audio.Media.RELATIVE_PATH, getSubDir());

        // Save the sound file in a background thread
        mSaveSoundFileThread = new Thread() {
            public void run() {
                // Try AAC first.
                String outPath = makeRingtoneFilename(title, ".m4a");
                Uri outUri = null;
                if (outPath == null) {
                    Runnable runnable = () -> showFinalAlert(new Exception(), R.string.no_unique_filename);
                    mHandler.post(runnable);
                    return;
                }
                File outFile = new File(outPath);
                boolean fallbackToWAV = false;
                try {
                    // Write the new file
                    if (PermissionUtils.hasMediaAudioPermission(mContext)) {
                        values.put(MediaStore.Audio.Media.DISPLAY_NAME, title + ".m4a");
                        values.put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4a-latm");
                        outUri = getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
                        mSoundFile.WriteFile(mContext, outUri, startFrame, endFrame - startFrame);
                    } else {
                        mSoundFile.WriteFile(outFile, startFrame, endFrame - startFrame);
                    }
                } catch (Exception e) {
                    // log the error and try to create a .wav file instead
                    if (outFile.exists()) {
                        boolean status = outFile.delete();
                        Log.d(TAG, "Delete file: " + outPath + "status:" + status);
                    }
                    StringWriter writer = new StringWriter();
                    e.printStackTrace(new PrintWriter(writer));
                    Log.e(TAG, "Error: Failed to create " + outPath);
                    Log.e(TAG, writer.toString());
                    fallbackToWAV = true;
                }

                // Try to create a .wav file if creating a .m4a file failed.
                if (fallbackToWAV) {
                    outPath = makeRingtoneFilename(title, ".wav");
                    if (outPath == null) {
                        Runnable runnable = () -> showFinalAlert(new Exception(), R.string.no_unique_filename);
                        mHandler.post(runnable);
                        return;
                    }
                    outFile = new File(outPath);
                    try {
                        // create the .wav file
                        if (PermissionUtils.hasMediaAudioPermission(mContext)) {
                            values.put(MediaStore.Audio.Media.DISPLAY_NAME, title + ".wav");
                            values.put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav");
                            outUri = getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
                            mSoundFile.WriteWAVFile(mContext, outUri, startFrame, endFrame - startFrame);
                        } else {
                            mSoundFile.WriteWAVFile(outFile, startFrame, endFrame - startFrame);
                        }
                    } catch (Exception e) {
                        // Creating the .wav file also failed. Stop the progress dialog, show an
                        // error message and exit.
                        Exception exception = e;
                        mProgressDialog.dismiss();
                        if (outFile.exists()) {
                            boolean status = outFile.delete();
                            Log.d(TAG, "Delete file: " + outPath + "status:" + status);
                        }
                        mInfoContent = exception.toString();
                        runOnUiThread(() -> mInfo.setText(mInfoContent));

                        CharSequence errorMessage;
                        if (exception.getMessage() != null
                                && exception.getMessage().equals("No space left on device")) {
                            errorMessage = getResources().getText(R.string.no_space_error);
                            exception = null;
                        } else {
                            errorMessage = getResources().getText(R.string.write_error);
                        }
                        final CharSequence finalErrorMessage = errorMessage;
                        final Exception finalException = exception;
                        Runnable runnable = () -> showFinalAlert(finalException, finalErrorMessage);
                        mHandler.post(runnable);
                        return;
                    }
                }

                // Try to load the new file to make sure it worked
                try {
                    final SoundFile.ProgressListener listener = frac -> {
                        // Do nothing - we're not going to try to
                        // estimate when reloading a saved sound
                        // since it's usually fast, but hard to
                        // estimate anyway.
                        return false; // Keep going
                    };
                    if (PermissionUtils.hasMediaAudioPermission(mContext) && outUri != null) {
                        SoundFile.uriExists(mContext, outUri);
                    } else {
                        SoundFile.create(outPath, listener);
                    }
                } catch (final Exception e) {
                    mProgressDialog.dismiss();
                    Log.e(TAG, "Unexpected error: " + e.getMessage(), e);
                    mInfoContent = e.toString();
                    runOnUiThread(() -> mInfo.setText(mInfoContent));

                    Runnable runnable = () -> showFinalAlert(e, getResources().getText(R.string.write_error));
                    mHandler.post(runnable);
                    return;
                }

                mProgressDialog.dismiss();

                final String finalOutPath = outPath;
                final Uri finalOutUri = outUri;
                Runnable runnable = () -> afterSavingRingtone(title, finalOutPath, finalOutUri, duration);
                mHandler.post(runnable);
            }
        };
        mSaveSoundFileThread.start();
    }

    private void afterSavingRingtone(CharSequence title, String outPath, Uri outUri, int duration) {
        Uri newUri;
        if (PermissionUtils.hasMediaAudioPermission(mContext) && outUri == null) {
            File outFile = new File(outPath);
            long fileSize = outFile.length();
            if (fileSize <= 512) {
                boolean status = outFile.delete();
                if (status) {
                    new AlertDialog.Builder(this).setTitle(R.string.alert_title_failure)
                            .setMessage(R.string.too_small_error).setPositiveButton(R.string.alert_ok_button, null)
                            .setCancelable(false).show();
                }
                return;
            }

            // Create the database record, pointing to the existing file path
            String mimeType;
            if (outPath.endsWith(".m4a")) {
                mimeType = "audio/mp4a-latm";
            } else if (outPath.endsWith(".wav")) {
                mimeType = "audio/wav";
            } else {
                // This should never happen.
                mimeType = "audio/mpeg";
            }

            String artist = "" + getResources().getText(R.string.artist_name);

            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DATA, outPath);
            values.put(MediaStore.MediaColumns.TITLE, title.toString());
            values.put(MediaStore.MediaColumns.SIZE, fileSize);
            values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);

            values.put(MediaStore.Audio.Media.ARTIST, artist);
            values.put(MediaStore.Audio.Media.DURATION, duration);

            values.put(MediaStore.Audio.Media.IS_RINGTONE, mNewFileKind == FileSaveDialog.FILE_KIND_RINGTONE);
            values.put(MediaStore.Audio.Media.IS_NOTIFICATION, mNewFileKind == FileSaveDialog.FILE_KIND_NOTIFICATION);
            values.put(MediaStore.Audio.Media.IS_ALARM, mNewFileKind == FileSaveDialog.FILE_KIND_ALARM);
            values.put(MediaStore.Audio.Media.IS_MUSIC, mNewFileKind == FileSaveDialog.FILE_KIND_MUSIC);

            // Insert it into the database
            Uri uri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                uri = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
            } else {
                uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            }
            assert uri != null;
            newUri = getContentResolver().insert(uri, values);
            setResult(RESULT_OK, new Intent().setData(newUri));
        } else {
            newUri = outUri;
        }

        // If Ringdroid was launched to get content, just return
        if (mWasGetContentIntent) {
            finish();
            return;
        }

        // There's nothing more to do with music or an alarm. Show a
        // success message and then quit.
        if (mNewFileKind == FileSaveDialog.FILE_KIND_MUSIC || mNewFileKind == FileSaveDialog.FILE_KIND_ALARM) {
            Toast.makeText(this, R.string.save_success_message, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // If it's a notification, give the user the option of making
        // this their default notification. If they say no, we're finished.
        if (mNewFileKind == FileSaveDialog.FILE_KIND_NOTIFICATION) {
            new AlertDialog.Builder(RingdroidEditActivity.this).setTitle(R.string.alert_title_success)
                    .setMessage(R.string.set_default_notification)
                    .setPositiveButton(R.string.alert_yes_button, (dialog, whichButton) -> {
                        RingdroidUtils.setDefaultRingTone(RingdroidEditActivity.this, RingtoneManager.TYPE_NOTIFICATION,
                                newUri, true);
                    }).setNegativeButton(R.string.alert_no_button, (dialog, whichButton) -> finish())
                    .setCancelable(false).show();
            return;
        }

        // If we get here, that means the type is a ringtone. There are
        // three choices: make this your default ringtone, assign it to a
        // contact, or do nothing.

        final Handler handler = new Handler(Looper.getMainLooper()) {
            public void handleMessage(@NonNull Message response) {
                int actionId = response.arg1;
                if (actionId == R.id.button_make_default) {
                    RingdroidUtils.setDefaultRingTone(RingdroidEditActivity.this, RingtoneManager.TYPE_RINGTONE, newUri,
                            true);
                } else if (actionId == R.id.button_choose_contact) {
                    chooseContactForRingtone(newUri);
                } else {
                    finish();
                }
            }
        };
        Message message = Message.obtain(handler);
        AfterSaveActionDialog dialog = new AfterSaveActionDialog(this, message);
        dialog.show();
    }

    private void chooseContactForRingtone(Uri uri) {
        try {
            Intent intent = new Intent(Intent.ACTION_EDIT, uri);
            intent.setClass(this, ChooseContactActivity.class);
            startActivityForResult(intent, REQUEST_CODE_CHOOSE_CONTACT);
        } catch (Exception e) {
            Log.e(TAG, "Couldn't open Choose Contact window");
        }
    }

    private void onSave() {
        if (mIsPlaying) {
            handlePause();
        }

        final Handler handler = new Handler(Looper.getMainLooper()) {
            public void handleMessage(@NonNull Message response) {
                CharSequence newTitle = (CharSequence) response.obj;
                mNewFileKind = response.arg1;
                saveRingtone(newTitle);
            }
        };
        Message message = Message.obtain(handler);
        FileSaveDialog dlog = new FileSaveDialog(this, getResources(), mTitle, message);
        dlog.show();
    }

    private long getCurrentTime() {
        return System.nanoTime() / 1000000;
    }

}
