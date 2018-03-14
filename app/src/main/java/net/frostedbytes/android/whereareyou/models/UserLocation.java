package net.frostedbytes.android.whereareyou.models;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

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
  public long TimeStamp;

  public UserLocation() {

    this.Latitude = 0.0;
    this.Longitude = 0.0;
    this.TimeStamp = 0;
  }

  public Map<String, Object> toMap() {

    HashMap<String, Object> result = new HashMap<>();
    result.put("Latitude", Latitude);
    result.put("Longitude", Longitude);
    return result;
  }
}
