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

public class UserLocation implements Serializable {

  /**
   * The latitude value for this location.
   */
  public double Latitude;

  /**
   * The longitude value for this location.
   */
  public double Longitude;

  /**
   * The number of ticks representing when this location was created.
   */
  @Exclude
  public long TimeStamp;

  public UserLocation() {

    this.Latitude = 0.0;
    this.Longitude = 0.0;
    this.TimeStamp = 0;
  }
}
