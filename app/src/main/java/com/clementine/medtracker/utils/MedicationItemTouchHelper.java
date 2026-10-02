package com.clementine.medtracker.utils;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import com.clementine.medtracker.adapters.MedicationAdapter;

/**
 * ItemTouchHelper.Callback implementation for medication list drag and drop
 * @author LuciCMD
 */
public class MedicationItemTouchHelper extends ItemTouchHelper.Callback {
    private final MedicationAdapter adapter;
    private final OnMedicationOrderChangeListener orderChangeListener;

    public interface OnMedicationOrderChangeListener {
        void onOrderChanged(String[] medications);
    }

    public MedicationItemTouchHelper(MedicationAdapter adapter,
                                     OnMedicationOrderChangeListener listener) {
        this.adapter = adapter;
        this.orderChangeListener = listener;
    }

    @Override
    public boolean isLongPressDragEnabled() {
        return false;
    }

    @Override
    public boolean isItemViewSwipeEnabled() {
        return false;
    }

    @Override
    public int getMovementFlags(@NonNull RecyclerView recyclerView,
                                @NonNull RecyclerView.ViewHolder viewHolder) {
        int dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
        return makeMovementFlags(dragFlags, 0);
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView,
                          @NonNull RecyclerView.ViewHolder source,
                          @NonNull RecyclerView.ViewHolder target) {
        adapter.moveItem(source.getBindingAdapterPosition(), target.getBindingAdapterPosition());
        orderChangeListener.onOrderChanged(
                adapter.getMedications().toArray(new String[0]));
        return true;
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        // Swiping is not enabled
    }
}