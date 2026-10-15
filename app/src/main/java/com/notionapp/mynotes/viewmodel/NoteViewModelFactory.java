package com.notionapp.mynotes.viewmodel;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.notionapp.mynotes.repository.NoteRepository;

/**
 * ViewModelFactory — required to pass the Repository into the ViewModel constructor.
 *
 * Why do we need this?
 * ViewModelProvider can only create ViewModels with no-arg constructors by default.
 * Our NoteViewModel needs a NoteRepository parameter, so we need a custom factory
 * to tell ViewModelProvider how to construct it.
 *
 * Usage in Activity:
 *   NoteViewModelFactory factory = new NoteViewModelFactory(repository);
 *   viewModel = new ViewModelProvider(this, factory).get(NoteViewModel.class);
 */
public class NoteViewModelFactory implements ViewModelProvider.Factory {

    private final NoteRepository repository;

    public NoteViewModelFactory(NoteRepository repository) {
        this.repository = repository;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(NoteViewModel.class)) {
            return (T) new NoteViewModel(repository);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
