package com.soul.reader.serviceimpl

import android.support.v4.app.Fragment
import com.soul.componentservice.readerbook.ReadBookService
import com.soul.reader.ReaderFragment

/**
 * Created by mrzhang on 2018/2/9.
 */
class ReadBookServiceImplKotlin : ReadBookService {
    override fun getReadBookFragment(): Fragment {
        return ReaderFragment()
    }
}
