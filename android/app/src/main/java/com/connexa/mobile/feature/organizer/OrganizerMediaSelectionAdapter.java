package com.connexa.mobile.feature.organizer;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.connexa.mobile.databinding.ItemOrganizerMediaBinding;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Displays media selected locally for an organizer event draft. */
public final class OrganizerMediaSelectionAdapter
        extends RecyclerView.Adapter<OrganizerMediaSelectionAdapter.MediaViewHolder> {

    public interface Listener {
        void onRemove(Uri uri);
    }

    private final Listener listener;
    private List<Uri> media = Collections.emptyList();

    public OrganizerMediaSelectionAdapter(Listener listener) {
        this.listener = Objects.requireNonNull(listener, "listener is required");
    }

    public void submit(List<Uri> media) {
        this.media = Collections.unmodifiableList(new ArrayList<>(
                Objects.requireNonNull(media, "media is required")));
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MediaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemOrganizerMediaBinding binding = ItemOrganizerMediaBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new MediaViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull MediaViewHolder holder, int position) {
        holder.bind(media.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return media.size();
    }

    static final class MediaViewHolder extends RecyclerView.ViewHolder {

        private final ItemOrganizerMediaBinding binding;

        MediaViewHolder(ItemOrganizerMediaBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Uri uri, Listener listener) {
            String fileName = uri.getLastPathSegment();
            binding.organizerMediaFileName.setText(
                    fileName == null || fileName.trim().isEmpty() ? uri.toString() : fileName);
            binding.organizerMediaMetadata.setText(uri.getScheme() == null ? "" : uri.getScheme());
            binding.organizerMediaRemoveButton.setOnClickListener(view -> listener.onRemove(uri));
        }
    }
}
