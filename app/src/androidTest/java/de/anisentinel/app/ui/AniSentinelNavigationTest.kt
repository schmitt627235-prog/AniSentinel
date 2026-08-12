package de.anisentinel.app.ui

import android.Manifest
import android.app.NotificationManager
import android.os.ParcelFileDescriptor
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import de.anisentinel.app.AniSentinelApplication
import de.anisentinel.app.MainActivity
import de.anisentinel.app.domain.repository.ThemePreference
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.MethodSorters
import org.junit.FixMethodOrder
import org.junit.runners.model.Statement
import org.junit.Test
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalTime

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AniSentinelNavigationTest {
    val rule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(NotificationPermissionResetRule())
        .around(rule)

    @Before
    fun resetTestState() {
        runBlocking {
            val container = (rule.activity.application as AniSentinelApplication).container
            container.settingsRepository.setLanguage("de")
            container.settingsRepository.setTheme(ThemePreference.SYSTEM)
            container.settingsRepository.setWatchProfileId("automatic")
            container.settingsRepository.setNotificationsEnabled(true)
            container.settingsRepository.setLiveDataEnabled(false)
            if (container.database.aniSentinelDao().favorite("skyward") != null) {
                container.favoritesRepository.setFavoriteEnabled(
                    "skyward",
                    false,
                    "SUB",
                    "balanced"
                )
            }
        }
        rule.activity.getSystemService(NotificationManager::class.java).cancelAll()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("Start").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @After
    fun restoreTestState() {
        runBlocking {
            val container = (rule.activity.application as AniSentinelApplication).container
            container.settingsRepository.setLanguage("de")
            container.settingsRepository.setTheme(ThemePreference.SYSTEM)
            container.settingsRepository.setNotificationsEnabled(true)
            container.settingsRepository.setLiveDataEnabled(false)
            if (container.database.aniSentinelDao().favorite("skyward") != null) {
                container.favoritesRepository.setFavoriteEnabled(
                    "skyward",
                    false,
                    "SUB",
                    "balanced"
                )
            }
        }
        rule.activity.getSystemService(NotificationManager::class.java).cancelAll()
    }

    @Test
    fun homeShowsReadableHeaderAndSourceStatus() {
        val greeting = when (LocalTime.now().hour) {
            in 5..10 -> "Guten Morgen!"
            in 11..17 -> "Guten Tag!"
            else -> "Guten Abend!"
        }
        rule.onNodeWithText(greeting).assertIsDisplayed()
        rule.onNodeWithTag(UiTags.CATALOG_STATUS).assertIsDisplayed()
    }

    @Test
    fun calendarOpensWithoutSeedData() {
        rule.onNodeWithTag(UiTags.NAV_PREFIX + "calendar").performClick()
        rule.onNodeWithText("Alle erwarteten Termine auf einen Blick.").assertIsDisplayed()
    }

    @Test
    fun bottomNavigationOpensSettings() {
        rule.onNodeWithTag(UiTags.NAV_PREFIX + "settings").performClick()
        rule.onNodeWithTag(UiTags.SETTINGS).assertIsDisplayed()
    }

    @Test
    fun settingsShowEachPrimarySettingExactlyOnce() {
        rule.onNodeWithTag(UiTags.NAV_PREFIX + "settings").performClick()

        rule.onAllNodesWithText("Darstellung").assertCountEquals(1)
        rule.onAllNodesWithText("Sprache").assertCountEquals(1)
        rule.onAllNodesWithText("Benachrichtigungen").assertCountEquals(1)
        rule.onAllNodesWithText("Watch-Profil").assertCountEquals(1)
        rule.onNodeWithTag(UiTags.SETTINGS_LIST).performScrollToIndex(8)
        val visibleProviderRows = rule.onAllNodesWithText("Anbieter", substring = false)
            .fetchSemanticsNodes().count { it.boundsInRoot.left >= 0f && it.boundsInRoot.right > 0f }
        assertEquals(1, visibleProviderRows)
        rule.onAllNodesWithText("Alle anzeigen").assertCountEquals(0)
    }

    @Test
    fun languageChangePersistsAcrossNavigationAndRecreation() {
        rule.onNodeWithTag(UiTags.NAV_PREFIX + "settings").performClick()
        rule.onNodeWithTag(UiTags.SETTINGS_LANGUAGE).performClick()
        rule.onNodeWithText("Customize AniSentinel to your needs.").assertIsDisplayed()

        rule.onNodeWithTag(UiTags.NAV_PREFIX + "discover").performClick()
        rule.onNodeWithTag(UiTags.DISCOVER_LIST).assertIsDisplayed()

        rule.onNodeWithTag(UiTags.NAV_PREFIX + "calendar").performClick()
        rule.onNodeWithText("All expected dates at a glance.").assertIsDisplayed()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText("All expected dates at a glance.").assertIsDisplayed()
    }

    @Test
    fun aboutEntryOpensRealLocalizedPage() {
        rule.onNodeWithTag(UiTags.NAV_PREFIX + "settings").performClick()
        rule.onNodeWithTag(UiTags.SETTINGS_LIST).performScrollToIndex(15)
        rule.onNodeWithTag(UiTags.ABOUT_ENTRY).performClick()

        rule.onNodeWithTag(UiTags.ABOUT).assertIsDisplayed()
        rule.onNodeWithText("Datenschutzgrundsatz").assertIsDisplayed()
        val versionName = rule.activity.packageManager
            .getPackageInfo(rule.activity.packageName, 0)
            .versionName.orEmpty()
        rule.onNodeWithText(versionName, substring = true).assertIsDisplayed()
    }

    @Test
    fun zPermissionGrantDoesNotCreateSyntheticReleaseNotification() {
        rule.onNodeWithTag(UiTags.NAV_PREFIX + "settings").performClick()
        rule.onNodeWithTag(UiTags.NOTIFICATION_DEMO).performScrollTo().performClick()
        clickPermissionDialog(allow = true)

        val manager = rule.activity.getSystemService(NotificationManager::class.java)
        rule.waitForIdle()
        assertEquals(0, manager.activeNotifications.size)
        rule.onNodeWithTag(UiTags.SETTINGS).assertIsDisplayed()
    }

    @Test
    fun deniedPermissionDoesNotSendDemoAndExplainsRecovery() {
        rule.onNodeWithTag(UiTags.NAV_PREFIX + "settings").performClick()
        rule.onNodeWithTag(UiTags.NOTIFICATION_DEMO).performScrollTo().performClick()
        clickPermissionDialog(allow = false)

        val manager = rule.activity.getSystemService(NotificationManager::class.java)
        rule.waitForIdle()
        assertEquals(0, manager.activeNotifications.size)
        rule.onNodeWithText(
            "Erneut versuchen oder bei dauerhafter Ablehnung die Android-Einstellungen öffnen"
        ).assertIsDisplayed()
    }

    @Test
    fun notificationPreferenceAndDemoAreSeparateActions() {
        rule.onNodeWithTag(UiTags.NAV_PREFIX + "settings").performClick()
        rule.onNodeWithTag(UiTags.NOTIFICATION_TOGGLE).performScrollTo().performClick()
        rule.onNodeWithText("Benachrichtigungen deaktiviert").assertIsDisplayed()
        rule.onNodeWithTag(UiTags.NOTIFICATION_DEMO).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun sourceFailureDoesNotExposeInternalRateLimitCode() {
        rule.onNodeWithTag(UiTags.CATALOG_STATUS).assertIsDisplayed()
        rule.onAllNodesWithText("rate_limited", substring = true).assertCountEquals(0)
    }

    @Test
    fun menuOpensDrawer() {
        rule.onNodeWithTag(UiTags.MENU).performClick()
        rule.onNodeWithTag(UiTags.DRAWER).assertIsDisplayed()
        rule.onNodeWithText("Entdecken & mehr").assertIsDisplayed()
    }

    private fun clickPermissionDialog(allow: Boolean) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val expectedId = if (allow) {
            "com.android.permissioncontroller:id/permission_allow_button"
        } else {
            "com.android.permissioncontroller:id/permission_deny_button"
        }
        val deadline = System.currentTimeMillis() + 5_000
        var clicked = false
        while (!clicked && System.currentTimeMillis() < deadline) {
            clicked = findNode(automation.rootInActiveWindow) { node ->
                node.viewIdResourceName == expectedId ||
                    node.text?.toString() in if (allow) {
                        setOf("Allow", "Zulassen")
                    } else {
                        setOf("Don’t allow", "Don't allow", "Nicht zulassen")
                    }
            }?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
            if (!clicked) Thread.sleep(100)
        }
        check(clicked) { "Permission dialog action was not found: allow=$allow" }
        rule.waitForIdle()
    }

    private fun findNode(
        node: AccessibilityNodeInfo?,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (node == null) return null
        if (predicate(node)) return node
        for (index in 0 until node.childCount) {
            findNode(node.getChild(index), predicate)?.let { return it }
        }
        return null
    }
}

private class NotificationPermissionResetRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement =
        object : Statement() {
            override fun evaluate() {
                resetPermission()
                base.evaluate()
            }
        }

    private fun resetPermission() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        runCatching {
            instrumentation.uiAutomation.revokeRuntimePermission(
                instrumentation.targetContext.packageName,
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
        executeShell(
            "pm clear-permission-flags ${instrumentation.targetContext.packageName} " +
                "${Manifest.permission.POST_NOTIFICATIONS} user-set user-fixed"
        )
    }

    private fun executeShell(command: String) {
        val descriptor: ParcelFileDescriptor =
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand(command)
        descriptor.close()
    }
}
