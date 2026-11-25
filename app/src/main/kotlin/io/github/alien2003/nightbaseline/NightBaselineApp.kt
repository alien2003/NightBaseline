package io.github.alien2003.nightbaseline

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** The application: the Hilt component root. The data modules are wired into it in a later step. */
@HiltAndroidApp
class NightBaselineApp : Application()
