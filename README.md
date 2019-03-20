## SoulComponent

# 前言

随着项目不断迭代、功能新增，人员增多，维护成本越来越高，这时候就必须进行模块化的拆分。在我看来，模块化是一种指导理念，其核心思想就是降低耦合、单独运行，下面来介绍下基于DDComponent的组件化开发。

# 实现功能：
1. 组件可以单独调试
2. 杜绝组件之前相互耦合，代码完全隔离，彻底解耦
3. 组件之间通过接口+实现的方式进行数据传输
4. 任意组件可以充当host，集成其他组件进行集成调试
5. 可以动态对已集成的组件进行加载和卸载
6. 支持kotlin组件

# 集成指南

## 主项目引用编译脚本
在根目录的gradle.properties文件中，增加属性：

```
mainmodulename=app
其中mainmodulename是项目中的host工程，一般为app

在根目录的build.gradle中增加配置

buildscript {
    dependencies {
        classpath 'com.walid:soul-component-build-gradle:0.0.4'
    }
}
```

## 混淆

在混淆文件中增加如下配置

```
-keep interface * {
  <methods>;
}
-keep class com.soul.component.componentlib.** {*;}
-keep class com.soul.router.** {*;}
-keep class com.soul.gen.** {*;}
-keep class * implements com.soul.component.componentlib.router.ISyringe {*;}
-keep class * implements com.soul.component.componentlib.applicationlike.IApplicationLike {*;}
开发组件、单独调试和集成调试
1、拆分组件为module工程
组件目录下 gradle.properties 配置
isRunAlone=true （是否单独运行）
debugComponent=sharecomponent（添加组件）
compileComponent=sharecomponent（添加组件）

```

# 开发组件、单独调试和集成调试

## 应用组件化编译脚本

```
combuild {
 applicationName = 'com.soul.reader.runalone.application.ReaderApplication' （Application 入口）
 isRegisterCompoAuto = false （是否自动注入组件，否则可以用反射方式注入）
}
```


## 创建 runAlone 目录进行调试界面开发

研发 > Android 组件化使用文档 > image2019-3-20_13-0-3.png


## 选择要编译的组件运行调试
研发 > Android 组件化使用文档 > image2019-3-20_13-0-52.png


# 组件数据交互

组件之间的数据传输（交互）是通过接口+实现的方式来完成的，组件之间完全面向接口编程

## 组件声明提供的服务（接口）
为了增加可读性，所有组件的服务都统一定义lib.service这个module中 ，例如定义ReadBookService服务接口

```
public interface ReadBookService {
    Fragment getReadBookFragment();
}
```

## 组件提供具体实现类

在

```
public class ReadBookServiceImpl implements ReadBookService {
    @Override
    public Fragment getReadBookFragment() {
        return new ReaderFragment();
    }
}
```

## 将实现类注册到Router中
实现类的注册时机在每个组件的ApplicationLike中，ApplicationLike相当于每个组件的Application类，控制组件的生命周期。

在组件加载的时候进行注册，同时在组件卸载的时候进行反注册

```
public class ReaderAppLike implements IApplicationLike {
SoulService service = SoulService.getInstance();
    @Override
    public void onCreate() {
        service.addService(ReadBookService.class.getSimpleName(), new ReadBookServiceImpl());
    }

    @Override
    public void onStop() {
        service.removeService(ReadBookService.class.getSimpleName());
    }
}
```

## 其他组件调用服务
由于代码隔离，其他组件只能对ReadBookService可见，所以只能针对这个接口进行编程。

通过Router获取服务的具体实现，使用前需要判空。

```
SoulService service = SoulService.getInstance();
if (service.getService(ReadBookService.class.getSimpleName()) != null) {
    ReadBookService service = (ReadBookService) router.getService(ReadBookService.class.getSimpleName());
    fragment = service.getReadBookFragment();
    t = getSupportFragmentManager().beginTransaction();
    ft.add(R.id.tab_content, fragment).commitAllowingStateLoss();
}
```

这样就做到了接口和实现的分离，组件之间完全针对接口编程。

# 结语

代码都是想通的，互相学习互相进步。
