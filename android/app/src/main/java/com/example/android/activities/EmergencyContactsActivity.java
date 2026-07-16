package com.example.android.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.example.android.R;
import com.example.android.adapters.EmergencyContactsAdapter;
import com.example.android.models.EmergencyContact;
import com.example.android.utils.EmergencyHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * Professional-grade Emergency Contacts screen.
 * Optimized for real-time UI synchronization and zero-lag performance.
 */
public class EmergencyContactsActivity extends AppCompatActivity {

    private static final int PERMISSION_READ_CONTACTS = 100;

    private RecyclerView recyclerView;
    private EmergencyContactsAdapter adapter;
    private View emptyView;
    private ExtendedFloatingActionButton fabAdd;
    private List<EmergencyContact> contacts = new ArrayList<>();
    private final Stack<AlertDialog> activeDialogs = new Stack<>();

    // ActivityResultLauncher replaces deprecated startActivityForResult
    private final ActivityResultLauncher<Intent> pickContactLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    handlePickedContact(result.getData());
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emergency_contacts);

        setupToolbar();
        initializeViews();
        loadContacts();
        setupListeners();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Emergency Contacts");
        }
        toolbar.setNavigationOnClickListener(v -> {
            if (!isFinishing()) {
                finish();
            }
        });
    }

    private void initializeViews() {
        recyclerView = findViewById(R.id.rv_contacts);
        emptyView = findViewById(R.id.empty_view);
        fabAdd = findViewById(R.id.fab_add_contact);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
    }

    private void loadContacts() {
        // Load from safe storage
        contacts = EmergencyHelper.getEmergencyContacts(this);
        
        adapter = new EmergencyContactsAdapter(contacts, new EmergencyContactsAdapter.ContactClickListener() {
            @Override public void onEditClick(int pos) { showEditDialog(pos); }
            @Override public void onDeleteClick(int pos) { showDeleteDialog(pos); }
            @Override public void onCallClick(int pos) { makeCall(contacts.get(pos).getPhoneNumber()); }
        });
        
        recyclerView.setAdapter(adapter);
        updateEmptyView();
    }

    private void setupListeners() {
        fabAdd.setOnClickListener(v -> {
            if (!isFinishing()) {
                showAddOptions();
            }
        });
    }

    private void showAddOptions() {
        if (isFinishing()) return;
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Add Contact")
                .setItems(new String[]{"Enter Manually", "Choose from Contacts"}, (d, w) -> {
                    if (w == 0) showAddDialog(); else pickFromPhoneBook();
                }).create();
        activeDialogs.push(dialog);
        dialog.show();
    }

    private void showAddDialog() {
        if (isFinishing()) return;
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_contact, null);
        final EditText nameIn = view.findViewById(R.id.input_name);
        final EditText phoneIn = view.findViewById(R.id.input_phone);
        final EditText relIn = view.findViewById(R.id.input_relationship);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("New Emergency Contact")
                .setView(view)
                .setPositiveButton("Save", (d, w) -> {
                    if (isFinishing()) return;
                    String name = nameIn.getText().toString().trim();
                    String phone = phoneIn.getText().toString().trim();
                    String rel = relIn.getText().toString().trim();
                    
                    if (isValid(name, phone)) {
                        EmergencyContact contact = new EmergencyContact(name, phone, rel);
                        if (EmergencyHelper.addEmergencyContact(this, contact)) {
                            // Professional UI Sync
                            contacts.clear();
                            contacts.addAll(EmergencyHelper.getEmergencyContacts(this));
                            adapter.notifyDataSetChanged();
                            updateEmptyView();
                            Toast.makeText(this, "Contact Saved", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .create();
        activeDialogs.push(dialog);
        dialog.show();
    }

    private void showEditDialog(int pos) {
        if (isFinishing()) return;
        EmergencyContact c = contacts.get(pos);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_contact, null);
        final EditText nameIn = view.findViewById(R.id.input_name);
        final EditText phoneIn = view.findViewById(R.id.input_phone);
        final EditText relIn = view.findViewById(R.id.input_relationship);

        nameIn.setText(c.getName());
        phoneIn.setText(c.getPhoneNumber());
        relIn.setText(c.getRelationship());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Update Contact")
                .setView(view)
                .setPositiveButton("Update", (d, w) -> {
                    if (isFinishing()) return;
                    c.setName(nameIn.getText().toString().trim());
                    c.setPhoneNumber(phoneIn.getText().toString().trim());
                    c.setRelationship(relIn.getText().toString().trim());
                    
                    if (EmergencyHelper.saveEmergencyContacts(this, contacts)) {
                        adapter.notifyItemChanged(pos);
                        Toast.makeText(this, "Updated", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .create();
        activeDialogs.push(dialog);
        dialog.show();
    }

    private void showDeleteDialog(int pos) {
        if (isFinishing()) return;
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Remove Contact")
                .setMessage("Are you sure you want to remove " + contacts.get(pos).getName() + "?")
                .setPositiveButton("Remove", (d, w) -> {
                    if (isFinishing()) return;
                    contacts.remove(pos);
                    if (EmergencyHelper.saveEmergencyContacts(this, contacts)) {
                        adapter.notifyItemRemoved(pos);
                        updateEmptyView();
                    }
                })
                .setNegativeButton("Cancel", null)
                .create();
        activeDialogs.push(dialog);
        dialog.show();
    }

    private void pickFromPhoneBook() {
        if (isFinishing()) return;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_CONTACTS}, PERMISSION_READ_CONTACTS);
        } else {
            Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
            pickContactLauncher.launch(intent);
        }
    }

    private void handlePickedContact(Intent data) {
        if (isFinishing()) return;
        Uri uri = data.getData();
        if (uri == null) return;
        
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            int nIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
            int pIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
            
            if (nIdx != -1 && pIdx != -1) {
                addAutoContact(cursor.getString(nIdx), cursor.getString(pIdx));
            }
            cursor.close();
        }
    }

    private void addAutoContact(String name, String phone) {
        EmergencyContact contact = new EmergencyContact(name, phone, "Trusted Contact");
        if (EmergencyHelper.addEmergencyContact(this, contact)) {
            contacts.clear();
            contacts.addAll(EmergencyHelper.getEmergencyContacts(this));
            adapter.notifyDataSetChanged();
            updateEmptyView();
            Toast.makeText(this, "Contact Added", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isValid(String n, String p) {
        if (n.isEmpty() || p.length() < 8) {
            Toast.makeText(this, "Please enter a valid name and phone", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    @Override
    protected void onDestroy() {
        while (!activeDialogs.isEmpty()) {
            AlertDialog dialog = activeDialogs.pop();
            if (dialog.isShowing()) {
                dialog.dismiss();
            }
        }
        super.onDestroy();
    }

    private void makeCall(String phone) {
        if (isFinishing()) return;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            startActivity(new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + phone)));
        }
    }

    private void updateEmptyView() {
        boolean isEmpty = contacts.isEmpty();
        if (emptyView != null) emptyView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        if (recyclerView != null) recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }
}
