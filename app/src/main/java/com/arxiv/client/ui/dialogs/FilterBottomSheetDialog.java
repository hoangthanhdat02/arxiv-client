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

        if ("cs.AI".equals(initialCategory)) {
            chipGroupCategory.check(R.id.chip_ai);
        } else if ("cs.LG".equals(initialCategory)) {
            chipGroupCategory.check(R.id.chip_lg);
        } else {
            chipGroupCategory.check(R.id.chip_all);
        }

        if ("submittedDate".equals(initialSort)) {
            radioGroupSort.check(R.id.radio_date);
        } else {
            radioGroupSort.check(R.id.radio_relevance);
        }

        btnApply.setOnClickListener(v -> {
            String selectedCategory = "";
            int selectedChipId = chipGroupCategory.getCheckedChipId();
            if (selectedChipId == R.id.chip_ai) {
                selectedCategory = "cs.AI";
            } else if (selectedChipId == R.id.chip_lg) {
                selectedCategory = "cs.LG";
            }

            String selectedSort = (radioGroupSort.getCheckedRadioButtonId() == R.id.radio_date)
                    ? "submittedDate"
                    : "relevance";

            if (listener != null) {
                listener.onFilterApplied(selectedCategory, selectedSort);
            }
            dismiss();
        });

        return view;
    }
}
