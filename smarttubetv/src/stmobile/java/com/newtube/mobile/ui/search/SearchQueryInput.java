package com.newtube.mobile.ui.search;

import android.content.Context;
import android.util.AttributeSet;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import androidx.appcompat.widget.AppCompatEditText;

/** A wrapping query editor whose keyboard still offers Search rather than a newline. */
public final class SearchQueryInput extends AppCompatEditText {
    public SearchQueryInput(Context context, AttributeSet attributes) {
        super(context, attributes);
    }

    @Override public InputConnection onCreateInputConnection(EditorInfo attributes) {
        InputConnection connection = super.onCreateInputConnection(attributes);
        attributes.imeOptions &= ~EditorInfo.IME_FLAG_NO_ENTER_ACTION;
        attributes.imeOptions |= EditorInfo.IME_FLAG_NO_EXTRACT_UI;
        return connection;
    }
}
