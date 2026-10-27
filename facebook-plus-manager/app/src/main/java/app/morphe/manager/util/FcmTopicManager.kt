/*
 * Copyright 2026 8BiT Softworks.
 * Derived from Morphe Manager.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

object FcmTopics {
    const val MANAGER_STABLE = "morphe_updates"
    const val MANAGER_DEV = "morphe_updates_dev"
    const val PATCHES_STABLE = "morphe_patches_updates"
    const val PATCHES_DEV = "morphe_patches_updates_dev"

    val all = listOf(MANAGER_STABLE, MANAGER_DEV, PATCHES_STABLE, PATCHES_DEV)
}

fun syncFcmTopics(
    notificationsEnabled: Boolean,
    useManagerPrereleases: Boolean,
    usePatchesPrereleases: Boolean = false,
) {
}
