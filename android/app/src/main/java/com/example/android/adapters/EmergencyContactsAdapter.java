package com.example.android.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.android.R;
import com.example.android.models.EmergencyContact;
import com.google.android.material.button.MaterialButton;

import java.util.List;

/**
 * Optimized Adapter for Emergency Contacts using Material 3 Components
 */
public class EmergencyContactsAdapter extends RecyclerView.Adapter<EmergencyContactsAdapter.ContactViewHolder> {

    private List<EmergencyContact> contacts;
    private ContactClickListener listener;

    public interface ContactClickListener {
        void onEditClick(int position);
        void onDeleteClick(int position);
        void onCallClick(int position);
    }

    public EmergencyContactsAdapter(List<EmergencyContact> contacts, ContactClickListener listener) {
        this.contacts = contacts;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_emergency_contact, parent, false);
        return new ContactViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ContactViewHolder holder, int position) {
        EmergencyContact contact = contacts.get(position);

        holder.nameText.setText(contact.getName());
        holder.phoneText.setText(contact.getPhoneNumber());

        String relationship = contact.getRelationship();
        if (relationship != null && !relationship.isEmpty()) {
            holder.relationshipText.setVisibility(View.VISIBLE);
            holder.relationshipText.setText(relationship);
        } else {
            holder.relationshipText.setVisibility(View.GONE);
        }

        holder.btnCall.setOnClickListener(v -> {
            int currentPos = holder.getAdapterPosition();
            if (currentPos != RecyclerView.NO_POSITION) listener.onCallClick(currentPos);
        });

        holder.btnEdit.setOnClickListener(v -> {
            int currentPos = holder.getAdapterPosition();
            if (currentPos != RecyclerView.NO_POSITION) listener.onEditClick(currentPos);
        });

        holder.btnDelete.setOnClickListener(v -> {
            int currentPos = holder.getAdapterPosition();
            if (currentPos != RecyclerView.NO_POSITION) listener.onDeleteClick(currentPos);
        });
    }

    @Override
    public int getItemCount() {
        return contacts.size();
    }

    static class ContactViewHolder extends RecyclerView.ViewHolder {
        TextView nameText;
        TextView phoneText;
        TextView relationshipText;
        MaterialButton btnCall;
        MaterialButton btnEdit;
        MaterialButton btnDelete;

        public ContactViewHolder(@NonNull View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.text_contact_name);
            phoneText = itemView.findViewById(R.id.text_contact_phone);
            relationshipText = itemView.findViewById(R.id.text_contact_relationship);
            btnCall = itemView.findViewById(R.id.btn_call);
            btnEdit = itemView.findViewById(R.id.btn_edit);
            btnDelete = itemView.findViewById(R.id.btn_delete);
        }
    }
}
