package com.soul;

import java.util.List;

/**
 * Created by nebula on 2020-03-26
 */
public class Test {
    public static void main(List<RefClass1> args) {
        Object a = false;
        System.out.println(a.getClass());
        System.out.println(a.getClass().isAssignableFrom(Boolean.class));
//        new RefClass1();
    }
}
