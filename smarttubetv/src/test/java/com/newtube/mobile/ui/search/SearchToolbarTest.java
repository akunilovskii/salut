package com.newtube.mobile.ui.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.graphics.Rect;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.liskovsoft.smartyoutubetv2.tv.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class, qualifiers = "mdpi")
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SearchToolbarTest {
    private Activity mActivity;
    private View mRoot;
    private SearchQueryInput mInput;
    private SearchToolbar mToolbar;

    @Before public void setUp() {
        mActivity = Robolectric.buildActivity(Activity.class).setup().get();
        mActivity.setTheme(R.style.Theme_NewTube);
        FrameLayout host = new FrameLayout(mActivity);
        mRoot = LayoutInflater.from(mActivity).inflate(R.layout.mobile_search_toolbar, host, false);
        host.addView(mRoot);
        mActivity.setContentView(host);
        mInput = mRoot.findViewById(R.id.mobile_search_input);
        mToolbar = new SearchToolbar(mRoot);
        measure();
    }

    @After public void tearDown() {
        mActivity.finish();
    }

    @Test public void emptyEditorKeepsTheReferenceFieldAndComfortableBackTarget() {
        assertEquals(56, mRoot.getHeight());
        assertEquals(40, mRoot.findViewById(R.id.mobile_search_field).getHeight());
        assertEquals(16f, mInput.getTextSize(), 0.01f);
        View back = mRoot.findViewById(R.id.mobile_search_back);
        assertEquals(56, back.getWidth());
        assertEquals(52, back.getHeight());
        assertEquals(View.VISIBLE, mRoot.findViewById(R.id.mobile_search_mic).getVisibility());
    }

    @Test public void longQueryWrapsThenResultsShowItsBeginningWithoutHorizontalScroll() {
        String query = "A long search phrase with enough words to wrap over multiple lines in the editor";
        mInput.setText(query);
        mToolbar.syncQuery();
        measure();
        assertTrue(mInput.getLineCount() > 1);
        assertTrue(mRoot.getHeight() > 56);
        assertEquals(View.GONE, mRoot.findViewById(R.id.mobile_search_mic).getVisibility());
        mToolbar.showResults(query);
        measure();
        TextView results = mRoot.findViewById(R.id.mobile_search_query);
        assertEquals(View.GONE, mInput.getVisibility());
        assertEquals(48, mRoot.getHeight());
        assertFalse(mRoot.getDefaultFocusHighlightEnabled());
        assertEquals(query, results.getText().toString());
        assertEquals(TextUtils.TruncateAt.END, results.getEllipsize());
        assertEquals(0, results.getScrollX());
        assertTrue(results.getLayout().getEllipsisCount(0) > 0);
        mToolbar.setOnEditRequested(mToolbar::showEditing);
        results.performClick();
        assertTrue(mToolbar.isEditing());
        assertEquals(query, mInput.getText().toString());
        assertEquals(View.VISIBLE, mInput.getVisibility());
    }

    @Test public void wrappingEditorStillOffersKeyboardSearch() {
        mInput.requestFocus();
        EditorInfo info = new EditorInfo();
        assertNotNull(mInput.onCreateInputConnection(info));
        assertEquals(EditorInfo.IME_ACTION_SEARCH, info.imeOptions & EditorInfo.IME_MASK_ACTION);
        assertEquals(0, info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION);
        assertTrue((info.imeOptions & EditorInfo.IME_FLAG_NO_EXTRACT_UI) != 0);
    }

    @Test public void backFeedbackStaysInsideItsTargetInBothModes() {
        assertBackFeedback();
        mInput.setText("query");
        mToolbar.showResults("query");
        measure();
        assertBackFeedback();
    }

    private void assertBackFeedback() {
        View back = mRoot.findViewById(R.id.mobile_search_back);
        LayerDrawable background = (LayerDrawable) back.getBackground();
        background.setBounds(0, 0, back.getWidth(), back.getHeight());
        RippleDrawable ripple = (RippleDrawable) background.getDrawable(0);
        assertEquals(new Rect(8, 4, 48, 44), ripple.getBounds());
        assertNotNull(ripple.findDrawableByLayerId(android.R.id.mask));
    }

    private void measure() {
        mRoot.measure(View.MeasureSpec.makeMeasureSpec(411, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.AT_MOST));
        mRoot.layout(0, 0, 411, mRoot.getMeasuredHeight());
    }
}
