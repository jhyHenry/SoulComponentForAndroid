//package com.soul;
//
//import java.util.Collection;
//
//import javassist.ClassPool;
//import javassist.CtClass;
//import javassist.NotFoundException;
//
///**
// * Created by nebula on 2020-04-06
// */
//public class JavassistTest {
//    public static void main(String[] args) throws NotFoundException {
//        ClassPool classPool = ClassPool.getDefault();
//        CtClass ctClass = classPool.get(Test.class.getName());
//        Collection<String> collection = ctClass.getRefClasses();
//        for (String s : collection) {
//            System.out.println(s);
//        }
//    }
//}
