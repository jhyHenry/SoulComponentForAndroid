package com.soul.share.service;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.soul.share.R;

/**
 * Author   : walid
 * Date     : 2020-07-21  14:34
 * Describe :
 */
public class Fragment extends androidx.fragment.app.Fragment {
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.share_activity_share, null);
    }
}
