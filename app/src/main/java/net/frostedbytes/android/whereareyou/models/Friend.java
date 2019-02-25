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
import java.util.Calendar;
import java.util.Locale;
import net.frostedbytes.android.whereareyou.BaseActivity;

public class Friend implements Serializable {

    @Exclude
    public static final String FRIENDS_ROOT = "Friends";

    /**
     * Email of friend.
     */
    public String Email;

    /**
     * Display name for friend.
     */
    public String FullName;

    /**
     * Unique identifier for user object.
     */
    public String Id;

    /**
     * The latitude value for user location.
     */
    public double Latitude;

    /**
     * The longitude value for user location.
     */
    public double Longitude;

    /**
     * User's photo URI path.
     */
    @Exclude
    public String PhotoUri;

    /**
     * The state of the friend. 0 = pending; 1 = waiting; 2 = accepted; 3 = rejected
     */
    public int Status;

    /**
     * The number of ticks representing when user location was created/updated.
     */
    public long TimeStamp;

    /**
     * Timestamp of most recent changes to friend object; in ticks.
     */
    public long UpdatedDate;

    public Friend() {

        this.Email = "";
        this.FullName = "";
        this.Id = BaseActivity.DEFAULT_ID;
        this.Latitude = 0;
        this.Longitude = 0;
        this.PhotoUri = "";
        this.TimeStamp = 0;
        this.Status = 0;
        this.UpdatedDate = Calendar.getInstance().getTimeInMillis();
    }

    public Friend(User user) {
        this();

        this.Email = user.Email;
        this.FullName = user.FullName;
        this.Latitude = user.Latitude;
        this.Longitude = user.Longitude;
        this.Id = user.Id;
        this.TimeStamp = user.TimeStamp;
    }

    @Exclude
    public String getEmailAsKey() {

        return this.Email.replace('@', '_').replace('.', '_');
    }

    @Override
    public String toString() {

        return String.format(Locale.ENGLISH, "%s", this.FullName);
    }
}
