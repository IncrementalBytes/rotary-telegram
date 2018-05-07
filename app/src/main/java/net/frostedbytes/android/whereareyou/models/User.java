/*
 * Copyright 2018 Ryan Ward
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package net.frostedbytes.android.whereareyou.models;

import com.google.firebase.firestore.Exclude;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.frostedbytes.android.whereareyou.BaseActivity;

public class User implements Serializable {

  @Exclude
  public static final String FRIEND_LIST = "FriendList";

  @Exclude
  public static final String LOCATION_LIST = "LocationList";

  @Exclude
  public static final String USERS_ROOT = "Users";

  /**
   * Email associated with this user object.
   */
  public String Email;

  /**
   * Number of minutes between location uploads.
   */
  @Exclude
  public int Frequency;

  /**
   * Collection of user objects.
   */
  @Exclude
  public Map<String, Friend> FriendList;

  /**
   * Display name for friend.
   */
  public String FullName;

  /**
   * Current status; e.g. Sharing, Not Sharing.
   */
  public boolean IsSharing;

  /**
   * Collection of locations for this object.
   */
  @Exclude
  public Map<String, UserLocation> LocationList;

  /**
   * Unique identifier for user object.
   */
  public String UserId;

  public User() {

    this.Email = "";
    this.Frequency = 1;
    this.FriendList = new HashMap<>();
    this.FullName = "";
    this.IsSharing = false;
    this.LocationList = new HashMap<>();
    this.UserId = BaseActivity.DEFAULT_ID;
  }

  /**
   * Replaces illegal characters in the email address with '_' so it can be used as a key.
   * @return The email address where illegal characters have been replaced with '_'
   */
  public String getEmailAsKey() {

    return this.Email.replace('@', '_').replace('.', '_');
  }

  /**
   * Gets the most recent timestamp in the location collection.
   * @return Number of ticks representing the most recent location.
   */
  @Exclude
  public long getMostRecentLocationTimeStamp() {

    if (this.LocationList != null && this.LocationList.size() > 0) {
      List<String> locationKeys = new ArrayList<>(this.LocationList.keySet());
      Collections.sort(locationKeys);
      return Long.parseLong(locationKeys.get(locationKeys.size() - 1));
    }

    return 0;
  }
}
