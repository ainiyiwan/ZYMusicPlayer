package com.zhimadai.cctvmall.zymusicplayer.application;

import android.app.Application;

/**
 * Author ： zhangyang
 * Date   ： 2017/10/16
 * Email  :  18610942105@163.com
 * Description  :
 */

public class MusicApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        AppCache.init(this);
    }
}


