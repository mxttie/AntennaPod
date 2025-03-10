package de.danoeh.antennapod.core.storage;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.MergeCursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;
import android.media.MediaMetadataRetriever;
import android.text.TextUtils;
import android.util.Log;

import org.apache.commons.lang3.Validate;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import de.danoeh.antennapod.core.R;
import de.danoeh.antennapod.core.event.ProgressEvent;
import de.danoeh.antennapod.core.feed.Chapter;
import de.danoeh.antennapod.core.feed.Feed;
import de.danoeh.antennapod.core.feed.FeedComponent;
import de.danoeh.antennapod.core.feed.FeedImage;
import de.danoeh.antennapod.core.feed.FeedItem;
import de.danoeh.antennapod.core.feed.FeedMedia;
import de.danoeh.antennapod.core.feed.FeedPreferences;
import de.danoeh.antennapod.core.preferences.UserPreferences;
import de.danoeh.antennapod.core.service.download.DownloadStatus;
import de.danoeh.antennapod.core.util.LongIntMap;
import de.danoeh.antennapod.core.util.flattr.FlattrStatus;
import de.greenrobot.event.EventBus;

// TODO Remove media column from feeditem table

/**
 * Implements methods for accessing the database
 */
public class PodDBAdapter implements AutoCloseable {
    
    private static final String TAG = "PodDBAdapter";
    public static final String DATABASE_NAME = "Antennapod.db";

    /**
     * Maximum number of arguments for IN-operator.
     */
    public static final int IN_OPERATOR_MAXIMUM = 800;

    /**
     * Maximum number of entries per search request.
     */
    public static final int SEARCH_LIMIT = 30;

    // Key-constants
    public static final String KEY_ID = "id";
    public static final String KEY_TITLE = "title";
    public static final String KEY_NAME = "name";
    public static final String KEY_LINK = "link";
    public static final String KEY_DESCRIPTION = "description";
    public static final String KEY_FILE_URL = "file_url";
    public static final String KEY_DOWNLOAD_URL = "download_url";
    public static final String KEY_PUBDATE = "pubDate";
    public static final String KEY_READ = "read";
    public static final String KEY_DURATION = "duration";
    public static final String KEY_POSITION = "position";
    public static final String KEY_SIZE = "filesize";
    public static final String KEY_MIME_TYPE = "mime_type";
    public static final String KEY_IMAGE = "image";
    public static final String KEY_FEED = "feed";
    public static final String KEY_MEDIA = "media";
    public static final String KEY_DOWNLOADED = "downloaded";
    public static final String KEY_LASTUPDATE = "last_update";
    public static final String KEY_FEEDFILE = "feedfile";
    public static final String KEY_REASON = "reason";
    public static final String KEY_SUCCESSFUL = "successful";
    public static final String KEY_FEEDFILETYPE = "feedfile_type";
    public static final String KEY_COMPLETION_DATE = "completion_date";
    public static final String KEY_FEEDITEM = "feeditem";
    public static final String KEY_CONTENT_ENCODED = "content_encoded";
    public static final String KEY_PAYMENT_LINK = "payment_link";
    public static final String KEY_START = "start";
    public static final String KEY_LANGUAGE = "language";
    public static final String KEY_AUTHOR = "author";
    public static final String KEY_HAS_CHAPTERS = "has_simple_chapters";
    public static final String KEY_TYPE = "type";
    public static final String KEY_ITEM_IDENTIFIER = "item_identifier";
    public static final String KEY_FLATTR_STATUS = "flattr_status";
    public static final String KEY_FEED_IDENTIFIER = "feed_identifier";
    public static final String KEY_REASON_DETAILED = "reason_detailed";
    public static final String KEY_DOWNLOADSTATUS_TITLE = "title";
    public static final String KEY_CHAPTER_TYPE = "type";
    public static final String KEY_PLAYBACK_COMPLETION_DATE = "playback_completion_date";
    public static final String KEY_AUTO_DOWNLOAD = "auto_download";
    public static final String KEY_AUTO_DELETE_ACTION = "auto_delete_action";
    public static final String KEY_PLAYED_DURATION = "played_duration";
    public static final String KEY_USERNAME = "username";
    public static final String KEY_PASSWORD = "password";
    public static final String KEY_IS_PAGED = "is_paged";
    public static final String KEY_NEXT_PAGE_LINK = "next_page_link";
    public static final String KEY_HIDE = "hide";
    public static final String KEY_LAST_UPDATE_FAILED = "last_update_failed";
    public static final String KEY_HAS_EMBEDDED_PICTURE = "has_embedded_picture";


    // Table names
    public static final String TABLE_NAME_FEEDS = "Feeds";
    public static final String TABLE_NAME_FEED_ITEMS = "FeedItems";
    public static final String TABLE_NAME_FEED_IMAGES = "FeedImages";
    public static final String TABLE_NAME_FEED_MEDIA = "FeedMedia";
    public static final String TABLE_NAME_DOWNLOAD_LOG = "DownloadLog";
    public static final String TABLE_NAME_QUEUE = "Queue";
    public static final String TABLE_NAME_SIMPLECHAPTERS = "SimpleChapters";
    public static final String TABLE_NAME_FAVORITES = "Favorites";

    // SQL Statements for creating new tables
    private static final String TABLE_PRIMARY_KEY = KEY_ID
            + " INTEGER PRIMARY KEY AUTOINCREMENT ,";

    public static final String CREATE_TABLE_FEEDS = "CREATE TABLE "
            + TABLE_NAME_FEEDS + " (" + TABLE_PRIMARY_KEY + KEY_TITLE
            + " TEXT," + KEY_FILE_URL + " TEXT," + KEY_DOWNLOAD_URL + " TEXT,"
            + KEY_DOWNLOADED + " INTEGER," + KEY_LINK + " TEXT,"
            + KEY_DESCRIPTION + " TEXT," + KEY_PAYMENT_LINK + " TEXT,"
            + KEY_LASTUPDATE + " TEXT," + KEY_LANGUAGE + " TEXT," + KEY_AUTHOR
            + " TEXT," + KEY_IMAGE + " INTEGER," + KEY_TYPE + " TEXT,"
            + KEY_FEED_IDENTIFIER + " TEXT," + KEY_AUTO_DOWNLOAD + " INTEGER DEFAULT 1,"
            + KEY_FLATTR_STATUS + " INTEGER,"
            + KEY_USERNAME + " TEXT,"
            + KEY_PASSWORD + " TEXT,"
            + KEY_IS_PAGED + " INTEGER DEFAULT 0,"
            + KEY_NEXT_PAGE_LINK + " TEXT,"
            + KEY_HIDE + " TEXT,"
            + KEY_LAST_UPDATE_FAILED + " INTEGER DEFAULT 0,"
            + KEY_AUTO_DELETE_ACTION + " INTEGER DEFAULT 0)";

    public static final String CREATE_TABLE_FEED_ITEMS = "CREATE TABLE "
            + TABLE_NAME_FEED_ITEMS + " (" + TABLE_PRIMARY_KEY + KEY_TITLE
            + " TEXT," + KEY_CONTENT_ENCODED + " TEXT," + KEY_PUBDATE
            + " INTEGER," + KEY_READ + " INTEGER," + KEY_LINK + " TEXT,"
            + KEY_DESCRIPTION + " TEXT," + KEY_PAYMENT_LINK + " TEXT,"
            + KEY_MEDIA + " INTEGER," + KEY_FEED + " INTEGER,"
            + KEY_HAS_CHAPTERS + " INTEGER," + KEY_ITEM_IDENTIFIER + " TEXT,"
            + KEY_FLATTR_STATUS + " INTEGER,"
            + KEY_IMAGE + " INTEGER,"
            + KEY_AUTO_DOWNLOAD + " INTEGER)";

    public static final String CREATE_TABLE_FEED_IMAGES = "CREATE TABLE "
            + TABLE_NAME_FEED_IMAGES + " (" + TABLE_PRIMARY_KEY + KEY_TITLE
            + " TEXT," + KEY_FILE_URL + " TEXT," + KEY_DOWNLOAD_URL + " TEXT,"
            + KEY_DOWNLOADED + " INTEGER)";

    public static final String CREATE_TABLE_FEED_MEDIA = "CREATE TABLE "
            + TABLE_NAME_FEED_MEDIA + " (" + TABLE_PRIMARY_KEY + KEY_DURATION
            + " INTEGER," + KEY_FILE_URL + " TEXT," + KEY_DOWNLOAD_URL
            + " TEXT," + KEY_DOWNLOADED + " INTEGER," + KEY_POSITION
            + " INTEGER," + KEY_SIZE + " INTEGER," + KEY_MIME_TYPE + " TEXT,"
            + KEY_PLAYBACK_COMPLETION_DATE + " INTEGER,"
            + KEY_FEEDITEM + " INTEGER,"
            + KEY_PLAYED_DURATION + " INTEGER,"
            + KEY_HAS_EMBEDDED_PICTURE + " INTEGER)";

    public static final String CREATE_TABLE_DOWNLOAD_LOG = "CREATE TABLE "
            + TABLE_NAME_DOWNLOAD_LOG + " (" + TABLE_PRIMARY_KEY + KEY_FEEDFILE
            + " INTEGER," + KEY_FEEDFILETYPE + " INTEGER," + KEY_REASON
            + " INTEGER," + KEY_SUCCESSFUL + " INTEGER," + KEY_COMPLETION_DATE
            + " INTEGER," + KEY_REASON_DETAILED + " TEXT,"
            + KEY_DOWNLOADSTATUS_TITLE + " TEXT)";

    public static final String CREATE_TABLE_QUEUE = "CREATE TABLE "
            + TABLE_NAME_QUEUE + "(" + KEY_ID + " INTEGER PRIMARY KEY,"
            + KEY_FEEDITEM + " INTEGER," + KEY_FEED + " INTEGER)";

    public static final String CREATE_TABLE_SIMPLECHAPTERS = "CREATE TABLE "
            + TABLE_NAME_SIMPLECHAPTERS + " (" + TABLE_PRIMARY_KEY + KEY_TITLE
            + " TEXT," + KEY_START + " INTEGER," + KEY_FEEDITEM + " INTEGER,"
            + KEY_LINK + " TEXT," + KEY_CHAPTER_TYPE + " INTEGER)";

    // SQL Statements for creating indexes
    public static final String CREATE_INDEX_FEEDITEMS_FEED = "CREATE INDEX "
            + TABLE_NAME_FEED_ITEMS + "_" + KEY_FEED + " ON " + TABLE_NAME_FEED_ITEMS + " ("
            + KEY_FEED + ")";

    public static final String CREATE_INDEX_FEEDITEMS_IMAGE = "CREATE INDEX "
            + TABLE_NAME_FEED_ITEMS + "_" + KEY_IMAGE + " ON " + TABLE_NAME_FEED_ITEMS + " ("
            + KEY_IMAGE + ")";

    public static final String CREATE_INDEX_QUEUE_FEEDITEM = "CREATE INDEX "
            + TABLE_NAME_QUEUE + "_" + KEY_FEEDITEM + " ON " + TABLE_NAME_QUEUE + " ("
            + KEY_FEEDITEM + ")";

    public static final String CREATE_INDEX_FEEDMEDIA_FEEDITEM = "CREATE INDEX "
            + TABLE_NAME_FEED_MEDIA + "_" + KEY_FEEDITEM + " ON " + TABLE_NAME_FEED_MEDIA + " ("
            + KEY_FEEDITEM + ")";

    public static final String CREATE_INDEX_SIMPLECHAPTERS_FEEDITEM = "CREATE INDEX "
            + TABLE_NAME_SIMPLECHAPTERS + "_" + KEY_FEEDITEM + " ON " + TABLE_NAME_SIMPLECHAPTERS + " ("
            + KEY_FEEDITEM + ")";

    public static final String CREATE_TABLE_FAVORITES = "CREATE TABLE "
            + TABLE_NAME_FAVORITES + "(" + KEY_ID + " INTEGER PRIMARY KEY,"
            + KEY_FEEDITEM + " INTEGER," + KEY_FEED + " INTEGER)";
    
    /**
     * Select all columns from the feed-table
     */
    private static final String[] FEED_SEL_STD = {
            TABLE_NAME_FEEDS + "." + KEY_ID,
            TABLE_NAME_FEEDS + "." + KEY_TITLE,
            TABLE_NAME_FEEDS + "." + KEY_FILE_URL,
            TABLE_NAME_FEEDS + "." + KEY_DOWNLOAD_URL,
            TABLE_NAME_FEEDS + "." + KEY_DOWNLOADED,
            TABLE_NAME_FEEDS + "." + KEY_LINK,
            TABLE_NAME_FEEDS + "." + KEY_DESCRIPTION,
            TABLE_NAME_FEEDS + "." + KEY_PAYMENT_LINK,
            TABLE_NAME_FEEDS + "." + KEY_LASTUPDATE,
            TABLE_NAME_FEEDS + "." + KEY_LANGUAGE,
            TABLE_NAME_FEEDS + "." + KEY_AUTHOR,
            TABLE_NAME_FEEDS + "." + KEY_IMAGE,
            TABLE_NAME_FEEDS + "." + KEY_TYPE,
            TABLE_NAME_FEEDS + "." + KEY_FEED_IDENTIFIER,
            TABLE_NAME_FEEDS + "." + KEY_AUTO_DOWNLOAD,
            TABLE_NAME_FEEDS + "." + KEY_FLATTR_STATUS,
            TABLE_NAME_FEEDS + "." + KEY_IS_PAGED,
            TABLE_NAME_FEEDS + "." + KEY_NEXT_PAGE_LINK,
            TABLE_NAME_FEEDS + "." + KEY_USERNAME,
            TABLE_NAME_FEEDS + "." + KEY_PASSWORD,
            TABLE_NAME_FEEDS + "." + KEY_HIDE,
            TABLE_NAME_FEEDS + "." + KEY_LAST_UPDATE_FAILED,
            TABLE_NAME_FEEDS + "." + KEY_AUTO_DELETE_ACTION,
    };
    
    /**
     * Select all columns from the feeditems-table except description and
     * content-encoded.
     */
    private static final String[] FEEDITEM_SEL_FI_SMALL = {
            TABLE_NAME_FEED_ITEMS + "." + KEY_ID,
            TABLE_NAME_FEED_ITEMS + "." + KEY_TITLE,
            TABLE_NAME_FEED_ITEMS + "." + KEY_PUBDATE,
            TABLE_NAME_FEED_ITEMS + "." + KEY_READ,
            TABLE_NAME_FEED_ITEMS + "." + KEY_LINK,
            TABLE_NAME_FEED_ITEMS + "." + KEY_PAYMENT_LINK,
            TABLE_NAME_FEED_ITEMS + "." + KEY_MEDIA,
            TABLE_NAME_FEED_ITEMS + "." + KEY_FEED,
            TABLE_NAME_FEED_ITEMS + "." + KEY_HAS_CHAPTERS,
            TABLE_NAME_FEED_ITEMS + "." + KEY_ITEM_IDENTIFIER,
            TABLE_NAME_FEED_ITEMS + "." + KEY_FLATTR_STATUS,
            TABLE_NAME_FEED_ITEMS + "." + KEY_IMAGE,
            TABLE_NAME_FEED_ITEMS + "." + KEY_AUTO_DOWNLOAD
    };

    /**
     * Contains FEEDITEM_SEL_FI_SMALL as comma-separated list. Useful for raw queries.
     */
    private static final String SEL_FI_SMALL_STR;

    static {
        String selFiSmall = Arrays.toString(FEEDITEM_SEL_FI_SMALL);
        SEL_FI_SMALL_STR = selFiSmall.substring(1, selFiSmall.length() - 1);
    }

    /**
     * Select id, description and content-encoded column from feeditems.
     */
    private static final String[] SEL_FI_EXTRA = {KEY_ID, KEY_DESCRIPTION,
            KEY_CONTENT_ENCODED, KEY_FEED};


    private SQLiteDatabase db;
    private static Context context;
    private static PodDBHelper dbHelper;
    private static AtomicInteger counter = new AtomicInteger(0);

    public static void init(Context context) {
        PodDBAdapter.context = context.getApplicationContext();
    }

    public static synchronized PodDBAdapter getInstance() {
        if(dbHelper == null) {
            dbHelper = new PodDBHelper(PodDBAdapter.context, DATABASE_NAME, null);
        }
        return new PodDBAdapter();
    }

    private PodDBAdapter() {}

    public PodDBAdapter open() {
        counter.incrementAndGet();
        if (db == null || !db.isOpen() || db.isReadOnly()) {
            Log.v(TAG, "Opening DB");
            try {
                db = dbHelper.getWritableDatabase();
            } catch (SQLException ex) {
                Log.e(TAG, Log.getStackTraceString(ex));
                db = dbHelper.getReadableDatabase();
            }
        }
        return this;
    }

    public void close() {
        if(counter.decrementAndGet() == 0) {
            Log.v(TAG, "Closing DB");
            db.close();
        }
        db = null;
    }

    public static boolean deleteDatabase() {
        if(dbHelper != null) {
            dbHelper.close();
            dbHelper = null;
        }
        if(context != null) { // may not have been initialized
            return context.deleteDatabase(PodDBAdapter.DATABASE_NAME);
        } else {
            return false;
        }
    }

    /**
     * Inserts or updates a feed entry
     *
     * @return the id of the entry
     */
    public long setFeed(Feed feed) {
        ContentValues values = new ContentValues();
        values.put(KEY_TITLE, feed.getTitle());
        values.put(KEY_LINK, feed.getLink());
        values.put(KEY_DESCRIPTION, feed.getDescription());
        values.put(KEY_PAYMENT_LINK, feed.getPaymentLink());
        values.put(KEY_AUTHOR, feed.getAuthor());
        values.put(KEY_LANGUAGE, feed.getLanguage());
        if (feed.getImage() != null) {
            if (feed.getImage().getId() == 0) {
                setImage(feed.getImage());
            }
            values.put(KEY_IMAGE, feed.getImage().getId());
        }

        values.put(KEY_FILE_URL, feed.getFile_url());
        values.put(KEY_DOWNLOAD_URL, feed.getDownload_url());
        values.put(KEY_DOWNLOADED, feed.isDownloaded());
        values.put(KEY_LASTUPDATE, feed.getLastUpdate().getTime());
        values.put(KEY_TYPE, feed.getType());
        values.put(KEY_FEED_IDENTIFIER, feed.getFeedIdentifier());

        Log.d(TAG, "Setting feed with flattr status " + feed.getTitle() + ": " + feed.getFlattrStatus().toLong());

        values.put(KEY_FLATTR_STATUS, feed.getFlattrStatus().toLong());
        values.put(KEY_IS_PAGED, feed.isPaged());
        values.put(KEY_NEXT_PAGE_LINK, feed.getNextPageLink());
        if(feed.getItemFilter() != null && feed.getItemFilter().getValues().length > 0) {
            values.put(KEY_HIDE, TextUtils.join( ",", feed.getItemFilter().getValues()));
        } else {
            values.put(KEY_HIDE, "");
        }
        values.put(KEY_LAST_UPDATE_FAILED, feed.hasLastUpdateFailed());
        if (feed.getId() == 0) {
            // Create new entry
            Log.d(this.toString(), "Inserting new Feed into db");
            feed.setId(db.insert(TABLE_NAME_FEEDS, null, values));
        } else {
            Log.d(this.toString(), "Updating existing Feed in db");
            db.update(TABLE_NAME_FEEDS, values, KEY_ID + "=?",
                    new String[]{String.valueOf(feed.getId())});
        }
        return feed.getId();
    }

    public void setFeedPreferences(FeedPreferences prefs) {
        if (prefs.getFeedID() == 0) {
            throw new IllegalArgumentException("Feed ID of preference must not be null");
        }
        ContentValues values = new ContentValues();
        values.put(KEY_AUTO_DOWNLOAD, prefs.getAutoDownload());
        values.put(KEY_AUTO_DELETE_ACTION,prefs.getAutoDeleteAction().ordinal());
        values.put(KEY_USERNAME, prefs.getUsername());
        values.put(KEY_PASSWORD, prefs.getPassword());
        db.update(TABLE_NAME_FEEDS, values, KEY_ID + "=?", new String[]{String.valueOf(prefs.getFeedID())});
    }

    public void setFeedItemFilter(long feedId, List<String> filterValues) {
        ContentValues values = new ContentValues();
        values.put(KEY_HIDE, TextUtils.join(",", filterValues));
        Log.d(TAG, TextUtils.join(",", filterValues));
        db.update(TABLE_NAME_FEEDS, values, KEY_ID + "=?", new String[]{String.valueOf(feedId)});
    }

    /**
     * Inserts or updates an image entry
     *
     * @return the id of the entry
     */
    public long setImage(FeedImage image) {
        boolean startedTransaction = false;
        if(false == db.inTransaction()) {
            db.beginTransaction();
            startedTransaction = true;
        }

        ContentValues values = new ContentValues();
        values.put(KEY_TITLE, image.getTitle());
        values.put(KEY_DOWNLOAD_URL, image.getDownload_url());
        values.put(KEY_DOWNLOADED, image.isDownloaded());
        values.put(KEY_FILE_URL, image.getFile_url());
        if (image.getId() == 0) {
            image.setId(db.insert(TABLE_NAME_FEED_IMAGES, null, values));
        } else {
            db.update(TABLE_NAME_FEED_IMAGES, values, KEY_ID + "=?",
                    new String[]{String.valueOf(image.getId())});
        }

        final FeedComponent owner = image.getOwner();
        if (owner != null && owner.getId() != 0) {
            values.clear();
            values.put(KEY_IMAGE, image.getId());
            if (owner instanceof Feed) {
                db.update(TABLE_NAME_FEEDS, values, KEY_ID + "=?", new String[]{String.valueOf(image.getOwner().getId())});
            }
        }
        if(startedTransaction) {
            db.setTransactionSuccessful();
            db.endTransaction();
        }
        return image.getId();
    }

    /**
     * Inserts or updates an image entry
     *
     * @return the id of the entry
     */
    public long setMedia(FeedMedia media) {
        ContentValues values = new ContentValues();
        values.put(KEY_DURATION, media.getDuration());
        values.put(KEY_POSITION, media.getPosition());
        values.put(KEY_SIZE, media.getSize());
        values.put(KEY_MIME_TYPE, media.getMime_type());
        values.put(KEY_DOWNLOAD_URL, media.getDownload_url());
        values.put(KEY_DOWNLOADED, media.isDownloaded());
        values.put(KEY_FILE_URL, media.getFile_url());
        values.put(KEY_HAS_EMBEDDED_PICTURE, media.hasEmbeddedPicture());

        if (media.getPlaybackCompletionDate() != null) {
            values.put(KEY_PLAYBACK_COMPLETION_DATE, media.getPlaybackCompletionDate().getTime());
        } else {
            values.put(KEY_PLAYBACK_COMPLETION_DATE, 0);
        }
        if (media.getItem() != null) {
            values.put(KEY_FEEDITEM, media.getItem().getId());
        }
        if (media.getId() == 0) {
            media.setId(db.insert(TABLE_NAME_FEED_MEDIA, null, values));
        } else {
            db.update(TABLE_NAME_FEED_MEDIA, values, KEY_ID + "=?",
                    new String[]{String.valueOf(media.getId())});
        }
        return media.getId();
    }

    public void setFeedMediaPlaybackInformation(FeedMedia media) {
        if (media.getId() != 0) {
            ContentValues values = new ContentValues();
            values.put(KEY_POSITION, media.getPosition());
            values.put(KEY_DURATION, media.getDuration());
            values.put(KEY_PLAYED_DURATION, media.getPlayedDuration());
            db.update(TABLE_NAME_FEED_MEDIA, values, KEY_ID + "=?",
                    new String[]{String.valueOf(media.getId())});
        } else {
            Log.e(TAG, "setFeedMediaPlaybackInformation: ID of media was 0");
        }
    }

    public void setFeedMediaPlaybackCompletionDate(FeedMedia media) {
        if (media.getId() != 0) {
            ContentValues values = new ContentValues();
            values.put(KEY_PLAYBACK_COMPLETION_DATE, media.getPlaybackCompletionDate().getTime());
            values.put(KEY_PLAYED_DURATION, media.getPlayedDuration());
            db.update(TABLE_NAME_FEED_MEDIA, values, KEY_ID + "=?",
                    new String[]{String.valueOf(media.getId())});
        } else {
            Log.e(TAG, "setFeedMediaPlaybackCompletionDate: ID of media was 0");
        }
    }

    /**
     * Insert all FeedItems of a feed and the feed object itself in a single
     * transaction
     */
    public void setCompleteFeed(Feed... feeds) {
        db.beginTransaction();
        for (Feed feed : feeds) {
            setFeed(feed);
            if (feed.getItems() != null) {
                for (FeedItem item : feed.getItems()) {
                    setFeedItem(item, false);
                }
            }
            if (feed.getPreferences() != null) {
                setFeedPreferences(feed.getPreferences());
            }
        }
        db.setTransactionSuccessful();
        db.endTransaction();
    }

    /**
     * Update the flattr status of a feed
     */
    public void setFeedFlattrStatus(Feed feed) {
        ContentValues values = new ContentValues();
        values.put(KEY_FLATTR_STATUS, feed.getFlattrStatus().toLong());
        db.update(TABLE_NAME_FEEDS, values, KEY_ID + "=?", new String[]{String.valueOf(feed.getId())});
    }

    /**
     * Get all feeds in the flattr queue.
     */
    public Cursor getFeedsInFlattrQueueCursor() {
        return db.query(TABLE_NAME_FEEDS, FEED_SEL_STD, KEY_FLATTR_STATUS + "=?",
                new String[]{String.valueOf(FlattrStatus.STATUS_QUEUE)}, null, null, null);
    }

    /**
     * Get all feed items in the flattr queue.
     */
    public Cursor getFeedItemsInFlattrQueueCursor() {
        return db.query(TABLE_NAME_FEED_ITEMS, FEEDITEM_SEL_FI_SMALL, KEY_FLATTR_STATUS + "=?",
                new String[]{String.valueOf(FlattrStatus.STATUS_QUEUE)}, null, null, null);
    }

    /**
     * Counts feeds and feed items in the flattr queue
     */
    public int getFlattrQueueSize() {
        int res = 0;
        Cursor c = db.rawQuery(String.format("SELECT count(*) FROM %s WHERE %s=%s",
                TABLE_NAME_FEEDS, KEY_FLATTR_STATUS, String.valueOf(FlattrStatus.STATUS_QUEUE)), null);
        if (c.moveToFirst()) {
            res = c.getInt(0);
            c.close();
        } else {
            Log.e(TAG, "Unable to determine size of flattr queue: Could not count number of feeds");
        }
        c = db.rawQuery(String.format("SELECT count(*) FROM %s WHERE %s=%s",
                TABLE_NAME_FEED_ITEMS, KEY_FLATTR_STATUS, String.valueOf(FlattrStatus.STATUS_QUEUE)), null);
        if (c.moveToFirst()) {
            res += c.getInt(0);
            c.close();
        } else {
            Log.e(TAG, "Unable to determine size of flattr queue: Could not count number of feed items");
        }

        return res;
    }

    /**
     * Updates the download URL of a Feed.
     */
    public void setFeedDownloadUrl(String original, String updated) {
        ContentValues values = new ContentValues();
        values.put(KEY_DOWNLOAD_URL, updated);
        db.update(TABLE_NAME_FEEDS, values, KEY_DOWNLOAD_URL + "=?", new String[]{original});
    }

    public void setFeedItemlist(List<FeedItem> items) {
        db.beginTransaction();
        for (FeedItem item : items) {
            setFeedItem(item, true);
        }
        db.setTransactionSuccessful();
        db.endTransaction();
    }

    public long setSingleFeedItem(FeedItem item) {
        db.beginTransaction();
        long result = setFeedItem(item, true);
        db.setTransactionSuccessful();
        db.endTransaction();
        return result;
    }

    /**
     * Update the flattr status of a FeedItem
     */
    public void setFeedItemFlattrStatus(FeedItem feedItem) {
        ContentValues values = new ContentValues();
        values.put(KEY_FLATTR_STATUS, feedItem.getFlattrStatus().toLong());
        db.update(TABLE_NAME_FEED_ITEMS, values, KEY_ID + "=?", new String[]{String.valueOf(feedItem.getId())});
    }

    /**
     * Update the flattr status of a feed or feed item specified by its payment link
     * and the new flattr status to use
     */
    public void setItemFlattrStatus(String url, FlattrStatus status) {
        //Log.d(TAG, "setItemFlattrStatus(" + url + ") = " + status.toString());
        ContentValues values = new ContentValues();
        values.put(KEY_FLATTR_STATUS, status.toLong());

        // regexps in sqlite would be neat!
        String[] query_urls = new String[]{
                "*" + url + "&*",
                "*" + url + "%2F&*",
                "*" + url + "",
                "*" + url + "%2F"
        };

        if (db.update(TABLE_NAME_FEEDS, values,
                KEY_PAYMENT_LINK + " GLOB ?"
                        + " OR " + KEY_PAYMENT_LINK + " GLOB ?"
                        + " OR " + KEY_PAYMENT_LINK + " GLOB ?"
                        + " OR " + KEY_PAYMENT_LINK + " GLOB ?", query_urls
        ) > 0) {
            Log.i(TAG, "setItemFlattrStatus found match for " + url + " = " + status.toLong() + " in Feeds table");
            return;
        }
        if (db.update(TABLE_NAME_FEED_ITEMS, values,
                KEY_PAYMENT_LINK + " GLOB ?"
                        + " OR " + KEY_PAYMENT_LINK + " GLOB ?"
                        + " OR " + KEY_PAYMENT_LINK + " GLOB ?"
                        + " OR " + KEY_PAYMENT_LINK + " GLOB ?", query_urls
        ) > 0) {
            Log.i(TAG, "setItemFlattrStatus found match for " + url + " = " + status.toLong() + " in FeedsItems table");
        }
    }

    /**
     * Reset flattr status to unflattrd for all items
     */
    public void clearAllFlattrStatus() {
        ContentValues values = new ContentValues();
        values.put(KEY_FLATTR_STATUS, 0);
        db.update(TABLE_NAME_FEEDS, values, null, null);
        db.update(TABLE_NAME_FEED_ITEMS, values, null, null);
    }

    /**
     * Inserts or updates a feeditem entry
     *
     * @param item     The FeedItem
     * @param saveFeed true if the Feed of the item should also be saved. This should be set to
     *                 false if the method is executed on a list of FeedItems of the same Feed.
     * @return the id of the entry
     */
    private long setFeedItem(FeedItem item, boolean saveFeed) {
        ContentValues values = new ContentValues();
        values.put(KEY_TITLE, item.getTitle());
        values.put(KEY_LINK, item.getLink());
        if (item.getDescription() != null) {
            values.put(KEY_DESCRIPTION, item.getDescription());
        }
        if (item.getContentEncoded() != null) {
            values.put(KEY_CONTENT_ENCODED, item.getContentEncoded());
        }
        values.put(KEY_PUBDATE, item.getPubDate().getTime());
        values.put(KEY_PAYMENT_LINK, item.getPaymentLink());
        if (saveFeed && item.getFeed() != null) {
            setFeed(item.getFeed());
        }
        values.put(KEY_FEED, item.getFeed().getId());
        if(item.isNew()) {
            values.put(KEY_READ, FeedItem.NEW);
        } else if(item.isPlayed()) {
            values.put(KEY_READ, FeedItem.PLAYED);
        } else {
            values.put(KEY_READ, FeedItem.UNPLAYED);
        }
        values.put(KEY_HAS_CHAPTERS, item.getChapters() != null || item.hasChapters());
        values.put(KEY_ITEM_IDENTIFIER, item.getItemIdentifier());
        values.put(KEY_FLATTR_STATUS, item.getFlattrStatus().toLong());
        values.put(KEY_AUTO_DOWNLOAD, item.getAutoDownload());
        if (item.hasItemImage()) {
            if (item.getImage().getId() == 0) {
                setImage(item.getImage());
            }
            values.put(KEY_IMAGE, item.getImage().getId());
        }

        if (item.getId() == 0) {
            item.setId(db.insert(TABLE_NAME_FEED_ITEMS, null, values));
        } else {
            db.update(TABLE_NAME_FEED_ITEMS, values, KEY_ID + "=?",
                    new String[]{String.valueOf(item.getId())});
        }
        if (item.getMedia() != null) {
            setMedia(item.getMedia());
        }
        if (item.getChapters() != null) {
            setChapters(item);
        }
        return item.getId();
    }

    public void setFeedItemRead(int played, long itemId, long mediaId,
                                boolean resetMediaPosition) {
        db.beginTransaction();
        ContentValues values = new ContentValues();

        values.put(KEY_READ, played);
        db.update(TABLE_NAME_FEED_ITEMS, values, KEY_ID + "=?", new String[]{String.valueOf(itemId)});

        if (resetMediaPosition) {
            values.clear();
            values.put(KEY_POSITION, 0);
            db.update(TABLE_NAME_FEED_MEDIA, values, KEY_ID + "=?", new String[]{String.valueOf(mediaId)});
        }

        db.setTransactionSuccessful();
        db.endTransaction();
    }

    /**
     * Sets the 'read' attribute of the item.
     * @param read must be one of FeedItem.PLAYED, FeedItem.NEW, FeedItem.UNPLAYED
     * @param itemIds items to change the value of
     */
    public void setFeedItemRead(int read, long... itemIds) {
        db.beginTransaction();
        ContentValues values = new ContentValues();
        for (long id : itemIds) {
            values.clear();
            values.put(KEY_READ, read);
            db.update(TABLE_NAME_FEED_ITEMS, values, KEY_ID + "=?", new String[]{String.valueOf(id)});
        }
        db.setTransactionSuccessful();
        db.endTransaction();
    }

    public void setChapters(FeedItem item) {
        ContentValues values = new ContentValues();
        for (Chapter chapter : item.getChapters()) {
            values.put(KEY_TITLE, chapter.getTitle());
            values.put(KEY_START, chapter.getStart());
            values.put(KEY_FEEDITEM, item.getId());
            values.put(KEY_LINK, chapter.getLink());
            values.put(KEY_CHAPTER_TYPE, chapter.getChapterType());
            if (chapter.getId() == 0) {
                chapter.setId(db.insert(TABLE_NAME_SIMPLECHAPTERS, null, values));
            } else {
                db.update(TABLE_NAME_SIMPLECHAPTERS, values, KEY_ID + "=?",
                        new String[]{String.valueOf(chapter.getId())});
            }
        }
    }

    public void setFeedLastUpdateFailed(long feedId, boolean failed) {
        final String sql = "UPDATE " + TABLE_NAME_FEEDS
                + " SET " + KEY_LAST_UPDATE_FAILED+ "=" + (failed ? "1" : "0")
                + " WHERE " + KEY_ID + "="+ feedId;
        db.execSQL(sql);
    }

    /**
     * Inserts or updates a download status.
     */
    public long setDownloadStatus(DownloadStatus status) {
        ContentValues values = new ContentValues();
        values.put(KEY_FEEDFILE, status.getFeedfileId());
        values.put(KEY_FEEDFILETYPE, status.getFeedfileType());
        values.put(KEY_REASON, status.getReason().getCode());
        values.put(KEY_SUCCESSFUL, status.isSuccessful());
        values.put(KEY_COMPLETION_DATE, status.getCompletionDate().getTime());
        values.put(KEY_REASON_DETAILED, status.getReasonDetailed());
        values.put(KEY_DOWNLOADSTATUS_TITLE, status.getTitle());
        if (status.getId() == 0) {
            status.setId(db.insert(TABLE_NAME_DOWNLOAD_LOG, null, values));
        } else {
            db.update(TABLE_NAME_DOWNLOAD_LOG, values, KEY_ID + "=?",
                    new String[]{String.valueOf(status.getId())});
        }
        return status.getId();
    }

    public void setFeedItemAutoDownload(FeedItem feedItem, boolean autoDownload) {
        ContentValues values = new ContentValues();
        values.put(KEY_AUTO_DOWNLOAD, autoDownload);
        db.update(TABLE_NAME_FEED_ITEMS, values, KEY_ID + "=?",
                new String[]{String.valueOf(feedItem.getId())});
    }

    public void setFeedsItemsAutoDownload(Feed feed, boolean autoDownload) {
        final String sql = "UPDATE " + TABLE_NAME_FEED_ITEMS
                + " SET " + KEY_AUTO_DOWNLOAD + "="+ (autoDownload ? "1" : "0")
                + " WHERE " + KEY_FEED + "=" + feed.getId();
        db.execSQL(sql);
    }

    /**
     * Adds the item to favorites
     */
    public void addFavoriteItem(FeedItem item) {
        // don't add an item that's already there...
        if (isItemInFavorites(item)) {
            Log.d(TAG, "item already in favorites");
            return;
        }
        ContentValues values = new ContentValues();
        values.put(KEY_FEEDITEM, item.getId());
        values.put(KEY_FEED, item.getFeedId());
        db.insert(TABLE_NAME_FAVORITES, null, values);
    }

    public void removeFavoriteItem(FeedItem item) {
        String deleteClause = String.format("DELETE FROM %s WHERE %s=%s AND %s=%s",
                TABLE_NAME_FAVORITES,
                KEY_FEEDITEM, item.getId(),
                KEY_FEED, item.getFeedId());
        db.execSQL(deleteClause);
    }

    public boolean isItemInFavorites(FeedItem item) {
        String query = String.format("SELECT %s from %s WHERE %s=%d",
                KEY_ID, TABLE_NAME_FAVORITES, KEY_FEEDITEM, item.getId());
        Cursor c = db.rawQuery(query, null);
        int count = c.getCount();
        c.close();
        return count > 0;
    }

    public long getDownloadLogSize() {
        final String query = String.format("SELECT COUNT(%s) FROM %s", KEY_ID, TABLE_NAME_DOWNLOAD_LOG);
        Cursor result = db.rawQuery(query, null);
        long count = 0;
        if (result.moveToFirst()) {
            count = result.getLong(0);
        }
        result.close();
        return count;
    }

    public void setQueue(List<FeedItem> queue) {
        ContentValues values = new ContentValues();
        db.beginTransaction();
        db.delete(TABLE_NAME_QUEUE, null, null);
        for (int i = 0; i < queue.size(); i++) {
            FeedItem item = queue.get(i);
            values.put(KEY_ID, i);
            values.put(KEY_FEEDITEM, item.getId());
            values.put(KEY_FEED, item.getFeed().getId());
            db.insertWithOnConflict(TABLE_NAME_QUEUE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        }
        db.setTransactionSuccessful();
        db.endTransaction();
    }

    public void clearQueue() {
        db.delete(TABLE_NAME_QUEUE, null, null);
    }

    public void removeFeedMedia(FeedMedia media) {
        db.delete(TABLE_NAME_FEED_MEDIA, KEY_ID + "=?",
                new String[]{String.valueOf(media.getId())});
    }

    public void removeChaptersOfItem(FeedItem item) {
        db.delete(TABLE_NAME_SIMPLECHAPTERS, KEY_FEEDITEM + "=?",
                new String[]{String.valueOf(item.getId())});
    }

    public void removeFeedImage(FeedImage image) {
        db.delete(TABLE_NAME_FEED_IMAGES, KEY_ID + "=?",
                new String[]{String.valueOf(image.getId())});
    }

    /**
     * Remove a FeedItem and its FeedMedia entry.
     */
    public void removeFeedItem(FeedItem item) {
        if (item.getMedia() != null) {
            removeFeedMedia(item.getMedia());
        }
        if (item.hasChapters() || item.getChapters() != null) {
            removeChaptersOfItem(item);
        }
        if (item.hasItemImage()) {
            removeFeedImage(item.getImage());
        }
        db.delete(TABLE_NAME_FEED_ITEMS, KEY_ID + "=?",
                new String[]{String.valueOf(item.getId())});
    }

    /**
     * Remove a feed with all its FeedItems and Media entries.
     */
    public void removeFeed(Feed feed) {
        db.beginTransaction();
        if (feed.getImage() != null) {
            removeFeedImage(feed.getImage());
        }
        if (feed.getItems() != null) {
            for (FeedItem item : feed.getItems()) {
                removeFeedItem(item);
            }
        }

        db.delete(TABLE_NAME_FEEDS, KEY_ID + "=?",
                new String[]{String.valueOf(feed.getId())});
        db.setTransactionSuccessful();
        db.endTransaction();
    }

    public void clearPlaybackHistory() {
        ContentValues values = new ContentValues();
        values.put(KEY_PLAYBACK_COMPLETION_DATE, 0);
        db.update(TABLE_NAME_FEED_MEDIA, values, null, null);
    }

    public void clearDownloadLog() {
        db.delete(TABLE_NAME_DOWNLOAD_LOG, null, null);
    }

    /**
     * Get all Feeds from the Feed Table.
     *
     * @return The cursor of the query
     */
    public final Cursor getAllFeedsCursor() {
        Cursor c = db.query(TABLE_NAME_FEEDS, FEED_SEL_STD, null, null, null, null,
                KEY_TITLE + " COLLATE NOCASE ASC");
        return c;
    }

    public final Cursor getFeedCursorDownloadUrls() {
        return db.query(TABLE_NAME_FEEDS, new String[]{KEY_ID, KEY_DOWNLOAD_URL}, null, null, null, null, null);
    }

    /**
     * Returns a cursor with all FeedItems of a Feed. Uses FEEDITEM_SEL_FI_SMALL
     *
     * @param feed The feed you want to get the FeedItems from.
     * @return The cursor of the query
     */
    public final Cursor getAllItemsOfFeedCursor(final Feed feed) {
        return getAllItemsOfFeedCursor(feed.getId());
    }

    public final Cursor getAllItemsOfFeedCursor(final long feedId) {
        Cursor c = db.query(TABLE_NAME_FEED_ITEMS, FEEDITEM_SEL_FI_SMALL, KEY_FEED
                        + "=?", new String[]{String.valueOf(feedId)}, null, null,
                null
        );
        return c;
    }

    /**
     * Return a cursor with the SEL_FI_EXTRA selection of a single feeditem.
     */
    public final Cursor getExtraInformationOfItem(final FeedItem item) {
        Cursor c = db
                .query(TABLE_NAME_FEED_ITEMS, SEL_FI_EXTRA, KEY_ID + "=?",
                        new String[]{String.valueOf(item.getId())}, null,
                        null, null);
        return c;
    }

    /**
     * Returns a cursor for a DB query in the FeedMedia table for a given ID.
     *
     * @param item The item you want to get the FeedMedia from
     * @return The cursor of the query
     */
    public final Cursor getFeedMediaOfItemCursor(final FeedItem item) {
        Cursor c = db.query(TABLE_NAME_FEED_MEDIA, null, KEY_ID + "=?",
                new String[]{String.valueOf(item.getMedia().getId())}, null,
                null, null);
        return c;
    }

    /**
     * Returns a cursor for a DB query in the FeedImages table for a given ID.
     *
     * @param id ID of the FeedImage
     * @return The cursor of the query
     */
    public final Cursor getImageCursor(final long id) {
        Cursor c = db.query(TABLE_NAME_FEED_IMAGES, null, KEY_ID + "=?",
                new String[]{String.valueOf(id)}, null, null, null);
        return c;
    }

    public final Cursor getSimpleChaptersOfFeedItemCursor(final FeedItem item) {
        Cursor c = db.query(TABLE_NAME_SIMPLECHAPTERS, null, KEY_FEEDITEM
                        + "=?", new String[]{String.valueOf(item.getId())}, null,
                null, null
        );
        return c;
    }

    public final Cursor getDownloadLog(final int feedFileType, final long feedFileId) {
        final String query = "SELECT * FROM " + TABLE_NAME_DOWNLOAD_LOG +
                " WHERE " + KEY_FEEDFILE + "=" + feedFileId + " AND " + KEY_FEEDFILETYPE + "=" + feedFileType
                + " ORDER BY " + KEY_ID + " DESC";
        Cursor c = db.rawQuery(query, null);
        return c;
    }

    public final Cursor getDownloadLogCursor(final int limit) {
        Cursor c = db.query(TABLE_NAME_DOWNLOAD_LOG, null, null, null, null,
                null, KEY_COMPLETION_DATE + " DESC LIMIT " + limit);
        return c;
    }

    /**
     * Returns a cursor which contains all feed items in the queue. The returned
     * cursor uses the FEEDITEM_SEL_FI_SMALL selection.
     * cursor uses the FEEDITEM_SEL_FI_SMALL selection.
     */
    public final Cursor getQueueCursor() {
        Object[] args = new String[] {
                SEL_FI_SMALL_STR,
                TABLE_NAME_FEED_ITEMS, TABLE_NAME_QUEUE,
                TABLE_NAME_FEED_ITEMS + "." + KEY_ID,
                TABLE_NAME_QUEUE + "." + KEY_FEEDITEM,
                TABLE_NAME_QUEUE + "." + KEY_ID };
        String query = String.format("SELECT %s FROM %s INNER JOIN %s ON %s=%s ORDER BY %s", args);
        Cursor c = db.rawQuery(query, null);
        return c;
    }

    public Cursor getQueueIDCursor() {
        Cursor c = db.query(TABLE_NAME_QUEUE, new String[]{KEY_FEEDITEM}, null, null, null, null, KEY_ID + " ASC", null);
        return c;
    }


    public final Cursor getFavoritesCursor() {
        Object[] args = new String[] {
                SEL_FI_SMALL_STR,
                TABLE_NAME_FEED_ITEMS, TABLE_NAME_FAVORITES,
                TABLE_NAME_FEED_ITEMS + "." + KEY_ID,
                TABLE_NAME_FAVORITES + "." + KEY_FEEDITEM,
                TABLE_NAME_FAVORITES + "." + KEY_ID };
        String query = String.format("SELECT %s FROM %s INNER JOIN %s ON %s=%s ORDER BY %s", args);
        Cursor c = db.rawQuery(query, null);
        return c;
    }

    /**
     * Returns a cursor which contains all feed items in the unread items list.
     * The returned cursor uses the FEEDITEM_SEL_FI_SMALL selection.
     */
    public final Cursor getUnreadItemsCursor() {
        Cursor c = db.query(TABLE_NAME_FEED_ITEMS, FEEDITEM_SEL_FI_SMALL, KEY_READ
                + "<" + FeedItem.PLAYED, null, null, null, KEY_PUBDATE + " DESC");
        return c;
    }

    public final Cursor getNewItemIdsCursor() {
        final String query = "SELECT " + KEY_ID
                + " FROM " + TABLE_NAME_FEED_ITEMS
                + " WHERE " + KEY_READ + "=" + FeedItem.NEW;
        return db.rawQuery(query, null);
    }

    /**
     * Returns a cursor which contains all items of a feed that are considered new.
     * The returned cursor uses the FEEDITEM_SEL_FI_SMALL selection.
     */
    public final Cursor getNewItemsIdsCursor(long feedId) {
        final String query = "SELECT " + KEY_ID
                + " FROM " + TABLE_NAME_FEED_ITEMS
                + " WHERE " + KEY_FEED + "=" + feedId
                + " AND " + KEY_READ + "=" + FeedItem.NEW
                + " ORDER BY " + KEY_PUBDATE + " DESC";
        Cursor c = db.rawQuery(query, null);
        return c;
    }

    /**
     * Returns a cursor which contains all feed items that are considered new.
     * The returned cursor uses the FEEDITEM_SEL_FI_SMALL selection.
     */
    public final Cursor getNewItemsCursor() {
        final String query = "SELECT " + SEL_FI_SMALL_STR
                + " FROM " + TABLE_NAME_FEED_ITEMS
                + " WHERE " + KEY_READ + "=" + FeedItem.NEW
                + " ORDER BY " + KEY_PUBDATE + " DESC";
        Cursor c = db.rawQuery(query, null);
        return c;
    }

    public final Cursor getRecentlyPublishedItemsCursor(int limit) {
        Cursor c = db.query(TABLE_NAME_FEED_ITEMS, FEEDITEM_SEL_FI_SMALL, null, null, null, null, KEY_PUBDATE + " DESC LIMIT " + limit);
        return c;
    }

    public Cursor getDownloadedItemsCursor() {
        final String query = "SELECT " + SEL_FI_SMALL_STR
                + " FROM " + TABLE_NAME_FEED_ITEMS
                + " INNER JOIN " + TABLE_NAME_FEED_MEDIA
                + " ON " + TABLE_NAME_FEED_ITEMS + "." + KEY_ID + "=" + TABLE_NAME_FEED_MEDIA + "." + KEY_FEEDITEM
                + " WHERE " + TABLE_NAME_FEED_MEDIA + "." + KEY_DOWNLOADED + ">0";
        Cursor c = db.rawQuery(query, null);
        return c;
    }

    /**
     * Returns a cursor which contains feed media objects with a playback
     * completion date in ascending order.
     *
     * @param limit The maximum row count of the returned cursor. Must be an
     *              integer >= 0.
     * @throws IllegalArgumentException if limit < 0
     */
    public final Cursor getCompletedMediaCursor(int limit) {
        Validate.isTrue(limit >= 0, "Limit must be >= 0");

        Cursor c = db.query(TABLE_NAME_FEED_MEDIA, null,
                KEY_PLAYBACK_COMPLETION_DATE + " > 0", null, null,
                null, String.format("%s DESC LIMIT %d", KEY_PLAYBACK_COMPLETION_DATE, limit));
        return c;
    }

    public final Cursor getSingleFeedMediaCursor(long id) {
        return db.query(TABLE_NAME_FEED_MEDIA, null, KEY_ID + "=?", new String[]{String.valueOf(id)}, null, null, null);
    }

    public final Cursor getFeedMediaCursorByItemID(String... mediaIds) {
        int length = mediaIds.length;
        if (length > IN_OPERATOR_MAXIMUM) {
            Log.w(TAG, "Length of id array is larger than "
                    + IN_OPERATOR_MAXIMUM + ". Creating multiple cursors");
            int numCursors = (int) (((double) length) / (IN_OPERATOR_MAXIMUM)) + 1;
            Cursor[] cursors = new Cursor[numCursors];
            for (int i = 0; i < numCursors; i++) {
                int neededLength = 0;
                String[] parts = null;
                final int elementsLeft = length - i * IN_OPERATOR_MAXIMUM;

                if (elementsLeft >= IN_OPERATOR_MAXIMUM) {
                    neededLength = IN_OPERATOR_MAXIMUM;
                    parts = Arrays.copyOfRange(mediaIds, i
                            * IN_OPERATOR_MAXIMUM, (i + 1)
                            * IN_OPERATOR_MAXIMUM);
                } else {
                    neededLength = elementsLeft;
                    parts = Arrays.copyOfRange(mediaIds, i
                            * IN_OPERATOR_MAXIMUM, (i * IN_OPERATOR_MAXIMUM)
                            + neededLength);
                }

                cursors[i] = db.rawQuery("SELECT * FROM "
                        + TABLE_NAME_FEED_MEDIA + " WHERE " + KEY_FEEDITEM + " IN "
                        + buildInOperator(neededLength), parts);
            }
            return new MergeCursor(cursors);
        } else {
            return db.query(TABLE_NAME_FEED_MEDIA, null, KEY_FEEDITEM + " IN "
                    + buildInOperator(length), mediaIds, null, null, null);
        }
    }

    /**
     * Builds an IN-operator argument depending on the number of items.
     */
    private String buildInOperator(int size) {
        if (size == 1) {
            return "(?)";
        }
        StringBuffer buffer = new StringBuffer("(");
        for (int i = 0; i < size - 1; i++) {
            buffer.append("?,");
        }
        buffer.append("?)");
        return buffer.toString();
    }

    public final Cursor getFeedCursor(final long id) {
        Cursor c = db.query(TABLE_NAME_FEEDS, FEED_SEL_STD, KEY_ID + "=" + id, null,
                null, null, null);
        return c;
    }

    public final Cursor getFeedItemCursor(final String id) {
        return getFeedItemCursor(new String[]{id});
    }

    public final Cursor getFeedItemCursor(final String[] ids) {
        if (ids.length > IN_OPERATOR_MAXIMUM) {
            throw new IllegalArgumentException(
                    "number of IDs must not be larger than "
                            + IN_OPERATOR_MAXIMUM
            );
        }

        return db.query(TABLE_NAME_FEED_ITEMS, FEEDITEM_SEL_FI_SMALL, KEY_ID + " IN "
                + buildInOperator(ids.length), ids, null, null, null);

    }

    public final Cursor getFeedItemCursor(final String podcastUrl, final String episodeUrl) {
        final String query = "SELECT " + SEL_FI_SMALL_STR + " FROM " + TABLE_NAME_FEED_ITEMS
                + " INNER JOIN " +
                    TABLE_NAME_FEEDS + " ON " + TABLE_NAME_FEED_ITEMS + "." + KEY_FEED + "=" +
                    TABLE_NAME_FEEDS + "." + KEY_ID + " WHERE " + TABLE_NAME_FEED_ITEMS + "." + KEY_ITEM_IDENTIFIER + "='" +
                    episodeUrl + "' AND " + TABLE_NAME_FEEDS + "." + KEY_DOWNLOAD_URL + "='" + podcastUrl + "'";
        return db.rawQuery(query, null);
    }

    public Cursor getImageAuthenticationCursor(final String imageUrl) {
        final String query = "SELECT " + KEY_USERNAME + "," + KEY_PASSWORD + " FROM "
                + TABLE_NAME_FEED_IMAGES + " INNER JOIN " + TABLE_NAME_FEEDS + " ON " +
                TABLE_NAME_FEED_IMAGES + "." + KEY_ID + "=" + TABLE_NAME_FEEDS + "." + KEY_IMAGE + " WHERE "
                + TABLE_NAME_FEED_IMAGES + "." + KEY_DOWNLOAD_URL + "='" + imageUrl + "' UNION SELECT "
                + KEY_USERNAME + "," + KEY_PASSWORD + " FROM " + TABLE_NAME_FEED_IMAGES + " INNER JOIN "
                + TABLE_NAME_FEED_ITEMS + " ON " + TABLE_NAME_FEED_IMAGES + "." + KEY_ID + "=" +
                TABLE_NAME_FEED_ITEMS + "." + KEY_IMAGE + " INNER JOIN " + TABLE_NAME_FEEDS + " ON "
                + TABLE_NAME_FEED_ITEMS + "." + KEY_FEED + "=" + TABLE_NAME_FEEDS + "." + KEY_ID + " WHERE "
                + TABLE_NAME_FEED_IMAGES + "." + KEY_DOWNLOAD_URL + "='" + imageUrl + "'";
        Log.d(TAG, "Query: " + query);
        return db.rawQuery(query, null);
    }

    public int getQueueSize() {
        final String query = String.format("SELECT COUNT(%s) FROM %s", KEY_ID, TABLE_NAME_QUEUE);
        Cursor c = db.rawQuery(query, null);
        int result = 0;
        if (c.moveToFirst()) {
            result = c.getInt(0);
        }
        c.close();
        return result;
    }

    public final int getNumberOfNewItems() {
        final String query = "SELECT COUNT(" + KEY_ID + ")"
                + " FROM " + TABLE_NAME_FEED_ITEMS
                + " WHERE " + KEY_READ + "=" + FeedItem.NEW;
        Cursor c = db.rawQuery(query, null);
        int result = 0;
        if (c.moveToFirst()) {
            result = c.getInt(0);
        }
        c.close();
        return result;
    }

    public final LongIntMap getFeedCounters(long... feedIds) {
        int setting = UserPreferences.getFeedCounterSetting();
        String whereRead;
        if(setting == UserPreferences.FEED_COUNTER_SHOW_NEW_UNPLAYED_SUM) {
            whereRead = "(" + KEY_READ + "=" + FeedItem.NEW
                    + " OR " + KEY_READ  + "=" + FeedItem.UNPLAYED + ")";
        } else if(setting == UserPreferences.FEED_COUNTER_SHOW_NEW) {
            whereRead = KEY_READ + "=" + FeedItem.NEW;
        } else if(setting == UserPreferences.FEED_COUNTER_SHOW_UNPLAYED) {
            whereRead = KEY_READ + "=" + FeedItem.UNPLAYED;
        } else { // NONE
            return new LongIntMap(0);
        }

        // work around TextUtils.join wanting only boxed items
        // and StringUtils.join() causing NoSuchMethodErrors on MIUI
        StringBuilder builder = new StringBuilder();
        for (long id : feedIds) {
            builder.append(id);
            builder.append(',');
        }
        if (feedIds.length > 0) {
            // there's an extra ',', get rid of it
            builder.deleteCharAt(builder.length() - 1);
        }

        final String query = "SELECT " + KEY_FEED + ", COUNT(" + KEY_ID + ") AS count "
                + " FROM " + TABLE_NAME_FEED_ITEMS
                + " WHERE " + KEY_FEED + " IN (" + builder.toString() + ") "
                + " AND " + whereRead + " GROUP BY " + KEY_FEED;

        Cursor c = db.rawQuery(query, null);
        LongIntMap result = new LongIntMap(c.getCount());
        if (c.moveToFirst()) {
            do {
                long feedId = c.getLong(0);
                int count = c.getInt(1);
                result.put(feedId, count);
            } while(c.moveToNext());
        }
        c.close();
        return result;
    }

    public final int getNumberOfDownloadedEpisodes() {
        final String query = "SELECT COUNT(DISTINCT " + KEY_ID + ") AS count FROM " + TABLE_NAME_FEED_MEDIA +
                " WHERE " + KEY_DOWNLOADED + " > 0";

        Cursor c = db.rawQuery(query, null);
        int result = 0;
        if (c.moveToFirst()) {
            result = c.getInt(0);
        }
        c.close();
        return result;
    }

    /**
     * Uses DatabaseUtils to escape a search query and removes ' at the
     * beginning and the end of the string returned by the escape method.
     */
    private String prepareSearchQuery(String query) {
        StringBuilder builder = new StringBuilder();
        DatabaseUtils.appendEscapedSQLString(builder, query);
        builder.deleteCharAt(0);
        builder.deleteCharAt(builder.length() - 1);
        return builder.toString();
    }

    /**
     * Searches for the given query in the description of all items or the items
     * of a specified feed.
     *
     * @return A cursor with all search results in SEL_FI_EXTRA selection.
     */
    public Cursor searchItemDescriptions(long feedID, String query) {
        if (feedID != 0) {
            // search items in specific feed
            return db.query(TABLE_NAME_FEED_ITEMS, FEEDITEM_SEL_FI_SMALL, KEY_FEED
                            + "=? AND " + KEY_DESCRIPTION + " LIKE '%"
                            + prepareSearchQuery(query) + "%'",
                    new String[]{String.valueOf(feedID)}, null, null,
                    null
            );
        } else {
            // search through all items
            return db.query(TABLE_NAME_FEED_ITEMS, FEEDITEM_SEL_FI_SMALL,
                    KEY_DESCRIPTION + " LIKE '%" + prepareSearchQuery(query)
                            + "%'", null, null, null, null
            );
        }
    }

    /**
     * Searches for the given query in the content-encoded field of all items or
     * the items of a specified feed.
     *
     * @return A cursor with all search results in SEL_FI_EXTRA selection.
     */
    public Cursor searchItemContentEncoded(long feedID, String query) {
        if (feedID != 0) {
            // search items in specific feed
            return db.query(TABLE_NAME_FEED_ITEMS, FEEDITEM_SEL_FI_SMALL, KEY_FEED
                            + "=? AND " + KEY_CONTENT_ENCODED + " LIKE '%"
                            + prepareSearchQuery(query) + "%'",
                    new String[]{String.valueOf(feedID)}, null, null,
                    null
            );
        } else {
            // search through all items
            return db.query(TABLE_NAME_FEED_ITEMS, FEEDITEM_SEL_FI_SMALL,
                    KEY_CONTENT_ENCODED + " LIKE '%"
                            + prepareSearchQuery(query) + "%'", null, null,
                    null, null
            );
        }
    }

    public Cursor searchItemTitles(long feedID, String query) {
        if (feedID != 0) {
            // search items in specific feed
            return db.query(TABLE_NAME_FEED_ITEMS, FEEDITEM_SEL_FI_SMALL, KEY_FEED
                            + "=? AND " + KEY_TITLE + " LIKE '%"
                            + prepareSearchQuery(query) + "%'",
                    new String[]{String.valueOf(feedID)}, null, null,
                    null
            );
        } else {
            // search through all items
            return db.query(TABLE_NAME_FEED_ITEMS, FEEDITEM_SEL_FI_SMALL,
                    KEY_TITLE + " LIKE '%"
                            + prepareSearchQuery(query) + "%'", null, null,
                    null, null
            );
        }
    }

    public Cursor searchItemChapters(long feedID, String searchQuery) {
        final String query;
        if (feedID != 0) {
            query = "SELECT " + SEL_FI_SMALL_STR + " FROM " + TABLE_NAME_FEED_ITEMS + " INNER JOIN " +
                    TABLE_NAME_SIMPLECHAPTERS + " ON " + TABLE_NAME_SIMPLECHAPTERS + "." + KEY_FEEDITEM + "=" +
                    TABLE_NAME_FEED_ITEMS + "." + KEY_ID + " WHERE " + TABLE_NAME_FEED_ITEMS + "." + KEY_FEED + "=" +
                    feedID + " AND " + TABLE_NAME_SIMPLECHAPTERS + "." + KEY_TITLE + " LIKE '%"
                    + prepareSearchQuery(searchQuery) + "%'";
        } else {
            query = "SELECT " + SEL_FI_SMALL_STR + " FROM " + TABLE_NAME_FEED_ITEMS + " INNER JOIN " +
                    TABLE_NAME_SIMPLECHAPTERS + " ON " + TABLE_NAME_SIMPLECHAPTERS + "." + KEY_FEEDITEM + "=" +
                    TABLE_NAME_FEED_ITEMS + "." + KEY_ID + " WHERE " + TABLE_NAME_SIMPLECHAPTERS + "." + KEY_TITLE + " LIKE '%"
                    + prepareSearchQuery(searchQuery) + "%'";
        }
        return db.rawQuery(query, null);
    }


    public static final int IDX_FEEDSTATISTICS_FEED = 0;
    public static final int IDX_FEEDSTATISTICS_NUM_ITEMS = 1;
    public static final int IDX_FEEDSTATISTICS_NEW_ITEMS = 2;
    public static final int IDX_FEEDSTATISTICS_LATEST_EPISODE = 3;
    public static final int IDX_FEEDSTATISTICS_IN_PROGRESS_EPISODES = 4;

    /**
     * Select number of items, new items, the date of the latest episode and the number of episodes in progress. The result
     * is sorted by the title of the feed.
     */
    private static final String FEED_STATISTICS_QUERY = "SELECT Feeds.id, num_items, new_items, latest_episode, in_progress FROM " +
            " Feeds LEFT JOIN " +
            "(SELECT feed,count(*) AS num_items," +
            " COUNT(CASE WHEN read=0 THEN 1 END) AS new_items," +
            " MAX(pubDate) AS latest_episode," +
            " COUNT(CASE WHEN position>0 THEN 1 END) AS in_progress," +
            " COUNT(CASE WHEN downloaded=1 THEN 1 END) AS episodes_downloaded " +
            " FROM FeedItems LEFT JOIN FeedMedia ON FeedItems.id=FeedMedia.feeditem GROUP BY FeedItems.feed)" +
            " ON Feeds.id = feed ORDER BY Feeds.title COLLATE NOCASE ASC;";

    public Cursor getFeedStatisticsCursor() {
        return db.rawQuery(FEED_STATISTICS_QUERY, null);
    }

    /**
     * Helper class for opening the Antennapod database.
     */
    private static class PodDBHelper extends SQLiteOpenHelper {

        private final static int VERSION = 1040000;

        private Context context;

        /**
         * Constructor.
         *
         * @param context Context to use
         * @param name    Name of the database
         * @param factory to use for creating cursor objects
         */
        public PodDBHelper(final Context context, final String name,
                           final CursorFactory factory) {
            super(context, name, factory, VERSION);
            this.context = context;
        }

        @Override
        public void onCreate(final SQLiteDatabase db) {
            db.execSQL(CREATE_TABLE_FEEDS);
            db.execSQL(CREATE_TABLE_FEED_ITEMS);
            db.execSQL(CREATE_TABLE_FEED_IMAGES);
            db.execSQL(CREATE_TABLE_FEED_MEDIA);
            db.execSQL(CREATE_TABLE_DOWNLOAD_LOG);
            db.execSQL(CREATE_TABLE_QUEUE);
            db.execSQL(CREATE_TABLE_SIMPLECHAPTERS);
            db.execSQL(CREATE_TABLE_FAVORITES);

            db.execSQL(CREATE_INDEX_FEEDITEMS_FEED);
            db.execSQL(CREATE_INDEX_FEEDITEMS_IMAGE);
            db.execSQL(CREATE_INDEX_FEEDMEDIA_FEEDITEM);
            db.execSQL(CREATE_INDEX_QUEUE_FEEDITEM);
            db.execSQL(CREATE_INDEX_SIMPLECHAPTERS_FEEDITEM);

        }

        @Override
        public void onUpgrade(final SQLiteDatabase db, final int oldVersion,
                              final int newVersion) {
            EventBus.getDefault().post(ProgressEvent.start(context.getString(R.string.progress_upgrading_database)));
            Log.w("DBAdapter", "Upgrading from version " + oldVersion + " to "
                    + newVersion + ".");
            if (oldVersion <= 1) {
                db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEEDS + " ADD COLUMN "
                        + KEY_TYPE + " TEXT");
            }
            if (oldVersion <= 2) {
                db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_SIMPLECHAPTERS
                        + " ADD COLUMN " + KEY_LINK + " TEXT");
            }
            if (oldVersion <= 3) {
                db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEED_ITEMS
                        + " ADD COLUMN " + KEY_ITEM_IDENTIFIER + " TEXT");
            }
            if (oldVersion <= 4) {
                db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEEDS + " ADD COLUMN "
                        + KEY_FEED_IDENTIFIER + " TEXT");
            }
            if (oldVersion <= 5) {
                db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_DOWNLOAD_LOG
                        + " ADD COLUMN " + KEY_REASON_DETAILED + " TEXT");
                db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_DOWNLOAD_LOG
                        + " ADD COLUMN " + KEY_DOWNLOADSTATUS_TITLE + " TEXT");
            }
            if (oldVersion <= 6) {
                db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_SIMPLECHAPTERS
                        + " ADD COLUMN " + KEY_CHAPTER_TYPE + " INTEGER");
            }
            if (oldVersion <= 7) {
                db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEED_MEDIA
                        + " ADD COLUMN " + KEY_PLAYBACK_COMPLETION_DATE
                        + " INTEGER");
            }
            if (oldVersion <= 8) {
                final int KEY_ID_POSITION = 0;
                final int KEY_MEDIA_POSITION = 1;

                // Add feeditem column to feedmedia table
                db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEED_MEDIA
                        + " ADD COLUMN " + KEY_FEEDITEM
                        + " INTEGER");
                Cursor feeditemCursor = db.query(PodDBAdapter.TABLE_NAME_FEED_ITEMS,
                        new String[]{KEY_ID, KEY_MEDIA}, "? > 0",
                        new String[]{KEY_MEDIA}, null, null, null);
                if (feeditemCursor.moveToFirst()) {
                    db.beginTransaction();
                    ContentValues contentValues = new ContentValues();
                    do {
                        long mediaId = feeditemCursor.getLong(KEY_MEDIA_POSITION);
                        contentValues.put(KEY_FEEDITEM, feeditemCursor.getLong(KEY_ID_POSITION));
                        db.update(PodDBAdapter.TABLE_NAME_FEED_MEDIA, contentValues, KEY_ID + "=?", new String[]{String.valueOf(mediaId)});
                        contentValues.clear();
                    } while (feeditemCursor.moveToNext());
                    db.setTransactionSuccessful();
                    db.endTransaction();
                }
                feeditemCursor.close();
            }
            if (oldVersion <= 9) {
                db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEEDS
                        + " ADD COLUMN " + KEY_AUTO_DOWNLOAD
                        + " INTEGER DEFAULT 1");
            }
            if (oldVersion <= 10) {
                db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEEDS
                        + " ADD COLUMN " + KEY_FLATTR_STATUS
                        + " INTEGER");
                db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEED_ITEMS
                        + " ADD COLUMN " + KEY_FLATTR_STATUS
                        + " INTEGER");
                db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEED_MEDIA
                        + " ADD COLUMN " + KEY_PLAYED_DURATION
                        + " INTEGER");
            }
            if (oldVersion <= 11) {
                db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEEDS
                        + " ADD COLUMN " + KEY_USERNAME
                        + " TEXT");
                db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEEDS
                        + " ADD COLUMN " + KEY_PASSWORD
                        + " TEXT");
                db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEED_ITEMS
                        + " ADD COLUMN " + KEY_IMAGE
                        + " INTEGER");
            }
            if (oldVersion <= 12) {
                db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEEDS
                        + " ADD COLUMN " + KEY_IS_PAGED + " INTEGER DEFAULT 0");
                db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEEDS
                        + " ADD COLUMN " + KEY_NEXT_PAGE_LINK + " TEXT");
            }
            if (oldVersion <= 13) {
                // remove duplicate rows in "Chapters" table that were created because of a bug.
                db.execSQL(String.format("DELETE FROM %s WHERE %s NOT IN " +
                                "(SELECT MIN(%s) as %s FROM %s GROUP BY %s,%s,%s,%s,%s)",
                        PodDBAdapter.TABLE_NAME_SIMPLECHAPTERS,
                        KEY_ID,
                        KEY_ID,
                        KEY_ID,
                        PodDBAdapter.TABLE_NAME_SIMPLECHAPTERS,
                        KEY_TITLE,
                        KEY_START,
                        KEY_FEEDITEM,
                        KEY_LINK,
                        KEY_CHAPTER_TYPE));
            }
            if(oldVersion <= 14) {
                db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEED_ITEMS
                        + " ADD COLUMN " + KEY_AUTO_DOWNLOAD + " INTEGER");
                db.execSQL("UPDATE " + PodDBAdapter.TABLE_NAME_FEED_ITEMS
                        + " SET " + KEY_AUTO_DOWNLOAD + " = "
                        + "(SELECT " + KEY_AUTO_DOWNLOAD
                        + " FROM " + PodDBAdapter.TABLE_NAME_FEEDS
                        + " WHERE " + PodDBAdapter.TABLE_NAME_FEEDS + "." + KEY_ID
                        + " = " + PodDBAdapter.TABLE_NAME_FEED_ITEMS + "." + KEY_FEED + ")");

                db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEEDS
                        + " ADD COLUMN " + KEY_HIDE + " TEXT");

                db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEEDS
                        + " ADD COLUMN " + KEY_LAST_UPDATE_FAILED + " INTEGER DEFAULT 0");

                // create indexes
                db.execSQL(PodDBAdapter.CREATE_INDEX_FEEDITEMS_FEED);
                db.execSQL(PodDBAdapter.CREATE_INDEX_FEEDITEMS_IMAGE);
                db.execSQL(PodDBAdapter.CREATE_INDEX_FEEDMEDIA_FEEDITEM);
                db.execSQL(PodDBAdapter.CREATE_INDEX_QUEUE_FEEDITEM);
                db.execSQL(PodDBAdapter.CREATE_INDEX_SIMPLECHAPTERS_FEEDITEM);
            }
            if(oldVersion <= 15) {
                db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEED_MEDIA
                        + " ADD COLUMN " + KEY_HAS_EMBEDDED_PICTURE + " INTEGER DEFAULT -1");
                db.execSQL("UPDATE " + PodDBAdapter.TABLE_NAME_FEED_MEDIA
                        + " SET " + KEY_HAS_EMBEDDED_PICTURE + "=0"
                        + " WHERE " + KEY_DOWNLOADED + "=0");
                Cursor c = db.rawQuery("SELECT " + KEY_FILE_URL
                        + " FROM " + PodDBAdapter.TABLE_NAME_FEED_MEDIA
                        + " WHERE " + KEY_DOWNLOADED + "=1 "
                        + " AND " + KEY_HAS_EMBEDDED_PICTURE + "=-1", null);
                if(c.moveToFirst()) {
                    MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                    do {
                        String fileUrl = c.getString(0);
                        try {
                            mmr.setDataSource(fileUrl);
                            byte[] image = mmr.getEmbeddedPicture();
                            if (image != null) {
                                db.execSQL("UPDATE " + PodDBAdapter.TABLE_NAME_FEED_MEDIA
                                        + " SET " + KEY_HAS_EMBEDDED_PICTURE + "=1"
                                        + " WHERE " + KEY_FILE_URL + "='"+ fileUrl + "'");
                            } else {
                                db.execSQL("UPDATE " + PodDBAdapter.TABLE_NAME_FEED_MEDIA
                                        + " SET " + KEY_HAS_EMBEDDED_PICTURE + "=0"
                                        + " WHERE " + KEY_FILE_URL + "='"+ fileUrl + "'");
                            }
                        } catch(Exception e) {
                            e.printStackTrace();
                        }
                    } while(c.moveToNext());
                }
                c.close();
            }
            if(oldVersion <= 16) {
                String selectNew = "SELECT " + PodDBAdapter.TABLE_NAME_FEED_ITEMS + "." + KEY_ID
                        + " FROM " + PodDBAdapter.TABLE_NAME_FEED_ITEMS
                        + " INNER JOIN " + PodDBAdapter.TABLE_NAME_FEED_MEDIA + " ON "
                        + PodDBAdapter.TABLE_NAME_FEED_ITEMS + "." + KEY_ID + "="
                        + PodDBAdapter.TABLE_NAME_FEED_MEDIA + "." + KEY_FEEDITEM
                        + " LEFT OUTER JOIN " + PodDBAdapter.TABLE_NAME_QUEUE + " ON "
                        + PodDBAdapter.TABLE_NAME_FEED_ITEMS + "." + KEY_ID + "="
                        + PodDBAdapter.TABLE_NAME_QUEUE + "." + KEY_FEEDITEM
                        + " WHERE "
                        + PodDBAdapter.TABLE_NAME_FEED_ITEMS + "." + KEY_READ + " = 0 AND " // unplayed
                        + PodDBAdapter.TABLE_NAME_FEED_MEDIA + "." + KEY_DOWNLOADED + " = 0 AND " // undownloaded
                        + PodDBAdapter.TABLE_NAME_FEED_MEDIA + "." + KEY_POSITION + " = 0 AND " // not partially played
                        + PodDBAdapter.TABLE_NAME_QUEUE + "." + KEY_ID + " IS NULL"; // not in queue
                String sql =  "UPDATE " + PodDBAdapter.TABLE_NAME_FEED_ITEMS
                        + " SET " + KEY_READ + "=" + FeedItem.NEW
                        + " WHERE " + KEY_ID + " IN (" + selectNew + ")";
                Log.d("Migration", "SQL: " + sql);
                db.execSQL(sql);
            }
            if(oldVersion <= 17) {
                db.execSQL("ALTER TABLE " + PodDBAdapter.TABLE_NAME_FEEDS
                        + " ADD COLUMN " + PodDBAdapter.KEY_AUTO_DELETE_ACTION + " INTEGER DEFAULT 0");
            }
            if(oldVersion < 1030005) {
                db.execSQL("UPDATE FeedItems SET auto_download=0 WHERE " +
                        "(read=1 OR id IN (SELECT feeditem FROM FeedMedia WHERE position>0 OR downloaded=1)) " +
                        "AND id NOT IN (SELECT feeditem FROM Queue)");
            }
            EventBus.getDefault().post(ProgressEvent.end());
        }
    }
}
