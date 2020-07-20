package com.soul.share;

import java.io.Serializable;

import cn.soul.android.component.annotation.ClassExposed;

/**
 * Author   : wentao
 * Date     : 2020-07-20  12:39
 * Describe :
 */
@ClassExposed
public class ShareBean implements Serializable {
    public String shareName;

    public ShareBean(String shareName) {
        this.shareName = shareName;
    }
}
