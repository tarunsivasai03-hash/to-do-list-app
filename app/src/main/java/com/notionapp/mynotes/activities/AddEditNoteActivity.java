package com.notionapp.mynotes.activities;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.notionapp.mynotes.R;
import com.notionapp.mynotes.databinding.ActivityAddEditNoteBinding;
import com.notionapp.mynotes.models.Note;
import com.notionapp.mynotes.utils.Constants;
import com.notionapp.mynotes.utils.NotionApp;
import com.notionapp.mynotes.viewmodel.NoteViewModel;
import com.notionapp.mynotes.viewmodel.NoteViewModelFactory;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * AddEditNoteActivity — handles both creating new notes and editing existing ones.
 * Features live word/char count stats counter and quick formatting tools.
 */
public class AddEditNoteActivity extends AppCompatActivity {

    private ActivityAddEditNoteBinding binding;
    private NoteViewModel viewModel;

    // Track whether we're editing an existing note
    private int currentNoteId = Constants.NO_NOTE_ID;
    private Note existingNote = null;

    // Track if form has been modified
    private boolean isFormDirty = false;

    // Whether the loaded note's text has been put into the fields (kept across rotation)
    private static final String STATE_POPULATED = "state_populated";
    private static final String STATE_DIRTY = "state_dirty";
    private boolean fieldsPopulated = false;

    private static final String STATE_PINNED = "state_pinned";
    private boolean pinned = false;

    private static final String STATE_REMINDER = "state_reminder";
    private long reminderTime = 0;

    private final androidx.activity.result.ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(
                    new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
                    granted -> showReminderDatePicker());

    // ==============================
    // Lifecycle
    // ==============================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityAddEditNoteBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupViewModel();
        setupCategoryDropdown();
        setupToolbar();
        setupFormChangeTracking();
        setupFormattingToolbar();

        if (savedInstanceState != null) {
            fieldsPopulated = savedInstanceState.getBoolean(STATE_POPULATED, false);
            isFormDirty = savedInstanceState.getBoolean(STATE_DIRTY, false);
            pinned = savedInstanceState.getBoolean(STATE_PINNED, false);
            reminderTime = savedInstanceState.getLong(STATE_REMINDER, 0);
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackPress();
            }
        });

        // Check if we're editing an existing note
        currentNoteId = getIntent().getIntExtra(Constants.EXTRA_NOTE_ID, Constants.NO_NOTE_ID);
        if (currentNoteId != Constants.NO_NOTE_ID) {
            loadExistingNote();
        } else {
            updateEditorStats();
            if (savedInstanceState == null) {
                binding.etTitle.requestFocus();
                getWindow().setSoftInputMode(
                        android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                                | android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_POPULATED, fieldsPopulated);
        outState.putBoolean(STATE_DIRTY, isFormDirty);
        outState.putBoolean(STATE_PINNED, pinned);
        outState.putLong(STATE_REMINDER, reminderTime);
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
    // Category Dropdown
    // ==============================

    private void setupCategoryDropdown() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                Constants.CATEGORIES
        );
        binding.actvCategory.setAdapter(adapter);

        // Set default category for new notes
        if (currentNoteId == Constants.NO_NOTE_ID) {
            String defaultCat = Constants.CATEGORIES.get(0);
            binding.actvCategory.setText(defaultCat, false);
            updateCategoryPsychology(defaultCat);
        }

        binding.actvCategory.setOnItemClickListener((parent, view, position, id) -> {
            String selected = adapter.getItem(position);
            if (selected != null) {
                isFormDirty = true;
                updateCategoryPsychology(selected);
            }
        });
    }

    private void updateCategoryPsychology(String category) {
        // Notion style: neutral page; the category is shown by its tag color only.
        if (category != null) {
            binding.actvCategory.setTextColor(
                    com.notionapp.mynotes.utils.CategoryUtils.getTagText(this, category));
        }
    }

    // ==============================
    // Toolbar
    // ==============================

    private void setupToolbar() {
        String title = (currentNoteId == Constants.NO_NOTE_ID)
                ? getString(R.string.add_note_title)
                : getString(R.string.edit_note_title);
        binding.toolbarAddEdit.setTitle(title);

        binding.toolbarAddEdit.setNavigationOnClickListener(v -> handleBackPress());

        binding.toolbarAddEdit.setOnMenuItemClickListener(menuItem -> {
            int id = menuItem.getItemId();
            if (id == R.id.action_save) {
                saveNote();
                return true;
            } else if (id == R.id.action_reminder) {
                pickReminder();
                return true;
            } else if (id == R.id.action_pin) {
                pinned = !pinned;
                isFormDirty = true;
                updatePinMenuTitle();
                return true;
            } else if (id == R.id.action_share) {
                shareNote();
                return true;
            }
            return false;
        });
        updatePinMenuTitle();

        binding.chipReminder.setOnClickListener(v -> pickReminder());
        binding.chipReminder.setOnCloseIconClickListener(v -> {
            reminderTime = 0;
            isFormDirty = true;
            updateReminderChip();
        });
        updateReminderChip();
    }

    // ==============================
    // Reminder
    // ==============================

    private void updateReminderChip() {
        if (reminderTime > 0) {
            binding.chipReminder.setText(getString(R.string.reminder_label,
                    android.text.format.DateUtils.formatDateTime(this, reminderTime,
                            android.text.format.DateUtils.FORMAT_SHOW_DATE
                                    | android.text.format.DateUtils.FORMAT_SHOW_TIME
                                    | android.text.format.DateUtils.FORMAT_ABBREV_MONTH)));
            binding.chipReminder.setVisibility(android.view.View.VISIBLE);
        } else {
            binding.chipReminder.setVisibility(android.view.View.GONE);
        }
    }

    private void pickReminder() {
        // Android 13+ needs a runtime permission to show the notification
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
                && androidx.core.content.ContextCompat.checkSelfPermission(
                        this, android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            // The date picker opens from the permission callback so the two don't overlap
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS);
            return;
        }
        showReminderDatePicker();
    }

    private void showReminderDatePicker() {
        com.google.android.material.datepicker.CalendarConstraints constraints =
                new com.google.android.material.datepicker.CalendarConstraints.Builder()
                        .setValidator(com.google.android.material.datepicker.DateValidatorPointForward.now())
                        .build();
        com.google.android.material.datepicker.MaterialDatePicker<Long> datePicker =
                com.google.android.material.datepicker.MaterialDatePicker.Builder.datePicker()
                        .setTitleText(R.string.reminder_pick_date)
                        .setCalendarConstraints(constraints)
                        .build();
        datePicker.addOnPositiveButtonClickListener(this::pickReminderTime);
        datePicker.show(getSupportFragmentManager(), "reminder_date");
    }

    private void pickReminderTime(long utcDateMillis) {
        java.util.Calendar now = java.util.Calendar.getInstance();
        int format = android.text.format.DateFormat.is24HourFormat(this)
                ? com.google.android.material.timepicker.TimeFormat.CLOCK_24H
                : com.google.android.material.timepicker.TimeFormat.CLOCK_12H;
        com.google.android.material.timepicker.MaterialTimePicker timePicker =
                new com.google.android.material.timepicker.MaterialTimePicker.Builder()
                        .setTimeFormat(format)
                        .setHour(now.get(java.util.Calendar.HOUR_OF_DAY))
                        .setMinute(now.get(java.util.Calendar.MINUTE))
                        .setTitleText(R.string.reminder_pick_time)
                        .build();
        timePicker.addOnPositiveButtonClickListener(v -> {
            // The date picker returns midnight UTC; rebuild it in the local time zone
            java.util.Calendar utc = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
            utc.setTimeInMillis(utcDateMillis);
            java.util.Calendar when = java.util.Calendar.getInstance();
            when.clear();
            when.set(utc.get(java.util.Calendar.YEAR), utc.get(java.util.Calendar.MONTH),
                    utc.get(java.util.Calendar.DAY_OF_MONTH),
                    timePicker.getHour(), timePicker.getMinute());

            if (when.getTimeInMillis() <= System.currentTimeMillis()) {
                Toast.makeText(this, R.string.reminder_in_future, Toast.LENGTH_SHORT).show();
                return;
            }
            reminderTime = when.getTimeInMillis();
            isFormDirty = true;
            updateReminderChip();

            if (!androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                Toast.makeText(this, R.string.reminder_notifications_off, Toast.LENGTH_LONG).show();
            }
        });
        timePicker.show(getSupportFragmentManager(), "reminder_time");
    }

    private void updatePinMenuTitle() {
        android.view.MenuItem item = binding.toolbarAddEdit.getMenu().findItem(R.id.action_pin);
        if (item != null) item.setTitle(pinned ? R.string.action_unpin : R.string.action_pin);
    }

    private void shareNote() {
        String title = binding.etTitle.getText() != null ? binding.etTitle.getText().toString() : "";
        String content = binding.etContent.getText() != null ? binding.etContent.getText().toString() : "";
        android.content.Intent send = new android.content.Intent(android.content.Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(android.content.Intent.EXTRA_SUBJECT, title);
        send.putExtra(android.content.Intent.EXTRA_TEXT, title.isEmpty() ? content : title + "\n\n" + content);
        startActivity(android.content.Intent.createChooser(send, getString(R.string.share_chooser)));
    }

    // ==============================
    // Load Existing Note (Edit Mode)
    // ==============================

    private void loadExistingNote() {
        // Save stays disabled until the note is loaded, so an edit can never be dropped.
        setSaveEnabled(false);

        viewModel.getLoadedNote().observe(this, note -> {
            if (note == null) {
                Toast.makeText(this, R.string.note_not_found, Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            existingNote = note;
            if (!fieldsPopulated) {
                binding.etTitle.setText(note.getTitle());
                binding.etContent.setText(note.getContent());
                binding.actvCategory.setText(note.getCategory(), false);
                pinned = note.isPinned();
                reminderTime = note.getReminderTime();
                fieldsPopulated = true;
                isFormDirty = false;
            }
            updateCategoryPsychology(binding.actvCategory.getText().toString());
            updateEditorStats();
            updatePinMenuTitle();
            updateReminderChip();
            setSaveEnabled(true);
        });
        viewModel.loadNote(currentNoteId);
    }

    private void setSaveEnabled(boolean enabled) {
        android.view.MenuItem item = binding.toolbarAddEdit.getMenu().findItem(R.id.action_save);
        if (item != null) item.setEnabled(enabled);
    }

    // ==============================
    // Save Note
    // ==============================

    private void saveNote() {
        String title    = binding.etTitle.getText() != null
                ? binding.etTitle.getText().toString().trim() : "";
        String content  = binding.etContent.getText() != null
                ? binding.etContent.getText().toString().trim() : "";
        String category = binding.actvCategory.getText() != null
                ? binding.actvCategory.getText().toString().trim() : "";

        if (title.isEmpty()) {
            binding.tilTitle.setError(getString(R.string.error_title_empty));
            binding.etTitle.requestFocus();
            return;
        }

        binding.tilTitle.setError(null);

        long currentTime = System.currentTimeMillis();

        if (currentNoteId == Constants.NO_NOTE_ID) {
            Note newNote = new Note(title, content, category, currentTime, currentTime);
            newNote.setPinned(pinned);
            newNote.setReminderTime(reminderTime);
            viewModel.insertNote(newNote);
            Toast.makeText(this, R.string.note_saved, Toast.LENGTH_SHORT).show();
        } else {
            if (existingNote != null) {
                existingNote.setTitle(title);
                existingNote.setContent(content);
                existingNote.setCategory(category);
                existingNote.setPinned(pinned);
                existingNote.setReminderTime(reminderTime);
                existingNote.setUpdatedDate(currentTime);
                viewModel.updateNote(existingNote);
            }
            Toast.makeText(this, R.string.note_updated, Toast.LENGTH_SHORT).show();
        }

        isFormDirty = false;
        finish();
    }

    // ==============================
    // Form Change Tracking & Live Stats
    // ==============================

    private void setupFormChangeTracking() {
        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                isFormDirty = true;
                updateEditorStats();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        };

        binding.etTitle.addTextChangedListener(watcher);
        binding.etContent.addTextChangedListener(watcher);
        binding.etContent.addTextChangedListener(new ListContinuationWatcher());
        setupCheckboxTapping();
    }

    /** Tapping the "[ ]" / "[x]" marker at the start of a line toggles it. */
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private void setupCheckboxTapping() {
        final EditText et = binding.etContent;
        final float slop = android.view.ViewConfiguration.get(this).getScaledTouchSlop();
        final float[] down = new float[2];

        et.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                down[0] = event.getX();
                down[1] = event.getY();
                return false;
            }
            if (event.getAction() != android.view.MotionEvent.ACTION_UP) return false;
            if (Math.abs(event.getX() - down[0]) > slop || Math.abs(event.getY() - down[1]) > slop) {
                return false;
            }

            Editable text = et.getText();
            if (text == null || et.getLayout() == null) return false;
            int offset = et.getOffsetForPosition(event.getX(), event.getY());
            int lineStart = offset;
            while (lineStart > 0 && text.charAt(lineStart - 1) != '\n') lineStart--;

            // Only the marker area (first 4 chars) toggles; elsewhere it's a normal tap
            if (offset - lineStart > 3 || lineStart + 3 > text.length()) return false;
            String head = text.subSequence(lineStart, Math.min(lineStart + 4, text.length())).toString();
            if (head.startsWith(com.notionapp.mynotes.utils.ChecklistUtils.OPEN)) {
                text.replace(lineStart + 1, lineStart + 2, "x");
                return true;
            } else if (head.startsWith(com.notionapp.mynotes.utils.ChecklistUtils.DONE)) {
                text.replace(lineStart + 1, lineStart + 2, " ");
                return true;
            }
            return false;
        });
    }

    private void updateEditorStats() {
        String content = binding.etContent.getText() != null
                ? binding.etContent.getText().toString() : "";

        int charCount = content.length();
        String trimmed = content.trim();
        int wordCount = trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;

        // Reading speed: average 200 words per minute
        int readTimeMin = (int) Math.ceil(wordCount / 200.0);
        String readTimeStr = readTimeMin <= 1
                ? getString(R.string.editor_stats_read_less_min)
                : getString(R.string.editor_stats_read_min, readTimeMin);

        binding.tvEditorStats.setText(
                getString(R.string.editor_stats_format, wordCount, charCount, readTimeStr)
        );
    }

    // ==============================
    // Quick Formatting Bar Actions
    // ==============================

    private void setupFormattingToolbar() {
        binding.btnInsertTimestamp.setOnClickListener(v -> {
            String timestamp = new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                    .format(new Date());
            insertTextAtCursor(binding.etContent, "[" + timestamp + "] ");
        });

        binding.btnInsertBullet.setOnClickListener(v -> {
            String current = binding.etContent.getText() != null
                    ? binding.etContent.getText().toString() : "";
            String insert = (current.isEmpty() || current.endsWith("\n")) ? "• " : "\n• ";
            insertTextAtCursor(binding.etContent, insert);
        });

        binding.btnInsertCheckbox.setOnClickListener(v -> {
            String current = binding.etContent.getText() != null
                    ? binding.etContent.getText().toString() : "";
            String insert = (current.isEmpty() || current.endsWith("\n")) ? "[ ] " : "\n[ ] ";
            insertTextAtCursor(binding.etContent, insert);
        });
    }

    private void insertTextAtCursor(EditText editText, String textToInsert) {
        int start = Math.max(editText.getSelectionStart(), 0);
        int end = Math.max(editText.getSelectionEnd(), 0);
        Editable editable = editText.getText();
        if (editable != null) {
            editable.replace(Math.min(start, end), Math.max(start, end), textToInsert, 0, textToInsert.length());
        }
    }

    // ==============================
    // Back Press Handling
    // ==============================

    private void handleBackPress() {
        if (isFormDirty) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.unsaved_title)
                    .setMessage(R.string.unsaved_message)
                    .setPositiveButton(R.string.discard_save, (dialog, which) -> saveNote())
                    .setNegativeButton(R.string.discard_discard, (dialog, which) -> finish())
                    .setNeutralButton(R.string.discard_keep, null)
                    .show();
        } else {
            finish();
        }
    }

    /**
     * Notion-style list behaviour: pressing Enter after "• item" or "[ ] item" starts a new
     * item; pressing Enter on an empty item ends the list.
     */
    private static class ListContinuationWatcher implements TextWatcher {
        private static final String[] PREFIXES = {"• ", "[ ] ", "[x] "};
        private boolean editing = false;

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}

        @Override
        public void afterTextChanged(Editable e) {
            if (editing) return;

            // Only react to a single typed newline
            int cursor = android.text.Selection.getSelectionStart(e);
            if (cursor < 1 || cursor > e.length() || e.charAt(cursor - 1) != '\n') return;

            int lineEnd = cursor - 1;
            int lineStart = lineEnd;
            while (lineStart > 0 && e.charAt(lineStart - 1) != '\n') lineStart--;
            String line = e.subSequence(lineStart, lineEnd).toString();

            for (String prefix : PREFIXES) {
                if (!line.startsWith(prefix)) continue;
                editing = true;
                try {
                    if (line.length() == prefix.length()) {
                        // Empty item: remove the prefix and the newline to exit the list
                        e.delete(lineStart, cursor);
                    } else {
                        // Continue the list; a checked item continues as unchecked
                        e.insert(cursor, prefix.equals("[x] ") ? "[ ] " : prefix);
                    }
                } finally {
                    editing = false;
                }
                return;
            }
        }
    }
}
