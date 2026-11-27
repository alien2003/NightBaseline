package io.github.alien2003.nightbaseline.nav

import android.app.Application
import io.github.alien2003.nightbaseline.ui.layout.FoldInfo
import io.github.alien2003.nightbaseline.ui.layout.LayoutInputs
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** [InteractionSweep] on a phone (412 x 915 dp). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "en-rUS-w412dp-h915dp-port-420dpi")
class InteractionSweepPhoneTest : InteractionSweep(LayoutInputs(412f, 915f))

/** [InteractionSweep] on the book foldable's outer screen, folded (380 x 872 dp). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "en-rUS-w380dp-h872dp-port-480dpi")
class InteractionSweepFoldClosedTest : InteractionSweep(LayoutInputs(380f, 872f))

/** [InteractionSweep] on the book foldable's inner screen, open in two panes across the fold (749 x 826 dp). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "en-rUS-w749dp-h826dp-port-480dpi")
class InteractionSweepFoldOpenTest : InteractionSweep(LayoutInputs(749f, 826f, verticalFold = FoldInfo(374.5f, separating = true)))
