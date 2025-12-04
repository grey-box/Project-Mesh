package com.greybox.projectmesh

import android.os.Bundle
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedStateRegistryOwner
import org.kodein.di.DI


// This is a custom factory class for creating ViewModels that can be injected with DI
class ViewModelFactory<T: ViewModel>(
    private val di: DI,
    owner: SavedStateRegistryOwner,
    defaultArgs: Bundle?,
    private val vmFactory: (DI, SavedStateHandle) -> T
): AbstractSavedStateViewModelFactory(owner, defaultArgs) {
    override fun <T : ViewModel> create(
        key: String,
        modelClass: Class<T>,
        handle: SavedStateHandle
    ): T {
        return vmFactory(di, handle) as T
    }
}