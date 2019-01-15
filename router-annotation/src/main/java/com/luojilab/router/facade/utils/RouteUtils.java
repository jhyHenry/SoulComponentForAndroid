package com.luojilab.router.facade.utils;

/**
 * Created by mrzhang on 2017/12/18.
 */

public class RouteUtils {

    private static final String ROUTERIMPL_OUTPUT_PKG = "com.luojilab.gen.router";
    private static final String DOT = ".";
    private static final String UIROUTER = "UiRouter";

    private static final String ROUTERTABLE = "RouterTable";

    public static String firstCharUpperCase(String str) {
        char[] ch = str.toCharArray();
        if (ch[0] >= 'a' && ch[0] <= 'z') {
            ch[0] = (char) (ch[0] - 32);
        }
        return new String(ch);
    }

    public static String genHostUIRouterClass(String host) {
        return ROUTERIMPL_OUTPUT_PKG + DOT + firstCharUpperCase(host) + UIROUTER;
    }

    public static String genRouterTable(String host) {
        return "./UIRouterTable/" + firstCharUpperCase(host) + ROUTERTABLE + ".txt";
    }

}
