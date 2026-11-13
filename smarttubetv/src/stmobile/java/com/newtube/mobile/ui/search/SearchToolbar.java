package com.newtube.mobile.ui.search;

import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;

import com.liskovsoft.smartyoutubetv2.tv.R;

/** Presentation of the wrapping editor and the start-aligned, ellipsized results query. */
final class SearchToolbar {
    private final View mRoot;
    private final View mWrapper;
    private final View mField;
    private final View mTextFrame;
    private final EditText mInput;
    private final TextView mQuery;
    private final ImageButton mBack;
    private final View mClear;
    private final View mMic;
    private boolean mEditing = true;

    SearchToolbar(View root) {
        mRoot = root;
        mWrapper = root.findViewById(R.id.mobile_search_field_wrapper);
        mField = root.findViewById(R.id.mobile_search_field);
        mTextFrame = root.findViewById(R.id.mobile_search_text_frame);
        mInput = root.findViewById(R.id.mobile_search_input);
        mQuery = root.findViewById(R.id.mobile_search_query);
        mBack = root.findViewById(R.id.mobile_search_back);
        mClear = root.findViewById(R.id.mobile_search_clear);
        mMic = root.findViewById(R.id.mobile_search_mic);
        syncQuery();
    }

    void setOnEditRequested(Runnable callback) {
        mQuery.setOnClickListener(view -> callback.run());
    }

    boolean isEditing() {
        return mEditing;
    }

    void showEditing() {
        mEditing = true;
        mQuery.setVisibility(View.GONE);
        mInput.setVisibility(View.VISIBLE);
        syncQuery();
    }

    void showResults(String query) {
        mEditing = false;
        mInput.setVisibility(View.GONE);
        mQuery.setText(query);
        mQuery.setVisibility(View.VISIBLE);
        mQuery.scrollTo(0, 0);
        mRoot.requestFocus();
        syncQuery();
    }

    void syncQuery() {
        boolean hasText = mInput.length() > 0;
        boolean expandedEditor = mEditing && hasText;
        mClear.setVisibility(hasText ? View.VISIBLE : View.GONE);
        mMic.setVisibility(expandedEditor ? View.GONE : View.VISIBLE);
        mRoot.setMinimumHeight(dp(mEditing ? 56 : 48));
        int endGap = expandedEditor ? (int) (4 * mRoot.getResources().getDisplayMetrics().density) : dp(8);
        mWrapper.setPaddingRelative(0, 0, endGap, dp(mEditing ? 8 : 0));

        FrameLayout.LayoutParams field = (FrameLayout.LayoutParams) mField.getLayoutParams();
        // The reference uses pixel-offset rounding for this four-dp inset at fractional density.
        int top = expandedEditor ? 0 : (int) (4 * mRoot.getResources().getDisplayMetrics().density);
        if (field.topMargin != top) {
            field.topMargin = top;
            mField.setLayoutParams(field);
        }
        FrameLayout.LayoutParams text = (FrameLayout.LayoutParams) mTextFrame.getLayoutParams();
        int end = hasText ? dp(56) : 0;
        if (text.getMarginEnd() != end) {
            text.setMarginEnd(end);
            mTextFrame.setLayoutParams(text);
        }
        ViewGroup.LayoutParams back = mBack.getLayoutParams();
        int height = dp(mEditing ? 52 : 48);
        if (back.height != height) {
            back.height = height;
            mBack.setLayoutParams(back);
        }
        mBack.setPaddingRelative(dp(16), dp(12), dp(16), dp(mEditing ? 16 : 12));
    }

    private int dp(int value) {
        return Math.round(value * mRoot.getResources().getDisplayMetrics().density);
    }
}
