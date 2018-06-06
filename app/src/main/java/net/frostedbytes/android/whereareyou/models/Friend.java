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
import java.util.Calendar;
import java.util.Locale;
import net.frostedbytes.android.whereareyou.BaseActivity;

public class Friend extends User implements Serializable {

  @Exclude
  public static final String FRIEND_LIST = "FriendList";

  @Exclude
  public static final String FRIENDS_ROOT = "Friends";

  /**
   * Value indicating whether or not this friend request has been accepted.
   */
  public boolean IsAccepted;

  /**
   * Value indicating whether or not this friend request has been declined.
   */
  public boolean IsDeclined;

  /**
   * Value indicating whether or not this friend status is pending.
   */
  public boolean IsPending;

  /**
   * Value indicating whether or not this user made the request.
   */
  public boolean IsRequestedBy;

  /**
   * Timestamp of most recent changes to friend object; in ticks.
   */
  public long UpdatedDate;

  public Friend() {

    this.Email = "";
    this.Emails = new ArrayList<>();
    this.FullName = "";
    this.IsAccepted = false;
    this.IsDeclined = false;
    this.IsPending = false;
    this.IsRequestedBy = false;
    this.IsSharing = false;
    this.PhotoUri = "";
    this.UpdatedDate = Calendar.getInstance().getTimeInMillis();
    this.UserId = BaseActivity.DEFAULT_ID;
  }

  public Friend(User user) {
    this();

    this.Email = user.Email;
    this.FullName = user.FullName;
    this.UserId = user.UserId;
  }

  @Override
  public String toString() {

    return String.format(Locale.ENGLISH, "%s", this.FullName);
  }
}
