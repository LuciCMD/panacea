package com.clementine.medtracker.adapters;

import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RadioButton;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.clementine.medtracker.R;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * RecyclerView adapter for medication list with drag and drop support.
 * Selection is tracked by medication name so it survives category filtering.
 * @author LuciCMD
 */
public class MedicationAdapter extends RecyclerView.Adapter<MedicationAdapter.MedicationViewHolder> {
    private final List<String> medications = new ArrayList<>();
    private String selectedMedication = null;
    private final OnStartDragListener dragListener;
    private final OnMedicationSelectedListener selectionListener;
    private TypeIconResolver typeIconResolver;
    private boolean dragEnabled = false;

    public interface OnStartDragListener {
        void onStartDrag(MedicationViewHolder viewHolder);
    }

    public interface OnMedicationSelectedListener {
        void onMedicationSelected(String medication);
    }

    /** Lets the host resolve a medication's type icon without coupling the adapter to the data layer. */
    public interface TypeIconResolver {
        int getTypeIcon(String medicationName);
    }

    public MedicationAdapter(OnStartDragListener dragListener, OnMedicationSelectedListener selectionListener) {
        this.dragListener = dragListener;
        this.selectionListener = selectionListener;
    }

    public void setTypeIconResolver(TypeIconResolver resolver) {
        this.typeIconResolver = resolver;
    }

    @NonNull
    @Override
    public MedicationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_medication, parent, false);
        return new MedicationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MedicationViewHolder holder, int position) {
        String medication = medications.get(position);
        holder.radioButton.setText(medication);
        holder.radioButton.setChecked(medication.equals(selectedMedication));

        // Type icon
        if (typeIconResolver != null) {
            holder.typeIcon.setImageResource(typeIconResolver.getTypeIcon(medication));
            holder.typeIcon.setVisibility(View.VISIBLE);
        } else {
            holder.typeIcon.setVisibility(View.GONE);
        }

        // Handle drag visibility
        holder.dragHandle.setVisibility(dragEnabled ? View.VISIBLE : View.GONE);

        holder.radioButton.setOnClickListener(v -> selectByName(medication));

        if (dragEnabled) {
            holder.dragHandle.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    dragListener.onStartDrag(holder);
                }
                return false;
            });
        } else {
            holder.dragHandle.setOnTouchListener(null);
        }
    }

    @Override
    public int getItemCount() {
        return medications.size();
    }

    public void setMedications(List<String> newMedications) {
        medications.clear();
        medications.addAll(newMedications);
        // Drop a stale selection that's no longer visible
        if (selectedMedication != null && !medications.contains(selectedMedication)) {
            selectedMedication = null;
        }
        notifyDataSetChanged();
    }

    public List<String> getMedications() {
        return new ArrayList<>(medications);
    }

    public void moveItem(int fromPosition, int toPosition) {
        if (fromPosition < toPosition) {
            for (int i = fromPosition; i < toPosition; i++) {
                Collections.swap(medications, i, i + 1);
            }
        } else {
            for (int i = fromPosition; i > toPosition; i--) {
                Collections.swap(medications, i, i - 1);
            }
        }
        notifyItemMoved(fromPosition, toPosition);
    }

    public String getSelectedMedication() {
        return selectedMedication;
    }

    private void selectByName(String medicationName) {
        String previous = selectedMedication;
        selectedMedication = medicationName;
        if (previous != null) {
            int prevIndex = medications.indexOf(previous);
            if (prevIndex != -1) notifyItemChanged(prevIndex);
        }
        int newIndex = medications.indexOf(medicationName);
        if (newIndex != -1) notifyItemChanged(newIndex);
        selectionListener.onMedicationSelected(medicationName);
    }

    public void selectMedicationByName(String medicationName) {
        if (medications.contains(medicationName)) {
            selectByName(medicationName);
        }
    }

    public void setDragEnabled(boolean enabled) {
        this.dragEnabled = enabled;
        notifyDataSetChanged();
    }

    public static class MedicationViewHolder extends RecyclerView.ViewHolder {
        final RadioButton radioButton;
        final View dragHandle;
        final ImageView typeIcon;

        MedicationViewHolder(View itemView) {
            super(itemView);
            radioButton = itemView.findViewById(R.id.medication_radio);
            dragHandle = itemView.findViewById(R.id.drag_handle);
            typeIcon = itemView.findViewById(R.id.type_icon);
        }
    }
}
