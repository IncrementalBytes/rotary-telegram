package net.frostedbytes.android.whereareyou.models;

import com.google.firebase.database.Exclude;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import net.frostedbytes.android.whereareyou.BaseActivity;

public class UserFriend implements Serializable {

  /**
   * Display name for friend
   */
  public String FullName;

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
  @Exclude
  public String UserId;

  public UserFriend() {

    this.FullName = "";
    this.IsSharing = false;
    this.LocationList = new HashMap<>();
    this.UserId = BaseActivity.DEFAULT_ID;
  }
}
