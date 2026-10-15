package com.notionapp.mynotes.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.notionapp.mynotes.R;
import com.notionapp.mynotes.adapters.NoteAdapter;
import com.notionapp.mynotes.databinding.ActivityMainBinding;
import com.notionapp.mynotes.models.Note;
import com.notionapp.mynotes.utils.Constants;
import com.notionapp.mynotes.utils.NotionApp;
import com.notionapp.mynotes.viewmodel.NoteViewModel;
import com.notionapp.mynotes.viewmodel.NoteViewModelFactory;

import java.util.Arrays;
import java.util.List;

/**
 * MainActivity — the home screen of the Notes app.
 *
 * Responsibilities:
 * 1. Display all notes in a RecyclerView (Grid vs List layout mode support)
 * 2. Handle search, sort, and category filtering
 * 3. Navigate to AddEditNoteActivity for creating/editing
 * 4. Swipe-to-delete with undo
 * 5. Theme switching (Light/Dark/System)
 * 6. Dynamic result count and contextual empty state display
 * 7. FAB auto-hiding on scroll
 */
public class MainActivity extends AppCompatActivity {

    private static final String PREF_VIEW_MODE = "pref_view_mode";
    private static final String VIEW_MODE_GRID = "grid";
    private static final String VIEW_MODE_LIST = "list";

    // ViewBinding — inflated from activity_main.xml
    private ActivityMainBinding binding;

    // ViewModel — survives configuration changes
    private NoteViewModel viewModel;

    // RecyclerView adapter
    private NoteAdapter noteAdapter;

    // View state tracking
    private boolean isGridView = true;
    private androidx.activity.OnBackPressedCallback backToNotes;

    // Debounces search so we don't re-query on every keystroke
    private static final long SEARCH_DEBOUNCE_MS = 300;
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;

    // ==============================
    // Lifecycle
    // ==============================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Inflate layout using ViewBinding
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Load saved view mode preference
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        String savedMode = prefs.getString(PREF_VIEW_MODE, VIEW_MODE_GRID);
        isGridView = VIEW_MODE_GRID.equals(savedMode);

        // Initialize ViewModel with the repository from Application class
        setupViewModel();

        // Setup all UI components
        setupRecyclerView();
        setupSearchBar();
        setupCategoryChips();
        setupFab();
        setupToolbarMenu();
        setupSwipeToDelete();

        // Start observing notes data
        observeNotes();
    }

    @Override
    protected void onDestroy() {
        if (pendingSearch != null) searchHandler.removeCallbacks(pendingSearch);
        super.onDestroy();
    }

    // ==============================
    // ViewModel Setup
    // ==============================

    private void setupViewModel() {
        NotionApp app = (NotionApp) getApplication();
        NoteViewModelFactory factory = new NoteViewModelFactory(app.getRepository());
        viewModel = new ViewModelProvider(this, factory).get(NoteViewModel.class);
    }

    // ==============================
    // RecyclerView Setup
    // ==============================

    private void setupRecyclerView() {
        noteAdapter = new NoteAdapter(note -> {
            // Trashed notes can't be edited — offer restore / delete instead
            if (note.getState() == Note.STATE_TRASHED) {
                showNoteActions(note);
            } else {
                openAddEditNote(note.getId());
            }
        });

        noteAdapter.setOnNoteLongClickListener(note -> {
            binding.rvNotes.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            showNoteActions(note);
        });

        binding.rvNotes.setAdapter(noteAdapter);
        applyLayoutManager();

        // Keep the newest/edited note in view instead of leaving it off-screen
        noteAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                if (positionStart == 0) binding.rvNotes.scrollToPosition(0);
            }

            @Override
            public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
                if (toPosition == 0) binding.rvNotes.scrollToPosition(0);
            }
        });

        // Hide/Show FAB on scroll
        binding.rvNotes.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy > 10) {
                    binding.fabAddNote.shrink();
                } else if (dy < -10) {
                    binding.fabAddNote.extend();
                }
            }
        });
    }

    private void applyLayoutManager() {
        if (isGridView) {
            // 2 columns on phones, 3 on tablets / landscape
            int spans = getResources().getConfiguration().screenWidthDp >= 600 ? 3 : 2;
            binding.rvNotes.setLayoutManager(
                    new StaggeredGridLayoutManager(spans, StaggeredGridLayoutManager.VERTICAL)
            );
        } else {
            binding.rvNotes.setLayoutManager(new LinearLayoutManager(this));
        }
    }

    // ==============================
    // Observe Notes (LiveData)
    // ==============================

    private void observeNotes() {
        // One list covers search, category filter and sort.
        viewModel.notes.observe(this, this::updateNotesList);
    }

    /**
     * Updates the adapter list, note count header, and empty state display.
     */
    private void updateNotesList(List<Note> notes) {
        noteAdapter.submitList(notes);

        int count = notes != null ? notes.size() : 0;
        String category = viewModel.getCategoryFilter();

        if (count == 0) {
            binding.rvNotes.setVisibility(View.GONE);
            binding.tvNotesCount.setVisibility(View.GONE);
            binding.layoutEmptyState.setVisibility(View.VISIBLE);

            // Contextual empty state titles & subtitles
            int viewState = viewModel.getViewState();
            binding.btnEmptyAction.setVisibility(
                    viewModel.isSearching() || !category.isEmpty()
                            || viewState != Note.STATE_ACTIVE ? View.GONE : View.VISIBLE);
            if (viewState != Note.STATE_ACTIVE && !viewModel.isSearching() && category.isEmpty()) {
                boolean archive = viewState == Note.STATE_ARCHIVED;
                binding.tvEmptyTitle.setText(archive
                        ? R.string.empty_archive_title : R.string.empty_trash_title);
                binding.tvEmptySubtitle.setText(archive
                        ? R.string.empty_archive_subtitle : R.string.empty_trash_subtitle);
            } else if (viewModel.isSearching()) {
                binding.tvEmptyTitle.setText(R.string.empty_search_title);
                binding.tvEmptySubtitle.setText(R.string.empty_search_subtitle);
            } else if (!category.isEmpty()) {
                binding.tvEmptyTitle.setText(getString(R.string.empty_state_title));
                binding.tvEmptySubtitle.setText(getString(R.string.empty_category_subtitle, category));
            } else {
                binding.tvEmptyTitle.setText(R.string.empty_state_title);
                binding.tvEmptySubtitle.setText(R.string.empty_state_subtitle);
            }
        } else {
            binding.rvNotes.setVisibility(View.VISIBLE);
            binding.layoutEmptyState.setVisibility(View.GONE);
            binding.tvNotesCount.setVisibility(View.VISIBLE);

            // Set result count header text
            if (viewModel.isSearching()) {
                binding.tvNotesCount.setText(getResources()
                        .getQuantityString(R.plurals.search_results, count, count));
            } else if (!category.isEmpty()) {
                binding.tvNotesCount.setText(getResources()
                        .getQuantityString(R.plurals.notes_in_category, count, count, category));
            } else {
                binding.tvNotesCount.setText(getResources()
                        .getQuantityString(R.plurals.notes_count, count, count));
            }
        }
    }

    // ==============================
    // Search Bar
    // ==============================

    private void setupSearchBar() {
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String query = s.toString().trim();
                binding.btnClearSearch.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);

                if (pendingSearch != null) searchHandler.removeCallbacks(pendingSearch);
                pendingSearch = () -> viewModel.setSearchQuery(query);
                // Clearing applies immediately; typing is debounced.
                searchHandler.postDelayed(pendingSearch, query.isEmpty() ? 0 : SEARCH_DEBOUNCE_MS);
            }
        });

        binding.etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager)
                                getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                v.clearFocus();
                return true;
            }
            return false;
        });

        // Clear search button
        binding.btnClearSearch.setOnClickListener(v -> {
            if (binding.etSearch.getText() != null) {
                binding.etSearch.getText().clear();
            }
        });
    }

    // ==============================
    // Category Filter Chips
    // ==============================

    private void setupCategoryChips() {
        binding.chipGroupCategories.removeAllViews();

        // Add "All" chip
        Chip allChip = createFilterChip(getString(R.string.category_all), "");
        binding.chipGroupCategories.addView(allChip);

        // Add chip for every aesthetic category
        for (String category : Constants.CATEGORIES) {
            Chip chip = createFilterChip(category, category);
            binding.chipGroupCategories.addView(chip);
        }
    }

    private Chip createFilterChip(String label, String categoryValue) {
        Chip chip = new Chip(this, null, com.google.android.material.R.style.Widget_Material3_Chip_Filter);
        chip.setText(label);
        chip.setChipStrokeWidth(1.0f);
        chip.setChipStrokeColorResource(R.color.colorOutline);
        chip.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);

        boolean isSelected = categoryValue.equals(viewModel.getCategoryFilter());
        updateSingleChipStyle(chip, isSelected);

        chip.setOnClickListener(v -> {
            viewModel.setCategoryFilter(categoryValue);

            for (int i = 0; i < binding.chipGroupCategories.getChildCount(); i++) {
                View child = binding.chipGroupCategories.getChildAt(i);
                if (child instanceof Chip) {
                    Chip c = (Chip) child;
                    updateSingleChipStyle(c, c == chip);
                }
            }
        });

        return chip;
    }

    private void updateSingleChipStyle(Chip chip, boolean isSelected) {
        if (isSelected) {
            chip.setChipBackgroundColorResource(R.color.chip_selected_bg);
            chip.setTextColor(ContextCompat.getColor(this, R.color.chip_selected_text));
        } else {
            chip.setChipBackgroundColorResource(R.color.chip_unselected_bg);
            chip.setTextColor(ContextCompat.getColor(this, R.color.chip_unselected_text));
        }
    }

    // ==============================
    // Floating Action Button
    // ==============================

    private void setupFab() {
        binding.fabAddNote.setOnClickListener(v -> openAddEditNote(Constants.NO_NOTE_ID));
        binding.btnEmptyAction.setOnClickListener(v -> openAddEditNote(Constants.NO_NOTE_ID));
    }

    private void openAddEditNote(int noteId) {
        Intent intent = new Intent(this, AddEditNoteActivity.class);
        intent.putExtra(Constants.EXTRA_NOTE_ID, noteId);
        startActivity(intent);
    }

    // ==============================
    // Toolbar Menu
    // ==============================

    private void setupToolbarMenu() {
        // Update menu icon on startup
        Menu menu = binding.toolbar.getMenu();
        MenuItem viewItem = menu.findItem(R.id.action_view_mode);
        if (viewItem != null) {
            viewItem.setIcon(isGridView ? R.drawable.ic_view_list : R.drawable.ic_view_grid);
        }

        binding.toolbar.setOnMenuItemClickListener(menuItem -> {
            int id = menuItem.getItemId();
            if (id == R.id.action_view_mode) {
                toggleViewMode(menuItem);
                return true;
            } else if (id == R.id.action_sort) {
                showSortDialog();
                return true;
            } else if (id == R.id.action_theme) {
                showThemeDialog();
                return true;
            } else if (id == R.id.action_notes) {
                switchView(Note.STATE_ACTIVE);
                return true;
            } else if (id == R.id.action_archive_view) {
                switchView(Note.STATE_ARCHIVED);
                return true;
            } else if (id == R.id.action_trash_view) {
                switchView(Note.STATE_TRASHED);
                return true;
            } else if (id == R.id.action_empty_trash) {
                showEmptyTrashDialog();
                return true;
            }
            return false;
        });

        binding.toolbar.setNavigationOnClickListener(v -> switchView(Note.STATE_ACTIVE));

        backToNotes = new androidx.activity.OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                switchView(Note.STATE_ACTIVE);
            }
        };
        getOnBackPressedDispatcher().addCallback(this, backToNotes);

        applyViewState();
    }

    // ==============================
    // Notes / Archive / Trash views
    // ==============================

    private void switchView(int state) {
        viewModel.setViewState(state);
        applyViewState();
    }

    /** Updates title, back arrow, menu items and FAB for the current view. */
    private void applyViewState() {
        int state = viewModel.getViewState();

        int title = state == Note.STATE_ARCHIVED ? R.string.title_archive
                : state == Note.STATE_TRASHED ? R.string.title_trash : R.string.app_name;
        binding.toolbar.setTitle(title);
        if (state == Note.STATE_ACTIVE) {
            binding.toolbar.setNavigationIcon(null);
        } else {
            binding.toolbar.setNavigationIcon(R.drawable.ic_back);
        }
        binding.toolbar.setNavigationContentDescription(R.string.back_to_notes);
        backToNotes.setEnabled(state != Note.STATE_ACTIVE);

        Menu menu = binding.toolbar.getMenu();
        setMenuVisible(menu, R.id.action_notes, state != Note.STATE_ACTIVE);
        setMenuVisible(menu, R.id.action_archive_view, state != Note.STATE_ARCHIVED);
        setMenuVisible(menu, R.id.action_trash_view, state != Note.STATE_TRASHED);
        setMenuVisible(menu, R.id.action_empty_trash, state == Note.STATE_TRASHED);

        if (state == Note.STATE_ACTIVE) {
            binding.fabAddNote.show();
        } else {
            binding.fabAddNote.hide();
        }
    }

    private void setMenuVisible(Menu menu, int id, boolean visible) {
        MenuItem item = menu.findItem(id);
        if (item != null) item.setVisible(visible);
    }

    /** Context actions for a note, depending on which list it is in. */
    private void showNoteActions(Note note) {
        final java.util.ArrayList<String> labels = new java.util.ArrayList<>();
        final java.util.ArrayList<Runnable> actions = new java.util.ArrayList<>();

        switch (note.getState()) {
            case Note.STATE_TRASHED:
                labels.add(getString(R.string.opt_restore));
                actions.add(() -> {
                    viewModel.setNoteState(note, Note.STATE_ACTIVE);
                    showSnack(R.string.note_restored, null);
                });
                labels.add(getString(R.string.opt_delete_forever));
                actions.add(() -> confirmDeleteForever(note));
                break;
            case Note.STATE_ARCHIVED:
                labels.add(getString(R.string.opt_unarchive));
                actions.add(() -> {
                    viewModel.setNoteState(note, Note.STATE_ACTIVE);
                    showSnack(R.string.note_unarchived, () ->
                            viewModel.setNoteState(note, Note.STATE_ARCHIVED));
                });
                labels.add(getString(R.string.opt_trash));
                actions.add(() -> trashWithUndo(note, Note.STATE_ARCHIVED));
                break;
            default:
                labels.add(getString(note.isPinned() ? R.string.opt_unpin : R.string.opt_pin));
                actions.add(() -> {
                    viewModel.togglePin(note);
                    showSnack(note.isPinned() ? R.string.note_unpinned : R.string.note_pinned, null);
                });
                labels.add(getString(R.string.opt_archive));
                actions.add(() -> {
                    viewModel.setNoteState(note, Note.STATE_ARCHIVED);
                    showSnack(R.string.note_archived, () ->
                            viewModel.setNoteState(note, Note.STATE_ACTIVE));
                });
                labels.add(getString(R.string.opt_trash));
                actions.add(() -> trashWithUndo(note, Note.STATE_ACTIVE));
                break;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(note.getTitle())
                .setItems(labels.toArray(new String[0]), (dialog, which) -> actions.get(which).run())
                .show();
    }

    private void trashWithUndo(Note note, int previousState) {
        viewModel.setNoteState(note, Note.STATE_TRASHED);
        showSnack(R.string.note_trashed, () -> viewModel.setNoteState(note, previousState));
    }

    private void confirmDeleteForever(Note note) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_title)
                .setMessage(R.string.delete_message)
                .setPositiveButton(R.string.delete_confirm, (d, w) -> {
                    viewModel.deleteForever(note);
                    showSnack(R.string.note_deleted_forever, null);
                })
                .setNegativeButton(R.string.delete_cancel, null)
                .show();
    }

    private void showEmptyTrashDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.empty_trash_confirm_title)
                .setMessage(R.string.empty_trash_confirm_message)
                .setPositiveButton(R.string.delete_confirm, (d, w) -> viewModel.emptyTrash())
                .setNegativeButton(R.string.delete_cancel, null)
                .show();
    }

    /** Snackbar in the app's style, with an optional Undo action. */
    private void showSnack(int message, Runnable undo) {
        Snackbar bar = Snackbar.make(binding.getRoot(), message,
                        undo != null ? Snackbar.LENGTH_LONG : Snackbar.LENGTH_SHORT)
                .setBackgroundTint(ContextCompat.getColor(this, R.color.snackbar_bg))
                .setTextColor(ContextCompat.getColor(this, R.color.snackbar_text));
        if (undo != null) {
            bar.setAction(R.string.undo, v -> undo.run())
                    .setActionTextColor(ContextCompat.getColor(this, R.color.snackbar_action));
        }
        bar.show();
    }

    private void toggleViewMode(MenuItem menuItem) {
        isGridView = !isGridView;

        // Save preference
        getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(PREF_VIEW_MODE, isGridView ? VIEW_MODE_GRID : VIEW_MODE_LIST)
                .apply();

        // Update menu icon
        menuItem.setIcon(isGridView ? R.drawable.ic_view_list : R.drawable.ic_view_grid);

        // Apply new layout manager
        applyLayoutManager();
    }

    private void showSortDialog() {
        String[] options = {
                getString(R.string.sort_newest),
                getString(R.string.sort_oldest),
                getString(R.string.sort_title_az),
                getString(R.string.sort_title_za)
        };

        Integer sortOrderValue = viewModel.getCurrentSortOrder().getValue();
        int currentSort = sortOrderValue != null ? sortOrderValue : Constants.SORT_NEWEST;

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.sort_title)
                .setSingleChoiceItems(options, currentSort, (dialog, which) -> {
                    viewModel.setSortOrder(which);
                    dialog.dismiss();
                })
                .show();
    }

    private void showThemeDialog() {
        String[] options = {
                getString(R.string.theme_light),
                getString(R.string.theme_dark),
                getString(R.string.theme_system)
        };

        int currentTheme = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
                .getInt(Constants.PREF_THEME_MODE, Constants.THEME_SYSTEM);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.theme_title)
                .setSingleChoiceItems(options, currentTheme, (dialog, which) -> {
                    getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
                            .edit()
                            .putInt(Constants.PREF_THEME_MODE, which)
                            .apply();

                    switch (which) {
                        case Constants.THEME_LIGHT:
                            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                            break;
                        case Constants.THEME_DARK:
                            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                            break;
                        default:
                            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                            break;
                    }
                    dialog.dismiss();
                })
                .show();
    }

    // ==============================
    // Swipe to Delete
    // ==============================

    private void setupSwipeToDelete() {
        final android.graphics.drawable.Drawable deleteIcon =
                ContextCompat.getDrawable(this, R.drawable.ic_delete);
        final Paint bgPaint = new Paint();
        bgPaint.setColor(ContextCompat.getColor(this, R.color.notion_red_bg));
        final float cornerRadius = getResources().getDimension(R.dimen.card_corner_radius);

        ItemTouchHelper.SimpleCallback swipeCallback =
                new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                if (position < 0 || position >= noteAdapter.getCurrentList().size()) return;

                Note note = noteAdapter.getCurrentList().get(position);

                // Move to trash immediately; Undo puts it back where it was.
                trashWithUndo(note, viewModel.getViewState());
            }

            @Override
            public int getSwipeDirs(@NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder) {
                // Notes already in the trash are handled via the actions dialog
                return viewModel.getViewState() == Note.STATE_TRASHED
                        ? 0 : super.getSwipeDirs(recyclerView, viewHolder);
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder,
                                    float dX, float dY, int actionState,
                                    boolean isCurrentlyActive) {
                View itemView = viewHolder.itemView;

                if (deleteIcon != null && dX != 0) {
                    int iconMargin = (itemView.getHeight() - deleteIcon.getIntrinsicHeight()) / 2;
                    boolean toLeft = dX < 0;

                    RectF rect = toLeft
                            ? new RectF(itemView.getRight() + dX, itemView.getTop(),
                                    itemView.getRight(), itemView.getBottom())
                            : new RectF(itemView.getLeft(), itemView.getTop(),
                                    itemView.getLeft() + dX, itemView.getBottom());
                    c.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint);

                    int iconTop = itemView.getTop() + iconMargin;
                    int iconBottom = iconTop + deleteIcon.getIntrinsicHeight();
                    int iconLeft = toLeft
                            ? itemView.getRight() - iconMargin - deleteIcon.getIntrinsicWidth()
                            : itemView.getLeft() + iconMargin;
                    deleteIcon.setBounds(iconLeft, iconTop,
                            iconLeft + deleteIcon.getIntrinsicWidth(), iconBottom);
                    deleteIcon.draw(c);
                }

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }
        };

        new ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.rvNotes);
    }
}
