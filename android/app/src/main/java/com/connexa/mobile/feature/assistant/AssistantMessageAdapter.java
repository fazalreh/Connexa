package com.connexa.mobile.feature.assistant;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.connexa.mobile.R;
import com.connexa.mobile.core.assistant.AssistantMessage;
import com.connexa.mobile.core.assistant.AssistantMessageRole;
import com.connexa.mobile.core.assistant.AssistantReference;
import com.google.android.material.card.MaterialCardView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Renders immutable assistant messages and routes event references through a caller-owned action.
 */
public final class AssistantMessageAdapter
        extends RecyclerView.Adapter<AssistantMessageAdapter.MessageViewHolder> {

    public interface OnEventReferenceClickListener {
        void onEventReferenceClick(AssistantReference reference);
    }

    private final OnEventReferenceClickListener referenceClickListener;
    private List<AssistantMessage> messages = Collections.emptyList();

    public AssistantMessageAdapter(OnEventReferenceClickListener referenceClickListener) {
        this.referenceClickListener = Objects.requireNonNull(
                referenceClickListener, "referenceClickListener is required");
    }

    public void submitMessages(List<AssistantMessage> messages) {
        List<AssistantMessage> source = Objects.requireNonNull(messages, "messages are required");
        List<AssistantMessage> copy = new ArrayList<>(source.size());
        for (AssistantMessage message : source) {
            copy.add(Objects.requireNonNull(message, "messages cannot contain null values"));
        }
        this.messages = Collections.unmodifiableList(copy);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_assistant_message, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        AssistantMessage message = messages.get(position);
        boolean isUser = message.getRole() == AssistantMessageRole.USER;

        holder.messageBody.setText(message.getContent());
        holder.messageCard.setCardBackgroundColor(holder.itemView.getContext().getColor(
                isUser ? R.color.connexa_primary : R.color.connexa_surface_container));
        holder.messageBody.setTextColor(holder.itemView.getContext().getColor(
                isUser ? R.color.connexa_on_primary : R.color.connexa_on_surface));

        FrameLayout.LayoutParams cardParams = (FrameLayout.LayoutParams) holder.messageCard.getLayoutParams();
        cardParams.gravity = isUser ? Gravity.END : Gravity.START;
        holder.messageCard.setLayoutParams(cardParams);

        holder.referenceContainer.removeAllViews();
        if (message.getReferences().isEmpty()) {
            holder.referenceContainer.setVisibility(View.GONE);
            return;
        }

        holder.referenceContainer.setVisibility(View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(holder.itemView.getContext());
        for (AssistantReference reference : message.getReferences()) {
            View referenceView = inflater.inflate(
                    R.layout.item_assistant_reference, holder.referenceContainer, false);
            ((TextView) referenceView.findViewById(R.id.assistant_reference_title))
                    .setText(reference.getTitle());
            TextView description = referenceView.findViewById(R.id.assistant_reference_description);
            if (reference.getDescription().isEmpty()) {
                description.setVisibility(View.GONE);
            } else {
                description.setText(reference.getDescription());
                description.setVisibility(View.VISIBLE);
            }
            referenceView.setOnClickListener(ignored -> referenceClickListener.onEventReferenceClick(reference));
            holder.referenceContainer.addView(referenceView);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static final class MessageViewHolder extends RecyclerView.ViewHolder {

        private final MaterialCardView messageCard;
        private final TextView messageBody;
        private final LinearLayout referenceContainer;

        MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageCard = itemView.findViewById(R.id.assistant_message_card);
            messageBody = itemView.findViewById(R.id.assistant_message_body);
            referenceContainer = itemView.findViewById(R.id.assistant_reference_container);
        }
    }
}
