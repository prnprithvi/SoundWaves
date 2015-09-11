package org.bottiger.podcast;

import org.bottiger.podcast.adapters.PlaylistAdapter;
import org.bottiger.podcast.listeners.DownloadProgressObservable;
import org.bottiger.podcast.listeners.PaletteListener;
import org.bottiger.podcast.listeners.PlayerStatusObservable;
import org.bottiger.podcast.playlist.Playlist;
import org.bottiger.podcast.provider.FeedItem;
import org.bottiger.podcast.provider.IEpisode;
import org.bottiger.podcast.service.Downloader.EpisodeDownloadManager;
import org.bottiger.podcast.service.PlayerService;
import org.bottiger.podcast.utils.PaletteHelper;
import org.bottiger.podcast.utils.StrUtils;
import org.bottiger.podcast.utils.UIUtils;
import org.bottiger.podcast.views.DownloadButtonView;
import org.bottiger.podcast.views.PlayPauseImageView;
import org.bottiger.podcast.views.PlayerButtonView;
import org.bottiger.podcast.views.PlayerSeekbar;
import org.bottiger.podcast.views.TextViewObserver;
import org.bottiger.podcast.views.TopPlayer;
import org.bottiger.podcast.views.dialogs.DialogBulkDownload;
import org.bottiger.podcast.views.dialogs.DialogPlaylistFilters;

import android.app.Activity;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v7.graphics.Palette;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.facebook.drawee.view.SimpleDraweeView;
import com.squareup.otto.Produce;
import com.squareup.otto.Subscribe;

public class PlaylistFragment extends AbstractEpisodeFragment implements OnSharedPreferenceChangeListener
    {

    private static final String PLAYLIST_WELCOME_DISMISSED = "playlist_welcome_dismissed";
    private static final boolean PLAYLIST_WELCOME_DISMISSED_DEFAULT = false;

    private View mPlaylistContainer;
    private View mPlaylistWelcomeContainer;
    private View mPlaylistEmptyContainer;

    private TopPlayer mTopPlayer;
    private SimpleDraweeView mPhoto;

    private TextView mEpisodeTitle;
    private TextView mEpisodeInfo;
    private TextViewObserver mCurrentTime;
    private TextViewObserver mTotalTime;
    private PlayPauseImageView mPlayPauseButton;
    private PlayerSeekbar mPlayerSeekbar;
    private DownloadButtonView mPlayerDownloadButton;
    private PlayerButtonView mForwardButton;
    private PlayerButtonView mBackButton;
    private PlayerButtonView mFavoriteButton;

    private RecyclerView mRecyclerView;
    private View mOverlay;

	private SharedPreferences.OnSharedPreferenceChangeListener spChanged;

	/** ID of the current expanded episode */
	private long mExpandedEpisodeId = -1;
	private String mExpandedEpisodeKey = "currentExpanded";

	// ItemTouchHelperResources
    private Paint mSwipePaint = new Paint();
    private Bitmap mSwipeIcon;
    private int mSwipeBgColor = R.color.colorBgPrimaryDark;
    private int mSwipeIconID = R.drawable.ic_hearing_white;

    DownloadProgressObservable mDownloadProgressObservable = null;

    private Playlist mPlaylist;
    private Context mContext;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        mDownloadProgressObservable = new DownloadProgressObservable((SoundWaves) mContext.getApplicationContext());
        int color = getResources().getColor(mSwipeBgColor);
        mSwipePaint.setColor(color);
        mSwipeIcon = BitmapFactory.decodeResource(getResources(), mSwipeIconID);
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onDestroyView() {
        EpisodeDownloadManager.resetDownloadProgressObservable();
        SoundWaves.getBus().unregister(mAdapter);
        SoundWaves.getBus().unregister(mPlayPauseButton);
        SoundWaves.getBus().unregister(mPlayerSeekbar);
        SoundWaves.getBus().unregister(mCurrentTime);
        super.onDestroyView();
    }

	// Read here:
	// http://developer.android.com/reference/android/app/Fragment.html#Layout
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

        mDownloadProgressObservable = EpisodeDownloadManager.getDownloadProgressObservable((SoundWaves) mContext.getApplicationContext());

		TopActivity.getPreferences().registerOnSharedPreferenceChangeListener(
                spChanged);

		if (savedInstanceState != null) {
			// Restore last state for checked position.
			mExpandedEpisodeId = savedInstanceState.getLong(
					mExpandedEpisodeKey, mExpandedEpisodeId);
		}

	}

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        super.onViewCreated(view,savedInstanceState);

        mTopPlayer =   (TopPlayer) view.findViewById(R.id.session_photo_container);
        mPhoto =            (SimpleDraweeView) view.findViewById(R.id.session_photo);

        mPlaylistContainer = view.findViewById(R.id.playlist_container);
        mPlaylistWelcomeContainer = view.findViewById(R.id.playlist_welcome_screen);
        mPlaylistEmptyContainer = view.findViewById(R.id.playlist_empty);

        mEpisodeTitle         =    (TextView) view.findViewById(R.id.episode_title);
        mEpisodeInfo         =    (TextView) view.findViewById(R.id.episode_info);

        mCurrentTime       =    (TextViewObserver) view.findViewById(R.id.current_time);
        mTotalTime         =    (TextViewObserver) view.findViewById(R.id.total_time);

        mPlayPauseButton         =    (PlayPauseImageView) view.findViewById(R.id.play_pause_button);
        mPlayerSeekbar          =    (PlayerSeekbar) view.findViewById(R.id.player_progress);
        mPlayerDownloadButton   =    (DownloadButtonView) view.findViewById(R.id.download);
        mBackButton = (PlayerButtonView)view.findViewById(R.id.rewind_button);
        mForwardButton = (PlayerButtonView)view.findViewById(R.id.fast_forward_button);
        mFavoriteButton = (PlayerButtonView)view.findViewById(R.id.favorite);

        mRecyclerView = (RecyclerView) view.findViewById(R.id.my_recycler_view);
        mOverlay = view.findViewById(R.id.playlist_overlay);

        setPlaylistViewState(mPlaylist);

        // use a linear layout manager
        //mLayoutManager = new ExpandableLayoutManager(mContext, mSwipeRefreshView, mTopPlayer, mRecyclerView, mPhoto);
        mLayoutManager = new LinearLayoutManager(mContext);
        mRecyclerView.setLayoutManager(mLayoutManager);

        // specify an adapter (see also next example)
        mAdapter = new PlaylistAdapter(getActivity(), mOverlay, mDownloadProgressObservable);
        mAdapter.setHasStableIds(true);

        SoundWaves.getBus().register(mAdapter);
        SoundWaves.getBus().register(mPlayPauseButton);
        SoundWaves.getBus().register(mPlayerSeekbar);
        SoundWaves.getBus().register(mCurrentTime);

        mRecyclerView.setAdapter(mAdapter);

        //RecentItemsRecyclerListener l = new RecentItemsRecyclerListener(mAdapter);
        //mRecyclerView.setRecyclerListener(l);

        if (mPlaylist != null && !mPlaylist.isEmpty()) {
            IEpisode episode = mPlaylist.first();
            bindHeader(episode);
        }


        //mRecyclerView.addOnItemTouchListener(new RecyclerItemTouchListener());

        ///////

        /*
        SwipeDismissRecyclerViewTouchListener touchListener =
                new SwipeDismissRecyclerViewTouchListener(
                        mRecyclerView,
                        new SwipeDismissRecyclerViewTouchListener.DismissCallbacks() {
                            @Override
                            public boolean canDismiss(int position) {
                                return true;
                            }

                            @Override
                            public void onDismiss(RecyclerView recyclerView, int[] reverseSortedPositions) {

                                final int itemPosition = reverseSortedPositions[0]+1;
                                final IEpisode episode = mPlaylist.getItem(itemPosition);
                                final int currentPriority = episode.getPriority();
                                final ContentResolver contentResolver = getActivity().getContentResolver();

                                episode.setPriority(-1);

                                if (episode instanceof FeedItem) {
                                    FeedItem item = (FeedItem) episode;
                                    item.markAsListened();
                                }

                                episode.update(contentResolver);
                                mPlaylist.removeItem(itemPosition);
                                // do not call notifyItemRemoved for every item, it will cause gaps on deleting items
                                mAdapter.notifyDataSetChanged();
                                String episodeRemoved = getResources().getString(R.string.playlist_episode_dismissed);

                                Snackbar.make(view, episodeRemoved, Snackbar.LENGTH_LONG)
                                        .setAction(R.string.playlist_episode_dismissed_undo, new View.OnClickListener() {
                                            @Override
                                            public void onClick(View v) {
                                                episode.setPriority(currentPriority);
                                                mPlaylist.setItem(itemPosition, episode);
                                                mAdapter.notifyDataSetChanged();

                                                if (episode instanceof FeedItem) {
                                                    FeedItem item = (FeedItem) episode;
                                                    item.markAsListened(0);
                                                }

                                                episode.update(contentResolver);
                                            }
                                        }).show();

                                //snackbar.getView().setLa

                                  //      .show();
                            }
                        });
        mRecyclerView.setOnTouchListener(touchListener);
        */

        // init swipe to dismiss logic
        ItemTouchHelper swipeToDismissTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT, ItemTouchHelper.RIGHT) {

            @Override
            public void onChildDraw(Canvas c,
                                    RecyclerView recyclerView,
                                    RecyclerView.ViewHolder viewHolder,
                                    float dX,
                                    float dY,
                                    int actionState,
                                    boolean isCurrentlyActive) {

                if (actionState != ItemTouchHelper.ACTION_STATE_SWIPE)
                    return;

                // http://stackoverflow.com/questions/30820806/adding-a-colored-background-with-text-icon-under-swiped-row-when-using-androids
                View itemView = viewHolder.itemView;

                Paint p = new Paint();
                int color = getResources().getColor(R.color.colorBgPrimary);
                //p.setARGB(255, 0, 255, 0);
                p.setColor(color);

                c.drawRect((float) itemView.getLeft(), (float) itemView.getTop(), dX,
                        (float) itemView.getBottom(), mSwipePaint);

                int height2 = mSwipeIcon.getHeight()/2;
                int heightView = itemView.getHeight();
                int bitmapTopPos = heightView/2-height2+itemView.getTop();
                Paint p2 = new Paint();

                int bitmapLeftPos = (int)UIUtils.convertDpToPixel(25, getContext());

                c.drawBitmap(mSwipeIcon, bitmapLeftPos, bitmapTopPos, p2);

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);

            }

            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                // callback for drag-n-drop, false to skip this feature
                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                // callback for swipe to dismiss, removing item from data and adapter
                //items.remove(viewHolder.getAdapterPosition());

                final int itemPosition = viewHolder.getAdapterPosition()+1;
                final IEpisode episode = mPlaylist.getItem(itemPosition);
                final int currentPriority = episode.getPriority();
                final ContentResolver contentResolver = getActivity().getContentResolver();

                episode.setPriority(-1);

                if (episode instanceof FeedItem) {
                    FeedItem item = (FeedItem) episode;
                    item.markAsListened();
                }

                //String episodeRemoved = getResources().getString(R.string.playlist_episode_dismissed);
                Snackbar snack = Snackbar.make(view, R.string.playlist_episode_dismissed, Snackbar.LENGTH_LONG)
                        .setAction(R.string.playlist_episode_dismissed_undo, new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                episode.setPriority(currentPriority);
                                mPlaylist.setItem(itemPosition, episode);
                                mAdapter.notifyDataSetChanged();

                                if (episode instanceof FeedItem) {
                                    FeedItem item = (FeedItem) episode;
                                    item.markAsListened(0);
                                }

                                episode.update(contentResolver);
                            }
                        })
                        .setActionTextColor(getResources().getColor(R.color.white_opaque));

                View view = snack.getView();
                FrameLayout.LayoutParams params = (FrameLayout.LayoutParams)view.getLayoutParams();
                params.bottomMargin = DrawerActivity.getStatusBarHeight(getResources())*2;
                view.setLayoutParams(params);

                snack.show();

                mAdapter.notifyItemRemoved(viewHolder.getAdapterPosition());
            }
        });
        swipeToDismissTouchHelper.attachToRecyclerView(mRecyclerView);




        //mRecyclerView.setItemAnimator(new SlideInLeftAnimator());
    }

    @Override
    public void onAttach(Context context) {
        mContext = context;
        super.onAttach(context);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);

        view = inflater.inflate(R.layout.playlist_fragment_main, container, false);

        return view;
    }

    @Override
    public void onStart() {
        SoundWaves.getBus().register(this);
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
        SoundWaves.getBus().unregister(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        //SoundWaves.getBus().unregister(this);
        if (mPlaylist != null && mPlaylist.getItem(0) != null){
            SoundWaves.getBus().unregister(mPlayerDownloadButton);
        }
    }

    @Override
    public void onResume() {
        //SoundWaves.getBus().register(this);
        if (mPlaylist != null) {
            IEpisode item = mPlaylist.getItem(0);

            if (item != null) {
                mPlayPauseButton.setEpisode(item, PlayPauseImageView.LOCATION.PLAYLIST);
                mBackButton.setEpisode(item);
                mForwardButton.setEpisode(item);
                mPlayerDownloadButton.setEpisode(item);
                mFavoriteButton.setEpisode(item);
                SoundWaves.getBus().register(mPlayerDownloadButton);
            }
            playlistChanged(mPlaylist);
        }
        super.onResume();
    }


    public void bindHeader(final IEpisode item) {

        if (mEpisodeTitle == null)
            return;

        mEpisodeTitle.setText(item.getTitle());
        mEpisodeInfo.setText(item.getDescription());

        final int color =getResources().getColor(R.color.white_opaque);
        mEpisodeTitle.setTextColor(color);
        mEpisodeInfo.setTextColor(color);

        long duration = item.getDuration();
        if (duration > 0) {
            mTotalTime.setText(StrUtils.formatTime(duration));
        } else {
            mTotalTime.setText("");
        }

        long offset = item.getOffset();
        if (offset > 0) {
            mCurrentTime.setText(StrUtils.formatTime(offset));
        } else {
            mCurrentTime.setText("");
        }
        mCurrentTime.setEpisode(item);
        mTotalTime.setEpisode(item);

        mPlayPauseButton.setEpisode(item, PlayPauseImageView.LOCATION.PLAYLIST);
        mBackButton.setEpisode(item);
        mForwardButton.setEpisode(item);
        mPlayerDownloadButton.setEpisode(item);
        mFavoriteButton.setEpisode(item);

        if (SoundWaves.sBoundPlayerService != null &&
                SoundWaves.sBoundPlayerService.getCurrentItem() != null &&
                SoundWaves.sBoundPlayerService.getCurrentItem().equals(item) &&
                SoundWaves.sBoundPlayerService.isPlaying()) {
            mPlayPauseButton.setStatus(PlayerStatusObservable.STATUS.PLAYING);
        } else {
            mPlayPauseButton.setStatus(PlayerStatusObservable.STATUS.PAUSED);
        }
        //mDownloadProgressObservable.registerObserver(mDownloadButton);

        mBackButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (SoundWaves.sBoundPlayerService != null) {
                    SoundWaves.sBoundPlayerService.getPlayer().rewind(item);
                }
            }
        });

        mForwardButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (SoundWaves.sBoundPlayerService != null) {
                    SoundWaves.sBoundPlayerService.getPlayer().fastForward(item);
                }
            }
        });

        mPlayerSeekbar.setEpisode(item);
        mPlayerSeekbar.setOverlay(mOverlay);

        mPlayerDownloadButton.setEpisode(item);

        //mDownloadProgressObservable.registerObserver(mPlayerDownloadButton);

        final Activity activity = getActivity();

        //mTopPlayer.setEpisodeId(item);

        String artworkURL = item.getArtwork(activity);
        if (!TextUtils.isEmpty(artworkURL)) {
            PaletteHelper.generate(artworkURL, activity, mTopPlayer);
            PaletteHelper.generate(artworkURL, activity, mPlayPauseButton);
            PaletteHelper.generate(artworkURL, activity, mBackButton);
            PaletteHelper.generate(artworkURL, activity, mForwardButton);
            PaletteHelper.generate(artworkURL, activity, mPlayerDownloadButton);
            PaletteHelper.generate(artworkURL, activity, mFavoriteButton);

            PaletteHelper.generate(artworkURL, activity, new PaletteListener() {
                @Override
                public void onPaletteFound(Palette argChangedPalette) {
                    Palette.Swatch swatch = argChangedPalette.getMutedSwatch();

                    if (swatch == null)
                        return;

                    int colorText = swatch.getTitleTextColor();
                    mEpisodeTitle.setTextColor(color);
                    mEpisodeInfo.setTextColor(color);
                }

                @Override
                public String getPaletteUrl() {
                    return item.getArtwork(activity);
                }
            });
        }

        if (item != null && item.getArtwork(activity) != null) {
            Log.d("MissingImage", "Setting image");
            Uri uri = Uri.parse(item.getArtwork(activity));
            mPhoto.setImageURI(uri);
        }

        if (mTopPlayer.getVisibleHeight() == 0) {
            mTopPlayer.setPlayerHeight(mTopPlayer.getMaximumSize());
        }
    }

    private void setPlaylistViewState(@Nullable Playlist argPlaylist) {

        boolean dismissedWelcomePage = sharedPreferences.getBoolean(PLAYLIST_WELCOME_DISMISSED, PLAYLIST_WELCOME_DISMISSED_DEFAULT);

        if (argPlaylist == null || argPlaylist.isEmpty()) {
            mPlaylistContainer.setVisibility(View.GONE);

            if (dismissedWelcomePage) {
                mPlaylistEmptyContainer.setVisibility(View.VISIBLE);
                mPlaylistWelcomeContainer.setVisibility(View.GONE);
            } else {
                mPlaylistWelcomeContainer.setVisibility(View.VISIBLE);
                mPlaylistEmptyContainer.setVisibility(View.GONE);
            }
        } else {
            if (!dismissedWelcomePage) {
                sharedPreferences.edit().putBoolean(PLAYLIST_WELCOME_DISMISSED, true).commit();
            }

            if (mPlaylistContainer == null)
                return;

            mPlaylistContainer.setVisibility(View.VISIBLE);
            mPlaylistWelcomeContainer.setVisibility(View.GONE);
            mPlaylistEmptyContainer.setVisibility(View.GONE);
        }
    }

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
        outState.putLong(mExpandedEpisodeKey, mExpandedEpisodeId);
	}

    @Override
    public boolean onContextItemSelected(MenuItem item) {

        // Handle item selection
        switch (item.getItemId()) {
            case R.id.menu_playlist_context_play_next:
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }


     @Override
     public void onCreateContextMenu(ContextMenu menu, View v,
                                     ContextMenuInfo menuInfo) {
         super.onCreateContextMenu(menu, v, menuInfo);
         MenuInflater inflater = getActivity().getMenuInflater();
     }

     @Override
     public void onCreateOptionsMenu(final Menu menu, MenuInflater inflater) {
         inflater.inflate(R.menu.playlist_actionbar, menu);
         super.onCreateOptionsMenu(menu, inflater);
         return;
     }

     @Override
     public boolean onOptionsItemSelected(MenuItem item) {
         switch (item.getItemId()) {
             case R.id.menu_bulk_download: {
                 DialogBulkDownload dialogBulkDownload = new DialogBulkDownload();
                 Dialog dialog = dialogBulkDownload.onCreateDialog(getActivity(), mPlaylist);
                 dialog.show();
                 return true;
             }
             case R.id.action_filter_playlist: {
                 DialogPlaylistFilters dialogPlaylistFilters = DialogPlaylistFilters.newInstance();
                 dialogPlaylistFilters.show(getFragmentManager(), getTag());
                 return true;
             }
         }
         return super.onOptionsItemSelected(item);
     }

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		if (key.equals(ApplicationConfiguration.showListenedKey)) {
			//RecentItemFragment.this.getAdapter().notifyDataSetChanged();
		}
	}

    @Produce
    public Playlist producePlaylist() {
        Playlist playlist = mPlaylist;

        if (playlist != null)
            return playlist;

        PlayerService service = PlayerService.getInstance();
        if (service != null) {
            mPlaylist = service.getPlaylist();
        }

        return mPlaylist;
    }

    @Subscribe
    public void playlistChanged(@NonNull Playlist argPlaylist) {
        mPlaylist = argPlaylist;

        if (mPlaylistContainer == null)
            return;

        setPlaylistViewState(mPlaylist);

        if (!mPlaylist.isEmpty()) {
            IEpisode episode = mPlaylist.first();
            bindHeader(episode);
        }
    }

    }
