package com.soul;

/**
 * Created by nebula on 2020-03-26
 */
public class Test {
    public static void main(String[] args) {
        Object a = false;
        System.out.println(a.getClass());
        System.out.println(a.getClass().isAssignableFrom(Boolean.class));
    }
}
