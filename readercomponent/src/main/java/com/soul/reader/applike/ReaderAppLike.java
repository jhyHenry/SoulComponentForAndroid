package com.soul.reader.applike;

import com.soul.component.componentlib.applicationlike.IApplicationLike;
import com.soul.component.componentlib.router.Router;
import com.soul.component.componentlib.router.ui.UIRouter;
import com.soul.componentservice.readerbook.ReadBookService;
import com.soul.reader.serviceimpl.ReadBookServiceImpl;

/**
 * Created by mrzhang on 2017/6/15.
 */

public class ReaderAppLike implements IApplicationLike {

    Router router = Router.getInstance();
    UIRouter uiRouter = UIRouter.getInstance();

    @Override
    public void onCreate() {
        uiRouter.registerUI("reader");
        router.addService(ReadBookService.class.getSimpleName(), new ReadBookServiceImpl());
//        router.addService(ReadBookService.class.getSimpleName(), new ReadBookServiceImplKotlin());
    }

    @Override
    public void onStop() {
        uiRouter.unregisterUI("reader");
        router.removeService(ReadBookService.class.getSimpleName());
    }
}
