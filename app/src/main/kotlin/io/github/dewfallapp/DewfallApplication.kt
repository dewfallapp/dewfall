package io.github.dewfallapp

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** The app's Application class. Hilt builds the app-wide dependency container from it. */
@HiltAndroidApp class DewfallApplication : Application()
