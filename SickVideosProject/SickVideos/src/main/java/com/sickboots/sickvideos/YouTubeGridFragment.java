package com.sickboots.sickvideos;

import android.app.Fragment;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import com.koushikdutta.urlimageviewhelper.UrlImageViewCallback;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;
import com.sickboots.sickvideos.database.YouTubeData;
import com.sickboots.sickvideos.lists.UIAccess;
import com.sickboots.sickvideos.lists.YouTubeList;
import com.sickboots.sickvideos.lists.YouTubeListDB;
import com.sickboots.sickvideos.lists.YouTubeListLive;
import com.sickboots.sickvideos.lists.YouTubeListSpec;
import com.sickboots.sickvideos.misc.ApplicationHub;
import com.sickboots.sickvideos.misc.ScrollTriggeredAnimator;
import com.sickboots.sickvideos.misc.StandardAnimations;
import com.sickboots.sickvideos.misc.Util;
import com.sickboots.sickvideos.youtube.VideoPlayer;
import com.sickboots.sickvideos.youtube.YouTubeAPI;

import org.joda.time.Period;
import org.joda.time.Seconds;
import org.joda.time.format.ISOPeriodFormat;
import org.joda.time.format.PeriodFormatter;

import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;

import uk.co.senab.actionbarpulltorefresh.library.PullToRefreshAttacher;

public class YouTubeGridFragment extends Fragment
    implements Observer, PullToRefreshAttacher.OnRefreshListener, UIAccess.UIAccessListener {

  // Activity should host a player
  public interface HostActivitySupport {
    public VideoPlayer videoPlayer();

    void fragmentInstalled();

    public void installFragment(Fragment fragment, boolean animate);
  }

  private static final String SEARCH_QUERY = "query";
  private static final String LIST_TYPE = "type";
  private static final String CHANNEL_ID = "channel";
  private static final String RELATED_TYPE = "related";
  private static final String PLAYLIST_ID = "playlist";
  private YouTubeListSpec.ListType listType;
  private YouTubeListAdapter mAdapter;
  private YouTubeList mList;
  private int itemResID = 0;
  private float mImageAlpha = .6f;
  private View mEmptyView;
  private GridView mGridView;

  public static YouTubeGridFragment relatedFragment(YouTubeAPI.RelatedPlaylistType relatedType) {
    return newInstance(YouTubeListSpec.ListType.RELATED, null, null, relatedType, null);
  }

  public static YouTubeGridFragment videosFragment(String playlistID) {
    return newInstance(YouTubeListSpec.ListType.VIDEOS, null, playlistID, null, null);
  }

  public static YouTubeGridFragment playlistsFragment(String channelID) {
    return newInstance(YouTubeListSpec.ListType.PLAYLISTS, channelID, null, null, null);
  }

  public static YouTubeGridFragment likedFragment() {
    return newInstance(YouTubeListSpec.ListType.LIKED, null, null, null, null);
  }

  public static YouTubeGridFragment subscriptionsFragment() {
    return newInstance(YouTubeListSpec.ListType.SUBSCRIPTIONS, null, null, null, null);
  }

  public static YouTubeGridFragment searchFragment(String searchQuery) {
    return newInstance(YouTubeListSpec.ListType.SEARCH, null, null, null, searchQuery);
  }

  private static YouTubeGridFragment newInstance(YouTubeListSpec.ListType listType, String channelID, String playlistID, YouTubeAPI.RelatedPlaylistType relatedType, String searchQuery) {
    YouTubeGridFragment fragment = new YouTubeGridFragment();

    Bundle args = new Bundle();

    args.putSerializable(LIST_TYPE, listType);
    args.putSerializable(RELATED_TYPE, relatedType);
    args.putString(CHANNEL_ID, channelID);
    args.putString(PLAYLIST_ID, playlistID);
    args.putString(SEARCH_QUERY, searchQuery);

    fragment.setArguments(args);

    return fragment;
  }

  public CharSequence actionBarTitle() {
    CharSequence title = null;
    if (mList != null)
      title = mList.name();

    // if video player is up, show the video title
    if (player().visible()) {
      title = player().title();
    }

    return title;
  }

  // Observer
  @Override
  public void update(Observable observable, Object data) {
    if (data instanceof String) {
      String input = (String) data;

      if (input.equals(ApplicationHub.BACK_BUTTON_NOTIFICATION)) {
        // if the video player is visible, close it
        player().close(true);
      }
    }
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {

    listType = (YouTubeListSpec.ListType) getArguments().getSerializable(LIST_TYPE);

    ViewGroup rootView;

    rootView = (ViewGroup) inflater.inflate(R.layout.fragment_youtube_grid, container, false);
    mGridView = (GridView) rootView.findViewById(R.id.gridview);

    mList = createList(getArguments());

    mEmptyView = Util.emptyListView(getActivity(), "Talking to YouTube...");
    rootView.addView(mEmptyView);

    mGridView.setEmptyView(mEmptyView);

    mAdapter = new YouTubeListAdapter();

    mGridView.setAdapter(mAdapter);
    mGridView.setOnItemClickListener(mAdapter);

    // .015 is the default
    mGridView.setFriction(0.01f);

    // Add the Refreshable View and provide the refresh listener;
    Util.PullToRefreshListener ptrl = (Util.PullToRefreshListener) getActivity();
    ptrl.addRefreshableView(mGridView, this);

    // load data if we have it already
    loadFromList();

    View dimmerView = rootView.findViewById(R.id.dimmer);

    new ScrollTriggeredAnimator(mGridView, dimmerView);

    // triggers an update for the title, lame hack
    HostActivitySupport provider = (HostActivitySupport) getActivity();
    provider.fragmentInstalled();

    return rootView;
  }

  @Override
  public void onStart() {
    super.onStart();

    // for back button notification
    ApplicationHub.instance().addObserver(this);
  }

  @Override
  public void onStop() {
    super.onStop();

    // for back button notification
    ApplicationHub.instance().deleteObserver(this);
  }

  public void handleClick(YouTubeData itemMap) {
    switch (mList.type()) {
      case RELATED:
      case SEARCH:
      case LIKED:
      case VIDEOS:
        String videoId = itemMap.mVideo;
        String title = itemMap.mTitle;

        player().open(videoId, title, true);
        break;
      case PLAYLISTS: {
        String playlistID = itemMap.mPlaylist;

        if (playlistID != null) {
          Fragment frag = YouTubeGridFragment.videosFragment(playlistID);

          HostActivitySupport provider = (HostActivitySupport) getActivity();

          provider.installFragment(frag, true);
        }
      }
      break;
    }
  }

  private VideoPlayer player() {
    HostActivitySupport provider = (HostActivitySupport) getActivity();

    return provider.videoPlayer();
  }

  public void onRefreshStarted(View view) {
    mList.refresh();

    mEmptyView.setVisibility(View.VISIBLE);

    mGridView.setEmptyView(mEmptyView);
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    boolean handled = mList.handleActivityResult(requestCode, requestCode, data);

    if (!handled) {
      switch (requestCode) {
      }
    }
  }

  @Override
  public void onResults() {
    loadFromList();

    // get rid of the empty view.  Its not used after initial load, and this also
    // handles the case of no results.  we don't want the progress spinner to sit there and spin forever.
    mGridView.setEmptyView(null);
    mEmptyView.setVisibility(View.INVISIBLE);
  }

  private void loadFromList() {
    mAdapter.clear();

    List items = mList.getItems();

    if (items != null)
      mAdapter.addAll(items);

    // stop the pull to refresh indicator
    Util.PullToRefreshListener ptrl = (Util.PullToRefreshListener) getActivity();
    ptrl.setRefreshComplete();
  }

  private YouTubeList createList(Bundle argsBundle) {
    YouTubeList result = null;

    String channelID = argsBundle.getString(CHANNEL_ID);
    String playlistID = argsBundle.getString(PLAYLIST_ID);
    String query = argsBundle.getString(SEARCH_QUERY);
    YouTubeAPI.RelatedPlaylistType relatedType = (YouTubeAPI.RelatedPlaylistType) argsBundle.getSerializable(RELATED_TYPE);

    UIAccess access = new UIAccess(this);

    switch (listType) {
      case SUBSCRIPTIONS:
        result = new YouTubeListDB(YouTubeListSpec.subscriptionsSpec(), access);
        break;
      case PLAYLISTS:
        result = new YouTubeListDB(YouTubeListSpec.playlistsSpec(channelID), access);
        break;
      case CATEGORIES:
        result = new YouTubeListLive(YouTubeListSpec.categoriesSpec(), access);
        break;
      case LIKED:
        result = new YouTubeListLive(YouTubeListSpec.likedSpec(), access);
        break;
      case RELATED:
        result = new YouTubeListDB(YouTubeListSpec.relatedSpec(relatedType, channelID), access);
        break;
      case SEARCH:
        result = new YouTubeListLive(YouTubeListSpec.searchSpec(query), access);
        break;
      case VIDEOS:
        result = new YouTubeListLive(YouTubeListSpec.videosSpec(playlistID), access);
        break;
    }

    return result;
  }

  // ===========================================================================
  // Adapter

  private class YouTubeListAdapter extends ArrayAdapter<YouTubeData> implements AdapterView.OnItemClickListener, VideoMenuView.VideoMenuViewListener {
    private final LayoutInflater inflater;
    int animationID = 0;
    boolean showHidden = false;

    public YouTubeListAdapter() {
      super(getActivity(), 0);

      inflater = LayoutInflater.from(getActivity());
    }

    private void animateViewForClick(final View theView) {
      switch (animationID) {
        case 0:
          StandardAnimations.dosomething(theView);
          break;
        case 1:
          StandardAnimations.upAndAway(theView);
          break;
        case 2:
          StandardAnimations.rockBounce(theView);
          break;
        case 3:
          StandardAnimations.winky(theView, mImageAlpha);
          break;
        default:
          StandardAnimations.rubberClick(theView);
          animationID = -1;
          break;
      }

      animationID += 1;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
      ViewHolder holder = (ViewHolder) v.getTag();

      if (holder != null) {
        animateViewForClick(holder.image);

        YouTubeData map = getItem(position);
        handleClick(map);
      } else {
        Util.log("no holder on click?");
      }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      ViewHolder holder = null;

      if (convertView == null) {
        convertView = inflater.inflate(R.layout.youtube_grid_item, null);

        holder = new ViewHolder();
        holder.image = (ImageView) convertView.findViewById(R.id.image);
        holder.title = (TextView) convertView.findViewById(R.id.text_view);
        holder.description = (TextView) convertView.findViewById(R.id.description_view);
        holder.duration = (TextView) convertView.findViewById(R.id.duration);
        holder.menuButton = (VideoMenuView) convertView.findViewById(R.id.menu_button);

        convertView.setTag(holder);
      } else {
        holder = (ViewHolder) convertView.getTag();

        // reset some stuff that might have been set on an animation (not sure if this is needed)
        holder.image.setAlpha(1.0f);
        holder.image.setScaleX(1.0f);
        holder.image.setScaleY(1.0f);
      }

      YouTubeData itemMap = getItem(position);

      holder.image.setAnimation(null);

      int defaultImageResID = 0;

      UrlImageViewHelper.setUrlDrawable(holder.image, itemMap.mThumbnail, defaultImageResID, new UrlImageViewCallback() {

        @Override
        public void onLoaded(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
          if (!loadedFromCache) {
            imageView.setAlpha(mImageAlpha / 2);
            imageView.animate().setDuration(800).alpha(mImageAlpha);
          } else
            imageView.setAlpha(mImageAlpha);
        }

      });

      boolean hidden = false;
      if (!showHidden) {
        String hiddenValue = itemMap.mHidden;
        if (hiddenValue != null)
          hidden = true;
      }

      if (hidden)
        holder.title.setText("(Hidden)");
      else
        holder.title.setText( itemMap.mTitle);

      String duration = itemMap.mDuration;
      if (duration != null) {
        holder.duration.setVisibility(View.VISIBLE);

        PeriodFormatter formatter = ISOPeriodFormat.standard();
        Period p = formatter.parsePeriod(duration);
        Seconds s = p.toStandardSeconds();

        holder.duration.setText(Util.millisecondsToDuration(s.getSeconds() * 1000));
      } else {
        holder.duration.setVisibility(View.GONE);
      }

      // hide description if empty
      if (holder.description != null) {
        String desc = (String) itemMap.mDescription;
        if (desc != null && (desc.length() > 0)) {
          holder.description.setVisibility(View.VISIBLE);
          holder.description.setText(desc);
        } else {
          holder.description.setVisibility(View.GONE);
        }
      }

      // set video id on menu button so clicking can know what video to act on
      // only set if there is a videoId, playlists and others don't need this menu
      String videoId = (String) itemMap.mVideo;
      if (videoId != null && (videoId.length() > 0)) {
        holder.menuButton.setVisibility(View.VISIBLE);
        holder.menuButton.setListener(this);
        holder.menuButton.mVideoMap = itemMap;
      } else {
        holder.menuButton.setVisibility(View.GONE);
      }

      // load more data if at the end
      mList.updateHighestDisplayedIndex(position);

      return convertView;
    }

    // VideoMenuViewListener
    @Override
    public void showVideoInfo(YouTubeData videoMap) {

    }

    // VideoMenuViewListener
    @Override
    public void showVideoOnYouTube(YouTubeData videoMap) {
      YouTubeAPI.playMovieUsingIntent(getActivity(), videoMap.mVideo);
    }

    // VideoMenuViewListener
    @Override
    public void hideVideo(YouTubeData videoMap) {
      // hidden is either null or not null
      // toggle it
      String hidden = (String) videoMap.mHidden;
      videoMap.mHidden = (hidden == null) ? "" : null;

      mList.updateItem(videoMap);
    }

    class ViewHolder {
      TextView title;
      TextView description;
      TextView duration;
      ImageView image;
      VideoMenuView menuButton;
    }
  }

}
