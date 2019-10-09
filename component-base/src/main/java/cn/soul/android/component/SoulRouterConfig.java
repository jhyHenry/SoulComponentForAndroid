package cn.soul.android.component;

import android.app.Application;

/**
 * @author panxinghai
 * <p>
 * date : 2019-10-08 18:25
 */
public class SoulRouterConfig {
    Application context;
    SoulRouter.NavigateCallback navigateCallback;

    private SoulRouterConfig(Builder builder) {
        context = builder.mContext;
        navigateCallback = builder.mNavigateCallback;
    }

    public static class Builder {
        private Application mContext;
        private SoulRouter.NavigateCallback mNavigateCallback;

        public Builder(Application context) {
            mContext = context;
        }

        public Builder setNavigateCallback(SoulRouter.NavigateCallback navigateCallback) {
            mNavigateCallback = navigateCallback;
            return this;
        }

        public SoulRouterConfig build() {
            return new SoulRouterConfig(this);
        }
    }
}
