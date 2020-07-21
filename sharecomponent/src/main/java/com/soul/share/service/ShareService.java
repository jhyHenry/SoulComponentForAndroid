package com.soul.share.service;

import androidx.fragment.app.Fragment;

import cn.soul.android.component.IComponentService;

/**
 * Author   : wentao
 * Date     : 2020-07-20  13:18
 * Describe :
 */
public interface ShareService extends IComponentService {
    String getShareName();

    Fragment getFragment();
}
