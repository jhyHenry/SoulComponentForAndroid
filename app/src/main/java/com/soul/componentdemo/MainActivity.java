package com.soul.componentdemo;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.soul.share.ShareBean;
import com.soul.share.service.ShareService;

import cn.soul.android.component.SoulRouter;


public class MainActivity extends AppCompatActivity {

    Fragment fragment;
    FragmentTransaction ft;

    Button installReadBookBtn;
    Button test_share;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
//        Toast.makeText(this, getResources().getStringArray(R.array.avatar_planetb)[0], 2000).show();
        installReadBookBtn = findViewById(R.id.install_share);
        test_share = findViewById(R.id.test_share);
        final ShareBean shareBean = new ShareBean("test");
        installReadBookBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SoulRouter.instance().route("/share/ShareActivity").withSerializable("shareBean", shareBean).navigate(MainActivity.this);
            }
        });

        test_share.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ShareService shareService = SoulRouter.instance().service(ShareService.class);
                if (shareService != null) {
                    Toast.makeText(MainActivity.this, shareService.getShareName(), Toast.LENGTH_SHORT).show();
                }
            }
        });

        showFragment();
    }

    private void showFragment() {
        if (fragment != null) {
            ft = getSupportFragmentManager().beginTransaction();
            ft.remove(fragment).commit();
            fragment = null;
        }


        ShareService shareService = SoulRouter.instance().service(ShareService.class);

        if (shareService != null) {
            fragment = shareService.getFragment();
            if (fragment != null) {
                ft = getSupportFragmentManager().beginTransaction();
                ft.add(R.id.tab_content, fragment).commitAllowingStateLoss();
            }
        }

//        Router router = Router.getInstance();
//        if (router.getService(ReadBookService.class.getSimpleName()) != null) {
//            ReadBookService service = (ReadBookService) router.getService(ReadBookService.class.getSimpleName());
//            fragment = service.getReadBookFragment();
//            ft = getSupportFragmentManager().beginTransaction();
//            ft.add(R.id.tab_content, fragment).commitAllowingStateLoss();
//        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (data != null) {
//            ToastManager.show(BaseApplication.getAppContext(), data.getStringExtra("result"));
        }
    }

}
