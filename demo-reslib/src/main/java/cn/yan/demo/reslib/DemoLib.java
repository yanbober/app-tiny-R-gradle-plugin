package cn.yan.demo.reslib;

import android.content.Context;

/**
 * Created by yan on 18-5-3.
 */

public class DemoLib {
    private int[] age = {R.string.name_age, R.string.app_name};

    public static String getAndridCopy(Context context) {
        return context.getResources().getString(android.R.string.copy);
    }

    public static String getStringT(Context context) {
        return context.getResources().getString(R.string.name_t);
    }

    public String getStringAge(Context context) {
        return context.getResources().getString(age[0]);
    }
}
