package com.personal.sportspk.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.createSavedStateHandle

/**
 * Builds a [ViewModelProvider.Factory] for a ViewModel that takes a [SavedStateHandle].
 *
 * The SavedStateHandle is what carries selection and filter state across process death and
 * Activity recreation, which is the backbone of the fold-continuity requirement: unfolding may
 * recreate the Activity, but the selected game and league come back with it.
 */
fun savedStateViewModelFactory(
    create: (SavedStateHandle) -> ViewModel,
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
        create(extras.createSavedStateHandle()) as T
}

/** For ViewModels that need no saved state. */
fun viewModelFactory(create: () -> ViewModel): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
            create() as T
    }
