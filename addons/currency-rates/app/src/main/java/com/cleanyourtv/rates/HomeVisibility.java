package com.cleanyourtv.rates;

/** Fail closed on app switches and launcher dialogs. No screen content is inspected. */
final class HomeVisibility {
    static boolean isHome(String packageName, String className) {
        return "com.gathin.tvhome".equals(packageName)
            && "com.gathin.tvhome.MainActivity".equals(className);
    }
}
