package net.frostedbytes.android.whereareyou.models;

import com.google.firebase.database.Exclude;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.frostedbytes.android.whereareyou.BaseActivity;

public class User implements Serializable {

  @Exclude
  public static final String USERS_ROOT = "Users";

  @Exclude
  public static final String USER_FRIENDS_ROOT = "FriendList";

  /**
   * Number of minutes between location uploads.
   */
  public int Frequency;

  /**
   * Collection of user objects
   */
  public Map<String, UserFriend> FriendList;

  /**
   * Display name for friend
   */
  public String FullName;

  /**
   * Number of locations to save.
   */
  public int History;

  /**
   * Current status; e.g. Sharing, Not Sharing
   */
  public boolean IsSharing;

  /**
   * Collection of locations for this object
   */
  public Map<String, UserLocation> LocationList;

  /**
   * Unique identifier for user object
   */
  public String UserId;

  public User() {

    this.Frequency = 1;
    this.FriendList = new HashMap<>();
    this.FullName = "";
    this.History = 5;
    this.IsSharing = false;
    this.LocationList = new HashMap<>();
    this.UserId = BaseActivity.DEFAULT_ID;
  }

  /**
   * Gets the most recent timestamp in the location collection.
   * @return Number of ticks representing the most recent location.
   */
  public long get_LatestTimeStamp() {

    if (this.LocationList != null && this.LocationList.size() > 0) {
      List<String> locationKeys = new ArrayList<>();
      locationKeys.addAll(this.LocationList.keySet());
      Collections.sort(locationKeys);
      return Long.parseLong(locationKeys.get(locationKeys.size() - 1));
    }

    return 0;
  }
}
