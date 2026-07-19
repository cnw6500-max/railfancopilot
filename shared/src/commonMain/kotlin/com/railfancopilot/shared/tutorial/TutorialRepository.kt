package com.railfancopilot.shared.tutorial

import com.russhwolf.settings.Settings

class TutorialRepository(private val settings: Settings = Settings()) {

    private val KEY_ONBOARDING_COMPLETE = "tutorial_onboarding_complete"
    private fun stepKey(step: TutorialStep) = "tutorial_step_seen_${step.name}"

    /** True if the user has completed or dismissed the initial onboarding flow. */
    val isOnboardingComplete: Boolean
        get() = settings.getBoolean(KEY_ONBOARDING_COMPLETE, defaultValue = false)

    fun markOnboardingComplete() {
        settings.putBoolean(KEY_ONBOARDING_COMPLETE, true)
        TutorialStep.entries.forEach { markStepSeen(it) }
    }

    fun resetOnboarding() {
        settings.putBoolean(KEY_ONBOARDING_COMPLETE, false)
        TutorialStep.entries.forEach { settings.remove(stepKey(it)) }
    }

    /** Returns the ordered list of steps to show in the onboarding flow. */
    fun onboardingSteps(): List<TutorialStep> = TutorialStep.entries

    /** True if a specific feature tooltip/coach mark has been shown. */
    fun isStepSeen(step: TutorialStep): Boolean =
        settings.getBoolean(stepKey(step), defaultValue = false)

    fun markStepSeen(step: TutorialStep) {
        settings.putBoolean(stepKey(step), true)
    }

    /** Returns steps not yet seen — use to drive coach marks on individual screens. */
    fun unseenSteps(): List<TutorialStep> =
        TutorialStep.entries.filter { !isStepSeen(it) }
}
