package utils;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;

/**
 * Created by hefei on 2017/5/14.
 */

public class FragmentManagerUtils {
    public static  void showFragment(Fragment parentFragment, Fragment fragment, int fragmentId){
        assert parentFragment != null && fragment != null;

        FragmentManager fm = parentFragment.getChildFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        if(fragment.getParentFragment() == null){
            ft.add(fragmentId,  fragment);
        }
        else {
            ft.show(fragment);
        }

        ft.commitAllowingStateLoss();
    }

    public static void removeFragment(Fragment parentFragment, Fragment fragment){
        assert parentFragment != null && fragment != null;

        FragmentManager fm = parentFragment.getChildFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        ft.remove(fragment);

        ft.commitAllowingStateLoss();
    }
}
