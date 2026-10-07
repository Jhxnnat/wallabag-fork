package fr.gaulupeau.apps.Poche.data;

import android.app.Activity;
import android.content.Context;
import android.text.TextUtils;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.network.ArticlePreviewImageLoader;
import fr.gaulupeau.apps.Poche.data.dao.entities.Article;
import fr.gaulupeau.apps.Poche.data.dao.entities.Tag;
import fr.gaulupeau.apps.Poche.ui.ArticleActionsHelper;

import static fr.gaulupeau.apps.Poche.data.ListTypes.LIST_TYPE_ARCHIVED;
import static fr.gaulupeau.apps.Poche.data.ListTypes.LIST_TYPE_FAVORITES;
import static fr.gaulupeau.apps.Poche.data.ListTypes.LIST_TYPE_UNREAD;

public class ListAdapter extends RecyclerView.Adapter<ListAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(int position);
    }

    private Context context;
    private Settings settings;
    private ArticleActionsHelper articleActionsHelper = new ArticleActionsHelper();

    private List<Article> articles;
    private OnItemClickListener listener;
    private int listType;

    private Article articleWithContextMenu;

    public ListAdapter(Context context, Settings settings,
                       List<Article> articles, OnItemClickListener listener, int listType) {
        this.context = context;
        this.settings = settings;
        this.articles = articles;
        this.listener = listener;
        this.listType = listType;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item, parent, false);
        return new ViewHolder(view, listener);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        holder.bind(articles.get(position));
    }

    @Override
    public int getItemCount() {
        return articles.size();
    }

    public boolean handleContextItemSelected(Activity activity, MenuItem item) {
        return articleWithContextMenu != null && articleActionsHelper
                .handleContextItemSelected(activity, articleWithContextMenu, item);
    }

    public class ViewHolder extends RecyclerView.ViewHolder
            implements View.OnClickListener, View.OnCreateContextMenuListener {

        OnItemClickListener listener;

        Article article;

        TextView title;
        TextView url;
        ImageView favourite;
        ImageView read;
        TextView readingTime;
        TextView tags;
        TextView annotationCount;
        TextView metaSeparator;
        ImageView previewPicture;

        ViewHolder(View itemView, OnItemClickListener listener) {
            super(itemView);
            this.listener = listener;

            title = itemView.findViewById(R.id.title);
            url = itemView.findViewById(R.id.url);
            favourite = itemView.findViewById(R.id.favourite);
            read = itemView.findViewById(R.id.read);
            readingTime = itemView.findViewById(R.id.estimatedReadingTime);
            tags = itemView.findViewById(R.id.tags);
            annotationCount = itemView.findViewById(R.id.annotationCount);
            metaSeparator = itemView.findViewById(R.id.metaSeparator);
            previewPicture = itemView.findViewById(R.id.previewPicture);

            itemView.setOnClickListener(this);
            itemView.setOnCreateContextMenuListener(this);
        }

        void bind(Article article) {
            this.article = article;

            title.setText(article.getTitle());
            url.setText(article.getDomain());

            metaSeparator.setVisibility(
                    TextUtils.isEmpty(article.getDomain()) ? View.GONE : View.VISIBLE);

            boolean showFavourite = false;
            boolean showRead = false;
            switch (listType) {
                case LIST_TYPE_UNREAD:
                case LIST_TYPE_ARCHIVED:
                    showFavourite = article.getFavorite();
                    break;

                case LIST_TYPE_FAVORITES:
                    showRead = article.getArchive();
                    break;

                default: // we don't actually use it right now
                    showFavourite = article.getFavorite();
                    showRead = article.getArchive();
                    break;
            }
            favourite.setVisibility(showFavourite ? View.VISIBLE : View.GONE);
            read.setVisibility(showRead ? View.VISIBLE : View.GONE);
            readingTime.setText(context.getString(R.string.listItem_estimatedReadingTime,
                    article.getEstimatedReadingTime(settings.getReadingSpeed())));

            List<Tag> tagList = article.getTags();
            if (tagList != null && !tagList.isEmpty()) {
                Tag.sortTagListByLabel(tagList);

                StringBuilder tagString = new StringBuilder();
                for (Tag tag : tagList) {
                    if (tagString.length() > 0) tagString.append(", ");
                    tagString.append(tag.getLabel());
                }

                tags.setText(tagString.toString());
                tags.setVisibility(View.VISIBLE);
            } else {
                tags.setVisibility(View.GONE);
            }

            int annotationTotal = article.getAnnotations().size();
            if (annotationTotal > 0) {
                annotationCount.setText(context.getString(
                        R.string.listItem_annotationCount, annotationTotal));
                annotationCount.setVisibility(View.VISIBLE);
            } else {
                annotationCount.setVisibility(View.GONE);
            }

            bindPreviewPicture(article);
        }

        private void bindPreviewPicture(Article article) {
            String previewUrl = article.getPreviewPictureURL();

            if (!settings.isPreviewImageEnabled() || TextUtils.isEmpty(previewUrl)) {
                previewPicture.setVisibility(View.GONE);
                previewPicture.setImageDrawable(null);
                previewPicture.setTag(null);
                return;
            }

            previewPicture.setVisibility(View.VISIBLE);
            previewPicture.setImageDrawable(null);

            final String tag = article.getId() + "|" + previewUrl;
            previewPicture.setTag(tag);

            Integer articleIdObject = article.getArticleId();
            int articleId = articleIdObject != null ? articleIdObject : -1;

            int targetSize = context.getResources()
                    .getDimensionPixelSize(R.dimen.list_item_preview_size);

            ArticlePreviewImageLoader.load(previewUrl, articleId, targetSize, bitmap -> {
                if (!tag.equals(previewPicture.getTag())) return; // view was recycled

                if (bitmap != null) {
                    previewPicture.setImageBitmap(bitmap);
                } else {
                    // no image available: don't render anything
                    previewPicture.setVisibility(View.GONE);
                }
            });
        }

        @Override
        public void onClick(View v) {
            int index = getAdapterPosition();
            if (index != RecyclerView.NO_POSITION) {
                listener.onItemClick(index);
            }
        }

        @Override
        public void onCreateContextMenu(ContextMenu menu, View v,
                                        ContextMenu.ContextMenuInfo menuInfo) {
            articleWithContextMenu = article;

            if (article == null) return;

            new MenuInflater(context) // not sure about this
                    .inflate(R.menu.article_list_context_menu, menu);

            articleActionsHelper.initMenu(menu, article);
        }

    }

}
