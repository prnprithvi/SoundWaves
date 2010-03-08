package info.xuluan.podcast;

import info.xuluan.podcast.provider.FeedItem;
import info.xuluan.podcast.provider.ItemColumns;
import info.xuluan.podcast.utils.IconCursorAdapter;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import android.app.AlertDialog;
import android.app.Service;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.ListView;

import java.util.HashMap;

public class MainActivity extends PodcastBaseActivity {

	private static final int MENU_REFRESH = Menu.FIRST + 1;
	private static final int MENU_SORT = Menu.FIRST + 2;
	private static final int MENU_DISPLAY = Menu.FIRST + 3;

	

	private static final int MENU_ITEM_START_DOWNLOAD = Menu.FIRST + 10;
	private static final int MENU_ITEM_START_PLAY = Menu.FIRST + 11;
	private static final String[] PROJECTION = new String[] { 
		 	ItemColumns._ID, // 0
			ItemColumns.TITLE, // 1
			ItemColumns.DURATION, 
			ItemColumns.SUB_TITLE, 
			ItemColumns.STATUS,
			ItemColumns.SUBS_ID,

	};

	private static HashMap<Integer, Integer> mIconMap;
	
	private long pref_order;
	private long pref_where;

	static {

		mIconMap = new HashMap<Integer, Integer>();
		mIconMap.put(ItemColumns.ITEM_STATUS_UNREAD, R.drawable.new_item);
		mIconMap.put(ItemColumns.ITEM_STATUS_READ, R.drawable.open_item);
		mIconMap.put(ItemColumns.ITEM_STATUS_DOWNLOAD_PAUSE, R.drawable.download);
		mIconMap.put(ItemColumns.ITEM_STATUS_DOWNLOAD_QUEUE, R.drawable.download);
		mIconMap.put(ItemColumns.ITEM_STATUS_DOWNLOADING_NOW, R.drawable.download);
		
		mIconMap.put(ItemColumns.ITEM_STATUS_NO_PLAY, R.drawable.music);
		mIconMap.put(ItemColumns.ITEM_STATUS_KEEP, R.drawable.music);
		mIconMap.put(ItemColumns.ITEM_STATUS_PLAYED, R.drawable.music);		

	}

	@Override
	public void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);

		setContentView(R.layout.main);
		setTitle(getResources().getString(R.string.title_read_list));

		getListView().setOnCreateContextMenuListener(this);
		Intent intent = getIntent();
		intent.setData(ItemColumns.URI);
		
		mPrevIntent = new Intent(this, SubsActivity.class);
		mNextIntent = new Intent(this, DownloadingActivity.class);
		
		getPref();

		startInit();

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(0, MENU_REFRESH, 0,
				getResources().getString(R.string.menu_update)).setIcon(
				android.R.drawable.ic_menu_rotate);
		menu.add(0, MENU_SORT, 1,
				getResources().getString(R.string.menu_sort)).setIcon(
				android.R.drawable.ic_menu_agenda);	
		menu.add(0, MENU_DISPLAY, 2,
				getResources().getString(R.string.menu_display)).setIcon(
				android.R.drawable.ic_menu_today);			
		return true;
	}
	
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        MenuItem item = menu.findItem(MENU_DISPLAY);
		String auto;
		if(pref_where==0){
			auto = "Only Undownload";
		}else{
			auto = "Display All";
		}        
        item.setTitle(auto);
        return true;
    }
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case MENU_REFRESH:
			mServiceBinder.start_update();
			return true;
		case MENU_SORT:
			 new AlertDialog.Builder(this)
             .setTitle("Chose Sort Mode")
             .setSingleChoiceItems(R.array.sort_select, (int) pref_order, new DialogInterface.OnClickListener() {
                 public void onClick(DialogInterface dialog, int select) {
      			
         			if(mCursor!=null)
         				mCursor.close();
         			
         			pref_order = select;
         			SharedPreferences prefsPrivate = getSharedPreferences("info.xuluan.podcast_preferences", Context.MODE_PRIVATE);
    				Editor prefsPrivateEditor = prefsPrivate.edit();
    				prefsPrivateEditor.putLong("pref_order", pref_order);
    				prefsPrivateEditor.commit();

         			mCursor = managedQuery(ItemColumns.URI, PROJECTION, getWhere(), null, getOrder());
         			mAdapter.changeCursor(mCursor);
         			//setListAdapter(mAdapter);         			
         			dialog.dismiss();

                 }
             })
            .show();
			return true;
		case MENU_DISPLAY:
 			if(mCursor!=null)
 				mCursor.close();
 			pref_where = 1- pref_where;

 			SharedPreferences prefsPrivate = getSharedPreferences("info.xuluan.podcast_preferences", Context.MODE_PRIVATE);
			Editor prefsPrivateEditor = prefsPrivate.edit();
			prefsPrivateEditor.putLong("pref_where", pref_where);
			prefsPrivateEditor.commit();
			
 			mCursor = managedQuery(ItemColumns.URI, PROJECTION,getWhere(), null, getOrder());
 			mAdapter.changeCursor(mCursor);
 			return true;			
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {

		Uri uri = ContentUris.withAppendedId(getIntent().getData(), id);
		String action = getIntent().getAction();
		if (Intent.ACTION_PICK.equals(action)
				|| Intent.ACTION_GET_CONTENT.equals(action)) {
			setResult(RESULT_OK, new Intent().setData(uri));
		} else {
			FeedItem item = FeedItem.getById(getContentResolver(), id);
			if ((item != null)
					&& (item.status == ItemColumns.ITEM_STATUS_UNREAD)) {
				item.status = ItemColumns.ITEM_STATUS_READ;
				item.update(getContentResolver());
			}

			startActivity(new Intent(Intent.ACTION_EDIT, uri));
		}
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View view,
			ContextMenuInfo menuInfo) {
		AdapterView.AdapterContextMenuInfo info;
		try {
			info = (AdapterView.AdapterContextMenuInfo) menuInfo;
		} catch (ClassCastException e) {
			log.error("bad menuInfo", e);
			return;
		}
		
		Cursor cursor = (Cursor) getListAdapter().getItem(info.position);
		if (cursor == null) {
			// For some reason the requested item isn't available, do nothing
			return;
		}
		
		
		FeedItem item = FeedItem.getById(getContentResolver(), cursor.getInt(0));
		if(item==null)
			return;
		// Setup the menu header
		menu.setHeaderTitle(item.title);
		if(item.status<ItemColumns.ITEM_STATUS_MAX_READING_VIEW){
			// Add a menu item to delete the note
			menu.add(0, MENU_ITEM_START_DOWNLOAD, 0, R.string.menu_download);		
		}else if(item.status>ItemColumns.ITEM_STATUS_MAX_DOWNLOADING_VIEW){
			menu.add(0, MENU_ITEM_START_PLAY, 0, R.string.menu_play);			
		}


	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterView.AdapterContextMenuInfo info;
		try {
			info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
		} catch (ClassCastException e) {
			log.error("bad menuInfo", e);
			return false;
		}

		switch (item.getItemId()) {
			case MENU_ITEM_START_DOWNLOAD: {
	
				FeedItem feeditem = FeedItem.getById(getContentResolver(), info.id);
				if (feeditem == null)
					return true;
	
				feeditem.status = ItemColumns.ITEM_STATUS_DOWNLOAD_QUEUE;
				feeditem.update(getContentResolver());
				mServiceBinder.start_download();
				return true;
			}
			case MENU_ITEM_START_PLAY: {
	
				FeedItem feeditem = FeedItem.getById(getContentResolver(), info.id);
				if (feeditem == null)
					return true;
		
				feeditem.play(this);
				return true;
			}		
		}
		return false;
	}

	@Override
	public void startInit() {

		mCursor = managedQuery(ItemColumns.URI, PROJECTION, getWhere(), null, getOrder());

		mAdapter = new IconCursorAdapter(this, R.layout.list_item, mCursor,
				new String[] { ItemColumns.TITLE, ItemColumns.SUB_TITLE,
						ItemColumns.DURATION, ItemColumns.STATUS }, new int[] {
						R.id.text1, R.id.text2, R.id.text3 }, mIconMap);
		setListAdapter(mAdapter);

		super.startInit();

	}
	public String getOrder() {
			String order = ItemColumns.CREATED + " DESC";
 			if(pref_order==0){
 				 order = ItemColumns.SUBS_ID +"," +order;
 			}else if(pref_order==1){
				 order = ItemColumns.STATUS +"," +order;
 			}
 			return order;
	}	

	public String getWhere() {
		String where = ItemColumns.STATUS + "<" + ItemColumns.ITEM_STATUS_MAX_PLAYLIST_VIEW;
			if(pref_where!=0){
				where =  ItemColumns.STATUS + "<" + ItemColumns.ITEM_STATUS_MAX_READING_VIEW;
			}
			return where;
}	
	
	public void getPref() {
		SharedPreferences pref = getSharedPreferences(
				"info.xuluan.podcast_preferences", Service.MODE_PRIVATE);
		pref_order = pref.getLong("pref_order",2);

		pref_where = pref.getLong("pref_where", 0);
	}
}
