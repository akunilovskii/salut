package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;

import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.sharedutils.misc.WeakHashSet;
import com.liskovsoft.sharedutils.prefs.SharedPreferencesBase;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.service.SidebarService;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager.AccountChangeListener;

public class AppPrefs extends SharedPreferencesBase implements AccountChangeListener {
    private static final String TAG = AppPrefs.class.getSimpleName();
    private static final String PREFS_DIR = "app_prefs";
    @SuppressLint("StaticFieldLeak")
    private static AppPrefs sInstance;
    private static boolean sSharedSettingsEnabled;
    private static final String ANONYMOUS_PROFILE_NAME = "anonymous";
    private static final String MULTI_PROFILES = "multi_profiles";
    private static final String STATE_UPDATER_DATA = "state_updater_data";
    private static final String CHANNEL_GROUP_DATA = "channel_group_data";
    private static final String SIDEBAR_DATA = "sidebar_data";
    private static final String VIEW_MANAGER_DATA = "view_manager_data";
    private static final String WEB_PROXY_URI = "web_proxy_uri";
    private static final String WEB_PROXY_ENABLED = "web_proxy_enabled";
    private static final String LAST_PROFILE_NAME = "last_profile_name";
    private static final String SHARED_SETTINGS_MIGRATED = "phone_shared_settings_v1";
    private static final String SHARED_SIDEBAR_DATA = "phone_shared_sidebar_data";
    private static final String ACCOUNT_GENERAL_DATA = "phone_account_general_data";
    private static final String[] SHARED_PROFILE_KEYS = {
            SharedSettingsData.GENERAL_DATA, "main_ui_data", "video_player_data",
            "video_player_tweaks_data", "blocked_channel_data"
    };
    private String mBootResolution;
    private final WeakHashSet<ProfileChangeListener> mListeners = new WeakHashSet<>();

    public interface ProfileChangeListener {
        default void onProfileChanging() {
        }

        void onProfileChanged();
    }

    private AppPrefs(Context context) {
        //super(context, R.xml.app_prefs); // IndexOutOfBoundsException on Google Streamer
        super(context);

        if (sSharedSettingsEnabled) {
            migrateSharedSettings();
        }
        initProfiles();
    }

    private void initProfiles() {
        MediaServiceManager.instance().addAccountListener(this);
    }

    @Override
    public void onAccountChanged(Account account) {
        if (sSharedSettingsEnabled) {
            // Flush delayed changes while account-specific fields still belong to the old profile.
            mListeners.forEach(ProfileChangeListener::onProfileChanging);
        }
        selectProfile(account);
        onProfileChanged();
    }

    public static AppPrefs instance(Context context) {
        if (sInstance == null) {
            sInstance = new AppPrefs(context);
        }

        return sInstance;
    }

    public boolean isMultiProfilesEnabled() {
        return !sSharedSettingsEnabled && getBoolean(MULTI_PROFILES, false);
    }

    public void enableMultiProfiles(boolean enabled) {
        if (sSharedSettingsEnabled) {
            return;
        }
        if (isMultiProfilesEnabled() == enabled) {
            return;
        }

        putBoolean(MULTI_PROFILES, enabled);
        onProfileChanged();
        //selectAccount(enabled ? MediaServiceManager.instance().getSelectedAccount() : null);
    }

    public String getBootResolution() {
        return mBootResolution;
    }

    public void setBootResolution(String resolution) {
        mBootResolution = resolution;
    }

    public String getStateUpdaterData() {
        // Always use multiple profiles for the history
        return getData(getProfileKey(STATE_UPDATER_DATA, true));
    }

    public void setStateUpdaterData(String data) {
        // Always use multiple profiles for the history
        setData(getProfileKey(STATE_UPDATER_DATA, true), data);
    }

    public String getChannelGroupData() {
        // Always use multiple profiles
        return getData(getProfileKey(CHANNEL_GROUP_DATA,  true));
    }

    public void setChannelGroupData(String data) {
        // Always use multiple profiles
        setData(getProfileKey(CHANNEL_GROUP_DATA,  true), data);
    }

    public String getSidebarData() {
        if (sSharedSettingsEnabled) {
            return SharedSettingsData.sidebarWithAccountContent(getData(SHARED_SIDEBAR_DATA),
                    getData(getProfileKey(SIDEBAR_DATA, true)));
        }
        // Always use multiple profiles
        return getData(getProfileKey(SIDEBAR_DATA,  true));
    }

    public void setSidebarData(String data) {
        if (sSharedSettingsEnabled) {
            setData(SHARED_SIDEBAR_DATA, SharedSettingsData.sidebarSettings(data));
        }
        // Always use multiple profiles
        setData(getProfileKey(SIDEBAR_DATA,  true), data);
    }

    public String getProfileData(String key) {
        if (sSharedSettingsEnabled && SharedSettingsData.GENERAL_DATA.equals(key)) {
            return SharedSettingsData.generalWithAccountState(getData(key), getGeneralAccountState());
        }
        return getData(getProfileKey(key, isMultiProfilesEnabled()));
    }

    public void setProfileData(String key, String data) {
        if (sSharedSettingsEnabled && SharedSettingsData.GENERAL_DATA.equals(key)) {
            setData(generalAccountKey(), SharedSettingsData.generalAccountState(data));
            setData(key, SharedSettingsData.generalSettings(data));
            return;
        }
        setData(getProfileKey(key, isMultiProfilesEnabled()), data);
    }

    /** Set by the phone Application before any preference singleton is created. */
    public static void setSharedSettingsEnabled(boolean enabled) {
        sSharedSettingsEnabled = enabled;
    }

    public static boolean isSharedSettingsEnabled() {
        return sSharedSettingsEnabled;
    }

    private void migrateSharedSettings() {
        if (getBoolean(SHARED_SETTINGS_MIGRATED, false)) {
            return;
        }
        boolean separateProfiles = getBoolean(MULTI_PROFILES, false);
        for (String key : SHARED_PROFILE_KEYS) {
            String data = getData(getProfileKey(key, separateProfiles));
            if (SharedSettingsData.GENERAL_DATA.equals(key)) {
                if (getData(generalAccountKey()) == null) {
                    setData(generalAccountKey(), SharedSettingsData.generalAccountState(data));
                }
                data = SharedSettingsData.generalSettings(data);
            }
            // An unset active-profile value must retain its default, not an old global value.
            setData(key, data != null ? data : "");
        }
        setData(SHARED_SIDEBAR_DATA, SharedSettingsData.sidebarSettings(
                getData(getProfileKey(SIDEBAR_DATA, true))));
        putBoolean(SHARED_SETTINGS_MIGRATED, true);
        putBoolean(MULTI_PROFILES, false);
    }

    private String getGeneralAccountState() {
        String key = generalAccountKey();
        String data = getData(key);
        if (data == null) {
            // Other profiles keep their old content, but never inherit the shared baseline's content.
            String profileName = getProfileName();
            String legacy = TextUtils.isEmpty(profileName) ? null :
                    getData(getProfileKey(SharedSettingsData.GENERAL_DATA, true));
            data = SharedSettingsData.generalAccountState(legacy);
            setData(key, data);
        }
        return data;
    }

    private String generalAccountKey() {
        String profileName = getProfileName();
        return (TextUtils.isEmpty(profileName) ? ANONYMOUS_PROFILE_NAME : profileName) + "_" + ACCOUNT_GENERAL_DATA;
    }

    //public String getData(String key) {
    //    // Don't sync hash here. Hashes won't match.
    //    return getString(key, null);
    //}
    //
    //public void setData(String key, String data) {
    //    if (checkData(key, data)) {
    //        putString(key, data);
    //    }
    //}

    public String getWebProxyUri() {
        return getString(WEB_PROXY_URI, "");
    }

    public void setWebProxyUri(String uri) {
        putString(WEB_PROXY_URI, uri);
    }

    public boolean isWebProxyEnabled() {
        return getBoolean(WEB_PROXY_ENABLED, false);
    }

    public void setWebProxyEnabled(boolean enabled) {
        putBoolean(WEB_PROXY_ENABLED, enabled);
    }

    private String getProfileName() {
        return getString(LAST_PROFILE_NAME, null);
    }

    private void setProfileName(String profileName) {
        putString(LAST_PROFILE_NAME, profileName);
    }

    private void selectProfile(Account account) {
        String profileName = account != null && account.getName() != null ? account.getName().replace(" ", "_") : ANONYMOUS_PROFILE_NAME;

        setProfileName(profileName);
    }

    private void onProfileChanged() {
        mListeners.forEach(ProfileChangeListener::onProfileChanged);
    }

    public void addListener(ProfileChangeListener listener) {
        if (!mListeners.contains(listener)) {
            if (listener instanceof GeneralData) {
                mListeners.add(0, listener); // data classes should be called before regular listeners
            } else if (listener instanceof SidebarService) {
                mListeners.add(mListeners.isEmpty() ? 0 : 1, listener); // data classes should be called before regular listeners
            } else {
                mListeners.add(listener);
            }
        }
    }

    public void removeListener(ProfileChangeListener listener) {
        mListeners.remove(listener);
    }

    //private String getProfileKey(String key) {
    //    String profileName = getProfileName();
    //    if (!TextUtils.isEmpty(profileName)) {
    //        key = profileName + "_" + key;
    //    }
    //
    //    return key;
    //}

    private String getProfileKey(String key, boolean isMultiProfilesEnabled) {
        String profileName = getProfileName();
        if (!TextUtils.isEmpty(profileName) && isMultiProfilesEnabled) {
            key = profileName + "_" + key;
        }

        return key;
    }
    
    @Override
    protected String getPrefsDir() {
        return PREFS_DIR;
    }
}
