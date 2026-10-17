package com.arxiv.client.ui.dialogs;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.arxiv.client.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;

public class FilterBottomSheetDialog extends BottomSheetDialogFragment {

    public interface FilterListener {
        void onFilterApplied(String category, String sortBy);
    }

    private final String initialCategory;
    private final String initialSort;
    private final FilterListener listener;

    public FilterBottomSheetDialog(String initialCategory, String initialSort, FilterListener listener) {
        this.initialCategory = initialCategory;
        this.initialSort = initialSort;
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_filter_bottom_sheet, container, false);

        ChipGroup chipGroupCategory = view.findViewById(R.id.chip_group_category);
        RadioGroup radioGroupSort = view.findViewById(R.id.radio_group_sort);
        MaterialButton btnApply = view.findViewById(R.id.btn_apply_filter);
        MaterialButton btnReset = view.findViewById(R.id.btn_reset_filter);

        restoreSelection(chipGroupCategory, radioGroupSort, initialCategory, initialSort);

        btnApply.setOnClickListener(v -> {
            String selectedCategory = resolveCategory(chipGroupCategory.getCheckedChipId());
            String selectedSort = (radioGroupSort.getCheckedRadioButtonId() == R.id.radio_date)
                    ? "submittedDate"
                    : "relevance";

            if (listener != null) {
                listener.onFilterApplied(selectedCategory, selectedSort);
            }
            dismiss();
        });

        // Reset to defaults: All categories + Relevance sort
        btnReset.setOnClickListener(v -> {
            chipGroupCategory.check(R.id.chip_all);
            radioGroupSort.check(R.id.radio_relevance);
        });

        return view;
    }

    private void restoreSelection(ChipGroup chipGroup, RadioGroup radioGroup, String category, String sort) {
        if ("cs.AI".equals(category)) {
            chipGroup.check(R.id.chip_ai);
        } else if ("cs.LG".equals(category)) {
            chipGroup.check(R.id.chip_lg);
        } else {
            chipGroup.check(R.id.chip_all);
        }

        if ("submittedDate".equals(sort)) {
            radioGroup.check(R.id.radio_date);
        } else {
            radioGroup.check(R.id.radio_relevance);
        }
    }

    private String resolveCategory(int chipId) {
        if (chipId == R.id.chip_ai) return "cs.AI";
        if (chipId == R.id.chip_lg) return "cs.LG";
        return "";
    }
}
