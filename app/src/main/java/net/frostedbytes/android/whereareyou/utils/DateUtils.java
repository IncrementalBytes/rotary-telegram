package net.frostedbytes.android.whereareyou.utils;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DateUtils {

  /**
   * Returns a user-friendly readable string of the date.
   *
   * @param date - Date; in ticks
   * @return - User-friendly readable string of the date; formatted YYYY/MM/dd @ HH:mm:ss
   */
  public static String formatDateForDisplay(long date) {

    Date temp = new Date(date);
    DateFormat dateFormat = new SimpleDateFormat("YYYY/MM/dd @ HH:mm:ss", Locale.ENGLISH);
    return dateFormat.format(temp);
  }
}
