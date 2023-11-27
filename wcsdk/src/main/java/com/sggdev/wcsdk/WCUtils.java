package com.sggdev.wcsdk;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class WCUtils {
    private static final SimpleDateFormat mServerDateFormat = genServerDateFormat();
    private static String mServerTimeStamp = WCUtils.currentTimeStamp();

    public static Boolean toBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        } else if (value instanceof String) {
            String stringValue = (String) value;
            if ("true".equalsIgnoreCase(stringValue)) {
                return true;
            } else if ("false".equalsIgnoreCase(stringValue)) {
                return false;
            }
        }
        return false;
    }

    public static Double toDouble(Object value) {
        if (value instanceof Double) {
            return (Double) value;
        } else if (value instanceof Number) {
            return ((Number) value).doubleValue();
        } else if (value instanceof String) {
            try {
                return Double.valueOf((String) value);
            } catch (NumberFormatException ignored) {
            }
        }
        return 0.0;
    }

    public static Integer toInteger(Object value) {
        if (value instanceof Integer) {
            return (Integer) value;
        } else if (value instanceof Number) {
            return ((Number) value).intValue();
        } else if (value instanceof String) {
            try {
                return (int) Double.parseDouble((String) value);
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    public static Long toLong(Object value) {
        if (value instanceof Long) {
            return (Long) value;
        } else if (value instanceof Number) {
            return ((Number) value).longValue();
        } else if (value instanceof String) {
            try {
                return (long) Double.parseDouble((String) value);
            } catch (NumberFormatException ignored) {
            }
        }
        return (long)0;
    }

    public static String toString(Object value) {
        if (value instanceof String) {
            return (String) value;
        } else if (value != null) {
            return String.valueOf(value);
        }
        return "";
    }

    private static SimpleDateFormat genServerDateFormat() {
        SimpleDateFormat res =  new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);
        res.setTimeZone(TimeZone.getTimeZone("UTC"));
        return res;
    }

    public static Date dateTimeFromTimeStamp(String astamp) {
        try {
            return mServerDateFormat.parse(astamp);
        } catch (ParseException e) {
            e.printStackTrace();
        }

        return null;
    }

    public static String millisecsToTimeStamp(long millis) {
        Date ldt = new Date(millis);
        return mServerDateFormat.format(ldt);
    }

    public static String currentTimeStamp() {
        Date ldt = new Date();
        return mServerDateFormat.format(ldt);
    }

    public static String serverTimeStamp() {
        return mServerTimeStamp;
    }

    public static void setServerTimeStamp(String sts) {
        mServerTimeStamp = sts;
    }

    public static long timeStampToMillisecs(String astamp) {
        try {
            Date ldt = mServerDateFormat.parse(astamp);
            if (ldt == null) return 0;
            return ldt.getTime();
        } catch (ParseException e) {
            e.printStackTrace();
        }

        return 0;
    }
}
