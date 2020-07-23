package com.soul.share;

import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import cn.soul.android.component.annotation.Inject;
import cn.soul.android.component.annotation.Router;

/**
 * Created by mrzhang on 2017/6/20.
 */
@Router(path = "/share/ShareActivity")
public class ShareActivity extends AppCompatActivity {

    @Inject
    String bookName;

    private TextView tvShareTitle;
    private TextView tvShareBook;
    private TextView tvAuthor;
    private TextView tvCounty;

    private final static int RESULT_CODE = 8888;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.share_activity_share);

        tvShareTitle = findViewById(R.id.share_title);
        tvShareBook = findViewById(R.id.share_tv_tag);
        tvAuthor = findViewById(R.id.share_tv_author);
        tvCounty = findViewById(R.id.share_tv_county);

        tvShareTitle.setText(R.string.app_name);

        if (bookName != null) {
            tvShareBook.setText(bookName);
        }
    }

}
