package com.notionapp.mynotes.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.notionapp.mynotes.models.Note;
import com.notionapp.mynotes.repository.NoteRepository;
import com.notionapp.mynotes.utils.Constants;

import java.util.List;

/**
 * NoteViewModel — the bridge between the UI (Activities) and data (Repository).
 *
 * Key responsibilities:
 * 1. Holds LiveData that the UI observes for reactive updates
 * 2. Manages CRUD operations through the Repository (off main thread)
 * 3. Tracks current sort order and active search/filter state
 * 4. Survives configuration changes (e.g., screen rotation)
 *
 * The ViewModel NEVER holds a reference to an Activity or View.
 * It communicates through LiveData observation only.
 *
 * NOTE: In Java, field initializers that capture other fields (via lambdas)
 * run before the constructor body, so `repository` is not yet assigned.
 * All switchMap LiveData must therefore be set up inside the constructor.
 */
public class NoteViewModel extends ViewModel {

    private final NoteRepository repository;

    private final MutableLiveData<Integer> currentSortOrder =
            new MutableLiveData<>(Constants.SORT_NEWEST);
    private final MutableLiveData<String> searchQuery = new MutableLiveData<>("");
    private final MutableLiveData<String> categoryFilter = new MutableLiveData<>("");

    /** Which list is shown: Note.STATE_ACTIVE (Notes), STATE_ARCHIVED or STATE_TRASHED. */
    private final MutableLiveData<Integer> viewState = new MutableLiveData<>(Note.STATE_ACTIVE);

    /** Notes for the current search + category + sort. The single list the UI observes. */
    public final LiveData<List<Note>> notes;

    /** Note being edited; survives rotation so unsaved edits are not reloaded over. */
    private final MutableLiveData<Note> loadedNote = new MutableLiveData<>();
    private boolean noteLoadRequested = false;

    public NoteViewModel(NoteRepository repository) {
        this.repository = repository;

        // Re-query whenever any of the three inputs change.
        MediatorLiveData<Object> trigger = new MediatorLiveData<>();
        trigger.addSource(currentSortOrder, v -> trigger.setValue(v));
        trigger.addSource(searchQuery, v -> trigger.setValue(v));
        trigger.addSource(categoryFilter, v -> trigger.setValue(v));
        trigger.addSource(viewState, v -> trigger.setValue(v));

        notes = Transformations.switchMap(trigger, ignored -> repository.getNotes(
                valueOr(searchQuery.getValue(), "").trim(),
                valueOr(categoryFilter.getValue(), ""),
                currentSortOrder.getValue() != null
                        ? currentSortOrder.getValue() : Constants.SORT_NEWEST,
                getViewState()));
    }

    private static String valueOr(String v, String fallback) {
        return v != null ? v : fallback;
    }

    // ==============================
    // State
    // ==============================

    public int getViewState() {
        return viewState.getValue() != null ? viewState.getValue() : Note.STATE_ACTIVE;
    }

    public void setViewState(int state) {
        if (state != getViewState()) viewState.setValue(state);
    }

    public LiveData<Integer> getCurrentSortOrder() { return currentSortOrder; }

    public String getSearchQuery() { return valueOr(searchQuery.getValue(), ""); }

    public String getCategoryFilter() { return valueOr(categoryFilter.getValue(), ""); }

    public boolean isSearching() { return !getSearchQuery().trim().isEmpty(); }

    public void setSortOrder(int sortOrder) { currentSortOrder.setValue(sortOrder); }

    public void setSearchQuery(String query) {
        String q = query != null ? query : "";
        if (!q.equals(searchQuery.getValue())) searchQuery.setValue(q);
    }

    public void setCategoryFilter(String category) {
        String c = category != null ? category : "";
        if (!c.equals(categoryFilter.getValue())) categoryFilter.setValue(c);
    }

    // ==============================
    // Editing a single note
    // ==============================

    public LiveData<Note> getLoadedNote() { return loadedNote; }

    /** Loads the note once per ViewModel; later calls (e.g. after rotation) are no-ops. */
    public void loadNote(int noteId) {
        if (noteLoadRequested) return;
        noteLoadRequested = true;
        repository.loadNote(noteId, loadedNote::postValue);
    }

    // ==============================
    // CRUD
    // ==============================

    public void insertNote(Note note) { repository.insertNote(note); }

    public void updateNote(Note note) { repository.updateNote(note); }

    /** Flips the pinned flag on a copy so list diffing sees the change. */
    public void togglePin(Note note) {
        Note copy = note.copy();
        copy.setPinned(!note.isPinned());
        repository.updateNote(copy);
    }

    public void setNoteState(Note note, int state) { repository.setState(note, state); }

    public void deleteForever(Note note) { repository.deleteForever(note); }

    public void emptyTrash() { repository.emptyTrash(); }
}
