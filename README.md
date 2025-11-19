# Ringdroid

You can now easily download Ringdroid from F-Droid:

Download from F-Droid:

<a href="https://f-droid.org/packages/org.thayyil.ringdroid/">
  <img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" width="200" />
</a>

Download from Github:

<a href="https://github.com/althafvly/ringdroid/releases/latest">
  <img src="https://img.shields.io/github/v/release/althafvly/ringdroid?label=GitHub%20Releases&logo=github" alt="GitHub Releases"  width="200" />
</a>

&nbsp;

**9 November 2025: Ringdroid 2.7.5 adds full support for scoped storage and Upto Android 16 devices, including improved handling of permissions. This release also introduces interface polish, WebP optimizations.**

**2 December 2016: Ringdroid 2.7.4 contains a fix for an [audio bug](https://github.com/google/ringdroid/issues/20) reported by users using 'Nougat' (Android 7.x).**

**22 June 2015: Ringdroid 2.7.3 contains fixes for several bugs reported by users of Ringdroid 2.7.2, and it uses WAV as a fall-back audio format for saving ringtones in case the default AAC (.m4a) encoding fails.**

**11 May 2015: Ringdroid 2.7.2 does not require the full network communication permission anymore as the code in charge of gathering anonymous usage statistics has been removed.**

**29 April 2015: Ringdroid 2.7 now supports 'Lollipop' (Android 5.x)! This version requests the audio recording permission in order to use the device's microphone. It also includes the ability to read OGG Vorbis audio files, and it now saves ringtones in AAC (.m4a) format. This is the recommended version for Android system versions 4.1 ('Jelly Bean') and newer.**

**23 August 2012: Ringdroid 2.6 is the recommended version for Android system versions 3.0 ('Honeycomb') to 4.0.4 ('Ice Cream Sandwich'). This version of Ringdroid requests full network communication, but solely for the purpose of sending anonymous usage statistics as described in the app's 'Privacy' menu.**

**2 January 2011: Ringdroid 2.5 is the recommended version for Android system versions 1.6 ('Donut') to 2.3.7 ('Gingerbread'). New features include a drop-down menu in the select view to edit/delete/assign ringtones and notifications, flinging support to scroll the waveform, and MR475 AMR files support. This version of Ringdroid requests full network communication, but solely for the purpose of sending anonymous usage statistics as described in the app's 'Privacy' menu.**

Ringdroid is an [Android](https://developers.google.com/android) application for recording and editing sounds, and creating ringtones, directly on the handset.

  * [FAQ](https://github.com/google/ringdroid/wiki/FAQ)
  * [User Guide](https://github.com/google/ringdroid/wiki/Using-Ringdroid)
  * [Old APKs](https://github.com/google/ringdroid/wiki/APKs)
  * [Issues](https://github.com/althafvly/ringdroid/issues)

For developers:

  * [Developers](https://github.com/google/ringdroid/wiki/Developers)
  * [Building](https://github.com/google/ringdroid/wiki/Building)
  * [Emulator](https://github.com/google/ringdroid/wiki/Emulator)

See also:

  * [How to put Ringtones on Droid HD](http://www.youtube.com/watch?v=AnAZ829lDVo)

#### Features

* Open an existing audio file
* View a scrollable waveform representation of the audio file at 5 zoom levels
* Set starting and ending points for a clip within the audio file, using an optional touch interface
* Play the selected portion of the audio, including an indicator cursor and autoscrolling of the waveform
* Play anywhere else by tapping the screen
* Save the clipped audio as a new audio file and mark it as Music, Ringtone, Alarm, or Notification.
* Record a new audio clip to edit
* Delete audio (with confirmation alert)
* Launches automatically in response to the GET_CONTENT intent with a mime type of audio/ if any other application wants to pick an audio file - for example the "Rings Extended" application.
* Assign a ringtone directly to a contact.

#### File formats
Supported file formats right now include:

* MP3
* AAC/MP4 (including unprotected iTunes music)
* WAV
* 3GPP/AMR (this is the format used when you record sounds directly on the handset)
* OGG

<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" width="300" />
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" width="300" />
