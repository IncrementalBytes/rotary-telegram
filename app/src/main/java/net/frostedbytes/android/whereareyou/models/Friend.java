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

import java.io.Serializable;
import java.util.HashMap;
import net.frostedbytes.android.whereareyou.BaseActivity;

public class Friend extends User implements Serializable {

  /**
   * Timestamp representing when this friend object was created; in ticks.
   */
  public long CreatedDate;

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
   * Timestamp of most recent changes to friend object; in ticks.
   */
  public long UpdatedDate;

  public Friend() {

    this.CreatedDate = 0;
    this.Email = "";
    this.FullName = "";
    this.IsAccepted = false;
    this.IsDeclined = false;
    this.IsPending = false;
    this.IsSharing = false;
    this.LocationList = new HashMap<>();
    this.UpdatedDate = 0;
    this.UserId = BaseActivity.DEFAULT_ID;
  }
}
