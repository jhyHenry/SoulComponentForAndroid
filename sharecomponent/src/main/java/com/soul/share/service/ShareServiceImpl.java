package com.soul.share.service;

import android.content.Context;

import cn.soul.android.component.annotation.Router;

/**
 * Author   : wentao
 * Date     : 2020-07-20  13:19
 * Describe :
 */
@Router(path = "/share/ShareService")
public class ShareServiceImpl implements ShareService {
    @Override
    public String getShareName() {
        return "ShareServiceImpl test";
    }

    @Override
    public void init(Context context) {

    }
}
