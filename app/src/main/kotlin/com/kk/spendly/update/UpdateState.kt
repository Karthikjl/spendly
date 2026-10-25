package com.kk.spendly.update

/** What the in-app update banner is showing. */
sealed interface UpdateState {
    data object None : UpdateState
    data class Available(val info: ReleaseInfo) : UpdateState
    /** Android needs the user to allow this app to install packages before the update can proceed. */
    data class NeedsPermission(val info: ReleaseInfo) : UpdateState
    data class Downloading(val info: ReleaseInfo, val progress: Float) : UpdateState
    data class Failed(val info: ReleaseInfo, val message: String) : UpdateState

    val release: ReleaseInfo?
        get() = when (this) {
            None -> null
            is Available -> info
            is NeedsPermission -> info
            is Downloading -> info
            is Failed -> info
        }
}
