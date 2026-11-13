package com.newtube.mobile.ui.channel;

import android.content.Context;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The playlist page header, as a one-item adapter concatenated in front of the video grid.
 *
 * <p>It holds zero items until {@link #setPlaylist} receives a playlist, which keeps channel
 * uploads (no playlist behind them) free of a header.</p>
 */
public class PlaylistHeaderAdapter extends RecyclerView.Adapter<PlaylistHeaderAdapter.HeaderHolder> {
    private static final int MAX_COVERS = 20;
    public interface Callbacks {
        void onPlayAll();
        void onShuffle();
        void onSearchChanged(String query);
    }

    private final Callbacks mCallbacks;
    private Video mPlaylist;
    private List<String> mCoverUrls = Collections.emptyList();
    private boolean mActionsEnabled;
    private String mSearchQuery = "";
    private boolean mSearchOpen;

    public PlaylistHeaderAdapter(Callbacks callbacks) {
        mCallbacks = callbacks;
    }

    /** @param playlist the opener card, or {@code null} for "this destination has no header". */
    public void setPlaylist(Video playlist) {
        boolean had = mPlaylist != null;
        if (playlist == null || mPlaylist == null
                || !TextUtils.equals(mPlaylist.getPlaylistId(), playlist.getPlaylistId())) {
            mSearchQuery = "";
            mSearchOpen = false;
        }
        mPlaylist = playlist;

        if (had && playlist == null) {
            notifyItemRemoved(0);
        } else if (!had && playlist != null) {
            notifyItemInserted(0);
        } else if (playlist != null) {
            notifyItemChanged(0);
        }
    }

    /** The first visible videos supply the mosaic and its dimmed first-video backdrop. */
    public void setVideos(List<Video> videos) {
        List<String> covers = new ArrayList<>();
        if (videos != null) {
            for (Video video : videos) {
                if (video != null && video.hasVideo()) {
                    covers.add(video.getCardImageUrl());
                    if (covers.size() == MAX_COVERS) {
                        break;
                    }
                }
            }
        }
        if (mCoverUrls.equals(covers)) {
            return;
        }
        mCoverUrls = covers;
        if (mPlaylist != null) {
            notifyItemChanged(0);
        }
    }

    /** Play all / Shuffle stay disabled until the grid actually holds something playable. */
    public void setActionsEnabled(boolean enabled) {
        if (mActionsEnabled == enabled) {
            return;
        }

        mActionsEnabled = enabled;

        if (mPlaylist != null) {
            notifyItemChanged(0);
        }
    }

    public boolean hasHeader() {
        return mPlaylist != null;
    }

    public String getSearchQuery() {
        return mSearchQuery;
    }

    public boolean isSearching() {
        return !mSearchQuery.isEmpty();
    }

    @Override
    public int getItemCount() {
        return mPlaylist != null ? 1 : 0;
    }

    @NonNull
    @Override
    public HeaderHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_mobile_playlist_header, parent, false);

        return new HeaderHolder(view, this, mCallbacks);
    }

    @Override
    public void onBindViewHolder(@NonNull HeaderHolder holder, int position) {
        holder.bind(mPlaylist, mCoverUrls, mActionsEnabled, mSearchOpen, mSearchQuery);
    }

    static class HeaderHolder extends RecyclerView.ViewHolder {
        private final LinearLayout mCoverGrid;
        private final ImageView mBackdrop;
        private final TextView mTitle;
        private final TextView mMeta;
        private final MaterialButton mPlayAll;
        private final MaterialButton mShuffle;
        private final ImageButton mSearchButton;
        private final EditText mSearchField;
        private List<String> mBoundCovers = Collections.emptyList();
        private String mBoundBackdrop;
        private boolean mBindingSearch;

        HeaderHolder(@NonNull View itemView, PlaylistHeaderAdapter adapter, Callbacks callbacks) {
            super(itemView);

            mCoverGrid = itemView.findViewById(R.id.mobile_playlist_cover_grid);
            mBackdrop = itemView.findViewById(R.id.mobile_channel_uploads_backdrop);
            mTitle = itemView.findViewById(R.id.mobile_channel_uploads_header_title);
            mMeta = itemView.findViewById(R.id.mobile_channel_uploads_header_meta);
            mPlayAll = itemView.findViewById(R.id.mobile_channel_uploads_header_play_all);
            mShuffle = itemView.findViewById(R.id.mobile_channel_uploads_shuffle);
            mSearchButton = itemView.findViewById(R.id.mobile_playlist_search_button);
            mSearchField = itemView.findViewById(R.id.mobile_playlist_search_field);

            mPlayAll.setOnClickListener(v -> callbacks.onPlayAll());
            mShuffle.setOnClickListener(v -> callbacks.onShuffle());
            mSearchButton.setOnClickListener(v -> {
                adapter.mSearchOpen = !adapter.mSearchOpen;
                mSearchField.setVisibility(adapter.mSearchOpen ? View.VISIBLE : View.GONE);
                updateSearchDescription(adapter.mSearchOpen);
                InputMethodManager keyboard = (InputMethodManager) itemView.getContext()
                        .getSystemService(Context.INPUT_METHOD_SERVICE);
                if (adapter.mSearchOpen) {
                    mSearchField.requestFocus();
                    mSearchField.post(() -> keyboard.showSoftInput(mSearchField, InputMethodManager.SHOW_IMPLICIT));
                } else {
                    mSearchField.setText("");
                    mSearchField.clearFocus();
                    keyboard.hideSoftInputFromWindow(mSearchField.getWindowToken(), 0);
                }
            });
            mSearchField.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence text, int start, int before, int count) { }
                @Override public void afterTextChanged(Editable text) {
                    if (mBindingSearch) {
                        return;
                    }
                    String query = text.toString().trim();
                    if (!TextUtils.equals(adapter.mSearchQuery, query)) {
                        adapter.mSearchQuery = query;
                        callbacks.onSearchChanged(query);
                    }
                }
            });
        }

        private void updateSearchDescription(boolean open) {
            mSearchButton.setContentDescription(itemView.getContext().getString(open
                    ? R.string.mobile_playlist_close_search : R.string.mobile_playlist_search));
            mSearchButton.setImageResource(open ? R.drawable.ic_search_clear : R.drawable.ic_mobile_search);
        }

        void bind(Video playlist, List<String> coverUrls, boolean actionsEnabled,
                boolean searchOpen, String searchQuery) {
            if (playlist == null) {
                return;
            }

            mTitle.setText(playlist.getTitle());

            CharSequence owner = playlist.getAuthor();
            CharSequence details = joinMeta(playlist.badge,
                    TextUtils.isEmpty(owner) ? playlist.getSecondTitle() : null);
            CharSequence meta = joinMeta(owner, details);
            mMeta.setText(meta);
            mMeta.setVisibility(TextUtils.isEmpty(meta) ? View.GONE : View.VISIBLE);

            mPlayAll.setEnabled(actionsEnabled);
            mShuffle.setEnabled(actionsEnabled);
            mSearchField.setVisibility(searchOpen ? View.VISIBLE : View.GONE);
            updateSearchDescription(searchOpen);
            if (!TextUtils.equals(mSearchField.getText(), searchQuery)) {
                mBindingSearch = true;
                mSearchField.setText(searchQuery);
                mBindingSearch = false;
            }

            String firstCover = coverUrls.isEmpty() ? playlist.getCardImageUrl() : coverUrls.get(0);
            bindCovers(coverUrls.isEmpty() ? Collections.singletonList(firstCover) : coverUrls);
            if (!TextUtils.equals(firstCover, mBoundBackdrop)) {
                mBoundBackdrop = firstCover;
                Glide.with(itemView).load(firstCover).centerCrop().into(mBackdrop);
            }
        }

        private void bindCovers(List<String> covers) {
            if (mBoundCovers.equals(covers)) {
                return;
            }
            for (int row = 0; row < mCoverGrid.getChildCount(); row++) {
                LinearLayout oldRow = (LinearLayout) mCoverGrid.getChildAt(row);
                for (int column = 0; column < oldRow.getChildCount(); column++) {
                    Glide.with(itemView).clear((ImageView) oldRow.getChildAt(column));
                }
            }
            mCoverGrid.removeAllViews();
            mBoundCovers = new ArrayList<>(covers);

            int rowCount = Math.min(3, (covers.size() + 6) / 7);
            int index = 0;
            int gap = (int) (itemView.getResources().getDisplayMetrics().density + 0.5f);
            for (int row = 0; row < rowCount; row++) {
                LinearLayout coverRow = new LinearLayout(itemView.getContext());
                coverRow.setOrientation(LinearLayout.HORIZONTAL);
                mCoverGrid.addView(coverRow, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
                int columnCount = Math.min(7, covers.size() - index);
                for (int column = 0; column < columnCount; column++) {
                    ImageView cover = new ImageView(itemView.getContext());
                    cover.setContentDescription(null);
                    cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                            0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
                    params.setMargins(gap, gap, gap, gap);
                    coverRow.addView(cover, params);
                    Glide.with(itemView).load(covers.get(index++)).override(200, 140)
                            .centerCrop().into(cover);
                }
            }
        }

        private CharSequence joinMeta(CharSequence first, CharSequence second) {
            if (TextUtils.isEmpty(first)) {
                return second;
            }
            if (TextUtils.isEmpty(second)) {
                return first;
            }

            return TextUtils.concat(first, " • ", second);
        }
    }
}
